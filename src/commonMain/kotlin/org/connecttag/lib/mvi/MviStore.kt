package org.connecttag.lib.mvi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The single source of truth for PlatformTag MVI state transitions.
 *
 * Actions are queued and processed sequentially:
 * validate -> reduce -> middleware -> optional nested dispatch/effect.
 */
class MviStore<State : MviState, Action : MviAction, Effect : MviEffect>(
    initialState: State,
    private val scope: CoroutineScope,
    private val reducer: (State, Action) -> State,
    private val middleware: List<MviMiddleware<State, Action, Effect>> = emptyList(),
    private val validators: List<ActionValidator<Action>> = emptyList(),
    private val validationFailureEffect: ((ActionValidationResult.Rejected, Action) -> Effect?)? = null,
    private val actionPolicyResolver: ActionPolicyResolver<Action> = ActionPolicyResolver {
        ActionPolicy()
    },
    private val config: MviStoreConfig = MviStoreConfig(),
    private val logger: MviLogger = MviLogger.None,
    private val errorHandler: MviStoreErrorHandler = MviStoreErrorHandler.None,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : MviContainer<State, Action, Effect> {

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<State> = _state.asStateFlow()

    private val actionQueue = Channel<QueuedAction<Action>>(config.actionBufferCapacity)
    private val effectQueue = Channel<Effect>(config.effectBufferCapacity)
    override val effect: Flow<Effect> = effectQueue.receiveAsFlow()

    private var nextActionId: Long = 0L
    private var nextPolicyGeneration: Long = 0L
    private val reservedPolicyKeys = mutableSetOf<String>()
    private val latestReplaceGenerationByKey = mutableMapOf<String, Long>()
    private val latestDebounceGenerationByKey = mutableMapOf<String, Long>()
    private val debounceJobsByKey = mutableMapOf<String, Job>()
    private val lastThrottleMarkByKey = mutableMapOf<String, TimeMark>()

    private val dispatcher = ActionDispatcher<Action> { action -> dispatch(action) }
    private val effects = EffectEmitter<Effect> { effect -> emitEffect(effect) }

    init {
        scope.launch {
            processActions()
        }
    }

    override fun onAction(action: Action) {
        dispatch(action)
    }

    fun dispatch(action: Action): Boolean {
        val policy = actionPolicyResolver.resolve(action)
        val policyKey = actionPolicyKey(action, policy)
        return when (policy.strategy) {
            ActionExecutionStrategy.Sequential -> enqueue(action, policy, policyKey)
            ActionExecutionStrategy.DropIfRunning -> dispatchDropIfRunning(action, policy, policyKey)
            ActionExecutionStrategy.ReplaceRunning -> dispatchReplace(action, policy, policyKey)
            ActionExecutionStrategy.Debounce -> dispatchDebounce(action, policy, policyKey)
            ActionExecutionStrategy.Throttle -> dispatchThrottle(action, policy, policyKey)
        }
    }

    private fun enqueue(
        action: Action,
        policy: ActionPolicy,
        policyKey: String,
        generation: Long = 0L,
        releaseReservedKey: Boolean = false,
    ): Boolean {
        val result = actionQueue.trySend(
            QueuedAction(
                action = action,
                policy = policy,
                policyKey = policyKey,
                generation = generation,
                releaseReservedKey = releaseReservedKey,
            ),
        )
        if (result.isFailure) {
            logger.log("MVI action delivery failed: ${action::class.simpleName}")
            if (releaseReservedKey) reservedPolicyKeys.remove(policyKey)
            result.exceptionOrNull()?.let { error ->
                errorHandler.onError(MviPipelineStage.ActionDelivery, action, error)
            }
        }
        return result.isSuccess
    }

    private fun dispatchDropIfRunning(action: Action, policy: ActionPolicy, policyKey: String): Boolean {
        if (!reservedPolicyKeys.add(policyKey)) {
            logger.log("MVI action dropped while running: ${action::class.simpleName}")
            return false
        }
        return enqueue(action, policy, policyKey, releaseReservedKey = true)
    }

    private fun dispatchReplace(action: Action, policy: ActionPolicy, policyKey: String): Boolean {
        val generation = nextPolicyGeneration++
        latestReplaceGenerationByKey[policyKey] = generation
        return enqueue(action, policy, policyKey, generation = generation)
    }

    private fun dispatchDebounce(action: Action, policy: ActionPolicy, policyKey: String): Boolean {
        val generation = nextPolicyGeneration++
        latestDebounceGenerationByKey[policyKey] = generation
        debounceJobsByKey.remove(policyKey)?.cancel()
        debounceJobsByKey[policyKey] = scope.launch {
            if (policy.window > ZERO) delay(policy.window)
            if (latestDebounceGenerationByKey[policyKey] == generation) {
                enqueue(action, policy, policyKey, generation = generation)
            }
        }
        return true
    }

    private fun dispatchThrottle(action: Action, policy: ActionPolicy, policyKey: String): Boolean {
        val lastMark = lastThrottleMarkByKey[policyKey]
        if (lastMark != null && policy.window > ZERO && lastMark.elapsedNow() < policy.window) {
            logger.log("MVI action throttled: ${action::class.simpleName}")
            return false
        }
        lastThrottleMarkByKey[policyKey] = timeSource.markNow()
        return enqueue(action, policy, policyKey)
    }

    fun emitEffect(effect: Effect): Boolean {
        val result = effectQueue.trySend(effect)
        if (result.isFailure) {
            logger.log("MVI effect delivery failed: ${effect::class.simpleName}")
            result.exceptionOrNull()?.let { error ->
                errorHandler.onError(MviPipelineStage.EffectDelivery, null, error)
            }
        }
        return result.isSuccess
    }

    /**
     * Applies an internal state reduction without manufacturing a UI action.
     *
     * UI actions remain the input to the validated action pipeline. This
     * method is for state produced by an already-running use case or
     * middleware and keeps that state in the same store source of truth.
     */
    fun reduceState(transform: (State) -> State) {
        try {
            _state.update(transform)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            errorHandler.onError(MviPipelineStage.Reducer, null, error)
        }
    }

    private suspend fun processActions() {
        for (queuedAction in actionQueue) {
            try {
                processQueuedAction(queuedAction)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                errorHandler.onError(MviPipelineStage.Middleware, queuedAction.action, error)
            }
        }
    }

    private suspend fun processQueuedAction(queuedAction: QueuedAction<Action>) {
        try {
            if (!queuedAction.isLatest()) return
            processAction(queuedAction.action)
        } finally {
            if (queuedAction.releaseReservedKey) {
                reservedPolicyKeys.remove(queuedAction.policyKey)
            }
        }
    }

    private suspend fun processAction(action: Action) {
        val validation = validate(action)
        if (validation is ActionValidationResult.Rejected) {
            validationFailureEffect?.invoke(validation, action)?.let { emitEffect(it) }
            logger.log("MVI action rejected: ${action::class.simpleName} (${validation.messageKey})")
            return
        }

        val previousState = _state.value
        val currentState = reduce(previousState, action) ?: return
        _state.value = currentState

        val context = MviActionContext(
            actionId = nextActionId++,
            action = action,
            previousState = previousState,
            currentState = currentState,
        )

        for (middlewareItem in middleware) {
            try {
                middlewareItem.process(context, dispatcher, effects)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                errorHandler.onError(MviPipelineStage.Middleware, action, error)
            }
        }
    }

    private fun validate(action: Action): ActionValidationResult {
        for (validator in validators) {
            val result = try {
                validator.validate(action)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                errorHandler.onError(MviPipelineStage.Validation, action, error)
                ActionValidationResult.Rejected(messageKey = "mvi.validation.failed")
            }
            if (result is ActionValidationResult.Rejected) return result
        }
        return ActionValidationResult.Accepted
    }

    private fun reduce(previousState: State, action: Action): State? {
        return try {
            reducer(previousState, action)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            errorHandler.onError(MviPipelineStage.Reducer, action, error)
            null
        }
    }

    private fun QueuedAction<Action>.isLatest(): Boolean {
        return when (policy.strategy) {
            ActionExecutionStrategy.ReplaceRunning ->
                latestReplaceGenerationByKey[policyKey] == generation
            ActionExecutionStrategy.Debounce ->
                latestDebounceGenerationByKey[policyKey] == generation
            ActionExecutionStrategy.Sequential,
            ActionExecutionStrategy.DropIfRunning,
            ActionExecutionStrategy.Throttle -> true
        }
    }

    private fun actionPolicyKey(action: Action, policy: ActionPolicy): String {
        return policy.key ?: action::class.simpleName ?: action::class.toString()
    }

    private data class QueuedAction<Action : MviAction>(
        val action: Action,
        val policy: ActionPolicy,
        val policyKey: String,
        val generation: Long,
        val releaseReservedKey: Boolean,
    )
}

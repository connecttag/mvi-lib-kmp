package org.connecttag.lib.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.connecttag.lib.coroutine.DefaultDispatcherProvider
import org.connecttag.lib.coroutine.DispatcherProvider
import org.connecttag.lib.uimodel.PageState
import org.connecttag.lib.uimodel.asPageStateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Shared KMP ViewModel adapter for PlatformTag MVI.
 *
 * The state pipeline belongs to [MviStore]. This class owns lifecycle
 * wiring and coroutine helpers only.
 */
abstract class BaseMviViewModel<State : MviState, Action : MviAction, Effect : MviEffect>(
    initialState: State,
    reducer: (State, Action) -> State,
    middleware: List<MviMiddleware<State, Action, Effect>> = emptyList(),
    validators: List<ActionValidator<Action>> = emptyList(),
    validationFailureEffect: ((ActionValidationResult.Rejected, Action) -> Effect?)? = null,
    actionPolicyResolver: ActionPolicyResolver<Action> = ActionPolicyResolver {
        ActionPolicy()
    },
    statePersistence: MviStatePersistence<State>? = null,
    config: MviStoreConfig = MviStoreConfig(),
    protected val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    logger: MviLogger = MviLogger.None,
    errorHandler: MviStoreErrorHandler = MviStoreErrorHandler.None,
) : ViewModel(), MviContainer<State, Action, Effect> {

    private val store = MviStore(
        initialState = statePersistence?.restore() ?: initialState,
        scope = viewModelScope,
        reducer = reducer,
        middleware = middleware,
        validators = validators,
        validationFailureEffect = validationFailureEffect,
        actionPolicyResolver = actionPolicyResolver,
        config = config,
        logger = logger,
        errorHandler = errorHandler,
    )

    init {
        if (statePersistence != null) {
            viewModelScope.launch {
                store.state.collect { statePersistence.save(it) }
            }
        }
    }

    override val state: StateFlow<State> = store.state
    val uiState: StateFlow<State> = state

    override val effect: Flow<Effect> = store.effect
    val uiEffect: Flow<Effect> = effect

    protected val currentState: State get() = state.value

    override fun onAction(action: Action) {
        store.onAction(action)
    }

    protected fun dispatch(action: Action): Boolean = store.dispatch(action)

    protected fun sendEffect(effect: Effect): Boolean = store.emitEffect(effect)

    protected fun launch(
        dispatcher: CoroutineDispatcher = dispatchers.main,
        block: suspend () -> Unit,
    ): Job = viewModelScope.launch(dispatcher) {
        block()
    }

    protected fun launchMain(block: suspend () -> Unit): Job = launch(dispatchers.main, block)
    protected fun launchIo(block: suspend () -> Unit): Job = launch(dispatchers.io, block)
    protected fun launchDefault(block: suspend () -> Unit): Job = launch(dispatchers.default, block)

    protected fun <T> loadPage(
        flow: Flow<Result<T>>,
        onResult: (PageState<T>) -> Unit,
    ) {
        launch {
            flow.asPageStateFlow().collect { onResult(it) }
        }
    }

    /**
     * Shared internal state update helper backed by the same MVI store.
     *
     * Subclasses must not add synthetic StateChanged actions merely to update
     * state after an already-running operation.
     */
    protected fun updateState(reduce: (State) -> State) {
        store.reduceState(reduce)
    }
}

package org.connecttag.lib.mvi

import kotlinx.coroutines.channels.Channel

/**
 * Configuration for the store pipeline.
 */
data class MviStoreConfig(
    val actionBufferCapacity: Int = Channel.BUFFERED,
    val effectBufferCapacity: Int = Channel.BUFFERED,
)

enum class MviPipelineStage {
    Validation,
    Reducer,
    Middleware,
    EffectDelivery,
    ActionDelivery,
}

fun interface MviLogger {
    fun log(message: String)

    companion object {
        val None = MviLogger { }
    }
}

fun interface MviStoreErrorHandler {
    fun onError(stage: MviPipelineStage, action: MviAction?, error: Throwable)

    companion object {
        val None = MviStoreErrorHandler { _, _, _ -> }
    }
}

sealed interface ActionValidationResult {
    data object Accepted : ActionValidationResult
    data class Rejected(
        val messageKey: String,
        val code: String? = null,
    ) : ActionValidationResult
}

fun interface ActionValidator<Action : MviAction> {
    fun validate(action: Action): ActionValidationResult
}

fun interface ActionDispatcher<Action : MviAction> {
    fun dispatch(action: Action): Boolean
}

fun interface EffectEmitter<Effect : MviEffect> {
    fun emit(effect: Effect): Boolean
}

data class MviActionContext<State : MviState, Action : MviAction>(
    val actionId: Long,
    val action: Action,
    val previousState: State,
    val currentState: State,
    val metadata: Map<String, String> = emptyMap(),
)

fun interface MviReducer<State : MviState, Action : MviAction> {
    fun reduce(state: State, action: Action): State
}

/**
 * Middleware for side effects that run after the reducer has produced the new state.
 */
fun interface MviMiddleware<State : MviState, Action : MviAction, Effect : MviEffect> {
    suspend fun process(
        context: MviActionContext<State, Action>,
        dispatcher: ActionDispatcher<Action>,
        effects: EffectEmitter<Effect>,
    )
}

package org.connecttag.lib.mvi

/**
 * A middleware that logs every action for debugging purposes.
 */
class LoggingMiddleware<S : MviState, A : MviAction, E : MviEffect>(
    private val logger: MviLogger,
    private val masker: MviValueMasker = DefaultMviValueMasker,
) : MviMiddleware<S, A, E> {
    constructor(logger: (String) -> Unit) : this(MviLogger { message -> logger(message) })

    override suspend fun process(
        context: MviActionContext<S, A>,
        dispatcher: ActionDispatcher<A>,
        effects: EffectEmitter<E>,
    ) {
        logger.log(
            "MVI Action #${context.actionId}: " +
                "${masker.mask(context.action)} | State: ${masker.mask(context.currentState)}",
        )
    }
}

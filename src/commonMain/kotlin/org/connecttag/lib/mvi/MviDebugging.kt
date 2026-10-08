package org.connecttag.lib.mvi

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class SensitiveData

interface MaskableForLogging {
    fun maskedForLogging(): String
}

fun interface MviValueMasker {
    fun mask(value: Any?): String
}

object DefaultMviValueMasker : MviValueMasker {
    override fun mask(value: Any?): String {
        return when (value) {
            null -> "null"
            is MaskableForLogging -> value.maskedForLogging()
            is CharSequence -> "***"
            else -> value::class.simpleName ?: "value"
        }
    }
}

class PrivacyMaskingLogger(
    private val delegate: MviLogger,
    private val masker: MviValueMasker = DefaultMviValueMasker,
) : MviLogger {
    override fun log(message: String) {
        delegate.log(masker.mask(message))
    }
}

data class MviDebugEvent(
    val actionId: Long,
    val action: String,
    val previousState: String,
    val currentState: String,
)

class MviDebugEventRecorder(
    private val maxEvents: Int = 100,
    private val masker: MviValueMasker = DefaultMviValueMasker,
) {
    private val events = ArrayDeque<MviDebugEvent>()

    init {
        require(maxEvents > 0) { "maxEvents must be greater than zero." }
    }

    fun record(context: MviActionContext<*, *>) {
        if (events.size == maxEvents) events.removeFirst()
        events.addLast(
            MviDebugEvent(
                actionId = context.actionId,
                action = masker.mask(context.action),
                previousState = masker.mask(context.previousState),
                currentState = masker.mask(context.currentState),
            ),
        )
    }

    fun snapshot(): List<MviDebugEvent> = events.toList()

    fun clear() {
        events.clear()
    }
}

class DebugRecordingMiddleware<State : MviState, Action : MviAction, Effect : MviEffect>(
    private val recorder: MviDebugEventRecorder,
) : MviMiddleware<State, Action, Effect> {
    override suspend fun process(
        context: MviActionContext<State, Action>,
        dispatcher: ActionDispatcher<Action>,
        effects: EffectEmitter<Effect>,
    ) {
        recorder.record(context)
    }
}

class MviReplayEngine<State : MviState, Action : MviAction>(
    private val initialState: State,
    private val reducer: (State, Action) -> State,
) {
    fun replay(actions: Iterable<Action>): State {
        return actions.fold(initialState) { state, action -> reducer(state, action) }
    }
}

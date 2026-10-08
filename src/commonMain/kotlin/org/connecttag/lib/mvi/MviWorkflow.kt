package org.connecttag.lib.mvi

interface MviSaga<State : MviState, Action : MviAction, Effect : MviEffect> {
    suspend fun run(
        context: MviActionContext<State, Action>,
        dispatcher: ActionDispatcher<Action>,
        effects: EffectEmitter<Effect>,
    )
}

class SagaMiddleware<State : MviState, Action : MviAction, Effect : MviEffect>(
    private val sagas: List<MviSaga<State, Action, Effect>>,
) : MviMiddleware<State, Action, Effect> {
    override suspend fun process(
        context: MviActionContext<State, Action>,
        dispatcher: ActionDispatcher<Action>,
        effects: EffectEmitter<Effect>,
    ) {
        for (saga in sagas) {
            saga.run(context, dispatcher, effects)
        }
    }
}

interface OptimisticAction : MviAction {
    val optimisticKey: String
}

class BoundedStateHistory<State : MviState>(
    private val capacity: Int = 20,
) {
    private val undoStack = ArrayDeque<State>()
    private val redoStack = ArrayDeque<State>()

    init {
        require(capacity > 0) { "capacity must be greater than zero." }
    }

    fun record(state: State) {
        if (undoStack.size == capacity) undoStack.removeFirst()
        undoStack.addLast(state)
        redoStack.clear()
    }

    fun undo(currentState: State): State? {
        if (undoStack.isEmpty()) return null
        val previous = undoStack.removeLast()
        redoStack.addLast(currentState)
        return previous
    }

    fun redo(currentState: State): State? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(currentState)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

class ChildStoreRegistry {
    private val stores = mutableMapOf<String, MviContainer<*, *, *>>()

    fun register(key: String, store: MviContainer<*, *, *>) {
        require(key.isNotBlank()) { "Child store key must not be blank." }
        stores[key] = store
    }

    fun unregister(key: String) {
        stores.remove(key)
    }

    fun get(key: String): MviContainer<*, *, *>? = stores[key]

    fun keys(): Set<String> = stores.keys.toSet()
}

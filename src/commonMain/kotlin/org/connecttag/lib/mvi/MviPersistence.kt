package org.connecttag.lib.mvi

interface MviStateSerializer<State : MviState> {
    fun serialize(state: State): String
    fun deserialize(serialized: String): State
}

interface MviStatePersistence<State : MviState> {
    fun restore(): State?
    fun save(state: State)
    fun clear()
}

class InMemoryMviStatePersistence<State : MviState>(
    private val serializer: MviStateSerializer<State>,
) : MviStatePersistence<State> {

    private var snapshot: String? = null

    override fun restore(): State? = snapshot?.let(serializer::deserialize)

    override fun save(state: State) {
        snapshot = serializer.serialize(state)
    }

    override fun clear() {
        snapshot = null
    }
}

data class StateSnapshotPolicy(
    val maxSnapshots: Int = 20,
) {
    init {
        require(maxSnapshots > 0) { "maxSnapshots must be greater than zero." }
    }
}

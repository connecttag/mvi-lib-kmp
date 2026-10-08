package org.connecttag.lib.mvi.android

import androidx.lifecycle.SavedStateHandle
import org.connecttag.lib.mvi.MviState
import org.connecttag.lib.mvi.MviStatePersistence
import org.connecttag.lib.mvi.MviStateSerializer

class SavedStateMviStatePersistence<State : MviState>(
    private val savedStateHandle: SavedStateHandle,
    private val key: String,
    private val serializer: MviStateSerializer<State>,
) : MviStatePersistence<State> {

    override fun restore(): State? {
        return savedStateHandle.get<String>(key)?.let(serializer::deserialize)
    }

    override fun save(state: State) {
        savedStateHandle[key] = serializer.serialize(state)
    }

    override fun clear() {
        savedStateHandle.remove<String>(key)
    }
}

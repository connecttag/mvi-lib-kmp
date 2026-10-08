package org.connecttag.lib.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.connecttag.lib.coroutine.DefaultDispatcherProvider
import org.connecttag.lib.coroutine.DispatcherProvider
import org.connecttag.lib.uimodel.PageState
import org.connecttag.lib.uimodel.asPageStateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lifecycle state holder for screens that do not have a real action-reducer
 * pipeline. It prevents synthetic StateChanged actions while retaining the
 * same state/effect and coroutine surface used by MVI screens.
 */
abstract class BaseStateViewModel<State : MviState, Effect : MviEffect>(
    initialState: State,
    protected val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<State> = mutableState.asStateFlow()
    val uiState: StateFlow<State> = state

    private val effectChannel = Channel<Effect>(Channel.BUFFERED)
    val effect: Flow<Effect> = effectChannel.receiveAsFlow()
    val uiEffect: Flow<Effect> = effect

    protected val currentState: State
        get() = state.value

    protected fun updateState(reduce: (State) -> State) {
        mutableState.update(reduce)
    }

    protected fun sendEffect(effect: Effect): Boolean {
        return effectChannel.trySend(effect).isSuccess
    }

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
            flow.asPageStateFlow().collect(onResult)
        }
    }
}

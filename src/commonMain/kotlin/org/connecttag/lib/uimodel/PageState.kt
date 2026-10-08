package org.connecttag.lib.uimodel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

data class PageState<out T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val hasData: Boolean get() = data != null
    val hasError: Boolean get() = error != null

    companion object {
        fun <T> loading(): PageState<T> = PageState(isLoading = true)
        fun <T> success(data: T): PageState<T> = PageState(data = data)
        fun <T> error(message: String): PageState<T> = PageState(error = message)
        fun error(throwable: Throwable): PageState<Nothing> =
            PageState(error = throwable.message ?: throwable.toString())
    }
}

fun <T> Result<T>.asPageState(): PageState<T> {
    return fold(
        onSuccess = { PageState.success(it) },
        onFailure = { PageState.error(it) }
    )
}

fun <T> Flow<Result<T>>.asPageStateFlow(): Flow<PageState<T>> {
    return this.map { it.asPageState() }
        .onStart { emit(PageState.loading()) }
}

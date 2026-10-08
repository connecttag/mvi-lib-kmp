package org.connecttag.lib.base

import kotlinx.coroutines.flow.Flow

public interface IParams

/**
 * Standard Clean Architecture UseCase contract for Kotlin Multiplatform.
 */
public interface BaseUseCase<T : Any, R : Any> {
    public suspend operator fun invoke(param: T): Flow<R>
}

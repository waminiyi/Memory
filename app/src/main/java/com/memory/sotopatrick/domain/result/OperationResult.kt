package com.memory.sotopatrick.domain.result

import com.memory.sotopatrick.domain.error.AppError

sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>
    data class Failure(val error: AppError) : OperationResult<Nothing>
}

inline fun <T, R> OperationResult<T>.map(transform: (T) -> R): OperationResult<R> =
    when (this) {
        is OperationResult.Success -> OperationResult.Success(transform(value))
        is OperationResult.Failure -> this
    }

inline fun <T, R> OperationResult<T>.flatMap(
    transform: (T) -> OperationResult<R>
): OperationResult<R> = when (this) {
    is OperationResult.Success -> transform(value)
    is OperationResult.Failure -> this
}

inline fun <T> OperationResult<T>.onSuccess(
    action: (T) -> Unit
): OperationResult<T> {
    if (this is OperationResult.Success) action(value)
    return this
}

inline fun <T> OperationResult<T>.onFailure(
    action: (AppError) -> Unit
): OperationResult<T> {
    if (this is OperationResult.Failure) action(error)
    return this
}

inline fun <T> OperationResult<T>.mapError(
    transform: (AppError) -> AppError
): OperationResult<T> = when (this) {
    is OperationResult.Success -> this
    is OperationResult.Failure -> OperationResult.Failure(transform(error))
}

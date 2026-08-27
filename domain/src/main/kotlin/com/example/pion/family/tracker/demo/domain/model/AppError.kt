package com.example.pion.family.tracker.demo.domain.model

/**
 * A domain-level failure. Every repository / use case that can fail returns [AppResult],
 * never throws across a module boundary.
 */
sealed interface AppError {
    data class Unexpected(val message: String?) : AppError
    data class Network(val message: String?) : AppError
    data class NotFound(val message: String?) : AppError
    data class Validation(val message: String?) : AppError
}

/** Result of an operation that can fail with an [AppError]. */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

package ru.homebudget.finkeeper.data.repository

/**
 * Обёртка для результатов операций с данными
 * Позволяет обрабатывать успешные и неуспешные результаты
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    object Loading : Result<Nothing>()

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    val isLoading: Boolean
        get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
        is Loading -> throw IllegalStateException("Result is still loading")
    }

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(exception)
        is Loading -> Loading
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(exception: Throwable): Result<Nothing> = Error(exception)
        fun loading(): Result<Nothing> = Loading
    }
}

/**
 * Расширения для удобной работы с Result
 */
suspend fun <T> Result<T>.onSuccess(action: suspend (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

suspend fun <T> Result<T>.onError(action: suspend (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(exception)
    }
    return this
}

suspend fun <T> Result<T>.onLoading(action: suspend () -> Unit): Result<T> {
    if (this is Result.Loading) {
        action()
    }
    return this
}

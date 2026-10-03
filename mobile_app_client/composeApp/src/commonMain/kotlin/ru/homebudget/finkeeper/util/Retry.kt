package ru.homebudget.finkeeper.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import ru.homebudget.finkeeper.data.network.isTimeoutOrUnresolvable

data class RetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMillis: Long = 300,
    val maxDelayMillis: Long = 2_000
)

/**
 * Не повторяем отмену корутины и таймауты/недоступный адрес: на «зависшей» сети каждая попытка
 * стоит полный таймаут, и ретраи лишь умножают ожидание. Обрывы соединения и 5xx — повторяем.
 */
fun isRetriableByDefault(throwable: Throwable): Boolean =
    throwable !is CancellationException && !throwable.isTimeoutOrUnresolvable()

suspend fun <T> withRetry(
    config: RetryConfig = RetryConfig(),
    shouldRetry: (Throwable) -> Boolean = ::isRetriableByDefault,
    block: suspend () -> T
): T {
    var attempt = 0
    var lastError: Throwable? = null

    while (attempt < config.maxAttempts) {
        try {
            return block()
        } catch (throwable: Throwable) {
            lastError = throwable
            attempt += 1
            if (attempt >= config.maxAttempts || !shouldRetry(throwable)) {
                throw throwable
            }
            val delayMs = computeDelayMillis(
                attempt = attempt,
                baseDelayMillis = config.baseDelayMillis,
                maxDelayMillis = config.maxDelayMillis
            )
            delay(delayMs)
        }
    }

    throw lastError ?: IllegalStateException("Retry failed without exception")
}

private fun computeDelayMillis(
    attempt: Int,
    baseDelayMillis: Long,
    maxDelayMillis: Long
): Long {
    val multiplier = 1L shl (attempt - 1)
    val delayValue = baseDelayMillis * multiplier
    return if (delayValue > maxDelayMillis) maxDelayMillis else delayValue
}

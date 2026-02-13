package ru.homebudget.finkeeper.util

import kotlinx.coroutines.delay

data class RetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMillis: Long = 300,
    val maxDelayMillis: Long = 2_000
)

suspend fun <T> withRetry(
    config: RetryConfig = RetryConfig(),
    shouldRetry: (Throwable) -> Boolean = { true },
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

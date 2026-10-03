package ru.homebudget.finkeeper.util

import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryTest {
    @Test
    fun timeoutIsNotRetried() = runTest {
        var calls = 0

        assertFailsWith<HttpRequestTimeoutException> {
            withRetry { calls++; throw HttpRequestTimeoutException("https://x/api", 30_000) }
        }

        assertEquals(1, calls)
        assertEquals(0L, currentTime) // без пауз между попытками
    }

    @Test
    fun cancellationIsNotRetried() = runTest {
        var calls = 0

        assertFailsWith<CancellationException> {
            withRetry { calls++; throw CancellationException("cancelled") }
        }

        assertEquals(1, calls)
    }

    @Test
    fun connectionResetIsStillRetried() = runTest {
        var calls = 0

        val result = withRetry {
            calls++
            if (calls < 3) throw IOException("connection reset")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun explicitPolicyOverridesDefault() = runTest {
        var calls = 0

        assertFailsWith<HttpRequestTimeoutException> {
            withRetry(shouldRetry = { true }) { calls++; throw HttpRequestTimeoutException("https://x/api", 1) }
        }

        assertEquals(3, calls)
    }
}

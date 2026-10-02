package ru.homebudget.finkeeper.data.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import ru.homebudget.finkeeper.data.remote.ApiException
import ru.homebudget.finkeeper.data.repository.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerReachabilityTest {
    private val requestTimeout = HttpRequestTimeoutException("https://x/api", 30_000)

    @Test
    fun timeoutsAndIoErrorsAreConnectivityFailures() {
        assertTrue(requestTimeout.isConnectivityFailure())
        assertTrue(ConnectTimeoutException("connect").isConnectivityFailure())
        assertTrue(IOException("reset").isConnectivityFailure())
        assertTrue(IllegalStateException("wrapped", IOException("reset")).isConnectivityFailure())
    }

    @Test
    fun serverAnswersAndCancellationAreNotConnectivityFailures() {
        assertFalse(ApiException(500, "boom").isConnectivityFailure())
        assertFalse(ApiException(401, "unauthorized").isConnectivityFailure())
        assertFalse(IllegalStateException("bug").isConnectivityFailure())
        assertFalse(CancellationException("cancelled").isConnectivityFailure())
    }

    @Test
    fun onlyTimeoutsAreNotWorthRetrying() {
        assertTrue(requestTimeout.isTimeoutOrUnresolvable())
        assertTrue(ConnectTimeoutException("connect").isTimeoutOrUnresolvable())
        assertTrue(IllegalStateException("wrapped", requestTimeout).isTimeoutOrUnresolvable())
        assertFalse(IOException("connection reset").isTimeoutOrUnresolvable())
        assertFalse(ApiException(503, "unavailable").isTimeoutOrUnresolvable())
    }

    @Test
    fun stepsAfterFirstNetworkFailureAreSkipped() = runTest {
        val phase = ServerPhase()
        var secondStepRan = false

        assertNull(phase.step<String> { throw requestTimeout })
        assertTrue(phase.isUnreachable)
        assertNull(phase.step { secondStepRan = true; "never" })

        assertFalse(secondStepRan)
    }

    @Test
    fun swallowedNetworkErrorInResultAlsoShortCircuits() = runTest {
        val phase = ServerPhase()

        val result = phase.stepResult { Result.error(requestTimeout) }

        assertTrue(result is Result.Error)
        assertTrue(phase.isUnreachable)
    }

    @Test
    fun serverErrorDoesNotMarkUnreachable() = runTest {
        val phase = ServerPhase()

        assertFailsWith<ApiException> { phase.step<Unit> { throw ApiException(500, "boom") } }
        assertFalse(phase.isUnreachable)
        assertEquals("ok", phase.step { "ok" })
    }

    @Test
    fun hangingServerIsCutOffByBudget() = runTest {
        val reachable = runServerPhase(budgetMillis = 20_000) {
            step { delay(10 * 60_000L) } // «зависший» сервер: ответа нет
        }

        assertFalse(reachable)
        assertEquals(20_000L, currentTime) // виртуальное время: ждали ровно бюджет, не минуты
    }

    @Test
    fun healthyServerFinishesWithinBudget() = runTest {
        val reachable = runServerPhase(budgetMillis = 20_000) {
            step { delay(500) }
        }

        assertTrue(reachable)
    }

    @Test
    fun networkFailureInsidePhaseReportsUnreachable() = runTest {
        val reachable = runServerPhase { step<Unit> { throw requestTimeout } }

        assertFalse(reachable)
    }
}

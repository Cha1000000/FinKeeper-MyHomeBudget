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
import kotlin.test.assertSame
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
    fun gatewayErrorsFromProxyAreConnectivityFailures() {
        assertTrue(ApiException(502, "bad gateway").isConnectivityFailure())
        assertTrue(ApiException(503, "unavailable").isConnectivityFailure())
        assertTrue(ApiException(504, "gateway timeout").isConnectivityFailure())
        assertTrue(IllegalStateException("wrapped", ApiException(502, "bad gateway")).isConnectivityFailure())
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
    fun serverErrorIsRecordedAndPhaseContinues() = runTest {
        val phase = ServerPhase()
        val boom = ApiException(500, "boom")

        assertNull(phase.step<Unit> { throw boom })
        assertFalse(phase.isUnreachable)
        assertEquals("ok", phase.step { "ok" })
        assertSame(boom, phase.serverError)
    }

    @Test
    fun firstServerErrorIsKept() = runTest {
        val phase = ServerPhase()
        val first = ApiException(500, "first")

        phase.stepResult { Result.error(first) }
        phase.step<Unit> { throw IllegalStateException("second") }

        assertSame(first, phase.serverError)
    }

    @Test
    fun optionalStepIgnoresServerErrorButNotNetworkFailure() = runTest {
        val phase = ServerPhase()

        assertNull(phase.optionalStep<Unit> { throw ApiException(404, "old server") })
        assertNull(phase.serverError)
        assertFalse(phase.isUnreachable)

        phase.optionalStep<Unit> { throw requestTimeout }
        assertTrue(phase.isUnreachable)
    }

    @Test
    fun cancellationIsNotSwallowedByStep() = runTest {
        val phase = ServerPhase()

        assertFailsWith<CancellationException> { phase.step<Unit> { throw CancellationException("stop") } }
        assertFalse(phase.isUnreachable)
        assertNull(phase.serverError)
    }

    @Test
    fun hangingServerIsCutOffByBudget() = runTest {
        val linkState = ServerLinkState()
        val result = runServerPhase(linkState, budgetMillis = 20_000) {
            step { delay(10 * 60_000L) } // «зависший» сервер: ответа нет
        }

        assertFalse(result.reachable)
        assertEquals(20_000L, currentTime) // виртуальное время: ждали ровно бюджет, не минуты
        // Индикатор связи не должен остаться зелёным, когда экран показывает «Нет связи»
        assertTrue(linkState.isUnreachable.value)
    }

    @Test
    fun slowButAliveServerFitsDefaultBudget() = runTest {
        val linkState = ServerLinkState()
        // Медленная мобильная сеть: 9 запросов по 3 с — больше прежних 20 с, но сервер жив
        val result = runServerPhase(linkState) {
            repeat(9) { step { delay(3_000) } }
        }

        assertTrue(result.reachable)
        assertFalse(linkState.isUnreachable.value)
    }

    @Test
    fun healthyServerFinishesWithinBudget() = runTest {
        val result = runServerPhase(budgetMillis = 20_000) {
            step { delay(500) }
        }

        assertTrue(result.reachable)
        assertNull(result.serverError)
    }

    @Test
    fun networkFailureInsidePhaseReportsUnreachable() = runTest {
        val result = runServerPhase { step<Unit> { throw requestTimeout } }

        assertFalse(result.reachable)
    }

    @Test
    fun serverErrorKeepsPhaseReachable() = runTest {
        val result = runServerPhase { step<Unit> { throw ApiException(500, "boom") } }

        assertTrue(result.reachable)
        assertTrue(result.serverError is ApiException)
    }

    @Test
    fun failureOutsideStepIsRecordedLikeStepFailure() {
        val serverPhase = ServerPhase()
        val boom = IllegalStateException("room")
        serverPhase.recordFailure(boom)
        assertSame(boom, serverPhase.serverError)
        assertFalse(serverPhase.isUnreachable)

        val networkPhase = ServerPhase()
        networkPhase.recordFailure(IOException("reset"))
        assertTrue(networkPhase.isUnreachable)
        assertNull(networkPhase.serverError)
    }
}

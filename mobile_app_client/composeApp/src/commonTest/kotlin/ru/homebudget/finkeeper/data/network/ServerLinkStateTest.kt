package ru.homebudget.finkeeper.data.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerLinkStateTest {
    @Test
    fun staleFailureDoesNotOverrideFresherSuccess() = runTest {
        val state = ServerLinkState()
        val slow = state.beginRequest() // медленный запрос, позже упадёт по таймауту
        val fast = state.beginRequest()

        state.reportResult(fast, reachable = true)
        state.reportResult(slow, reachable = false)

        assertFalse(state.isUnreachable.value)
    }

    @Test
    fun fresherFailureOverridesOlderSuccess() = runTest {
        val state = ServerLinkState()
        val older = state.beginRequest()
        val newer = state.beginRequest()

        state.reportResult(older, reachable = true)
        state.reportResult(newer, reachable = false)

        assertTrue(state.isUnreachable.value)
    }

    @Test
    fun budgetExpiryWinsOverRequestsStartedBefore() = runTest {
        val state = ServerLinkState()
        val inFlight = state.beginRequest()

        state.reportUnreachable()
        state.reportResult(inFlight, reachable = true)
        assertTrue(state.isUnreachable.value)

        // Следующий запрос, ответивший успешно, возвращает «на связи»
        state.reportResult(state.beginRequest(), reachable = true)
        assertFalse(state.isUnreachable.value)
    }
}

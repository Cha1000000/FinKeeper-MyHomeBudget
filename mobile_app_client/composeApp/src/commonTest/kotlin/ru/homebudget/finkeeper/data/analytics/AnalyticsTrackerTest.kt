package ru.homebudget.finkeeper.data.analytics

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsTrackerTest {
    private class UnknownHostException(message: String) : Exception(message)

    private class SocketTimeoutException(message: String) : Exception(message)

    // ── isBenignAnalyticsThrowable ──

    @Test
    fun isBenignAnalyticsThrowable_cancellationException_returnsTrue() {
        assertTrue(isBenignAnalyticsThrowable(CancellationException("cancelled")))
    }

    @Test
    fun isBenignAnalyticsThrowable_wrappedCancellationInCause_returnsTrue() {
        val wrapped = IllegalStateException("wrapper", CancellationException("inner"))
        assertTrue(isBenignAnalyticsThrowable(wrapped))
    }

    @Test
    fun isBenignAnalyticsThrowable_networkExceptions_returnsTrue() {
        assertTrue(isBenignAnalyticsThrowable(UnknownHostException("no dns")))
        assertTrue(isBenignAnalyticsThrowable(SocketTimeoutException("timeout")))
    }

    @Test
    fun isBenignAnalyticsThrowable_businessOrDbExceptions_returnsFalse() {
        assertFalse(isBenignAnalyticsThrowable(IllegalStateException("SQLite error: no such column")))
        assertFalse(isBenignAnalyticsThrowable(NullPointerException("unexpected null")))
        assertFalse(isBenignAnalyticsThrowable(IllegalArgumentException("bad argument")))
    }

    // ── NoOpAnalyticsTracker ──

    @Test
    fun noOpAnalyticsTracker_doesNotThrow() {
        NoOpAnalyticsTracker.logScreen(AnalyticsEvents.Screens.DASHBOARD)
        NoOpAnalyticsTracker.logEvent(AnalyticsEvents.EXPENSE_ADDED)
        NoOpAnalyticsTracker.logError(IllegalStateException("boom"), "test_context")
        NoOpAnalyticsTracker.setUserId(42L)
        NoOpAnalyticsTracker.setUserId(null)
    }
}

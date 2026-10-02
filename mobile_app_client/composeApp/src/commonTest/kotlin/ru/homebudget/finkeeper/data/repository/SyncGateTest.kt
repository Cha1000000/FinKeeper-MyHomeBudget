package ru.homebudget.finkeeper.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncGateTest {
    private val runningHistory = mutableListOf<Boolean>()
    private val gate = SyncGate(stuckAfterMillis = 1_000) { runningHistory += it }

    @Test
    fun secondRequestWhileRunning_isDeferredAndReturnedOnEnd() =
        runTest {
            val token = assertNotNull(gate.tryBegin(SyncRequest.Full(monthId = 1), nowMillis = 0))

            assertNull(gate.tryBegin(SyncRequest.Full(monthId = 2), nowMillis = 100))

            assertEquals(SyncRequest.Full(monthId = 2), gate.end(token))
            assertEquals(listOf(true, false), runningHistory)
        }

    @Test
    fun deferredFullSync_absorbsDeferredQueue() =
        runTest {
            val token = assertNotNull(gate.tryBegin(SyncRequest.Queue, nowMillis = 0))
            assertNull(gate.tryBegin(SyncRequest.Queue, nowMillis = 10))
            assertNull(gate.tryBegin(SyncRequest.Full(monthId = null), nowMillis = 20))

            assertEquals(SyncRequest.Full(monthId = null), gate.end(token))
            // Отложенное разобрано целиком: следующий цикл ничего лишнего не запустит
            val next = assertNotNull(gate.tryBegin(SyncRequest.Queue, nowMillis = 30))
            assertNull(gate.end(next))
        }

    @Test
    fun deferredMonth_isNotLostByLaterRequestWithoutMonth() =
        runTest {
            val token = assertNotNull(gate.tryBegin(SyncRequest.Queue, nowMillis = 0))
            assertNull(gate.tryBegin(SyncRequest.Full(monthId = 5), nowMillis = 10))
            assertNull(gate.tryBegin(SyncRequest.Full(monthId = null), nowMillis = 20))

            assertEquals(SyncRequest.Full(monthId = 5), gate.end(token))
        }

    @Test
    fun onlyQueueDeferred_returnsQueue() =
        runTest {
            val token = assertNotNull(gate.tryBegin(SyncRequest.Full(monthId = null), nowMillis = 0))
            assertNull(gate.tryBegin(SyncRequest.Queue, nowMillis = 10))

            assertEquals(SyncRequest.Queue, gate.end(token))
        }

    @Test
    fun nothingDeferred_endReturnsNull() =
        runTest {
            val token = assertNotNull(gate.tryBegin(SyncRequest.Queue, nowMillis = 0))

            assertNull(gate.end(token))
        }

    @Test
    fun stuckSync_isReplaced_andItsLateEndDoesNotReleaseTheNewOne() =
        runTest {
            val stuck = assertNotNull(gate.tryBegin(SyncRequest.Full(monthId = null), nowMillis = 0))
            val fresh = assertNotNull(gate.tryBegin(SyncRequest.Queue, nowMillis = 1_001))

            // Зависшая наконец завершилась: флаг новой не сбрасывается
            assertNull(gate.end(stuck))
            assertNull(gate.tryBegin(SyncRequest.Queue, nowMillis = 1_100))

            assertEquals(SyncRequest.Queue, gate.end(fresh))
            assertEquals(listOf(true, true, false), runningHistory)
        }
}

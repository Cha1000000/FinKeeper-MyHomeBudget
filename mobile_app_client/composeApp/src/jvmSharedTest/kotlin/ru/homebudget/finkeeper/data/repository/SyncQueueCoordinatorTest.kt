package ru.homebudget.finkeeper.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.homebudget.finkeeper.data.local.dao.SyncQueueDao
import ru.homebudget.finkeeper.data.local.dao.SyncQueueItem
import ru.homebudget.finkeeper.data.local.dao.UserDao
import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncQueueStatus

/**
 * Очередь синхронизации на настоящей SQLite: слияние операций, захват на отправку,
 * порядок операций одной записи.
 */
class SyncQueueCoordinatorTest {
    private val database =
        FinKeeperDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { FinKeeperDatabase.Schema.create(it) },
        )
    private val dao = SyncQueueDao(database)
    private val userId = UserDao(database).insert(username = "u", email = "u@example.com", passwordHash = "hash")

    // Версии записей (updated_at) — подменяются тестом
    private val versions = mutableMapOf<Pair<String, Long>, String>()
    private var opCounter = 0
    private val queue =
        SyncQueueCoordinator(
            syncQueueDao = dao,
            entityUpdatedAt = { type, id -> versions[type to id] },
            newOperationId = { "op-${++opCounter}" },
            dbContext = Dispatchers.Unconfined,
        )

    private val insert = SyncOperation.INSERT.value
    private val update = SyncOperation.UPDATE.value
    private val delete = SyncOperation.DELETE.value

    private fun items(): List<SyncQueueItem> = dao.getAllByUser(userId).sortedBy { it.id }

    private fun SyncQueueItem.meta(): SyncQueuePayloadMetadata = assertNotNull(decodeSyncQueuePayloadMetadata(payload))

    private suspend fun enqueue(
        operation: String,
        entityId: Long = 1,
        type: String = "category",
        deleteServerId: String? = null,
        amount: Long? = null,
    ) = queue.enqueue(userId, type, entityId, operation, deleteServerId, amount)

    @Test
    fun updateAfterPendingInsert_keepsInsertItsKeyAndPlace() = runTest {
        versions["category" to 1L] = "v1"
        enqueue(insert)
        val before = items().single()
        versions["category" to 1L] = "v2"
        enqueue(update)

        val after = items().single()
        assertEquals(before.id, after.id, "элемент остаётся на своём месте в очереди")
        assertEquals(insert, after.operation)
        assertEquals(before.meta().opId, after.meta().opId, "повтор создания идёт с тем же ключом")
    }

    @Test
    fun updateAfterFailedInsert_returnsItToPendingWithFreshRetries() = runTest {
        enqueue(insert)
        val id = items().single().id
        dao.updateStatus(id, SyncQueueStatus.SYNCING.value, null)
        dao.updateStatus(id, SyncQueueStatus.FAILED.value, "boom")

        enqueue(update)

        val item = items().single()
        assertEquals(SyncQueueStatus.PENDING.value, item.status)
        assertEquals(0, item.retryCount)
        assertNull(item.errorMessage)
    }

    @Test
    fun deleteOfNeverSentInsert_dropsBoth() = runTest {
        enqueue(insert)
        enqueue(delete)
        assertTrue(items().isEmpty())
    }

    @Test
    fun deleteWithServerIdAfterPendingInsert_isSent() = runTest {
        // Запись уже создана на сервере дочерней операцией, а её INSERT ещё в очереди
        enqueue(insert)
        enqueue(delete, deleteServerId = "77")

        val item = items().single()
        assertEquals(delete, item.operation)
        assertEquals("77", item.meta().deleteServerId)
    }

    @Test
    fun deleteWins_overLaterUpdate() = runTest {
        enqueue(update)
        enqueue(delete, deleteServerId = "5")
        enqueue(update)

        val item = items().single()
        assertEquals(delete, item.operation)
        assertEquals("5", item.meta().deleteServerId)
    }

    @Test
    fun itemBeingSent_isNotMerged_andNextItemWaitsForIt() = runTest {
        enqueue(insert)
        val first = assertNotNull(queue.claim(items().single().id))
        enqueue(update)

        val all = items()
        assertEquals(2, all.size)
        assertEquals(insert, all[0].operation)
        assertEquals(SyncQueueStatus.SYNCING.value, all[0].status, "отправляемый элемент не тронут")
        assertNull(queue.claim(all[1].id), "UPDATE ждёт, пока не закрыт INSERT")

        dao.updateStatus(first.id, SyncQueueStatus.COMPLETED.value, null)
        assertNotNull(queue.claim(all[1].id))
    }

    @Test
    fun failedEarlierInsert_blocksLaterItems_failedEarlierUpdate_doesNot() = runTest {
        enqueue(insert, entityId = 1)
        val insertItem = assertNotNull(queue.claim(items().single().id))
        enqueue(update, entityId = 1)
        dao.updateStatus(insertItem.id, SyncQueueStatus.FAILED.value, "500")
        assertNull(queue.claim(items().last().id), "без созданной записи обновлять нечего")

        enqueue(update, entityId = 2)
        val updateItem = assertNotNull(queue.claim(items().last().id))
        enqueue(update, entityId = 2)
        dao.updateStatus(updateItem.id, SyncQueueStatus.FAILED.value, "400")
        assertNotNull(queue.claim(items().last().id), "следующий UPDATE несёт более свежее состояние")
    }

    @Test
    fun goalAmount_isQueuedSeparately_andKeptByLaterPlainUpdate() = runTest {
        enqueue(update, type = "savings_goal", amount = 1000)
        enqueue(update, type = "savings_goal", amount = 2500)
        assertEquals(listOf(1000L, 2500L), items().map { it.meta().goalCurrentAmount })

        // Переименование сливается с последним элементом и не теряет установку суммы
        enqueue(update, type = "savings_goal")
        val all = items()
        assertEquals(2, all.size)
        assertEquals(2500L, all.last().meta().goalCurrentAmount)
    }

    @Test
    fun claim_skipsOutdatedUpdateOnlyWhenLaterItemExists() = runTest {
        versions["category" to 1L] = "v1"
        enqueue(update)
        val older = items().single()
        // Первый UPDATE отправлялся, когда запись поправили снова, и вернулся на повтор после сбоя
        dao.updateStatus(older.id, SyncQueueStatus.SYNCING.value, null)
        versions["category" to 1L] = "v2"
        enqueue(update)
        dao.updateStatus(older.id, SyncQueueStatus.PENDING.value, null)
        val newer = items().single { it.id != older.id }

        assertNull(queue.claim(older.id))
        assertEquals(SyncQueueStatus.COMPLETED.value, dao.getById(older.id)?.status, "устаревший закрыт")
        assertNotNull(queue.claim(newer.id))
    }

    @Test
    fun claim_neverSkipsOutdatedUpdateCarryingGoalAmount() = runTest {
        versions["savings_goal" to 1L] = "v1"
        enqueue(update, type = "savings_goal", amount = 100)
        val older = items().single()
        // Установка суммы отправлялась, когда копилку переименовали, и вернулась на повтор после сбоя.
        // Явная сумма 100 есть только в ней: более поздняя правка её не несёт
        dao.updateStatus(older.id, SyncQueueStatus.SYNCING.value, null)
        versions["savings_goal" to 1L] = "v2"
        enqueue(update, type = "savings_goal")
        dao.updateStatus(older.id, SyncQueueStatus.PENDING.value, null)
        assertEquals(2, items().size)

        val claimed = assertNotNull(queue.claim(older.id), "установка суммы уходит на сервер")
        assertEquals(100L, claimed.meta().goalCurrentAmount)
    }

    @Test
    fun claim_neverSkipsLoneInsert_andStampsVersionAtSendTime() = runTest {
        versions["category" to 1L] = "v1"
        enqueue(insert)
        // Запись поменяли без постановки операции (например, локальная сумма копилки)
        versions["category" to 1L] = "v2"

        val claimed = assertNotNull(queue.claim(items().single().id))
        assertEquals(SyncQueueStatus.SYNCING.value, claimed.status)
        assertEquals("v2", claimed.meta().entityUpdatedAt, "сравнение «грязности» — с отправленным состоянием")
        assertNull(queue.claim(claimed.id), "повторно не захватывается")
    }

    @Test
    fun followUp_isQueuedOnlyForDirtyRecordWithoutOtherItems() = runTest {
        enqueue(update)
        val sent = assertNotNull(queue.claim(items().single().id))
        dao.updateStatus(sent.id, SyncQueueStatus.COMPLETED.value, null)

        assertFalse(queue.enqueueFollowUpIfNeeded(sent) { false })
        assertTrue(queue.enqueueFollowUpIfNeeded(sent) { true })
        assertFalse(queue.enqueueFollowUpIfNeeded(sent) { true }, "второй раз не нужен: UPDATE уже в очереди")
        assertEquals(update, items().last().operation)
    }

    @Test
    fun renewOperationId_changesKeyAndReturnsToPending() = runTest {
        enqueue(update)
        val claimed = assertNotNull(queue.claim(items().single().id))
        queue.renewOperationIdAndReturnToPending(claimed.id)

        val item = items().single()
        assertEquals(SyncQueueStatus.PENDING.value, item.status)
        assertNotEquals(claimed.meta().opId, item.meta().opId)
    }

    @Test
    fun openInsertOperationId_returnsKeyOfParentInsert() = runTest {
        enqueue(insert, entityId = 9)
        assertEquals(items().single().meta().opId, queue.openInsertOperationId(userId, "category", 9))
        assertNull(queue.openInsertOperationId(userId, "category", 10))
    }

    @Test
    fun parallelEnqueues_neverDuplicateItems() = runTest {
        withContext(Dispatchers.Default) {
            (1..50).map { n ->
                async { enqueue(if (n == 1) insert else update, entityId = (n % 5).toLong()) }
            }.awaitAll()
        }
        val perEntity = items().groupBy { it.entityId }
        assertEquals(5, perEntity.size)
        perEntity.values.forEach { assertEquals(1, it.size, "одна операция на запись") }
    }

    @Test
    fun reactivationAfterPendingDelete_replacesDelete() = runTest {
        enqueue(delete, deleteServerId = "10")
        queue.enqueue(userId, "category", 1, update, reactivate = true)

        val item = items().single()
        assertEquals(update, item.operation, "удалили и восстановили до отправки — уходит восстановление")
        assertTrue(item.meta().reactivate)
        assertNull(item.meta().deleteServerId)
    }

    @Test
    fun plainUpdateAfterPendingDelete_keepsDelete() = runTest {
        enqueue(delete, deleteServerId = "10")
        enqueue(update)

        val item = items().single()
        assertEquals(delete, item.operation, "удаление побеждает обычную правку")
        assertEquals("10", item.meta().deleteServerId)
    }

    @Test
    fun updateAfterReactivation_keepsReactivationFlag() = runTest {
        queue.enqueue(userId, "category", 1, update, reactivate = true)
        enqueue(update)

        val item = items().single()
        assertEquals(update, item.operation)
        assertTrue(item.meta().reactivate, "слитая правка не теряет восстановление")
    }

    @Test
    fun reactivateFlag_isIgnoredForNonUpdate() = runTest {
        queue.enqueue(userId, "category", 1, insert, reactivate = true)

        assertFalse(items().single().meta().reactivate)
    }
}

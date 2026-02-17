package ru.homebudget.finkeeper.data.repository

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import ru.homebudget.finkeeper.data.local.dao.CategoryDao
import ru.homebudget.finkeeper.data.local.dao.ExpenseDao
import ru.homebudget.finkeeper.data.local.dao.MonthDao
import ru.homebudget.finkeeper.data.local.dao.SyncQueueDao
import ru.homebudget.finkeeper.data.local.dao.UserDao
import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.model.SyncOperation

class SyncQueueIntegrationTest {

    @Test
    fun syncQueue_insertFailRetryLifecycle_persistsStatusTransitions() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FinKeeperDatabase.Schema.create(driver)

        val database = FinKeeperDatabase(driver)
        val userDao = UserDao(database)
        val monthDao = MonthDao(database)
        val categoryDao = CategoryDao(database)
        val expenseDao = ExpenseDao(database)
        val syncQueueDao = SyncQueueDao(database)

        val userId = userDao.insert(username = "test_user", email = "test@example.com", passwordHash = "hash")
        val monthId = monthDao.insert(userId = userId, year = 2026, month = 2, serverId = "100")
        val categoryId =
            categoryDao.insertAndReturn(
                userId = userId,
                name = "Еда",
                type = "expense",
                icon = null,
                color = null,
                sortOrder = 0,
                isActive = 1,
                createdAt = "2026-02-16T00:00:00Z",
                updatedAt = "2026-02-16T00:00:00Z",
                serverId = "200",
            )
        val expenseId =
            expenseDao.insertAndReturn(
                userId = userId,
                monthId = monthId,
                categoryId = categoryId,
                amount = 1500,
                description = "Обед",
                date = "2026-02-16",
                createdAt = "2026-02-16T00:00:00Z",
                updatedAt = "2026-02-16T00:00:00Z",
            )

        syncQueueDao.insert(
            userId = userId,
            entityType = "expense",
            entityId = expenseId,
            operation = SyncOperation.INSERT.value,
            payload = null,
        )

        assertEquals(1L, syncQueueDao.getPendingCount())

        val pendingItem = syncQueueDao.getPendingItems(limit = 1).first()
        syncQueueDao.updateStatus(
            id = pendingItem.id,
            status = "failed",
            errorMessage = "boom",
        )

        val failedItem = syncQueueDao.getFailedItems(limit = 1).first()
        assertEquals("failed", failedItem.status)
        assertEquals("boom", failedItem.errorMessage)

        syncQueueDao.retryFailed()
        val retriedItem = syncQueueDao.getPendingItems(limit = 1).first()
        assertEquals("pending", retriedItem.status)
        assertEquals(null, retriedItem.errorMessage)
    }
}

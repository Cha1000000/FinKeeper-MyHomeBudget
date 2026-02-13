package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Expenses

/**
 * Data Access Object for Expense operations.
 * Provides type-safe SQL queries for the expenses table.
 */
class ExpenseDao(
    private val database: FinKeeperDatabase
) {
    private val queries = database.finKeeperDatabaseQueries

    /**
     * Insert a new expense
     */
    fun insert(
        userId: Long,
        monthId: Long,
        categoryId: Long,
        amount: Long,
        description: String?,
        date: String,
        createdAt: String,
        updatedAt: String,
        serverId: String? = null,
        syncStatus: String = "synced",
        isHidden: Long = 0L
    ) {
        queries.insertExpense(
            user_id = userId,
            month_id = monthId,
            category_id = categoryId,
            amount = amount,
            description = description,
            date = date,
            created_at = createdAt,
            updated_at = updatedAt,
            server_id = serverId,
            sync_status = syncStatus,
            is_hidden = isHidden
        )
    }

    /**
     * Insert an expense and return its generated ID
     */
    fun insertAndReturn(
        userId: Long,
        monthId: Long,
        categoryId: Long,
        amount: Long,
        description: String?,
        date: String,
        createdAt: String,
        updatedAt: String,
        serverId: String? = null,
        syncStatus: String = "synced",
        isHidden: Long = 0L
    ): Long {
        queries.insertExpense(
            user_id = userId,
            month_id = monthId,
            category_id = categoryId,
            amount = amount,
            description = description,
            date = date,
            created_at = createdAt,
            updated_at = updatedAt,
            server_id = serverId,
            sync_status = syncStatus,
            is_hidden = isHidden
        )
        return queries.lastInsertRowId().executeAsOne()
    }

    /**
     * Update an existing expense by local ID
     */
    fun update(
        id: Long,
        monthId: Long,
        categoryId: Long,
        amount: Long,
        description: String?,
        date: String,
        updatedAt: String,
        serverId: String? = null,
        syncStatus: String = "synced",
        isHidden: Long
    ) {
        queries.updateExpenseById(
            month_id = monthId,
            category_id = categoryId,
            amount = amount,
            description = description,
            date = date,
            updated_at = updatedAt,
            server_id = serverId,
            sync_status = syncStatus,
            is_hidden = isHidden,
            id = id
        )
    }

    /**
     * Update sync status by local ID
     */
    fun updateSyncStatus(id: Long, syncStatus: String, serverId: String? = null) {
        queries.updateExpenseSyncStatus(
            sync_status = syncStatus,
            server_id = serverId,
            id = id
        )
    }

    /**
     * Delete an expense by local ID
     */
    fun deleteById(id: Long) {
        queries.deleteExpenseById(id)
    }

    /**
     * Delete all expenses for a user
     */
    fun deleteAllByUser(userId: Long) {
        queries.deleteAllExpensesByUser(userId)
    }

    /**
     * Delete all expenses for a month
     */
    fun deleteAllByMonth(monthId: Long) {
        queries.deleteAllExpensesByMonth(monthId)
    }

    /**
     * Get an expense by local ID
     */
    fun getById(id: Long): Expense? {
        return queries.getExpenseById(id).executeAsOneOrNull()?.let { toExpense(it) }
    }

    /**
     * Get all expenses for a user
     */
    fun getAllByUser(userId: Long): List<Expense> {
        return queries.getAllExpensesByUser(userId).executeAsList().map { toExpense(it) }
    }

    /**
     * Get all expenses for a month
     */
    fun getByMonth(monthId: Long): List<Expense> {
        return queries.getExpensesByMonth(monthId).executeAsList().map { toExpense(it) }
    }

    /**
     * Get visible expenses for a month (not hidden)
     */
    fun getVisibleByMonth(monthId: Long): List<Expense> {
        return queries.getVisibleExpensesByMonth(monthId).executeAsList().map { toExpense(it) }
    }

    /**
     * Get expenses by category for a month
     */
    fun getByMonthAndCategory(monthId: Long, categoryId: Long): List<Expense> {
        return queries.getExpensesByMonthAndCategory(monthId, categoryId).executeAsList().map { toExpense(it) }
    }

    /**
     * Get total expense amount for a month
     */
    fun getTotalByMonth(monthId: Long): Long {
        return queries.getTotalVisibleExpenseByMonth(monthId).executeAsOneOrNull() ?: 0L
    }

    /**
     * Get total expense amount for a month by category
     */
    fun getTotalByMonthAndCategory(monthId: Long, categoryId: Long): Long {
        return queries.getTotalExpenseByMonthAndCategory(monthId, categoryId).executeAsOneOrNull() ?: 0L
    }

    /**
     * Get total visible expense amount for a month
     */
    fun getTotalVisibleByMonth(monthId: Long): Long {
        return queries.getTotalVisibleExpenseByMonth(monthId).executeAsOneOrNull() ?: 0L
    }

    /**
     * Get expenses pending sync (not synced with server)
     */
    fun getPendingSync(): List<Expense> {
        return queries.getExpensesPendingSync().executeAsList().map { toExpense(it) }
    }

    /**
     * Get count of expenses pending sync
     */
    fun getPendingSyncCount(): Long {
        return queries.getExpensesPendingSyncCount().executeAsOne()
    }

    /**
     * Check if expense exists by local ID
     */
    fun existsById(id: Long): Boolean {
        return queries.expenseExistsById(id).executeAsOne()
    }

    /**
     * Check if expense exists by server ID
     */
    fun existsByServerId(serverId: String): Boolean {
        return queries.expenseExistsByServerId(serverId).executeAsOne()
    }

    /**
     * Get expense by server ID
     */
    fun getByServerId(serverId: String): Expense? {
        return queries.getExpenseByServerId(serverId).executeAsOneOrNull()?.let { toExpense(it) }
    }

    /**
     * Delete all expenses (for testing or full sync reset)
     */
    fun deleteAll() {
        queries.deleteAllExpenses()
    }

    private fun toExpense(entity: Expenses): Expense {
        return Expense(
            id = entity.id,
            userId = entity.user_id,
            monthId = entity.month_id,
            categoryId = entity.category_id,
            amount = entity.amount,
            description = entity.description,
            date = entity.date,
            createdAt = entity.created_at,
            updatedAt = entity.updated_at,
            serverId = entity.server_id,
            syncStatus = entity.sync_status,
            isHidden = entity.is_hidden
        )
    }
}

/**
 * Модель расхода
 */
data class Expense(
    val id: Long,
    val userId: Long,
    val monthId: Long,
    val categoryId: Long,
    val amount: Long,
    val description: String?,
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String,
    val isHidden: Long
)

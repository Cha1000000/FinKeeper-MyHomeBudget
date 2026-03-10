package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Incomes

/**
 * Data Access Object for Income operations.
 * Provides type-safe SQL queries for the incomes table.
 */
class IncomeDao(
    private val database: FinKeeperDatabase
) {
    private val queries = database.finKeeperDatabaseQueries

    /**
     * Insert a new income
     */
    fun insert(
        userId: Long,
        monthId: Long,
        incomeSourceId: Long,
        amount: Long,
        description: String?,
        date: String,
        createdAt: String? = null,
        updatedAt: String? = null,
        serverId: String? = null,
        syncStatus: String = "synced"
    ) {
        val now = getCurrentTimestamp()
        queries.insertIncome(
            user_id = userId,
            month_id = monthId,
            income_source_id = incomeSourceId,
            amount = amount,
            description = description,
            date = date,
            created_at = createdAt ?: now,
            updated_at = updatedAt ?: createdAt ?: now,
            server_id = serverId,
            sync_status = syncStatus
        )
    }

    /**
     * Insert an income and return its generated ID
     */
    fun insertAndReturn(
        userId: Long,
        monthId: Long,
        incomeSourceId: Long,
        amount: Long,
        description: String?,
        date: String,
        createdAt: String? = null,
        updatedAt: String? = null,
        serverId: String? = null,
        syncStatus: String = "synced"
    ): Long {
        val now = getCurrentTimestamp()
        return database.transactionWithResult {
            queries.insertIncome(
                user_id = userId,
                month_id = monthId,
                income_source_id = incomeSourceId,
                amount = amount,
                description = description,
                date = date,
                created_at = createdAt ?: now,
                updated_at = updatedAt ?: createdAt ?: now,
                server_id = serverId,
                sync_status = syncStatus
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }

    /**
     * Update an existing income by local ID
     */
    fun update(
        id: Long,
        monthId: Long,
        incomeSourceId: Long,
        amount: Long,
        description: String?,
        date: String,
        updatedAt: String? = null,
        serverId: String? = null,
        syncStatus: String = "synced"
    ) {
        val now = getCurrentTimestamp()
        queries.updateIncomeById(
            month_id = monthId,
            income_source_id = incomeSourceId,
            amount = amount,
            description = description,
            date = date,
            updated_at = updatedAt ?: now,
            server_id = serverId,
            sync_status = syncStatus,
            id = id
        )
    }

    /**
     * Update sync status by local ID
     */
    fun updateSyncStatus(id: Long, syncStatus: String, serverId: String? = null) {
        queries.updateIncomeSyncStatus(
            sync_status = syncStatus,
            server_id = serverId,
            id = id
        )
    }

    /**
     * Delete an income by local ID
     */
    fun deleteById(id: Long) {
        queries.deleteIncomeById(id)
    }

    /**
     * Delete all incomes for a user
     */
    fun deleteAllByUser(userId: Long) {
        queries.deleteAllIncomesByUser(userId)
    }

    /**
     * Delete all incomes for a month
     */
    fun deleteAllByMonth(monthId: Long) {
        queries.deleteAllIncomesByMonth(monthId)
    }

    /**
     * Get an income by local ID
     */
    fun getById(id: Long): Income? {
        return queries.getIncomeById(id).executeAsOneOrNull()?.let { toIncome(it) }
    }

    /**
     * Get all incomes for a user
     */
    fun getAllByUser(userId: Long): List<Income> {
        return queries.getAllIncomesByUser(userId).executeAsList().map { toIncome(it) }
    }

    /**
     * Get all incomes for a month
     */
    fun getByMonth(monthId: Long): List<Income> {
        return queries.getIncomesByMonth(monthId).executeAsList().map { toIncome(it) }
    }

    /**
     * Get incomes by source for a month
     */
    fun getByMonthAndSource(monthId: Long, sourceId: Long): List<Income> {
        return queries.getIncomesByMonthAndSource(monthId, sourceId).executeAsList().map { toIncome(it) }
    }

    /**
     * Get total income amount for a month
     */
    fun getTotalByMonth(monthId: Long): Long {
        return queries.getTotalIncomeByMonth(monthId).executeAsOneOrNull() ?: 0L
    }

    /**
     * Get total income amount for a month by source
     */
    fun getTotalByMonthAndSource(monthId: Long, sourceId: Long): Long {
        return queries.getTotalIncomeByMonthAndSource(monthId, sourceId).executeAsOneOrNull() ?: 0L
    }

    /**
     * Get incomes pending sync (not synced with server)
     */
    fun getPendingSync(): List<Income> {
        return queries.getIncomesPendingSync().executeAsList().map { toIncome(it) }
    }

    /**
     * Get count of incomes pending sync
     */
    fun getPendingSyncCount(): Long {
        return queries.getIncomesPendingSyncCount().executeAsOne()
    }

    /**
     * Check if income exists by local ID
     */
    fun existsById(id: Long): Boolean {
        return queries.incomeExistsById(id).executeAsOne()
    }

    /**
     * Check if income exists by server ID
     */
    fun existsByServerId(serverId: String): Boolean {
        return queries.incomeExistsByServerId(serverId).executeAsOne()
    }

    /**
     * Get income by server ID
     */
    fun getByServerId(serverId: String): Income? {
        return queries.getIncomeByServerId(serverId).executeAsOneOrNull()?.let { toIncome(it) }
    }

    /**
     * Delete all incomes (for testing or full sync reset)
     */
    fun deleteAll() {
        queries.deleteAllIncomes()
    }

    private fun toIncome(entity: Incomes): Income {
        return Income(
            id = entity.id,
            userId = entity.user_id,
            monthId = entity.month_id,
            incomeSourceId = entity.income_source_id,
            amount = entity.amount,
            description = entity.description,
            date = entity.date,
            createdAt = entity.created_at,
            updatedAt = entity.updated_at,
            serverId = entity.server_id,
            syncStatus = entity.sync_status
        )
    }

    private fun getCurrentTimestamp(): String {
        return kotlinx.datetime.Clock.System.now().toString()
    }
}

/**
 * Модель дохода
 */
data class Income(
    val id: Long,
    val userId: Long,
    val monthId: Long,
    val incomeSourceId: Long,
    val amount: Long,
    val description: String?,
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String
)

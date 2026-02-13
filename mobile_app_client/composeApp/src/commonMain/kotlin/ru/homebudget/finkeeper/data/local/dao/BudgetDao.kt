package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Budgets

/**
 * Data Access Object для операций с бюджетами
 */
class BudgetDao(
    private val database: FinKeeperDatabase
) {
    private val queries = database.finKeeperDatabaseQueries

    /**
     * Вставка нового бюджета
     */
    fun insert(
        userId: Long,
        monthId: Long,
        categoryId: Long,
        limitAmount: Long,
        serverId: String? = null,
        syncStatus: String = "synced"
    ): Long {
        val now = getCurrentTimestamp()
        queries.insertBudget(
            user_id = userId,
            month_id = monthId,
            category_id = categoryId,
            limit_amount = limitAmount,
            created_at = now,
            updated_at = now,
            server_id = serverId,
            sync_status = syncStatus
        )
        return queries.lastInsertRowId().executeAsOne()
    }

    /**
     * Обновление бюджета
     */
    fun update(
        id: Long,
        monthId: Long,
        categoryId: Long,
        limitAmount: Long,
        serverId: String? = null,
        syncStatus: String = "synced"
    ) {
        val now = getCurrentTimestamp()
        queries.updateBudgetById(
            month_id = monthId,
            category_id = categoryId,
            limit_amount = limitAmount,
            updated_at = now,
            server_id = serverId,
            sync_status = syncStatus,
            id = id
        )
    }

    /**
     * Обновление статуса синхронизации
     */
    fun updateSyncStatus(id: Long, syncStatus: String, serverId: String? = null) {
        queries.updateBudgetSyncStatus(
            sync_status = syncStatus,
            server_id = serverId,
            id = id
        )
    }

    /**
     * Удаление бюджета
     */
    fun deleteById(id: Long) {
        queries.deleteBudgetById(id)
    }

    /**
     * Удаление всех бюджетов пользователя
     */
    fun deleteAllByUser(userId: Long) {
        queries.deleteAllBudgetsByUser(userId)
    }

    /**
     * Удаление всех бюджетов месяца
     */
    fun deleteAllByMonth(monthId: Long) {
        queries.deleteAllBudgetsByMonth(monthId)
    }

    /**
     * Получение бюджета по ID
     */
    fun getById(id: Long): Budget? {
        return queries.getBudgetById(id).executeAsOneOrNull()?.let { toBudget(it) }
    }

    /**
     * Получение бюджетов месяца
     */
    fun getByMonth(monthId: Long): List<Budget> {
        return queries.getBudgetsByMonth(monthId).executeAsList().map { toBudget(it) }
    }

    /**
     * Получение бюджетов пользователя
     */
    fun getByUser(userId: Long): List<Budget> {
        return queries.getBudgetsByUser(userId).executeAsList().map { toBudget(it) }
    }

    /**
     * Получение бюджета по месяцу и категории
     */
    fun getByMonthAndCategory(monthId: Long, categoryId: Long): Budget? {
        return queries.getBudgetByMonthAndCategory(monthId, categoryId).executeAsOneOrNull()?.let { toBudget(it) }
    }

    /**
     * Получение бюджетов ожидающих синхронизации
     */
    fun getPendingSync(): List<Budget> {
        return queries.getBudgetsPendingSync().executeAsList().map { toBudget(it) }
    }

    /**
     * Получение количества бюджетов ожидающих синхронизации
     */
    fun getPendingSyncCount(): Long {
        return queries.getBudgetsPendingSyncCount().executeAsOne()
    }

    /**
     * Проверка существования по ID
     */
    fun existsById(id: Long): Boolean {
        return queries.budgetExistsById(id).executeAsOne()
    }

    /**
     * Проверка существования по server ID
     */
    fun existsByServerId(serverId: String): Boolean {
        return queries.budgetExistsByServerId(serverId).executeAsOne()
    }

    /**
     * Получение по server ID
     */
    fun getByServerId(serverId: String): Budget? {
        return queries.getBudgetByServerId(serverId).executeAsOneOrNull()?.let { toBudget(it) }
    }

    /**
     * Удаление всех бюджетов
     */
    fun deleteAll() {
        queries.deleteAllBudgets()
    }

    private fun toBudget(entity: Budgets): Budget {
        return Budget(
            id = entity.id,
            userId = entity.user_id,
            monthId = entity.month_id,
            categoryId = entity.category_id,
            limitAmount = entity.limit_amount,
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
 * Модель бюджета
 */
data class Budget(
    val id: Long,
    val userId: Long,
    val monthId: Long,
    val categoryId: Long,
    val limitAmount: Long,
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String
)

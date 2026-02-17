package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Savings_goals

/**
 * DAO для работы с целями накоплений
 */
class SavingsGoalDao(private val database: FinKeeperDatabase) {
    
    private val queries = database.finKeeperDatabaseQueries
    
    /**
     * Получение цели по ID
     */
    fun getById(id: Long): SavingsGoal? = 
        queries.getSavingsGoalById(id).executeAsOneOrNull()?.let { toSavingsGoal(it) }
    
    /**
     * Получение всех целей пользователя
     */
    fun getAllByUser(userId: Long): List<SavingsGoal> = 
        queries.getAllSavingsGoalsByUser(userId).executeAsList().map { toSavingsGoal(it) }
    
    /**
     * Получение активных целей пользователя
     */
    fun getActiveByUser(userId: Long): List<SavingsGoal> = 
        queries.getActiveSavingsGoalsByUser(userId).executeAsList().map { toSavingsGoal(it) }
    
    /**
     * Создание новой цели
     */
    fun insert(
        userId: Long,
        name: String,
        targetAmount: Long,
        currentAmount: Long = 0L,
        color: String? = null,
        icon: String? = null,
        targetDate: String? = null,
        isAchieved: Long = 0L,
        serverId: String? = null,
        syncStatus: String = "synced"
    ): Long {
        val now = getCurrentDateTime()
        return database.transactionWithResult {
            queries.insertSavingsGoal(
                user_id = userId,
                name = name,
                target_amount = targetAmount,
                current_amount = currentAmount,
                color = color,
                icon = icon,
                target_date = targetDate,
                is_achieved = isAchieved,
                created_at = now,
                updated_at = now,
                server_id = serverId,
                sync_status = syncStatus
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }
    
    /**
     * Обновление цели
     */
    fun update(
        id: Long,
        name: String,
        targetAmount: Long,
        currentAmount: Long,
        color: String? = null,
        icon: String? = null,
        targetDate: String? = null,
        isAchieved: Long = 0L,
        serverId: String? = null,
        syncStatus: String = "synced"
    ) {
        val now = getCurrentDateTime()
        queries.updateSavingsGoalById(
            name = name,
            target_amount = targetAmount,
            current_amount = currentAmount,
            color = color,
            icon = icon,
            target_date = targetDate,
            is_achieved = isAchieved,
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
        queries.updateSavingsGoalSyncStatus(
            sync_status = syncStatus,
            server_id = serverId,
            id = id
        )
    }
    
    /**
     * Удаление цели по ID
     */
    fun deleteById(id: Long) {
        queries.deleteSavingsGoalById(id)
    }
    
    /**
     * Удаление всех целей пользователя
     */
    fun deleteAllByUser(userId: Long) {
        queries.deleteAllSavingsGoalsByUser(userId)
    }
    
    /**
     * Получение цели по server_id
     */
    fun getByServerId(serverId: String): SavingsGoal? = 
        queries.getSavingsGoalByServerId(serverId).executeAsOneOrNull()?.let { toSavingsGoal(it) }
    
    /**
     * Получение целей ожидающих синхронизации
     */
    fun getPendingSync(): List<SavingsGoal> = 
        queries.getSavingsGoalsPendingSync().executeAsList().map { toSavingsGoal(it) }
    
    /**
     * Проверка существования по ID
     */
    fun existsById(id: Long): Boolean {
        return queries.savingsGoalExistsById(id).executeAsOne()
    }

    /**
     * Проверка существования по server ID
     */
    fun existsByServerId(serverId: String): Boolean {
        return queries.savingsGoalExistsByServerId(serverId).executeAsOne()
    }
    
    /**
     * Удаление всех целей
     */
    fun deleteAll() {
        queries.deleteAllSavingsGoals()
    }
    
    private fun toSavingsGoal(entity: Savings_goals): SavingsGoal {
        return SavingsGoal(
            id = entity.id,
            userId = entity.user_id,
            name = entity.name,
            targetAmount = entity.target_amount,
            currentAmount = entity.current_amount,
            color = entity.color,
            icon = entity.icon,
            targetDate = entity.target_date,
            isAchieved = entity.is_achieved,
            createdAt = entity.created_at,
            updatedAt = entity.updated_at,
            serverId = entity.server_id,
            syncStatus = entity.sync_status
        )
    }
    
    private fun getCurrentDateTime(): String {
        val now = kotlinx.datetime.Clock.System.now()
        return now.toString()
    }
}

/**
 * Модель цели накопления
 */
data class SavingsGoal(
    val id: Long,
    val userId: Long,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val color: String?,
    val icon: String?,
    val targetDate: String?,
    val isAchieved: Long,
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String
)

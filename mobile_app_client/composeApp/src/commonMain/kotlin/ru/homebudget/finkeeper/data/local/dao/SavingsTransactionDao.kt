package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Savings_transactions

/**
 * DAO для работы с транзакциями накоплений
 */
class SavingsTransactionDao(private val database: FinKeeperDatabase) {
    
    private val queries = database.finKeeperDatabaseQueries
    
    /**
     * Получение транзакции по ID
     */
    fun getById(id: Long): SavingsTransaction? = 
        queries.getSavingsTransactionById(id).executeAsOneOrNull()?.let { toSavingsTransaction(it) }
    
    /**
     * Получение всех транзакций для цели
     */
    fun getByGoal(goalId: Long): List<SavingsTransaction> = 
        queries.getSavingsTransactionsByGoal(goalId).executeAsList().map { toSavingsTransaction(it) }
    
    /**
     * Получение всех транзакций пользователя
     */
    fun getAllByUser(userId: Long): List<SavingsTransaction> = 
        queries.getAllSavingsTransactionsByUser(userId).executeAsList().map { toSavingsTransaction(it) }
    
    /**
     * Создание новой транзакции
     */
    fun insert(
        userId: Long,
        savingsGoalId: Long,
        amount: Long,
        type: String,
        description: String? = null,
        date: String,
        serverId: String? = null,
        syncStatus: String = "synced"
    ): Long {
        val now = getCurrentDateTime()
        return database.transactionWithResult {
            queries.insertSavingsTransaction(
                user_id = userId,
                savings_goal_id = savingsGoalId,
                amount = amount,
                type = type,
                description = description,
                date = date,
                created_at = now,
                updated_at = now,
                server_id = serverId,
                sync_status = syncStatus
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }
    
    /**
     * Обновление транзакции
     */
    fun update(
        id: Long,
        savingsGoalId: Long,
        amount: Long,
        type: String,
        description: String? = null,
        date: String,
        serverId: String? = null,
        syncStatus: String = "synced"
    ) {
        val now = getCurrentDateTime()
        queries.updateSavingsTransactionById(
            savings_goal_id = savingsGoalId,
            amount = amount,
            type = type,
            description = description,
            date = date,
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
        queries.updateSavingsTransactionSyncStatus(
            sync_status = syncStatus,
            server_id = serverId,
            id = id
        )
    }
    
    /**
     * Удаление транзакции по ID
     */
    fun deleteById(id: Long) {
        queries.deleteSavingsTransactionById(id)
    }
    
    /**
     * Удаление всех транзакций пользователя
     */
    fun deleteAllByUser(userId: Long) {
        queries.deleteAllSavingsTransactionsByUser(userId)
    }
    
    /**
     * Удаление всех транзакций для цели
     */
    fun deleteAllByGoal(goalId: Long) {
        queries.deleteAllSavingsTransactionsByGoal(goalId)
    }
    
    /**
     * Получение транзакции по server_id
     */
    fun getByServerId(serverId: String): SavingsTransaction? = 
        queries.getSavingsTransactionByServerId(serverId).executeAsOneOrNull()?.let { toSavingsTransaction(it) }
    
    /**
     * Получение транзакций ожидающих синхронизации
     */
    fun getPendingSync(): List<SavingsTransaction> = 
        queries.getSavingsTransactionsPendingSync().executeAsList().map { toSavingsTransaction(it) }
    
    /**
     * Проверка существования по ID
     */
    fun existsById(id: Long): Boolean {
        return queries.savingsTransactionExistsById(id).executeAsOne()
    }

    /**
     * Проверка существования по server ID
     */
    fun existsByServerId(serverId: String): Boolean {
        return queries.savingsTransactionExistsByServerId(serverId).executeAsOne()
    }
    
    /**
     * Удаление всех транзакций
     */
    fun deleteAll() {
        queries.deleteAllSavingsTransactions()
    }
    
    private fun toSavingsTransaction(entity: Savings_transactions): SavingsTransaction {
        return SavingsTransaction(
            id = entity.id,
            userId = entity.user_id,
            savingsGoalId = entity.savings_goal_id,
            amount = entity.amount,
            type = entity.type,
            description = entity.description,
            date = entity.date,
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
 * Модель транзакции накопления
 */
data class SavingsTransaction(
    val id: Long,
    val userId: Long,
    val savingsGoalId: Long,
    val amount: Long,
    val type: String,
    val description: String?,
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String
)

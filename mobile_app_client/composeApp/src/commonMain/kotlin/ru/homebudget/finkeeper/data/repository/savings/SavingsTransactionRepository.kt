package ru.homebudget.finkeeper.data.repository.savings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.Month
import ru.homebudget.finkeeper.data.local.dao.SavingsTransactionDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.model.SavingsTransaction as RemoteSavingsTransaction

/**
 * Репозиторий для работы с транзакциями накоплений
 * Реализует offline-first логику
 */
class SavingsTransactionRepository(
    private val savingsTransactionDao: SavingsTransactionDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }

    /**
     * Получение всех транзакций для цели
     */
    suspend fun getTransactionsByGoal(goalId: Long): Result<List<RemoteSavingsTransaction>> =
        withContext(Dispatchers.Default) {
            try {
                val localTransactions = savingsTransactionDao.getByGoal(goalId)

                val result =
                    localTransactions.map { local ->
                        RemoteSavingsTransaction(
                            id = local.id.toInt(),
                            goalId = local.savingsGoalId.toInt(),
                            amount = local.amount.toDouble(),
                            date = local.date,
                            monthId = null,
                        )
                    }

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Создание новой транзакции накоплений
     */
    suspend fun createTransaction(
        userId: Long,
        goalId: Long,
        monthId: Long? = null,
        amount: Double,
        date: String,
        type: String = "deposit",
    ): Result<RemoteSavingsTransaction> =
        withContext(Dispatchers.Default) {
            println("[SAVINGS-TX] createTransaction START: userId=$userId, goalId=$goalId, monthId=$monthId, amount=$amount, type=$type")
            try {
                val localId =
                    savingsTransactionDao.insert(
                        userId = userId,
                        savingsGoalId = goalId,
                        monthId = monthId,
                        amount = amount.toLong(),
                        type = type,
                        description = null,
                        date = date,
                        serverId = null,
                        syncStatus = SyncStatus.PENDING.value,
                    )

                val localTransaction = savingsTransactionDao.getById(localId)!!
                println("[SAVINGS-TX] Created locally: id=$localId, goalId=$goalId, userId=$userId, amount=$amount")

                val result =
                    RemoteSavingsTransaction(
                        id = localTransaction.id.toInt(),
                        goalId = localTransaction.savingsGoalId.toInt(),
                        amount = localTransaction.amount.toDouble(),
                        date = localTransaction.date,
                        monthId = null,
                    )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = userId,
                    entityType = EntityType.SAVINGS_TRANSACTION.value,
                    entityId = localId,
                    operation = SyncOperation.INSERT.value,
                    payload = null,
                )

                Result.success(result)
            } catch (e: Exception) {
                println("[SAVINGS-TX] createTransaction ERROR: ${e.message}")
                e.printStackTrace()
                Result.error(e)
            }
        }

    /**
     * Удаление транзакции
     */
    suspend fun deleteTransaction(id: Long, month: Month): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val transaction = savingsTransactionDao.getById(id)
                val serverId = transaction?.serverId

                savingsTransactionDao.deleteById(id)

                if (transaction != null && serverId != null) {
                    syncManager.enqueueSync(
                        userId = transaction.userId,
                        entityType = EntityType.SAVINGS_TRANSACTION.value,
                        entityId = id,
                        operation = SyncOperation.DELETE.value,
                        payload = serverId,
                    )
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Синхронизация с сервером
     */
    suspend fun syncWithServer(
        userId: Long,
        goalId: Long,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val remoteTransactions = apiClient.getSavingsTransactions(goalId.toInt())
                val pendingDeleteServerIds =
                    syncManager.getPendingDeleteServerIds(EntityType.SAVINGS_TRANSACTION.value)

                for (remote in remoteTransactions) {
                    if (remote.id.toString() in pendingDeleteServerIds) {
                        continue
                    }

                    val existing = savingsTransactionDao.getByServerId(remote.id.toString())

                    if (existing != null) {
                        if (existing.syncStatus == SyncStatus.PENDING.value) {
                            continue
                        }

                        savingsTransactionDao.update(
                            id = existing.id,
                            savingsGoalId = existing.savingsGoalId,
                            monthId = existing.monthId,
                            amount = remote.amount.toLong(),
                            type = existing.type,
                            description = existing.description,
                            date = remote.date,
                            serverId = remote.id.toString(),
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                    } else {
                        savingsTransactionDao.insert(
                            userId = userId,
                            savingsGoalId = remote.goalId.toLong(),
                            monthId = null,
                            amount = remote.amount.toLong(),
                            type = if (remote.amount >= 0) "deposit" else "withdrawal",
                            description = null,
                            date = remote.date,
                            serverId = remote.id.toString(),
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.error(e)
            }
        }
}

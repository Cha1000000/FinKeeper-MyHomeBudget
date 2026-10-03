package ru.homebudget.finkeeper.data.repository.savings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.Month
import ru.homebudget.finkeeper.data.local.dao.MonthDao
import ru.homebudget.finkeeper.data.local.dao.SavingsGoalDao
import ru.homebudget.finkeeper.data.local.dao.SavingsTransactionDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.shouldApplyRemoteServerSnapshot
import ru.homebudget.finkeeper.data.model.SavingsTransaction as RemoteSavingsTransaction

/**
 * Репозиторий для работы с транзакциями накоплений
 * Реализует offline-first логику
 */
class SavingsTransactionRepository(
    private val savingsTransactionDao: SavingsTransactionDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
    private val monthDao: MonthDao,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }

    /** Сумма ещё не выгруженных пополнений копилок за месяц (см. [unsyncedDepositsTotal]) */
    suspend fun getUnsyncedDepositsTotal(userId: Long, monthLocalId: Long): Double =
        withContext(Dispatchers.Default) {
            unsyncedDepositsTotal(savingsTransactionDao.getAllByUser(userId), monthLocalId)
        }

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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Синхронизация с сервером
     */
    // Pull-синхронизация сериализуется мьютексом: Dashboard и Month ViewModel стартуют
    // параллельно, и две гонящиеся insert-ветки дублировали локальные записи
    private val syncPullMutex = Mutex()

    suspend fun syncWithServer(userId: Long, goalId: Long,): Result<Unit> =
        syncPullMutex.withLock { syncWithServerInternal(userId, goalId) }

    private suspend fun syncWithServerInternal(userId: Long, goalId: Long,): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val goal = savingsGoalDao.getById(goalId) ?: return@withContext Result.success(Unit)
                val serverGoalId = goal.serverId?.toIntOrNull() ?: return@withContext Result.success(Unit)
                val remoteTransactions = apiClient.getSavingsTransactions(serverGoalId)
                val pendingDeleteServerIds =
                    syncManager.getPendingDeleteServerIds(EntityType.SAVINGS_TRANSACTION.value)

                for (remote in remoteTransactions) {
                    if (remote.id.toString() in pendingDeleteServerIds) {
                        continue
                    }

                    val existing = savingsTransactionDao.getByServerId(remote.id.toString())
                    // Месяц пополнения нужен локально: правка транзакции шлёт month_id, и без него
                    // сервер отвязал бы пополнение от месяца (скрытый расход пропал бы из «Свободно»)
                    val localMonthId = remote.monthId?.let { monthDao.getByServerId(it.toString())?.id }

                    if (existing != null) {
                        if (
                            existing.syncStatus != SyncStatus.SYNCED.value ||
                            syncManager.hasActiveQueueOperation(EntityType.SAVINGS_TRANSACTION.value, existing.id)
                        ) {
                            continue
                        }

                        if (!shouldApplyRemoteServerSnapshot(existing.updatedAt, remote.updatedAt)) {
                            continue
                        }

                        savingsTransactionDao.update(
                            id = existing.id,
                            savingsGoalId = existing.savingsGoalId,
                            monthId = existing.monthId ?: localMonthId,
                            amount = remote.amount.toLong(),
                            type = existing.type,
                            description = existing.description,
                            date = remote.date,
                            updatedAt = remote.updatedAt ?: existing.updatedAt,
                            serverId = remote.id.toString(),
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                    } else {
                        savingsTransactionDao.insert(
                            userId = userId,
                            savingsGoalId = goalId,
                            monthId = localMonthId,
                            amount = remote.amount.toLong(),
                            type = if (remote.amount >= 0) "deposit" else "withdrawal",
                            description = null,
                            date = remote.date,
                            createdAt = remote.createdAt,
                            updatedAt = remote.updatedAt,
                            serverId = remote.id.toString(),
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                    }
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }
}

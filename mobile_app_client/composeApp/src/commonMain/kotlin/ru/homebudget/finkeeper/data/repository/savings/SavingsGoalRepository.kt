package ru.homebudget.finkeeper.data.repository.savings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.SavingsGoalDao
import ru.homebudget.finkeeper.data.local.dao.SavingsTransactionDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SavingsGoalNotEmptyException
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.shouldApplyRemoteServerSnapshot
import ru.homebudget.finkeeper.data.model.SavingsGoal as RemoteSavingsGoal

/**
 * Репозиторий для работы с целями накоплений
 * Реализует offline-first логику
 */
class SavingsGoalRepository(
    private val savingsGoalDao: SavingsGoalDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
    private val savingsTransactionDao: SavingsTransactionDao,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }

    /**
     * Получение всех целей накоплений пользователя
     */
    suspend fun getAllSavingsGoals(userId: Long): Result<List<RemoteSavingsGoal>> =
        withContext(Dispatchers.Default) {
            try {
                val localGoals = savingsGoalDao.getAllByUser(userId)

                val result =
                    localGoals.map { local ->
                        RemoteSavingsGoal(
                            id = local.id.toInt(),
                            userId = local.userId.toInt(),
                            name = local.name,
                            targetAmount = local.targetAmount.toDouble(),
                            currentAmount = local.currentAmount.toDouble(),
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
     * Создание новой цели накоплений
     */
    suspend fun createSavingsGoal(
        userId: Long,
        name: String,
        targetAmount: Double,
    ): Result<RemoteSavingsGoal> =
        withContext(Dispatchers.Default) {
            try {
                val localId =
                    savingsGoalDao.insert(
                        userId = userId,
                        name = name,
                        targetAmount = targetAmount.toLong(),
                        currentAmount = 0L,
                        serverId = null,
                        syncStatus = SyncStatus.PENDING.value,
                    )

                val localGoal = savingsGoalDao.getById(localId)!!

                val result =
                    RemoteSavingsGoal(
                        id = localGoal.id.toInt(),
                        userId = localGoal.userId.toInt(),
                        name = localGoal.name,
                        targetAmount = localGoal.targetAmount.toDouble(),
                        currentAmount = localGoal.currentAmount.toDouble(),
                    )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = userId,
                    entityType = EntityType.SAVINGS_GOAL.value,
                    entityId = localId,
                    operation = SyncOperation.INSERT.value,
                    payload = null,
                )

                Result.success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Обновление цели накоплений
     */
    suspend fun updateSavingsGoal(
        id: Long,
        name: String? = null,
        targetAmount: Double? = null,
        currentAmount: Double? = null,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val existing = savingsGoalDao.getById(id) ?: return@withContext Result.error(Exception("Savings goal not found"))

                val newCurrentAmount = currentAmount?.toLong() ?: existing.currentAmount
                println("[SAVINGS-GOAL] updateSavingsGoal: id=$id, name=$name, targetAmount=$targetAmount, currentAmount=$currentAmount")
                println("[SAVINGS-GOAL] updateSavingsGoal: existing.name=${existing.name}, finalName=${name?.takeIf { it.isNotBlank() } ?: existing.name}")
                
                // Используем новое имя только если оно не null и не пустое, иначе сохраняем старое
                val finalName = name?.takeIf { it.isNotBlank() } ?: existing.name
                
                savingsGoalDao.update(
                    id = id,
                    name = finalName,
                    targetAmount = targetAmount?.toLong() ?: existing.targetAmount,
                    currentAmount = newCurrentAmount,
                    color = existing.color,
                    icon = existing.icon,
                    targetDate = existing.targetDate,
                    isAchieved = existing.isAchieved,
                    serverId = existing.serverId,
                    syncStatus = SyncStatus.PENDING.value,
                )
                
                // Проверяем что данные записались
                val updated = savingsGoalDao.getById(id)
                println("[SAVINGS-GOAL] updateSavingsGoal: verified currentAmount=${updated?.currentAmount}")

                // Сумму отправляем, только если пользователь её поменял: иначе сервер ведёт её сам
                // по транзакциям, а локальная может включать ещё не отправленные пополнения
                syncManager.enqueueSync(
                    userId = existing.userId,
                    entityType = EntityType.SAVINGS_GOAL.value,
                    entityId = id,
                    operation = SyncOperation.UPDATE.value,
                    payload = null,
                    goalCurrentAmount = newCurrentAmount.takeIf { it != existing.currentAmount },
                )

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Обновление currentAmount только локально (без синхронизации на сервер).
     * Используется при добавлении транзакции копилки, т.к. сервер сам обновляет currentAmount
     * при получении savings_transaction.
     */
    suspend fun updateCurrentAmountLocally(
        id: Long,
        currentAmount: Double,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val existing = savingsGoalDao.getById(id) ?: return@withContext Result.error(Exception("Savings goal not found"))

                println("[SAVINGS-GOAL] updateCurrentAmountLocally: id=$id, newCurrentAmount=${currentAmount.toLong()}")
                
                savingsGoalDao.update(
                    id = id,
                    name = existing.name,
                    targetAmount = existing.targetAmount,
                    currentAmount = currentAmount.toLong(),
                    color = existing.color,
                    icon = existing.icon,
                    targetDate = existing.targetDate,
                    isAchieved = existing.isAchieved,
                    // Версию записи не меняем: это не правка пользователя, а локальное отражение
                    // транзакции. Иначе ждущий INSERT/UPDATE копилки счёл бы себя устаревшим
                    updatedAt = existing.updatedAt,
                    serverId = existing.serverId,
                    syncStatus = existing.syncStatus, // Не меняем статус синхронизации
                )

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Удаление цели накоплений
     */
    suspend fun deleteSavingsGoal(id: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val goal = savingsGoalDao.getById(id) ?: return@withContext Result.success(Unit)
                // Пополнения копилки уже вычтены из «Свободно» своих месяцев: удаление непустой
                // копилки «потеряло» бы её сумму. Сначала средства выводят или переводят
                if (goal.currentAmount != 0L) return@withContext Result.error(SavingsGoalNotEmptyException())

                // Внешние ключи в локальной БД не включены — транзакции копилки удаляем сами
                // (на сервере их удалит удаление копилки)
                savingsTransactionDao.deleteAllByGoal(id)
                savingsGoalDao.deleteById(id)

                // DELETE ставим и без serverId: тогда очередь просто выбросит ещё не отправленный INSERT
                syncManager.enqueueSync(
                    userId = goal.userId,
                    entityType = EntityType.SAVINGS_GOAL.value,
                    entityId = id,
                    operation = SyncOperation.DELETE.value,
                    payload = goal.serverId,
                )

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

    suspend fun syncWithServer(userId: Long): Result<Unit> =
        syncPullMutex.withLock { syncWithServerInternal(userId) }

    private suspend fun syncWithServerInternal(userId: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val remoteGoals = apiClient.getSavingsGoals()
                val pendingDeleteServerIds = syncManager.getPendingDeleteServerIds(EntityType.SAVINGS_GOAL.value)

                for (remote in remoteGoals) {
                    if (remote.id.toString() in pendingDeleteServerIds) {
                        continue
                    }

                    val existing = savingsGoalDao.getByServerId(remote.id.toString())

                    if (existing != null) {
                        if (
                            existing.syncStatus != SyncStatus.SYNCED.value ||
                            syncManager.hasActiveQueueOperation(EntityType.SAVINGS_GOAL.value, existing.id)
                        ) {
                            println("[SAVINGS-GOAL] syncWithServer: skipping update for id=${existing.id}, name='${existing.name}' - local changes pending")
                            continue
                        }

                        if (!shouldApplyRemoteServerSnapshot(existing.updatedAt, remote.updatedAt)) {
                            continue
                        }
                        
                        savingsGoalDao.update(
                            id = existing.id,
                            name = remote.name,
                            targetAmount = remote.targetAmount.toLong(),
                            currentAmount = remote.currentAmount.toLong(),
                            color = existing.color,
                            icon = existing.icon,
                            targetDate = existing.targetDate,
                            isAchieved = existing.isAchieved,
                            updatedAt = remote.updatedAt ?: existing.updatedAt,
                            serverId = remote.id.toString(),
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                    } else {
                        savingsGoalDao.insert(
                            userId = userId,
                            name = remote.name,
                            targetAmount = remote.targetAmount.toLong(),
                            currentAmount = remote.currentAmount.toLong(),
                            color = null,
                            icon = null,
                            targetDate = null,
                            isAchieved = 0L,
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

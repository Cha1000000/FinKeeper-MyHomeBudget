package ru.homebudget.finkeeper.data.repository.savings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.SavingsGoalDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.model.SavingsGoal as RemoteSavingsGoal

/**
 * Репозиторий для работы с целями накоплений
 * Реализует offline-first логику
 */
class SavingsGoalRepository(
    private val savingsGoalDao: SavingsGoalDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
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
                    userId = currentUserId,
                    entityType = EntityType.SAVINGS_GOAL.value,
                    entityId = localId,
                    operation = SyncOperation.INSERT.value,
                    payload = null,
                )

                Result.success(result)
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

                savingsGoalDao.update(
                    id = id,
                    name = name ?: existing.name,
                    targetAmount = targetAmount?.toLong() ?: existing.targetAmount,
                    currentAmount = currentAmount?.toLong() ?: existing.currentAmount,
                    color = existing.color,
                    icon = existing.icon,
                    targetDate = existing.targetDate,
                    isAchieved = existing.isAchieved,
                    serverId = existing.serverId,
                    syncStatus = SyncStatus.PENDING.value,
                )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = currentUserId,
                    entityType = EntityType.SAVINGS_GOAL.value,
                    entityId = id,
                    operation = SyncOperation.UPDATE.value,
                    payload = null,
                )

                Result.success(Unit)
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
                val goal = savingsGoalDao.getById(id)
                val serverId = goal?.serverId

                savingsGoalDao.deleteById(id)

                if (serverId != null) {
                    syncManager.enqueueSync(
                        userId = currentUserId,
                        entityType = EntityType.SAVINGS_GOAL.value,
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
    suspend fun syncWithServer(userId: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val remoteGoals = apiClient.getSavingsGoals()

                for (remote in remoteGoals) {
                    val existing = savingsGoalDao.getByServerId(remote.id.toString())

                    if (existing != null) {
                        savingsGoalDao.update(
                            id = existing.id,
                            name = remote.name,
                            targetAmount = remote.targetAmount.toLong(),
                            currentAmount = remote.currentAmount.toLong(),
                            color = existing.color,
                            icon = existing.icon,
                            targetDate = existing.targetDate,
                            isAchieved = existing.isAchieved,
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

package ru.homebudget.finkeeper.data.repository.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.BudgetDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.model.Budget as RemoteBudget

/**
 * Репозиторий для работы с бюджетами
 * Реализует offline-first логику
 */
class BudgetRepository(
    private val budgetDao: BudgetDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }

    /**
     * Получение бюджетов по месяцу
     */
    suspend fun getBudgetsByMonth(monthId: Long): Result<List<RemoteBudget>> =
        withContext(Dispatchers.Default) {
            try {
                val localBudgets = budgetDao.getByMonth(monthId)

                val result =
                    localBudgets.map { local ->
                        RemoteBudget(
                            id = local.id.toInt(),
                            monthId = local.monthId.toInt(),
                            categoryId = local.categoryId.toInt(),
                            limitAmount = local.limitAmount.toDouble(),
                        )
                    }

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Создание или обновление бюджета
     */
    suspend fun setBudget(
        userId: Long,
        monthId: Long,
        categoryId: Long,
        limitAmount: Double,
    ): Result<RemoteBudget> =
        withContext(Dispatchers.Default) {
            try {
                val existing = budgetDao.getByMonthAndCategory(monthId, categoryId)

                val budgetId =
                    if (existing != null) {
                        budgetDao.update(
                            id = existing.id,
                            monthId = monthId,
                            categoryId = categoryId,
                            limitAmount = limitAmount.toLong(),
                            serverId = existing.serverId,
                            syncStatus = SyncStatus.PENDING.value,
                        )

                        // Добавляем операцию в очередь синхронизации
                        syncManager.enqueueSync(
                            userId = currentUserId,
                            entityType = EntityType.BUDGET.value,
                            entityId = existing.id,
                            operation = SyncOperation.UPDATE.value,
                            payload = null,
                        )

                        existing.id
                    } else {
                        val newId =
                            budgetDao.insert(
                                userId = userId,
                                monthId = monthId,
                                categoryId = categoryId,
                                limitAmount = limitAmount.toLong(),
                                serverId = null,
                                syncStatus = SyncStatus.PENDING.value,
                            )

                        // Добавляем операцию в очередь синхронизации
                        syncManager.enqueueSync(
                            userId = currentUserId,
                            entityType = EntityType.BUDGET.value,
                            entityId = newId,
                            operation = SyncOperation.INSERT.value,
                            payload = null,
                        )

                        newId
                    }

                val localBudget = budgetDao.getById(budgetId)!!

                val result =
                    RemoteBudget(
                        id = localBudget.id.toInt(),
                        monthId = localBudget.monthId.toInt(),
                        categoryId = localBudget.categoryId.toInt(),
                        limitAmount = localBudget.limitAmount.toDouble(),
                    )

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Удаление бюджета
     */
    suspend fun deleteBudget(id: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val budget = budgetDao.getById(id)
                val serverId = budget?.serverId

                budgetDao.deleteById(id)

                if (serverId != null) {
                    syncManager.enqueueSync(
                        userId = currentUserId,
                        entityType = EntityType.BUDGET.value,
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
        monthId: Long,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val remoteBudgets = apiClient.getBudgets(monthId.toInt())

                for (remote in remoteBudgets) {
                    val existing = budgetDao.getByServerId(remote.id.toString())

                    if (existing != null) {
                        budgetDao.update(
                            id = existing.id,
                            monthId = monthId,
                            categoryId = remote.categoryId.toLong(),
                            limitAmount = remote.limitAmount.toLong(),
                            serverId = remote.id.toString(),
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                    } else {
                        budgetDao.insert(
                            userId = userId,
                            monthId = monthId,
                            categoryId = remote.categoryId.toLong(),
                            limitAmount = remote.limitAmount.toLong(),
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

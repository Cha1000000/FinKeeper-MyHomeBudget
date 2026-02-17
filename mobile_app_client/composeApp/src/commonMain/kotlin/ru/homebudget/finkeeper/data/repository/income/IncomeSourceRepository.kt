package ru.homebudget.finkeeper.data.repository.income

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.IncomeSourceDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.model.IncomeSource as RemoteIncomeSource

/**
 * Репозиторий для работы с источниками дохода
 * Реализует offline-first логику
 */
class IncomeSourceRepository(
    private val incomeSourceDao: IncomeSourceDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }

    /**
     * Получение всех источников дохода пользователя
     */
    suspend fun getAllIncomeSources(userId: Long): Result<List<RemoteIncomeSource>> =
        withContext(Dispatchers.Default) {
            try {
                val localSources = incomeSourceDao.getAllByUser(userId)

                val result =
                    localSources.map { local ->
                        RemoteIncomeSource(
                            id = local.id.toInt(),
                            userId = local.userId.toInt(),
                            name = local.name,
                            isActive = local.isActive.toInt(),
                        )
                    }

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Получение активных источников дохода пользователя
     */
    suspend fun getActiveIncomeSources(userId: Long): Result<List<RemoteIncomeSource>> =
        withContext(Dispatchers.Default) {
            try {
                val localSources = incomeSourceDao.getActiveByUser(userId)

                val result =
                    localSources.map { local ->
                        RemoteIncomeSource(
                            id = local.id.toInt(),
                            userId = local.userId.toInt(),
                            name = local.name,
                            isActive = local.isActive.toInt(),
                        )
                    }

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Создание нового источника дохода
     */
    suspend fun createIncomeSource(
        userId: Long,
        name: String,
    ): Result<RemoteIncomeSource> =
        withContext(Dispatchers.Default) {
            try {
                val maxSortOrder = incomeSourceDao.getMaxSortOrder(userId)
                val localId =
                    incomeSourceDao.insert(
                        userId = userId,
                        name = name,
                        sortOrder = maxSortOrder + 1,
                        isActive = 1L,
                        serverId = null,
                        syncStatus = SyncStatus.PENDING.value,
                    )

                val localSource = incomeSourceDao.getById(localId)!!

                val result =
                    RemoteIncomeSource(
                        id = localSource.id.toInt(),
                        userId = localSource.userId.toInt(),
                        name = localSource.name,
                        isActive = localSource.isActive.toInt(),
                    )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = currentUserId,
                    entityType = EntityType.INCOME_SOURCE.value,
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
     * Обновление источника дохода
     */
    suspend fun updateIncomeSource(
        id: Long,
        name: String? = null,
        isActive: Boolean? = null,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val existing = incomeSourceDao.getById(id) ?: return@withContext Result.error(Exception("Income source not found"))

                incomeSourceDao.update(
                    id = id,
                    name = name ?: existing.name,
                    sortOrder = existing.sortOrder,
                    isActive = when (isActive) {
                        true -> 1L
                        false -> 0L
                        null -> existing.isActive
                    },
                    serverId = existing.serverId,
                    syncStatus = SyncStatus.PENDING.value,
                )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = currentUserId,
                    entityType = EntityType.INCOME_SOURCE.value,
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
     * Удаление источника дохода
     */
    suspend fun deleteIncomeSource(id: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                incomeSourceDao.deleteById(id)

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = currentUserId,
                    entityType = EntityType.INCOME_SOURCE.value,
                    entityId = id,
                    operation = SyncOperation.DELETE.value,
                    payload = null,
                )

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
                val remoteSources = apiClient.getIncomeSources()

                for (remote in remoteSources) {
                    val existing = incomeSourceDao.getByServerId(remote.id.toString())

                    if (existing != null) {
                        incomeSourceDao.update(
                            id = existing.id,
                            name = remote.name,
                            sortOrder = 0L, // Default sort order
                            isActive = remote.isActive.toLong(),
                            serverId = remote.id.toString(),
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                    } else {
                        incomeSourceDao.insert(
                            userId = userId,
                            name = remote.name,
                            sortOrder = 0L,
                            isActive = remote.isActive.toLong(),
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

package ru.homebudget.finkeeper.data.repository.income

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import ru.homebudget.finkeeper.data.repository.shouldApplyRemoteServerSnapshot
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
                            isFixed = local.isFixed.toInt(),
                            fixedAmount = local.fixedAmount?.let { it.toDouble() / 100.0 },
                            autoDay = local.autoDay?.toInt(),
                            requireConfirm = local.requireConfirm.toInt(),
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
                            isFixed = local.isFixed.toInt(),
                            fixedAmount = local.fixedAmount?.let { it.toDouble() / 100.0 },
                            autoDay = local.autoDay?.toInt(),
                            requireConfirm = local.requireConfirm.toInt(),
                        )
                    }

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Маппинг localId → serverId для источников дохода (используется при reorder)
     */
    suspend fun getServerIdMapping(userId: Long): Map<Int, Int> =
        withContext(Dispatchers.Default) {
            val localSources = incomeSourceDao.getAllByUser(userId)
            localSources.mapNotNull { local ->
                val sid = local.serverId?.toIntOrNull() ?: return@mapNotNull null
                local.id.toInt() to sid
            }.toMap()
        }

    /**
     * Создание нового источника дохода
     */
    suspend fun createIncomeSource(
        userId: Long,
        name: String,
        isFixed: Boolean = false,
        fixedAmount: Double? = null,
        autoDay: Int? = null,
        requireConfirm: Boolean = false,
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
                        isFixed = if (isFixed) 1L else 0L,
                        fixedAmount = fixedAmount?.let { (it * 100).toLong() },
                        autoDay = autoDay?.toLong(),
                        requireConfirm = if (requireConfirm) 1L else 0L,
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
        isFixed: Boolean? = null,
        fixedAmount: Double? = null,
        autoDay: Int? = null,
        requireConfirm: Boolean? = null,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val existing = incomeSourceDao.getById(id) ?: return@withContext Result.error(Exception("Income source not found"))

                val newIsFixed = when (isFixed) {
                    true -> 1L
                    false -> 0L
                    null -> existing.isFixed
                }

                incomeSourceDao.update(
                    id = id,
                    name = name ?: existing.name,
                    sortOrder = existing.sortOrder,
                    isActive = when (isActive) {
                        true -> 1L
                        false -> 0L
                        null -> existing.isActive
                    },
                    isFixed = newIsFixed,
                    fixedAmount = if (newIsFixed == 1L) fixedAmount?.let { (it * 100).toLong() } ?: existing.fixedAmount else null,
                    autoDay = if (newIsFixed == 1L) autoDay?.toLong() ?: existing.autoDay else null,
                    requireConfirm = if (newIsFixed == 1L) {
                        when (requireConfirm) {
                            true -> 1L
                            false -> 0L
                            null -> existing.requireConfirm
                        }
                    } else 0L,
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
                val source = incomeSourceDao.getById(id)
                val serverId = source?.serverId

                incomeSourceDao.deleteById(id)

                if (serverId != null) {
                    syncManager.enqueueSync(
                        userId = currentUserId,
                        entityType = EntityType.INCOME_SOURCE.value,
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
    // Pull-синхронизация сериализуется мьютексом: Dashboard и Month ViewModel стартуют
    // параллельно, и две гонящиеся insert-ветки дублировали локальные записи
    private val syncPullMutex = Mutex()

    suspend fun syncWithServer(userId: Long): Result<Unit> =
        syncPullMutex.withLock { syncWithServerInternal(userId) }

    private suspend fun syncWithServerInternal(userId: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val remoteSources = apiClient.getIncomeSources()
                val pendingDeleteServerIds = syncManager.getPendingDeleteServerIds(EntityType.INCOME_SOURCE.value)

                for (remote in remoteSources) {
                    if (remote.id.toString() in pendingDeleteServerIds) {
                        continue
                    }

                    val existing = incomeSourceDao.getByServerId(remote.id.toString())

                    if (existing != null) {
                        if (
                            existing.syncStatus != SyncStatus.SYNCED.value ||
                            syncManager.hasActiveQueueOperation(EntityType.INCOME_SOURCE.value, existing.id)
                        ) {
                            continue
                        }

                        if (!shouldApplyRemoteServerSnapshot(existing.updatedAt, remote.updatedAt)) {
                            continue
                        }

                        incomeSourceDao.update(
                            id = existing.id,
                            name = remote.name,
                            sortOrder = remote.sortOrder.toLong(),
                            isActive = remote.isActive.toLong(),
                            isFixed = remote.isFixed.toLong(),
                            fixedAmount = remote.fixedAmount?.let { (it * 100).toLong() },
                            autoDay = remote.autoDay?.toLong(),
                            requireConfirm = remote.requireConfirm.toLong(),
                            updatedAt = remote.updatedAt ?: existing.updatedAt,
                            serverId = remote.id.toString(),
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                    } else {
                        incomeSourceDao.insert(
                            userId = userId,
                            name = remote.name,
                            sortOrder = remote.sortOrder.toLong(),
                            isActive = remote.isActive.toLong(),
                            isFixed = remote.isFixed.toLong(),
                            fixedAmount = remote.fixedAmount?.let { (it * 100).toLong() },
                            autoDay = remote.autoDay?.toLong(),
                            requireConfirm = remote.requireConfirm.toLong(),
                            createdAt = remote.createdAt,
                            updatedAt = remote.updatedAt,
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

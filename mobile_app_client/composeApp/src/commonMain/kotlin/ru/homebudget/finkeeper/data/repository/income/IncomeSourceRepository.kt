package ru.homebudget.finkeeper.data.repository.income

import kotlinx.coroutines.CancellationException
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
import ru.homebudget.finkeeper.data.repository.BlankNameException
import ru.homebudget.finkeeper.data.repository.DuplicateNameException
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.mergeReorderedSubset
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Новый порядок источников дохода: [reorderedIds] — переставленные записи (весь список или его часть,
     * например только нефиксированные), остальные остаются на своих местах. Порядок сохраняется
     * локально сразу и уходит на сервер через очередь — одной операцией на весь список
     * (следующая перестановка заменяет ещё не отправленную).
     */
    suspend fun reorderIncomeSources(userId: Long, reorderedIds: List<Long>): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val current = incomeSourceDao.getActiveByUser(userId).map { it.id }
                incomeSourceDao.applySortOrder(mergeReorderedSubset(current, reorderedIds))
                syncManager.enqueueSync(
                    userId = userId,
                    entityType = EntityType.INCOME_SOURCE_ORDER.value,
                    entityId = 0L,
                    operation = SyncOperation.UPDATE.value,
                )
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
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
        isFixed: Boolean = false,
        fixedAmount: Double? = null,
        autoDay: Int? = null,
        requireConfirm: Boolean = false,
    ): Result<RemoteIncomeSource> =
        withContext(Dispatchers.Default) {
            try {
                val name = name.trim()
                if (name.isEmpty()) return@withContext Result.error(BlankNameException())

                val existingByName = incomeSourceDao.getByName(userId, name)
                if (existingByName != null && existingByName.isActive == 0L) {
                    // Удалённый источник с тем же именем: восстанавливаем его вместе с историей
                    // доходов (так же поступает сервер, имена у него уникальны)
                    incomeSourceDao.update(
                        id = existingByName.id,
                        name = existingByName.name,
                        sortOrder = incomeSourceDao.getMaxSortOrder(userId) + 1,
                        isActive = 1L,
                        isFixed = if (isFixed) 1L else 0L,
                        fixedAmount = if (isFixed) fixedAmount?.let { (it * 100).toLong() } else null,
                        autoDay = if (isFixed) autoDay?.toLong() else null,
                        requireConfirm = if (isFixed && requireConfirm) 1L else 0L,
                        serverId = existingByName.serverId,
                        syncStatus = SyncStatus.PENDING.value,
                    )
                    syncManager.enqueueSync(
                        userId = currentUserId,
                        entityType = EntityType.INCOME_SOURCE.value,
                        entityId = existingByName.id,
                        operation = SyncOperation.UPDATE.value,
                        payload = null,
                        reactivate = true,
                    )
                    println("[INCOME-SOURCE] createIncomeSource: reactivated id=${existingByName.id}, name='$name'")
                }
                if (existingByName != null) {
                    val restored = incomeSourceDao.getById(existingByName.id) ?: existingByName
                    val result = RemoteIncomeSource(
                        id = restored.id.toInt(),
                        userId = restored.userId.toInt(),
                        name = restored.name,
                        isActive = restored.isActive.toInt(),
                        isFixed = restored.isFixed.toInt(),
                        fixedAmount = restored.fixedAmount?.let { it.toDouble() / 100.0 },
                        autoDay = restored.autoDay?.toInt(),
                        requireConfirm = restored.requireConfirm.toInt(),
                    )
                    return@withContext Result.success(result)
                }

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
            } catch (e: CancellationException) {
                throw e
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
                val newName = name?.trim() ?: existing.name
                if (newName != existing.name) {
                    if (newName.isEmpty()) return@withContext Result.error(BlankNameException())
                    // Сервер хранит имена уникальными (вместе с удалёнными) и отклонит переименование
                    val sameName = incomeSourceDao.getByName(existing.userId, newName)
                    if (sameName != null && sameName.id != id) return@withContext Result.error(DuplicateNameException(newName))
                }

                val newIsFixed = when (isFixed) {
                    true -> 1L
                    false -> 0L
                    null -> existing.isFixed
                }

                incomeSourceDao.update(
                    id = id,
                    name = newName,
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
                val remoteSources = apiClient.getIncomeSources()
                val pendingDeleteServerIds = syncManager.getPendingDeleteServerIds(EntityType.INCOME_SOURCE.value)
                // Свой порядок ещё не отправлен — серверный его не перетирает
                val keepLocalOrder = syncManager.hasActiveQueueOperation(EntityType.INCOME_SOURCE_ORDER.value, 0L)

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
                            sortOrder = if (keepLocalOrder) existing.sortOrder else remote.sortOrder.toLong(),
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
                        val byName = incomeSourceDao.getByName(userId, remote.name)
                        if (byName != null) {
                            incomeSourceDao.update(
                                id = byName.id,
                                name = remote.name,
                                sortOrder = remote.sortOrder.toLong(),
                                isActive = remote.isActive.toLong(),
                                isFixed = remote.isFixed.toLong(),
                                fixedAmount = remote.fixedAmount?.let { (it * 100).toLong() },
                                autoDay = remote.autoDay?.toLong(),
                                requireConfirm = remote.requireConfirm.toLong(),
                                updatedAt = remote.updatedAt ?: byName.updatedAt,
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
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }
}

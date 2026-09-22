package ru.homebudget.finkeeper.data.repository.category

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.CategoryDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.shouldApplyRemoteServerSnapshot
import ru.homebudget.finkeeper.data.model.Category as RemoteCategory

/**
 * Репозиторий для работы с категориями
 * Реализует offline-first логику
 */
class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }

    /**
     * Получение всех категорий пользователя
     * Сначала читает из локальной БД, затем синхронизирует с сервером
     */
    suspend fun getAllCategories(userId: Long): Result<List<RemoteCategory>> =
        withContext(Dispatchers.Default) {
            try {
                // Сначала читаем из локальной БД
                val localCategories = categoryDao.getAllByUser(userId)

                // Преобразуем локальные модели в удалённые
                val result =
                    localCategories.map { local ->
                        RemoteCategory(
                            id = local.id.toInt(),
                            userId = local.userId.toInt(),
                            name = local.name,
                            sortOrder = local.sortOrder.toInt(),
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
     * Получение активных категорий пользователя
     */
    suspend fun getActiveCategories(userId: Long): Result<List<RemoteCategory>> =
        withContext(Dispatchers.Default) {
            try {
                val localCategories = categoryDao.getActiveByUser(userId)

                val result =
                    localCategories.map { local ->
                        RemoteCategory(
                            id = local.id.toInt(),
                            userId = local.userId.toInt(),
                            name = local.name,
                            sortOrder = local.sortOrder.toInt(),
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
     * Получение категорий по типу
     */
    suspend fun getCategoriesByType(
        userId: Long,
        type: String,
    ): Result<List<RemoteCategory>> =
        withContext(Dispatchers.Default) {
            try {
                val localCategories =
                    when (type) {
                        "expense" -> categoryDao.getExpenseCategories(userId)
                        "income" -> categoryDao.getIncomeCategories(userId)
                        else -> emptyList()
                    }

                val result =
                    localCategories.map { local ->
                        RemoteCategory(
                            id = local.id.toInt(),
                            userId = local.userId.toInt(),
                            name = local.name,
                            sortOrder = local.sortOrder.toInt(),
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
     * Маппинг localId → serverId для категорий (используется при reorder)
     */
    suspend fun getServerIdMapping(userId: Long): Map<Int, Int> =
        withContext(Dispatchers.Default) {
            val localCategories = categoryDao.getAllByUser(userId)
            localCategories.mapNotNull { local ->
                val sid = local.serverId?.toIntOrNull() ?: return@mapNotNull null
                local.id.toInt() to sid
            }.toMap()
        }

    /**
     * Создание новой категории
     */
    suspend fun createCategory(
        userId: Long,
        name: String,
        type: String,
        icon: String? = null,
        color: String? = null,
        isFixed: Boolean = false,
        fixedAmount: Double? = null,
        autoDay: Int? = null,
        requireConfirm: Boolean = false,
    ): Result<RemoteCategory> =
        withContext(Dispatchers.Default) {
            try {
                val existingByName = categoryDao.getByName(userId, name)
                if (existingByName != null) {
                    val result = RemoteCategory(
                        id = existingByName.id.toInt(),
                        userId = existingByName.userId.toInt(),
                        name = existingByName.name,
                        sortOrder = existingByName.sortOrder.toInt(),
                        isActive = existingByName.isActive.toInt(),
                        isFixed = existingByName.isFixed.toInt(),
                        fixedAmount = existingByName.fixedAmount?.let { it.toDouble() / 100.0 },
                        autoDay = existingByName.autoDay?.toInt(),
                        requireConfirm = existingByName.requireConfirm.toInt(),
                    )
                    return@withContext Result.success(result)
                }

                // Сначала сохраняем локально
                val maxSortOrder = categoryDao.getMaxSortOrder(userId) ?: 0L
                val now = Clock.System.now().toString()
                val localId =
                    categoryDao.insertAndReturn(
                        userId = userId,
                        name = name,
                        type = type,
                        icon = icon,
                        color = color,
                        sortOrder = maxSortOrder + 1,
                        isActive = 1L,
                        isFixed = if (isFixed) 1L else 0L,
                        fixedAmount = fixedAmount?.let { (it * 100).toLong() },
                        autoDay = autoDay?.toLong(),
                        requireConfirm = if (requireConfirm) 1L else 0L,
                        createdAt = now,
                        updatedAt = now,
                        serverId = null,
                        syncStatus = SyncStatus.PENDING.value,
                    )

                val localCategory = categoryDao.getById(localId)!!

                val result =
                    RemoteCategory(
                        id = localCategory.id.toInt(),
                        userId = localCategory.userId.toInt(),
                        name = localCategory.name,
                        sortOrder = localCategory.sortOrder.toInt(),
                        isActive = localCategory.isActive.toInt(),
                    )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = currentUserId,
                    entityType = EntityType.CATEGORY.value,
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
     * Обновление категории
     */
    suspend fun updateCategory(
        id: Long,
        name: String? = null,
        icon: String? = null,
        color: String? = null,
        isActive: Boolean? = null,
        isFixed: Boolean? = null,
        fixedAmount: Double? = null,
        autoDay: Int? = null,
        requireConfirm: Boolean? = null,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val existing = categoryDao.getById(id) ?: return@withContext Result.error(Exception("Category not found"))
                val now = Clock.System.now().toString()

                val newIsFixed = when (isFixed) {
                    true -> 1L
                    false -> 0L
                    null -> existing.isFixed
                }

                categoryDao.update(
                    id = id,
                    name = name ?: existing.name,
                    type = existing.type,
                    icon = icon ?: existing.icon,
                    color = color ?: existing.color,
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
                    updatedAt = now,
                    serverId = existing.serverId,
                    syncStatus = SyncStatus.PENDING.value,
                )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = currentUserId,
                    entityType = EntityType.CATEGORY.value,
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
     * Удаление категории
     */
    suspend fun deleteCategory(id: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val category = categoryDao.getById(id)
                val serverId = category?.serverId

                categoryDao.deleteById(id)

                if (serverId != null) {
                    syncManager.enqueueSync(
                        userId = currentUserId,
                        entityType = EntityType.CATEGORY.value,
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
                val remoteCategories = apiClient.getCategories()
                val pendingDeleteServerIds = syncManager.getPendingDeleteServerIds(EntityType.CATEGORY.value)

                for (remote in remoteCategories) {
                    if (remote.id.toString() in pendingDeleteServerIds) {
                        continue
                    }

                    val existing = categoryDao.getByServerId(remote.id.toString())

                    if (existing != null) {
                        if (
                            existing.syncStatus != SyncStatus.SYNCED.value ||
                            syncManager.hasActiveQueueOperation(EntityType.CATEGORY.value, existing.id)
                        ) {
                            continue
                        }

                        if (!shouldApplyRemoteServerSnapshot(existing.updatedAt, remote.updatedAt)) {
                            continue
                        }

                        categoryDao.update(
                            id = existing.id,
                            name = remote.name,
                            type = existing.type,
                            icon = existing.icon,
                            color = existing.color,
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
                        val remoteCreatedAt = remote.createdAt ?: remote.updatedAt ?: Clock.System.now().toString()
                        val remoteUpdatedAt = remote.updatedAt ?: remote.createdAt ?: remoteCreatedAt
                        val byName = categoryDao.getByName(userId, remote.name)
                        if (byName != null) {
                            categoryDao.update(
                                id = byName.id,
                                name = remote.name,
                                type = byName.type,
                                icon = byName.icon,
                                color = byName.color,
                                sortOrder = remote.sortOrder.toLong(),
                                isActive = remote.isActive.toLong(),
                                isFixed = remote.isFixed.toLong(),
                                fixedAmount = remote.fixedAmount?.let { (it * 100).toLong() },
                                autoDay = remote.autoDay?.toLong(),
                                requireConfirm = remote.requireConfirm.toLong(),
                                updatedAt = remoteUpdatedAt,
                                serverId = remote.id.toString(),
                                syncStatus = SyncStatus.SYNCED.value,
                            )
                        } else {
                            categoryDao.insert(
                                userId = userId,
                                name = remote.name,
                                type = "expense",
                                icon = null,
                                color = null,
                                sortOrder = remote.sortOrder.toLong(),
                                isActive = remote.isActive.toLong(),
                                isFixed = remote.isFixed.toLong(),
                                fixedAmount = remote.fixedAmount?.let { (it * 100).toLong() },
                                autoDay = remote.autoDay?.toLong(),
                                requireConfirm = remote.requireConfirm.toLong(),
                                createdAt = remoteCreatedAt,
                                updatedAt = remoteUpdatedAt,
                                serverId = remote.id.toString(),
                                syncStatus = SyncStatus.SYNCED.value,
                            )
                        }
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    suspend fun updateCategorySortOrder(categoryId: Long, sortOrder: Long) {
        withContext(Dispatchers.Default) {
            categoryDao.updateSortOrder(categoryId, sortOrder)
        }
    }
}

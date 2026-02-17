package ru.homebudget.finkeeper.data.repository.category

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
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
                        )
                    }

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
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
    ): Result<RemoteCategory> =
        withContext(Dispatchers.Default) {
            try {
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
                        isActive = 1L, // isActive = true -> 1L
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
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val existing = categoryDao.getById(id) ?: return@withContext Result.error(Exception("Category not found"))
                val now = Clock.System.now().toString()

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
                categoryDao.deleteById(id)

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = currentUserId,
                    entityType = EntityType.CATEGORY.value,
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
                // Получаем данные с сервера
                val remoteCategories = apiClient.getCategories()
                val now = Clock.System.now().toString()

                // Обновляем локальные данные
                for (remote in remoteCategories) {
                    val existing = categoryDao.getByServerId(remote.id.toString())

                    if (existing != null) {
                        // Обновляем существующую
                        categoryDao.update(
                            id = existing.id,
                            name = remote.name,
                            type = "expense", // Default type
                            icon = null,
                            color = null,
                            sortOrder = remote.sortOrder.toLong(),
                            isActive = remote.isActive.toLong(),
                            updatedAt = now,
                            serverId = remote.id.toString(),
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                    } else {
                        // Создаём новую
                        categoryDao.insert(
                            userId = userId,
                            name = remote.name,
                            type = "expense", // Default type
                            icon = null,
                            color = null,
                            sortOrder = remote.sortOrder.toLong(),
                            isActive = remote.isActive.toLong(),
                            createdAt = now,
                            updatedAt = now,
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

    suspend fun updateCategorySortOrder(categoryId: Long, sortOrder: Long) {
        withContext(Dispatchers.Default) {
            categoryDao.updateSortOrder(categoryId, sortOrder)
        }
    }
}

package ru.homebudget.finkeeper.data.local.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Sync_queue
import ru.homebudget.finkeeper.data.local.model.SyncQueueStatus

/**
 * Data Access Object для операций с очередью синхронизации
 */
class SyncQueueDao(
    private val database: FinKeeperDatabase
) {
    private val queries = database.finKeeperDatabaseQueries

    /**
     * Вставка нового элемента в очередь
     */
    fun insert(
        userId: Long,
        entityType: String,
        entityId: Long,
        operation: String,
        payload: String? = null
    ) {
        queries.insertSyncQueueItem(
            user_id = userId,
            entity_type = entityType,
            entity_id = entityId,
            operation = operation,
            payload = payload,
            created_at = getCurrentTimestamp(),
            status = SyncQueueStatus.PENDING.value
        )
    }

    /**
     * Обновление статуса элемента
     */
    fun updateStatus(id: Long, status: String, errorMessage: String? = null) {
        queries.updateSyncQueueItemStatus(
            status = status,
            error_message = errorMessage,
            id = id
        )
    }

    /**
     * Отметка элемента как завершённого
     */
    fun markCompleted(id: Long) {
        queries.updateSyncQueueItemCompleted(id)
    }

    /**
     * Удаление элемента по ID
     */
    fun deleteById(id: Long) {
        queries.deleteSyncQueueItemById(id)
    }

    /**
     * Удаление элемента по типу и ID сущности
     */
    fun deleteByTypeAndId(entityType: String, entityId: Long) {
        queries.deleteSyncQueueItemByIdAndType(
            entity_type = entityType,
            entity_id = entityId
        )
    }

    /**
     * Удаление всех элементов пользователя
     */
    fun deleteAllByUser(userId: Long) {
        queries.deleteAllSyncQueueItemsByUser(userId)
    }

    /**
     * Получение элемента по ID
     */
    fun getById(id: Long): SyncQueueItem? {
        return queries.getSyncQueueItemById(id).executeAsOneOrNull()?.let { toSyncQueueItem(it) }
    }

    /**
     * Получение ожидающих элементов
     */
    fun getPendingItems(limit: Long = 50): List<SyncQueueItem> {
        return queries.getPendingSyncQueueItems(limit).executeAsList().map { toSyncQueueItem(it) }
    }

    /**
     * Получение неудачных элементов
     */
    fun getFailedItems(limit: Long = 50): List<SyncQueueItem> {
        return queries.getFailedSyncQueueItems(limit).executeAsList().map { toSyncQueueItem(it) }
    }

    /**
     * Получение всех элементов пользователя
     */
    fun getAllByUser(userId: Long): List<SyncQueueItem> {
        return queries.getAllSyncQueueItemsByUser(userId).executeAsList().map { toSyncQueueItem(it) }
    }

    /**
     * Получение количества ожидающих элементов
     */
    fun getPendingCount(): Long {
        return queries.getSyncQueueItemCount().executeAsOne()
    }

    /**
     * Проверка существования элемента
     */
    fun exists(entityType: String, entityId: Long): Boolean {
        return queries.syncQueueItemExists(
            entity_type = entityType,
            entity_id = entityId
        ).executeAsOne()
    }

    /**
     * Очистка всех элементов
     */
    fun deleteAll() {
        queries.deleteAllSyncQueueItems()
    }

    /**
     * Очистка завершённых элементов
     */
    fun clearCompleted() {
        queries.clearCompletedSyncQueueItems()
    }

    /**
     * Повтор неудачных операций
     */
    fun retryFailed() {
        queries.updateFailedToPending()
    }

    /**
     * Flow для отслеживания ожидающих элементов
     */
    fun observePendingItems(limit: Long = 50): Flow<List<SyncQueueItem>> {
        return queries.getPendingSyncQueueItems(limit).asFlow().mapToList().map { list ->
            list.map { toSyncQueueItem(it) }
        }
    }

    /**
     * Flow для отслеживания количества ожидающих элементов
     */
    fun observePendingCount(): Flow<Long> {
        return queries.getSyncQueueItemCount().asFlow().map { it.executeAsOne() }
    }

    private fun toSyncQueueItem(entity: Sync_queue): SyncQueueItem {
        return SyncQueueItem(
            id = entity.id,
            userId = entity.user_id,
            entityType = entity.entity_type,
            entityId = entity.entity_id,
            operation = entity.operation,
            payload = entity.payload,
            createdAt = entity.created_at,
            retryCount = entity.retry_count.toInt(),
            lastAttempt = entity.last_attempt,
            status = entity.status,
            errorMessage = entity.error_message
        )
    }

    private fun getCurrentTimestamp(): String {
        return kotlinx.datetime.Clock.System.now().toString()
    }
}

/**
 * Модель элемента очереди синхронизации
 */
data class SyncQueueItem(
    val id: Long,
    val userId: Long,
    val entityType: String,
    val entityId: Long,
    val operation: String,
    val payload: String?,
    val createdAt: String,
    val retryCount: Int,
    val lastAttempt: String?,
    val status: String,
    val errorMessage: String?
)

package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Income_sources

/**
 * Data Access Object для операций с источниками дохода
 */
class IncomeSourceDao(
    private val database: FinKeeperDatabase
) {
    private val queries = database.finKeeperDatabaseQueries

    /**
     * Вставка нового источника дохода
     */
    fun insert(
        userId: Long,
        name: String,
        sortOrder: Long = 0L,
        isActive: Long = 1L,
        serverId: String? = null,
        syncStatus: String = "synced"
    ): Long {
        val now = getCurrentTimestamp()
        queries.insertIncomeSource(
            user_id = userId,
            name = name,
            sort_order = sortOrder,
            is_active = isActive,
            created_at = now,
            updated_at = now,
            server_id = serverId,
            sync_status = syncStatus
        )
        return queries.lastInsertRowId().executeAsOne()
    }

    /**
     * Обновление источника дохода
     */
    fun update(
        id: Long,
        name: String,
        sortOrder: Long,
        isActive: Long,
        serverId: String? = null,
        syncStatus: String = "synced"
    ) {
        val now = getCurrentTimestamp()
        queries.updateIncomeSourceById(
            name = name,
            sort_order = sortOrder,
            is_active = isActive,
            updated_at = now,
            server_id = serverId,
            sync_status = syncStatus,
            id = id
        )
    }

    /**
     * Обновление статуса синхронизации
     */
    fun updateSyncStatus(id: Long, syncStatus: String, serverId: String? = null) {
        queries.updateIncomeSourceSyncStatus(
            sync_status = syncStatus,
            server_id = serverId,
            id = id
        )
    }

    /**
     * Удаление источника дохода
     */
    fun deleteById(id: Long) {
        queries.deleteIncomeSourceById(id)
    }

    /**
     * Удаление всех источников дохода пользователя
     */
    fun deleteAllByUser(userId: Long) {
        queries.deleteAllIncomeSourcesByUser(userId)
    }

    /**
     * Получение источника дохода по ID
     */
    fun getById(id: Long): IncomeSource? {
        return queries.getIncomeSourceById(id).executeAsOneOrNull()?.let { toIncomeSource(it) }
    }

    /**
     * Получение всех источников дохода пользователя
     */
    fun getAllByUser(userId: Long): List<IncomeSource> {
        return queries.getAllIncomeSourcesByUser(userId).executeAsList().map { toIncomeSource(it) }
    }

    /**
     * Получение активных источников дохода пользователя
     */
    fun getActiveByUser(userId: Long): List<IncomeSource> {
        return queries.getActiveIncomeSourcesByUser(userId).executeAsList().map { toIncomeSource(it) }
    }

    /**
     * Получение источников дохода ожидающих синхронизации
     */
    fun getPendingSync(): List<IncomeSource> {
        return queries.getIncomeSourcesPendingSync().executeAsList().map { toIncomeSource(it) }
    }

    /**
     * Получение количества источников дохода ожидающих синхронизации
     */
    fun getPendingSyncCount(): Long {
        return queries.getIncomeSourcesPendingSyncCount().executeAsOne()
    }

    /**
     * Получение максимального порядка сортировки
     */
    fun getMaxSortOrder(userId: Long): Long {
        return queries.getMaxIncomeSourceSortOrder(userId).executeAsOne()
    }

    /**
     * Проверка существования по ID
     */
    fun existsById(id: Long): Boolean {
        return queries.incomeSourceExistsById(id).executeAsOne()
    }

    /**
     * Проверка существования по server ID
     */
    fun existsByServerId(serverId: String): Boolean {
        return queries.incomeSourceExistsByServerId(serverId).executeAsOne()
    }

    /**
     * Получение по server ID
     */
    fun getByServerId(serverId: String): IncomeSource? {
        return queries.getIncomeSourceByServerId(serverId).executeAsOneOrNull()?.let { toIncomeSource(it) }
    }

    /**
     * Удаление всех источников дохода
     */
    fun deleteAll() {
        queries.deleteAllIncomeSources()
    }

    private fun toIncomeSource(entity: Income_sources): IncomeSource {
        return IncomeSource(
            id = entity.id,
            userId = entity.user_id,
            name = entity.name,
            sortOrder = entity.sort_order,
            isActive = entity.is_active,
            createdAt = entity.created_at,
            updatedAt = entity.updated_at,
            serverId = entity.server_id,
            syncStatus = entity.sync_status
        )
    }

    private fun getCurrentTimestamp(): String {
        return kotlinx.datetime.Clock.System.now().toString()
    }
}

/**
 * Модель источника дохода
 */
data class IncomeSource(
    val id: Long,
    val userId: Long,
    val name: String,
    val sortOrder: Long,
    val isActive: Long,
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String
)

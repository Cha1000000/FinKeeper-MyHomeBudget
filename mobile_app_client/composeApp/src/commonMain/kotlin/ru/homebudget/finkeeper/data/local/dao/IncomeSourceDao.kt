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
        isFixed: Long = 0L,
        fixedAmount: Long? = null,
        autoDay: Long? = null,
        requireConfirm: Long = 0L,
        createdAt: String? = null,
        updatedAt: String? = null,
        serverId: String? = null,
        syncStatus: String = "synced"
    ): Long {
        val now = getCurrentTimestamp()
        return database.transactionWithResult {
            queries.insertIncomeSource(
                user_id = userId,
                name = name,
                sort_order = sortOrder,
                is_active = isActive,
                is_fixed = isFixed,
                fixed_amount = fixedAmount,
                auto_day = autoDay,
                require_confirm = requireConfirm,
                created_at = createdAt ?: now,
                updated_at = updatedAt ?: createdAt ?: now,
                server_id = serverId,
                sync_status = syncStatus
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }

    /**
     * Обновление источника дохода
     */
    fun update(
        id: Long,
        name: String,
        sortOrder: Long,
        isActive: Long,
        isFixed: Long = 0L,
        fixedAmount: Long? = null,
        autoDay: Long? = null,
        requireConfirm: Long = 0L,
        updatedAt: String? = null,
        serverId: String? = null,
        syncStatus: String = "synced"
    ) {
        val now = getCurrentTimestamp()
        queries.updateIncomeSourceById(
            name = name,
            sort_order = sortOrder,
            is_active = isActive,
            is_fixed = isFixed,
            fixed_amount = fixedAmount,
            auto_day = autoDay,
            require_confirm = requireConfirm,
            updated_at = updatedAt ?: now,
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

    /** Порядок списка: sort_order = позиция в [orderedIds] */
    fun applySortOrder(orderedIds: List<Long>) {
        queries.transaction {
            orderedIds.forEachIndexed { index, id -> queries.updateIncomeSourceSortOrder(sort_order = index.toLong(), id = id) }
        }
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
     * Получение по имени пользователя и названию
     */
    fun getByName(userId: Long, name: String): IncomeSource? {
        // executeAsList().firstOrNull(), а не executeAsOneOrNull(): у пострадавших от бага дублей
        // юзеров локально может быть >1 строки с одинаковым (user_id, name). Дедуп — в runAdditiveMigrations.
        return queries.getIncomeSourceByName(userId, name).executeAsList().firstOrNull()?.let { toIncomeSource(it) }
    }

    /**
     * Получение фиксированных источников дохода пользователя
     */
    fun getFixedByUser(userId: Long): List<IncomeSource> {
        return queries.getFixedIncomeSourcesByUser(userId).executeAsList().map { toIncomeSource(it) }
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
            isFixed = entity.is_fixed,
            fixedAmount = entity.fixed_amount,
            autoDay = entity.auto_day,
            requireConfirm = entity.require_confirm,
            createdAt = entity.created_at,
            updatedAt = entity.updated_at,
            serverId = entity.server_id,
            syncStatus = entity.sync_status
        )
    }

    private fun getCurrentTimestamp(): String {
        return kotlin.time.Clock.System.now().toString()
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
    val isFixed: Long = 0L,
    val fixedAmount: Long? = null,
    val autoDay: Long? = null,
    val requireConfirm: Long = 0L,
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String
)

package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Months

/**
 * Data Access Object для операций с месяцами
 */
class MonthDao(
    private val database: FinKeeperDatabase,
) {
    private val queries = database.finKeeperDatabaseQueries

    /**
     * Вставка нового месяца
     */
    fun insert(
        userId: Long,
        year: Long,
        month: Long,
        serverId: String? = null,
        syncStatus: String = "synced",
    ): Long {
        val now = getCurrentTimestamp()
        return database.transactionWithResult {
            queries.insertMonth(
                user_id = userId,
                year = year,
                month = month,
                created_at = now,
                updated_at = now,
                server_id = serverId,
                sync_status = syncStatus,
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }

    /**
     * Получение или создание месяца
     */
    fun getOrCreate(
        userId: Long,
        year: Long,
        month: Long,
    ): Month {
        val existing =
            queries
                .getMonthByUserAndYearMonth(
                    user_id = userId,
                    year = year,
                    month = month,
                ).executeAsOneOrNull()

        return if (existing != null) {
            toMonth(existing)
        } else {
            val id = insert(userId, year, month, syncStatus = "pending")
            Month(
                id = id,
                userId = userId,
                year = year,
                month = month,
                createdAt = getCurrentTimestamp(),
                updatedAt = getCurrentTimestamp(),
                serverId = null,
                syncStatus = "pending",
            )
        }
    }

    /**
     * Обновление месяца
     */
    fun update(
        id: Long,
        year: Long,
        month: Long,
        serverId: String? = null,
        syncStatus: String = "synced",
    ) {
        val now = getCurrentTimestamp()
        queries.updateMonthById(
            year = year,
            month = month,
            updated_at = now,
            server_id = serverId,
            sync_status = syncStatus,
            id = id,
        )
    }

    /**
     * Обновление статуса синхронизации
     */
    fun updateSyncStatus(
        id: Long,
        syncStatus: String,
        serverId: String? = null,
    ) {
        queries.updateMonthSyncStatus(
            sync_status = syncStatus,
            server_id = serverId,
            id = id,
        )
    }

    /**
     * Обновление serverId
     */
    fun updateServerId(
        id: Long,
        serverId: String,
    ) {
        val now = getCurrentTimestamp()
        queries.updateMonthSyncStatus(
            sync_status = "synced",
            server_id = serverId,
            id = id,
        )
    }

    /**
     * Удаление месяца
     */
    fun deleteById(id: Long) {
        queries.deleteMonthById(id)
    }

    /**
     * Удаление всех месяцев пользователя
     */
    fun deleteAllByUser(userId: Long) {
        queries.deleteAllMonthsByUser(userId)
    }

    /**
     * Получение месяца по ID
     */
    fun getById(id: Long): Month? = queries.getMonthById(id).executeAsOneOrNull()?.let { toMonth(it) }

    /**
     * Получение месяца по пользователю и году/месяцу
     */
    fun getByUserAndYearMonth(
        userId: Long,
        year: Long,
        month: Long,
    ): Month? =
        queries
            .getMonthByUserAndYearMonth(
                user_id = userId,
                year = year,
                month = month,
            ).executeAsOneOrNull()
            ?.let { toMonth(it) }

    /**
     * Получение всех месяцев пользователя
     */
    fun getAllByUser(userId: Long): List<Month> = queries.getAllMonthsByUser(userId).executeAsList().map { toMonth(it) }

    /**
     * Получение месяцев ожидающих синхронизации
     */
    fun getPendingSync(): List<Month> = queries.getMonthsPendingSync().executeAsList().map { toMonth(it) }

    /**
     * Получение количества месяцев ожидающих синхронизации
     */
    fun getPendingSyncCount(): Long = queries.getMonthsPendingSyncCount().executeAsOne()

    /**
     * Проверка существования по ID
     */
    fun existsById(id: Long): Boolean = queries.monthExistsById(id).executeAsOne()

    /**
     * Проверка существования по server ID
     */
    fun existsByServerId(serverId: String): Boolean = queries.monthExistsByServerId(serverId).executeAsOne()

    /**
     * Получение по server ID
     */
    fun getByServerId(serverId: String): Month? = queries.getMonthByServerId(serverId).executeAsOneOrNull()?.let { toMonth(it) }

    /**
     * Удаление всех месяцев
     */
    fun deleteAll() {
        queries.deleteAllMonths()
    }

    private fun toMonth(entity: Months): Month =
        Month(
            id = entity.id,
            userId = entity.user_id,
            year = entity.year,
            month = entity.month,
            createdAt = entity.created_at,
            updatedAt = entity.updated_at,
            serverId = entity.server_id,
            syncStatus = entity.sync_status,
        )

    private fun getCurrentTimestamp(): String =
        kotlin.time.Clock.System
            .now()
            .toString()
}

/**
 * Модель месяца
 */
data class Month(
    val id: Long,
    val userId: Long,
    val year: Long,
    val month: Long,
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String,
)

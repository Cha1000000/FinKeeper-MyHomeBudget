package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Planned_overrides

/**
 * DAO исключений план-слоя (skip / override регулярного платежа на месяц)
 */
class PlannedOverrideDao(
    private val database: FinKeeperDatabase
) {
    private val queries = database.finKeeperDatabaseQueries

    fun upsert(
        userId: Long,
        templateType: String,
        templateId: Long,
        monthId: Long,
        overrideAmount: Long?,
        overrideDay: Long?,
        isSkipped: Long,
        createdAt: String,
        updatedAt: String,
        syncStatus: String,
    ) {
        queries.upsertPlannedOverride(
            user_id = userId,
            template_type = templateType,
            template_id = templateId,
            month_id = monthId,
            override_amount = overrideAmount,
            override_day = overrideDay,
            is_skipped = isSkipped,
            created_at = createdAt,
            updated_at = updatedAt,
            sync_status = syncStatus,
        )
    }

    fun delete(userId: Long, templateType: String, templateId: Long, monthId: Long) {
        queries.deletePlannedOverride(userId, templateType, templateId, monthId)
    }

    fun get(userId: Long, templateType: String, templateId: Long, monthId: Long): PlannedOverrideEntity? {
        return queries.getPlannedOverride(userId, templateType, templateId, monthId)
            .executeAsOneOrNull()?.let { toEntity(it) }
    }

    fun getByMonth(userId: Long, monthId: Long): List<PlannedOverrideEntity> {
        return queries.getPlannedOverridesByMonth(userId, monthId).executeAsList().map { toEntity(it) }
    }

    /** Удаляет только синхронизированные строки месяца (локальные pending-правки переживают pull) */
    fun deleteSyncedByMonth(userId: Long, monthId: Long) {
        queries.deleteSyncedPlannedOverridesByMonth(userId, monthId)
    }

    fun updateSyncStatus(userId: Long, templateType: String, templateId: Long, monthId: Long, syncStatus: String) {
        queries.updatePlannedOverrideSyncStatus(syncStatus, userId, templateType, templateId, monthId)
    }

    fun deleteAllByUser(userId: Long) {
        queries.deleteAllPlannedOverridesByUser(userId)
    }

    private fun toEntity(row: Planned_overrides) = PlannedOverrideEntity(
        id = row.id,
        userId = row.user_id,
        templateType = row.template_type,
        templateId = row.template_id,
        monthId = row.month_id,
        overrideAmount = row.override_amount,
        overrideDay = row.override_day,
        isSkipped = row.is_skipped,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
        syncStatus = row.sync_status,
    )
}

data class PlannedOverrideEntity(
    val id: Long,
    val userId: Long,
    val templateType: String,
    val templateId: Long,
    val monthId: Long,
    val overrideAmount: Long?,
    val overrideDay: Long?,
    val isSkipped: Long,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: String,
)

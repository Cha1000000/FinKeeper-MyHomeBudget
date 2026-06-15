package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.Auto_created_records
import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase

/**
 * DAO зеркала серверной auto_created_records: какие фиксированные шаблоны
 * уже материализованы в реальные записи за месяц. Read-only реплика,
 * полностью замещается данными сервера при pull.
 */
class AutoCreatedDao(
    private val database: FinKeeperDatabase
) {
    private val queries = database.finKeeperDatabaseQueries

    fun insert(
        userId: Long,
        templateType: String,
        templateId: Long,
        monthId: Long,
        createdRecordType: String,
        createdAt: String,
    ) {
        queries.insertAutoCreatedRecord(userId, templateType, templateId, monthId, createdRecordType, createdAt)
    }

    fun getByMonth(userId: Long, monthId: Long): List<AutoCreatedEntity> {
        return queries.getAutoCreatedByMonth(userId, monthId).executeAsList().map { toEntity(it) }
    }

    /** Полное замещение данных месяца (pull с сервера) */
    fun replaceForMonth(userId: Long, monthId: Long, records: List<AutoCreatedEntity>) {
        database.transaction {
            queries.deleteAutoCreatedByMonth(userId, monthId)
            records.forEach {
                queries.insertAutoCreatedRecord(
                    user_id = userId,
                    template_type = it.templateType,
                    template_id = it.templateId,
                    month_id = monthId,
                    created_record_type = it.createdRecordType,
                    created_at = it.createdAt,
                )
            }
        }
    }

    fun deleteAllByUser(userId: Long) {
        queries.deleteAllAutoCreatedByUser(userId)
    }

    private fun toEntity(row: Auto_created_records) = AutoCreatedEntity(
        templateType = row.template_type,
        templateId = row.template_id,
        monthId = row.month_id,
        createdRecordType = row.created_record_type,
        createdAt = row.created_at,
    )
}

data class AutoCreatedEntity(
    val templateType: String,
    val templateId: Long,
    val monthId: Long = 0L,
    val createdRecordType: String,
    val createdAt: String,
)

package ru.homebudget.finkeeper.data.repository.planned

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.AutoCreatedDao
import ru.homebudget.finkeeper.data.local.dao.AutoCreatedEntity
import ru.homebudget.finkeeper.data.local.dao.CategoryDao
import ru.homebudget.finkeeper.data.local.dao.IncomeSourceDao
import ru.homebudget.finkeeper.data.local.dao.MonthDao
import ru.homebudget.finkeeper.data.local.dao.PlannedOverrideDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.planned.PlannedCalculator
import ru.homebudget.finkeeper.data.planned.PlannedOverrideData
import ru.homebudget.finkeeper.data.planned.PlannedResult
import ru.homebudget.finkeeper.data.planned.PlannedTemplate
import ru.homebudget.finkeeper.data.planned.TEMPLATE_TYPE_CATEGORY
import ru.homebudget.finkeeper.data.planned.TEMPLATE_TYPE_INCOME_SOURCE
import ru.homebudget.finkeeper.data.planned.plannedKey
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager

/**
 * Кодирование ключа исключения (тип шаблона, локальный id шаблона, локальный id месяца)
 * в entityId очереди синхронизации. Очередь хранит только ключ — актуальное ПОЛНОЕ
 * состояние читается из planned_overrides в момент отправки (last-write-wins),
 * отсутствие строки = дефолтное состояние (сервер удалит своё исключение).
 */
object PlannedQueueKey {
    private const val TYPE_BIT_INCOME = 1L

    fun encode(templateType: String, templateId: Long, monthId: Long): Long {
        val typeBit = if (templateType == TEMPLATE_TYPE_INCOME_SOURCE) TYPE_BIT_INCOME else 0L
        return (monthId shl 22) or (templateId shl 1) or typeBit
    }

    fun decode(entityId: Long): Triple<String, Long, Long> {
        val templateType = if (entityId and TYPE_BIT_INCOME == TYPE_BIT_INCOME) TEMPLATE_TYPE_INCOME_SOURCE else TEMPLATE_TYPE_CATEGORY
        val templateId = (entityId shr 1) and 0x1FFFFF
        val monthId = entityId shr 22
        return Triple(templateType, templateId, monthId)
    }
}

/**
 * Репозиторий план-слоя: локальное вычисление виртуальных запланированных платежей
 * (оффлайн, через [PlannedCalculator]) + offline-first действия skip/override/reset
 * + подтверждение «Оплачено/Получено» (только при сети).
 */
class PlannedRepository(
    private val categoryDao: CategoryDao,
    private val incomeSourceDao: IncomeSourceDao,
    private val monthDao: MonthDao,
    private val plannedOverrideDao: PlannedOverrideDao,
    private val autoCreatedDao: AutoCreatedDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }

    private fun nowIso(): String = Clock.System.now().toString()

    /** Локальное вычисление плана месяца (работает оффлайн) */
    suspend fun getPlanned(monthId: Long): PlannedResult =
        withContext(Dispatchers.Default) {
            val month = monthDao.getById(monthId) ?: return@withContext PlannedResult.EMPTY

            val templates = buildList {
                categoryDao.getFixedByUser(currentUserId)
                    .filter { it.fixedAmount != null && it.autoDay != null }
                    .forEach {
                        add(
                            PlannedTemplate(
                                templateType = TEMPLATE_TYPE_CATEGORY,
                                templateId = it.id,
                                name = it.name,
                                fixedAmountCents = it.fixedAmount!!,
                                autoDay = it.autoDay!!.toInt(),
                                requireConfirm = it.requireConfirm == 1L,
                            )
                        )
                    }
                incomeSourceDao.getFixedByUser(currentUserId)
                    .filter { it.fixedAmount != null && it.autoDay != null }
                    .forEach {
                        add(
                            PlannedTemplate(
                                templateType = TEMPLATE_TYPE_INCOME_SOURCE,
                                templateId = it.id,
                                name = it.name,
                                fixedAmountCents = it.fixedAmount!!,
                                autoDay = it.autoDay!!.toInt(),
                                requireConfirm = it.requireConfirm == 1L,
                            )
                        )
                    }
            }

            val materialized = autoCreatedDao.getByMonth(currentUserId, monthId)
                .map { plannedKey(it.templateType, it.templateId) }
                .toSet()

            val overrides = plannedOverrideDao.getByMonth(currentUserId, monthId).associate { row ->
                plannedKey(row.templateType, row.templateId) to PlannedOverrideData(
                    overrideAmountCents = row.overrideAmount,
                    overrideDay = row.overrideDay?.toInt(),
                    isSkipped = row.isSkipped == 1L,
                )
            }

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            PlannedCalculator.calculate(
                templates = templates,
                materializedKeys = materialized,
                overrides = overrides,
                year = month.year.toInt(),
                month = month.month.toInt(),
                today = today,
            )
        }

    /** Пропуск / возврат платежа в план месяца (offline-first) */
    suspend fun setSkipped(monthId: Long, templateType: String, templateId: Long, skipped: Boolean): Result<Unit> =
        upsertOverrideState(monthId, templateType, templateId) { existing ->
            OverrideState(
                amountCents = existing?.overrideAmount,
                day = existing?.overrideDay?.toInt(),
                isSkipped = skipped,
            )
        }

    /** Override суммы/дня на месяц (null = вернуть значение из шаблона); offline-first */
    suspend fun setOverride(monthId: Long, templateType: String, templateId: Long, amountCents: Long?, day: Int?): Result<Unit> =
        upsertOverrideState(monthId, templateType, templateId) { existing ->
            OverrideState(
                amountCents = amountCents,
                day = day,
                isSkipped = existing?.isSkipped == 1L,
            )
        }

    /** Полный сброс исключения к шаблону; offline-first */
    suspend fun resetOverride(monthId: Long, templateType: String, templateId: Long): Result<Unit> =
        upsertOverrideState(monthId, templateType, templateId) { OverrideState(null, null, false) }

    private data class OverrideState(val amountCents: Long?, val day: Int?, val isSkipped: Boolean)

    private suspend fun upsertOverrideState(
        monthId: Long,
        templateType: String,
        templateId: Long,
        buildState: (existing: ru.homebudget.finkeeper.data.local.dao.PlannedOverrideEntity?) -> OverrideState,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val existing = plannedOverrideDao.get(currentUserId, templateType, templateId, monthId)
                val state = buildState(existing)
                val now = nowIso()
                val isDefaultState = !state.isSkipped && state.amountCents == null && state.day == null

                if (isDefaultState) {
                    plannedOverrideDao.delete(currentUserId, templateType, templateId, monthId)
                } else {
                    plannedOverrideDao.upsert(
                        userId = currentUserId,
                        templateType = templateType,
                        templateId = templateId,
                        monthId = monthId,
                        overrideAmount = state.amountCents,
                        overrideDay = state.day?.toLong(),
                        isSkipped = if (state.isSkipped) 1L else 0L,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING.value,
                    )
                }

                // В очередь кладём только ключ; актуальное полное состояние SyncManager
                // прочитает из БД при отправке (PUT идемпотентен, дефолт = удаление на сервере)
                syncManager.enqueueSync(
                    userId = currentUserId,
                    entityType = EntityType.PLANNED_OVERRIDE.value,
                    entityId = PlannedQueueKey.encode(templateType, templateId, monthId),
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
     * «Оплачено/Получено»: материализация планового платежа. Только при сети —
     * сервер создаёт реальную запись и фиксирует auto_created_records; локально
     * сразу ставим оптимистичный маркер, полный рефреш доедет синхронизацией.
     */
    suspend fun confirm(monthId: Long, templateType: String, templateId: Long, amountCents: Long?): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val serverMonthId = resolveServerMonthId(monthId)
                    ?: return@withContext Result.error(Exception("Нет соединения с сервером"))
                val serverTemplateId = resolveServerTemplateId(templateType, templateId)
                    ?: return@withContext Result.error(Exception("Шаблон ещё не синхронизирован"))

                apiClient.confirmPlanned(
                    monthId = serverMonthId,
                    templateType = templateType,
                    templateId = serverTemplateId,
                    amount = amountCents?.let { it / 100.0 },
                    operationId = "planned-confirm-$currentUserId-$monthId-$templateType-$templateId",
                )

                autoCreatedDao.insert(
                    userId = currentUserId,
                    templateType = templateType,
                    templateId = templateId,
                    monthId = monthId,
                    createdRecordType = if (templateType == TEMPLATE_TYPE_CATEGORY) "expense" else "income",
                    createdAt = nowIso(),
                )
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    // Pull-синхронизация сериализуется мьютексом (см. остальные репозитории)
    private val syncPullMutex = Mutex()

    /** Pull сырого состояния план-слоя месяца с сервера (вызывается из SyncManager) */
    suspend fun syncWithServer(userId: Long, monthId: Long) =
        syncPullMutex.withLock { syncWithServerInternal(userId, monthId) }

    private suspend fun syncWithServerInternal(userId: Long, monthId: Long) {
        val month = monthDao.getById(monthId) ?: return
        val serverMonthId = month.serverId?.toIntOrNull() ?: return

        val state = apiClient.getPlannedState(serverMonthId)

        val autoCreated = state.autoCreated.mapNotNull { dto ->
            val localTemplateId = resolveLocalTemplateId(dto.templateType, dto.templateId) ?: return@mapNotNull null
            AutoCreatedEntity(
                templateType = dto.templateType,
                templateId = localTemplateId,
                createdRecordType = dto.createdRecordType,
                createdAt = dto.createdAt ?: nowIso(),
            )
        }
        autoCreatedDao.replaceForMonth(userId, monthId, autoCreated)

        // Server wins для синхронизированных строк; локальные pending-правки переживают pull
        plannedOverrideDao.deleteSyncedByMonth(userId, monthId)
        state.overrides.forEach { dto ->
            val localTemplateId = resolveLocalTemplateId(dto.templateType, dto.templateId) ?: return@forEach
            val pendingLocal = plannedOverrideDao.get(userId, dto.templateType, localTemplateId, monthId)
            if (pendingLocal != null && pendingLocal.syncStatus != SyncStatus.SYNCED.value) return@forEach

            plannedOverrideDao.upsert(
                userId = userId,
                templateType = dto.templateType,
                templateId = localTemplateId,
                monthId = monthId,
                overrideAmount = dto.overrideAmount?.let { (it * 100).toLong() },
                overrideDay = dto.overrideDay?.toLong(),
                isSkipped = dto.isSkipped.toLong(),
                createdAt = dto.updatedAt ?: nowIso(),
                updatedAt = dto.updatedAt ?: nowIso(),
                syncStatus = SyncStatus.SYNCED.value,
            )
        }
    }

    private fun resolveLocalTemplateId(templateType: String, serverTemplateId: Int): Long? =
        if (templateType == TEMPLATE_TYPE_CATEGORY) {
            categoryDao.getByServerId(serverTemplateId.toString())?.id
        } else {
            incomeSourceDao.getByServerId(serverTemplateId.toString())?.id
        }

    /** Серверный id шаблона по локальному (null — шаблон ещё не доехал на сервер) */
    fun resolveServerTemplateId(templateType: String, localTemplateId: Long): Int? =
        if (templateType == TEMPLATE_TYPE_CATEGORY) {
            categoryDao.getById(localTemplateId)?.serverId?.toIntOrNull()
        } else {
            incomeSourceDao.getById(localTemplateId)?.serverId?.toIntOrNull()
        }

    private suspend fun resolveServerMonthId(monthId: Long): Int? {
        val month = monthDao.getById(monthId) ?: return null
        month.serverId?.toIntOrNull()?.let { return it }
        return try {
            val remote = apiClient.ensureMonth(month.year.toInt(), month.month.toInt())
            monthDao.updateServerId(month.id, remote.id.toString())
            remote.id
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

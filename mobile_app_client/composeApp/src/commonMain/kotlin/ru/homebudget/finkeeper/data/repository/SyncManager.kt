package ru.homebudget.finkeeper.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import ru.homebudget.finkeeper.data.local.dao.*
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncQueueStatus
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.network.ServerPhase
import ru.homebudget.finkeeper.data.network.ServerPhaseResult
import ru.homebudget.finkeeper.data.network.isConnectivityFailure
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.ApiException
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.budget.BudgetRepository
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.expense.ExpenseRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeSourceRepository
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
import ru.homebudget.finkeeper.data.repository.planned.PlannedQueueKey
import ru.homebudget.finkeeper.data.repository.planned.PlannedRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsGoalRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsTransactionRepository

/**
 * Менеджер синхронизации данных между локальной БД и сервером
 * Реализует двустороннюю синхронизацию с разрешением конфликтов
 */
class SyncManager(
    private val syncQueueDao: SyncQueueDao,
    private val categoryDao: CategoryDao,
    private val incomeSourceDao: IncomeSourceDao,
    private val incomeDao: IncomeDao,
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val savingsTransactionDao: SavingsTransactionDao,
    private val monthDao: MonthDao,
    private val apiClient: ApiClient,
    private val categoryRepository: CategoryRepository,
    private val incomeSourceRepository: IncomeSourceRepository,
    private val monthRepository: MonthRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val savingsTransactionRepository: SavingsTransactionRepository,
    private val plannedRepository: PlannedRepository,
    private val plannedOverrideDao: PlannedOverrideDao,
    private val syncStateStorage: SyncStateStorage,
    private val tokenStorage: TokenStorage,
) {
    private companion object {
        // Лимит авто-ретраев failed-операции. retry_count растёт ~+2 за цикл провала
        // (pending→syncing→failed), так что ~6 циклов хватает на транзиентные ошибки
        // (родитель ещё не синхронизирован, обрыв сети), но перманентно падающие
        // операции перестают штормить сервер. Ручной ретрай из Настроек сбрасывает счётчик.
        const val MAX_AUTO_RETRY_COUNT = 12L

        // Синхронизация, не завершившаяся за это время, считается зависшей: следующий запуск
        // её не ждёт. Отдельный HTTP-запрос ограничен HttpTimeout, так что это лишь страховка
        const val STUCK_SYNC_MILLIS = 180_000L

        // Выгрузка очереди: размер пачки и предел пачек за одну синхронизацию
        const val UPLOAD_BATCH_SIZE = 50L
        const val MAX_UPLOAD_ROUNDS = 20
    }

    /** Итог отправки одного элемента очереди. */
    private enum class ItemOutcome {
        /** Ушёл на сервер (или больше не нужен). */
        DONE,

        /** Сервер отказал — элемент в failed, повторится по лимиту попыток. */
        FAILED,

        /** Сервер не ответил — элемент снова pending, остальную очередь слать бессмысленно. */
        UNREACHABLE,
    }

    private val currentUserId: Long get() = tokenStorage.userId
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _pendingCount = MutableStateFlow(0L)
    val pendingCount: StateFlow<Long> = _pendingCount.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _lastSuccessfulSyncAt = MutableStateFlow(syncStateStorage.lastSuccessfulSyncAt)
    val lastSuccessfulSyncAt: StateFlow<String?> = _lastSuccessfulSyncAt.asStateFlow()

    private val _dataUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dataUpdated: SharedFlow<Unit> = _dataUpdated.asSharedFlow()

    init {
        updatePendingCount()
        // Очищаем старую ошибку при инициализации
        _lastSyncError.value = null
    }

    /**
     * Позволяет репозиториям и другим компонентам сообщить об ошибке синхронизации,
     * чтобы она стала видимой в UI через lastSyncError.
     */
    fun reportSyncError(message: String) {
        println("[SYNC] reportSyncError: $message")
        _lastSyncError.value = message
    }

    /**
     * Очищает сообщение об ошибке синхронизации.
     * Вызывается из UI когда пользователь хочет скрыть сообщение об ошибке.
     */
    fun clearSyncError() {
        _lastSyncError.value = null
    }

    /**
     * Сервер ответил на всю синхронизацию. Ошибку сбрасываем, только если не осталось
     * операций, которые сервер отверг: иначе пользователь потеряет сигнал о них
     */
    private fun markSyncSuccess(
        clearError: Boolean,
        timestamp: String = Clock.System.now().toString(),
    ) {
        syncStateStorage.lastSuccessfulSyncAt = timestamp
        _lastSuccessfulSyncAt.value = timestamp
        if (clearError) _lastSyncError.value = null
    }

    private fun Throwable.toSyncErrorText(): String = message ?: this::class.simpleName ?: "Server error"

    /**
     * Вызывается из WebSocketService при получении события об изменении данных на сервере.
     * Запускает синхронизацию с сервером и уведомляет ViewModels об обновлении.
     */
    fun notifyDataChanged() {
        // dataUpdated отправит сама синхронизация, когда данные действительно придут
        syncAll()
    }

    private val queue =
        SyncQueueCoordinator(
            syncQueueDao = syncQueueDao,
            entityUpdatedAt = ::getEntityUpdatedAt,
            newOperationId = ::generateOperationId,
        )

    /**
     * Добавляет операцию в очередь синхронизации.
     * [payload] для DELETE — serverId удаляемой записи.
     * [goalCurrentAmount] — сумма копилки, которую пользователь задал явно (только для её UPDATE).
     */
    fun enqueueSync(
        userId: Long,
        entityType: String,
        entityId: Long,
        operation: String,
        payload: String? = null,
        goalCurrentAmount: Long? = null,
        reactivate: Boolean = false,
    ) {
        // UNDISPATCHED: мьютекс очереди захватывается ещё в потоке вызывающего, поэтому операции
        // встают в очередь в порядке вызовов (родитель раньше детей)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (userId <= 0L) {
                    val message = "Cannot enqueue sync: invalid userId=$userId for $entityType#$entityId"
                    println("[SYNC] $message")
                    _lastSyncError.value = message
                    return@launch
                }

                println("[SYNC] enqueueSync: type=$entityType, entityId=$entityId, op=$operation")
                queue.enqueue(
                    userId = userId,
                    entityType = entityType,
                    entityId = entityId,
                    operation = operation,
                    deleteServerId = payload,
                    goalCurrentAmount = goalCurrentAmount,
                    reactivate = reactivate,
                )
                updatePendingCount()

                // Немедленно отправляем на сервер (если доступен)
                scheduleProcessQueue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message ?: e::class.simpleName ?: "Unknown enqueue sync error"
                println("[SYNC] enqueueSync ERROR: $errorMessage")
                _lastSyncError.value = errorMessage
                e.printStackTrace()
            }
        }
    }

    /**
     * Полная двусторонняя синхронизация:
     * 1. Сначала забираем изменения с сервера (download)
     * 2. Затем отправляем локальные изменения (upload)
     */
    fun syncAll(monthId: Long? = null) {
        scope.launch {
            val token = tryBeginSync(SyncRequest.Full(monthId)) ?: return@launch
            println("[SYNC] syncAll START: monthId=$monthId, userId=$currentUserId")
            try {
                if (currentUserId <= 0L) {
                    println("[SYNC] syncAll: SKIPPED, userId=$currentUserId (not logged in)")
                    return@launch
                }

                // Этап 1: Забираем данные с сервера (Download)
                val download = syncFromServer(monthId)
                if (!download.reachable) {
                    // Каждый следующий запрос упёрся бы в свой таймаут (минуты на «зависшем» сервере).
                    // Очередь не трогаем: операции остаются pending и уйдут, когда сервер ответит
                    println("[SYNC] syncAll: server unreachable, upload skipped")
                    return@launch
                }

                // Этап 2: Отправляем локальные изменения (Upload)
                val upload = uploadQueue()
                val pullError = download.serverError ?: upload.pullError
                pullFailed = pullError != null
                when {
                    // Сервер ответил ошибкой: часть данных не обновилась — не выдаём это за успех
                    pullError != null -> _lastSyncError.value = pullError.toSyncErrorText()
                    upload.reachable -> markSyncSuccess(clearError = !upload.hasFailedItems)
                    else -> println("[SYNC] syncAll: server unreachable during upload")
                }

                // Room уже обновлён скачиванием — уведомляем подписчиков, даже если выгрузка
                // упёрлась в недоступный сервер
                _dataUpdated.tryEmit(Unit)
                println("[SYNC] syncAll DONE")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[SYNC] syncAll ERROR: ${e.message}")
                _lastSyncError.value = e.message ?: "syncAll error"
                e.printStackTrace()
            } finally {
                endSync(token)
            }
        }
    }

    /**
     * [reachable] = `false` — сервер перестал отвечать: оставшиеся элементы ждут в pending.
     * [pullError] — сервер ответил ошибкой на догрузку скрытых расходов копилки.
     */
    private class UploadResult(
        val syncedAny: Boolean,
        val hasFailedItems: Boolean,
        val reachable: Boolean,
        val pullError: Throwable?,
    )

    // Последнее скачивание с сервера закончилось ошибкой сервера. Успешная выгрузка очереди
    // не должна стирать эту ошибку: снять её может только следующее успешное скачивание.
    // Меняется только под захваченной синхронизацией ([tryBeginSync])
    private var pullFailed = false

    /**
     * Отправляет очередь. Вызывается только внутри захваченной синхронизации ([tryBeginSync]).
     */
    private suspend fun uploadQueue(): UploadResult {
        // Авто-ретрай failed-операций под лимитом (транзиентные ошибки повторяем,
        // перманентные — не штормим)
        resetStaleSyncingOnce()
        syncQueueDao.retryRetriableFailed(MAX_AUTO_RETRY_COUNT)
        val depositMonthIds = mutableSetOf<Long>()
        var syncedAny = false
        var reachable = true
        // Очередь выгружается пачками, пока пачки полные и в них есть отправленные элементы:
        // иначе хвост длиннее пачки ждал бы следующего повода для синхронизации
        for (round in 1..MAX_UPLOAD_ROUNDS) {
            val pendingItems = syncQueueDao.getPendingItems(limit = UPLOAD_BATCH_SIZE)
            println("[SYNC] upload round $round: ${pendingItems.size} pending items")
            depositMonthIds += depositMonthIds(pendingItems)
            var progressed = false
            for (candidate in pendingItems) {
                // Свежая копия под мьютексом очереди: элемент могли слить с новой операцией
                val item = queue.claim(candidate.id) ?: continue
                when (syncItemToServer(item)) {
                    ItemOutcome.DONE -> {
                        syncedAny = true
                        progressed = true
                    }
                    ItemOutcome.FAILED -> progressed = true
                    ItemOutcome.UNREACHABLE -> {
                        reachable = false
                        break
                    }
                }
            }
            if (!reachable || !progressed || pendingItems.size < UPLOAD_BATCH_SIZE) break
        }
        var pullError: Throwable? = null
        if (reachable) {
            val hidden = pullHiddenSavingsExpenses(depositMonthIds)
            reachable = hidden.reachable
            pullError = hidden.serverError
        }

        // Очистка завершённых элементов
        syncQueueDao.clearCompleted()
        updatePendingCount()
        return UploadResult(
            syncedAny = syncedAny,
            hasFailedItems = syncQueueDao.getFailedItems(limit = 1).isNotEmpty(),
            reachable = reachable,
            pullError = pullError,
        )
    }

    private val syncGate = SyncGate(STUCK_SYNC_MILLIS) { running -> _isSyncing.value = running }

    private suspend fun tryBeginSync(request: SyncRequest): Long? =
        syncGate.tryBegin(request, Clock.System.now().toEpochMilliseconds())

    private suspend fun endSync(token: Long) {
        when (val next = withContext(NonCancellable) { syncGate.end(token) }) {
            is SyncRequest.Full -> syncAll(next.monthId)
            SyncRequest.Queue -> scheduleProcessQueue()
            null -> Unit
        }
    }

    /**
     * Синхронизация данных С СЕРВЕРА в локальную БД
     * Этот метод забирает актуальные данные с сервера и обновляет локальную БД
     */
    private suspend fun syncFromServer(monthId: Long?): ServerPhaseResult {
        // После первой сетевой неудачи остальные шаги пропускаются (иначе каждый ждёт свой таймаут)
        val phase = ServerPhase()
        try {
            println("[SYNC] syncFromServer START: userId=$currentUserId, monthId=$monthId")
            // Синхронизируем справочники
            phase.stepResult { categoryRepository.syncWithServer(currentUserId) }
            phase.stepResult { incomeSourceRepository.syncWithServer(currentUserId) }
            phase.stepResult { savingsGoalRepository.syncWithServer(currentUserId) }
            if (!phase.isUnreachable) {
                savingsGoalDao
                    .getAllByUser(currentUserId)
                    .filter { !it.serverId.isNullOrBlank() }
                    .forEach { goal ->
                        phase.stepResult { savingsTransactionRepository.syncWithServer(currentUserId, goal.id) }
                    }
            }

            // Синхронизируем данные за месяц (если указан)
            monthId?.let { id ->
                // До скачивания: сервер создаст регулярные записи, у которых наступил день
                phase.step { monthRepository.ensureOnServerChecked(id) }
                phase.stepResult { incomeRepository.syncWithServer(currentUserId, id) }
                phase.stepResult { expenseRepository.syncWithServer(currentUserId, id) }
                phase.stepResult { budgetRepository.syncWithServer(currentUserId, id) }
                // Старый сервер без planned-state не должен ломать остальную синхронизацию
                phase.optionalStep { plannedRepository.syncWithServer(currentUserId, id) }
            }
            phase.step { applyDeletedRecordsFromServer() }
            if (phase.isUnreachable) {
                println("[SYNC] syncFromServer: server unreachable, remaining steps skipped")
            } else {
                println("[SYNC] syncFromServer DONE")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[SYNC] syncFromServer ERROR: ${e.message}")
            e.printStackTrace()
            phase.recordFailure(e)
        }
        return ServerPhaseResult(reachable = !phase.isUnreachable, serverError = phase.serverError)
    }

    private suspend fun applyDeletedRecordsFromServer() {
        val since = syncStateStorage.lastDeletedRecordsSyncAt
        val deletedRecords = apiClient.getDeletedRecords(since = since)
        if (deletedRecords.isEmpty()) {
            println("[SYNC][TOMBSTONE] no deleted records, since=$since")
            return
        }

        println("[SYNC][TOMBSTONE] applying ${deletedRecords.size} deleted records, since=$since")

        val priority =
            mapOf(
                EntityType.INCOME.value to 0,
                EntityType.EXPENSE.value to 0,
                EntityType.SAVINGS_TRANSACTION.value to 0,
                EntityType.CATEGORY.value to 1,
                EntityType.INCOME_SOURCE.value to 1,
                EntityType.SAVINGS_GOAL.value to 2,
            )

        val deferred =
            deletedRecords
                .sortedWith(
                    compareBy<DeletedRecord> { priority[it.entityType] ?: 10 }
                        .thenBy { it.deletedAt },
                ).filterNot { deletedRecord -> applyDeletedRecord(deletedRecord) }

        // Отложенные tombstone (у записи есть неотправленные изменения) надо получить снова:
        // курсор не уходит дальше первого из них. Уже применённые придут повторно — применение
        // идемпотентно (записи нет или она уже неактивна)
        syncStateStorage.lastDeletedRecordsSyncAt =
            nextDeletedRecordsCursor(since, deletedRecords.map { it.deletedAt }, deferred.map { it.deletedAt })
    }

    /** `false` — tombstone отложен: у записи есть неотправленные изменения. */
    private fun applyDeletedRecord(deletedRecord: DeletedRecord): Boolean {
        return when (deletedRecord.entityType) {
            EntityType.CATEGORY.value -> {
                categoryDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.CATEGORY.value, local.id)
                    val hasDependentActiveQueue = hasActiveCategoryDependents(local.id)
                    if (!hasOwnActiveQueue && !hasDependentActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply category serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        // Сервер удаляет мягко — и мы тоже: при физическом удалении категория
                        // возвращалась синхронизацией под новым id, а её расходы теряли связь с ней
                        categoryDao.update(
                            id = local.id,
                            name = local.name,
                            type = local.type,
                            icon = local.icon,
                            color = local.color,
                            sortOrder = local.sortOrder,
                            isActive = 0L,
                            isFixed = local.isFixed,
                            fixedAmount = local.fixedAmount,
                            autoDay = local.autoDay,
                            requireConfirm = local.requireConfirm,
                            updatedAt = deletedRecord.deletedAt,
                            serverId = local.serverId,
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                        true
                    } else {
                        println("[SYNC][TOMBSTONE] skip category serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue, dependentActive=$hasDependentActiveQueue")
                        false
                    }
                } ?: run {
                    println("[SYNC][TOMBSTONE] skip category serverId=${deletedRecord.entityId}: local record not found")
                    true
                }
            }

            EntityType.INCOME_SOURCE.value -> {
                incomeSourceDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.INCOME_SOURCE.value, local.id)
                    val hasDependentActiveQueue = hasActiveIncomeSourceDependents(local.id)
                    if (!hasOwnActiveQueue && !hasDependentActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply income_source serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        // Мягкое удаление, как на сервере: доходы источника сохраняют связь с ним
                        incomeSourceDao.update(
                            id = local.id,
                            name = local.name,
                            sortOrder = local.sortOrder,
                            isActive = 0L,
                            isFixed = local.isFixed,
                            fixedAmount = local.fixedAmount,
                            autoDay = local.autoDay,
                            requireConfirm = local.requireConfirm,
                            updatedAt = deletedRecord.deletedAt,
                            serverId = local.serverId,
                            syncStatus = SyncStatus.SYNCED.value,
                        )
                        true
                    } else {
                        println("[SYNC][TOMBSTONE] skip income_source serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue, dependentActive=$hasDependentActiveQueue")
                        false
                    }
                } ?: run {
                    println("[SYNC][TOMBSTONE] skip income_source serverId=${deletedRecord.entityId}: local record not found")
                    true
                }
            }

            EntityType.INCOME.value -> {
                incomeDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.INCOME.value, local.id)
                    if (!hasOwnActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply income serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        incomeDao.deleteById(local.id)
                        true
                    } else {
                        println("[SYNC][TOMBSTONE] skip income serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue")
                        false
                    }
                } ?: run {
                    println("[SYNC][TOMBSTONE] skip income serverId=${deletedRecord.entityId}: local record not found")
                    true
                }
            }

            EntityType.EXPENSE.value -> {
                expenseDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.EXPENSE.value, local.id)
                    if (!hasOwnActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply expense serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        expenseDao.deleteById(local.id)
                        true
                    } else {
                        println("[SYNC][TOMBSTONE] skip expense serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue")
                        false
                    }
                } ?: run {
                    println("[SYNC][TOMBSTONE] skip expense serverId=${deletedRecord.entityId}: local record not found")
                    true
                }
            }

            EntityType.SAVINGS_TRANSACTION.value -> {
                savingsTransactionDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.SAVINGS_TRANSACTION.value, local.id)
                    if (!hasOwnActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply savings_transaction serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        savingsTransactionDao.deleteById(local.id)
                        true
                    } else {
                        println("[SYNC][TOMBSTONE] skip savings_transaction serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue")
                        false
                    }
                } ?: run {
                    println("[SYNC][TOMBSTONE] skip savings_transaction serverId=${deletedRecord.entityId}: local record not found")
                    true
                }
            }

            EntityType.SAVINGS_GOAL.value -> {
                savingsGoalDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.SAVINGS_GOAL.value, local.id)
                    val hasDependentActiveQueue = hasActiveSavingsGoalDependents(local.id)
                    if (!hasOwnActiveQueue && !hasDependentActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply savings_goal serverId=${deletedRecord.entityId} -> localId=${local.id} with child cleanup")
                        savingsTransactionDao.deleteAllByGoal(local.id)
                        savingsGoalDao.deleteById(local.id)
                        true
                    } else {
                        println("[SYNC][TOMBSTONE] skip savings_goal serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue, dependentActive=$hasDependentActiveQueue")
                        false
                    }
                } ?: run {
                    println("[SYNC][TOMBSTONE] skip savings_goal serverId=${deletedRecord.entityId}: local record not found")
                    true
                }
            }
            else -> true
        }
    }

    private fun hasActiveCategoryDependents(categoryId: Long): Boolean {
        val expenseHasActiveQueue =
            expenseDao.getAllByUser(currentUserId).any {
                it.categoryId == categoryId &&
                    hasActiveQueueOperation(EntityType.EXPENSE.value, it.id)
            }
        if (expenseHasActiveQueue) {
            return true
        }

        return budgetDao.getByUser(currentUserId).any {
            it.categoryId == categoryId &&
                hasActiveQueueOperation(EntityType.BUDGET.value, it.id)
        }
    }

    private fun hasActiveIncomeSourceDependents(incomeSourceId: Long): Boolean {
        return incomeDao.getAllByUser(currentUserId).any {
            it.incomeSourceId == incomeSourceId &&
                hasActiveQueueOperation(EntityType.INCOME.value, it.id)
        }
    }

    private fun hasActiveSavingsGoalDependents(goalId: Long): Boolean {
        return savingsTransactionDao.getByGoal(goalId).any {
            hasActiveQueueOperation(EntityType.SAVINGS_TRANSACTION.value, it.id)
        }
    }

    /**
     * Отправляет локальные изменения на сервер
     */
    /** [item] — уже захваченный элемент (в syncing), см. [SyncQueueCoordinator.claim]. */
    private suspend fun syncItemToServer(item: SyncQueueItem): ItemOutcome {
        try {
            println("[SYNC] syncItemToServer START: type=${item.entityType}, entityId=${item.entityId}, op=${item.operation}, userId=${item.userId}")

            when (item.entityType) {
                "category" -> syncCategoryToServer(item)
                "income_source" -> syncIncomeSourceToServer(item)
                "income" -> syncIncomeToServer(item)
                "expense" -> syncExpenseToServer(item)
                "budget" -> syncBudgetToServer(item)
                "savings_goal" -> syncSavingsGoalToServer(item)
                "savings_transaction" -> syncSavingsTransactionToServer(item)
                "planned_override" -> syncPlannedOverrideToServer(item)
                EntityType.CATEGORY_ORDER.value ->
                    apiClient.reorderCategories(activeServerIdsInOrder(categoryDao.getActiveByUser(item.userId)) { it.serverId }, getOperationId(item))
                EntityType.INCOME_SOURCE_ORDER.value ->
                    apiClient.reorderIncomeSources(activeServerIdsInOrder(incomeSourceDao.getActiveByUser(item.userId)) { it.serverId }, getOperationId(item))
                else -> println("[SYNC] syncItemToServer: UNKNOWN entityType=${item.entityType}")
            }

            println("[SYNC] syncItemToServer COMPLETED: type=${item.entityType}, entityId=${item.entityId}")
            syncQueueDao.updateStatus(
                id = item.id,
                status = SyncQueueStatus.COMPLETED.value,
                errorMessage = null,
            )
            // Страховка: запись изменили без постановки операции (или изменение не попало в очередь) —
            // без дополнительного UPDATE оно осталось бы только на устройстве
            if (queue.enqueueFollowUpIfNeeded(item) { isEntityDirty(item.entityType, item.entityId) }) {
                updatePendingCount()
            }
            return ItemOutcome.DONE
        } catch (e: CancellationException) {
            // Элемент уже помечен SYNCING, а из очереди берутся только pending: без возврата
            // операция осталась бы в очереди навсегда и не ушла бы на сервер
            withContext(NonCancellable) { syncQueueDao.returnToPending(item.id) }
            throw e
        } catch (e: Exception) {
            val errorMessage = e.message ?: e::class.simpleName ?: "Unknown sync error"
            println("[SYNC] syncItemToServer FAILED: type=${item.entityType}, entityId=${item.entityId}, error=$errorMessage")

            if (e.isConnectivityFailure()) {
                // Сервер недоступен — не вина операции: остаётся в очереди, попытка не тратится.
                // Об этом говорит индикатор «Нет связи», текст ошибки здесь лишний
                syncQueueDao.returnToPending(item.id)
                updatePendingCount()
                return ItemOutcome.UNREACHABLE
            }
            if (e is ApiException && e.code == ApiException.CODE_IDEMPOTENCY_KEY_REUSED) {
                // Прошлая попытка с этим ключом дошла до сервера, но ответ потерялся, а данные
                // с тех пор изменились: шлём текущее состояние под новым ключом (создания сюда не
                // попадают — их разбирает createOnServer)
                println("[SYNC] syncItemToServer: operation key reused, retrying ${item.entityType}#${item.entityId} with a new key")
                queue.renewOperationIdAndReturnToPending(item.id)
                updatePendingCount()
                scheduleProcessQueue()
                return ItemOutcome.DONE
            }
            _lastSyncError.value = "Sync ${item.entityType}: $errorMessage"

            // 404: записи на сервере уже нет (удалена с другого устройства) — повтор бесполезен
            val isNotFoundError = e is ApiException && e.statusCode == 404

            if (isNotFoundError) {
                println("[SYNC] syncItemToServer: 404 error detected, marking as COMPLETED to avoid infinite retry")
                // Удаляем у себя, только если сервер подтвердил, что нет самой записи: 404 бывает и на
                // связанную (например, целевую копилку пополнения), и тогда локальная запись нужна
                if (item.operation == SyncOperation.UPDATE.value && (e as? ApiException)?.code == ApiException.CODE_RECORD_NOT_FOUND) {
                    removeLocalRecordGoneOnServer(item)
                }
                // Для 404 ошибок помечаем как COMPLETED, чтобы не повторять бесконечно
                // Но сохраняем ошибку для информации
                syncQueueDao.updateStatus(
                    id = item.id,
                    status = SyncQueueStatus.COMPLETED.value,
                    errorMessage = "404 Not Found - $errorMessage",
                )
            } else {
                syncQueueDao.updateStatus(
                    id = item.id,
                    status = SyncQueueStatus.FAILED.value,
                    errorMessage = errorMessage,
                )
            }
            updatePendingCount()
            return if (isNotFoundError) ItemOutcome.DONE else ItemOutcome.FAILED
        }
    }

    /**
     * Итог создания записи на сервере: [Created] — создана этим запросом,
     * [Recovered] — уже была создана прошлой попыткой с тем же ключом (её ответ потерялся).
     */
    /**
     * Правка ушла на сервер, а записи там уже нет (удалили с другого устройства): удаление
     * побеждает — убираем запись и локально. Иначе она осталась бы «грязной» навсегда: tombstone
     * откладывается, пока у записи есть неотправленные изменения.
     * Категории и источники сервер удаляет мягко (404 для них — не удаление), их не трогаем.
     */
    private fun removeLocalRecordGoneOnServer(item: SyncQueueItem) {
        val id = item.entityId
        when (item.entityType) {
            EntityType.INCOME.value ->
                if (incomeDao.getById(id)?.serverId != null) incomeDao.deleteById(id) else return
            EntityType.EXPENSE.value ->
                if (expenseDao.getById(id)?.serverId != null) expenseDao.deleteById(id) else return
            EntityType.SAVINGS_TRANSACTION.value ->
                if (savingsTransactionDao.getById(id)?.serverId != null) savingsTransactionDao.deleteById(id) else return
            EntityType.SAVINGS_GOAL.value ->
                if (savingsGoalDao.getById(id)?.serverId != null) {
                    // Внешние ключи в локальной БД не включены — операции копилки удаляем сами
                    savingsTransactionDao.deleteAllByGoal(id)
                    savingsGoalDao.deleteById(id)
                } else {
                    return
                }
            else -> return
        }
        println("[SYNC] ${item.entityType}#$id is gone on server, removed locally")
        _dataUpdated.tryEmit(Unit)
    }

    /**
     * Текущий локальный порядок активных записей в серверных id. Записи без serverId (их создание
     * не дошло до сервера) пропускаются: создания стоят в очереди раньше порядка, так что сюда
     * попадает лишь запись, создание которой упало, — сервер поставит её в конец.
     */
    private fun <T> activeServerIdsInOrder(
        activeSorted: List<T>,
        serverIdOf: (T) -> String?,
    ): List<Int> = activeSorted.mapNotNull { serverIdOf(it)?.toIntOrNull() }

    /**
     * is_active для UPDATE категории/источника: 0 — удаление (оно мягкое, через UPDATE), 1 — только
     * восстановление, иначе null. Обычная правка не трогает активность, чтобы не воскресить
     * запись, удалённую на другом устройстве: удаление побеждает.
     */
    private fun activeFlagForUpdate(localIsActive: Long, item: SyncQueueItem): Int? =
        when {
            localIsActive == 0L -> 0
            decodeSyncQueuePayloadMetadata(item.payload)?.reactivate == true -> 1
            else -> null
        }

    private sealed interface CreateOutcome<out T> {
        class Created<T>(val remote: T) : CreateOutcome<T>

        class Recovered(val serverId: Int) : CreateOutcome<Nothing>
    }

    /**
     * Создание с ключом операции. Если ключ уже использован (ответ на прошлую попытку потерялся,
     * а запись с тех пор изменили), сервер отдаёт исходный ответ — берём из него id созданной
     * записи; текущее состояние затем досылается обновлением.
     */
    private suspend fun <T> createOnServer(create: suspend () -> T): CreateOutcome<T> =
        try {
            CreateOutcome.Created(create())
        } catch (e: ApiException) {
            val recoveredId =
                if (e.code == ApiException.CODE_IDEMPOTENCY_KEY_REUSED) originalResponseId(e.originalResponse) else null
            if (recoveredId == null) throw e
            println("[SYNC] createOnServer: operation key reused, record already exists on server id=$recoveredId")
            CreateOutcome.Recovered(recoveredId)
        }

    private fun originalResponseId(response: JsonElement?): Int? =
        (response as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull

    /**
     * Ключ для обновления записи. Если этот элемент мог создать запись (INSERT или UPDATE записи
     * без serverId), его ключ уже занят запросом создания — обновление идёт под производным ключом,
     * тоже стабильным между повторами.
     */
    private fun updateOperationId(
        item: SyncQueueItem,
        hadServerId: Boolean,
    ): String? {
        val operationId = getOperationId(item) ?: return null
        return if (item.operation == SyncOperation.INSERT.value || !hadServerId) "$operationId:u" else operationId
    }

    /**
     * Синхронизация категории на сервер.
     * INSERT и UPDATE сводятся к одному: нет serverId — создаём, есть — обновляем.
     */
    private suspend fun syncCategoryToServer(item: SyncQueueItem) {
        val operationId = getOperationId(item)
        if (item.operation == SyncOperation.DELETE.value) {
            getDeleteServerId(item)?.toIntOrNull()?.let { serverId ->
                apiClient.deleteCategory(serverId, operationId)
            }
            return
        }

        val category = categoryDao.getById(item.entityId) ?: return
        val knownServerId = category.serverId?.toIntOrNull()
        val serverId =
            knownServerId ?: when (
                val created =
                    createOnServer {
                        apiClient.createCategory(
                            name = category.name,
                            isFixed = if (category.isFixed == 1L) 1 else null,
                            fixedAmount = category.fixedAmount?.let { it.toDouble() / 100.0 },
                            autoDay = category.autoDay?.toInt(),
                            requireConfirm = if (category.isFixed == 1L) category.requireConfirm.toInt() else null,
                            operationId = operationId,
                        )
                    }
            ) {
                // Удалили до отправки создания: запись создана активной — досылаем удаление
                is CreateOutcome.Created if category.isActive == 0L -> {
                    categoryDao.updateSyncStatus(category.id, SyncStatus.PENDING.value, created.remote.id.toString())
                    created.remote.id
                }
                is CreateOutcome.Created -> {
                    applyCategoryServerSnapshot(item, category.id, created.remote)
                    return
                }
                is CreateOutcome.Recovered -> {
                    categoryDao.updateSyncStatus(category.id, SyncStatus.PENDING.value, created.serverId.toString())
                    created.serverId
                }
            }

        val remote =
            apiClient.updateCategory(
                id = serverId,
                request =
                    UpdateCategoryRequest(
                        name = category.name,
                        isActive = activeFlagForUpdate(category.isActive, item),
                        isFixed = category.isFixed.toInt(),
                        fixedAmount = category.fixedAmount?.let { it.toDouble() / 100.0 },
                        autoDay = category.autoDay?.toInt(),
                        requireConfirm = category.requireConfirm.toInt(),
                    ),
                operationId = updateOperationId(item, hadServerId = knownServerId != null),
            )
        applyCategoryServerSnapshot(item, category.id, remote)
    }

    /**
     * Синхронизация источника дохода на сервер (INSERT/UPDATE — как у категорий)
     */
    private suspend fun syncIncomeSourceToServer(item: SyncQueueItem) {
        val operationId = getOperationId(item)
        if (item.operation == SyncOperation.DELETE.value) {
            getDeleteServerId(item)?.toIntOrNull()?.let { serverId ->
                apiClient.deleteIncomeSource(serverId, operationId)
            }
            return
        }

        val source = incomeSourceDao.getById(item.entityId) ?: return
        val knownServerId = source.serverId?.toIntOrNull()
        val serverId =
            knownServerId ?: when (
                val created =
                    createOnServer {
                        apiClient.createIncomeSource(
                            name = source.name,
                            isFixed = if (source.isFixed == 1L) 1 else null,
                            fixedAmount = source.fixedAmount?.let { it.toDouble() / 100.0 },
                            autoDay = source.autoDay?.toInt(),
                            requireConfirm = if (source.isFixed == 1L) source.requireConfirm.toInt() else null,
                            operationId = operationId,
                        )
                    }
            ) {
                // Удалили до отправки создания: запись создана активной — досылаем удаление
                is CreateOutcome.Created if source.isActive == 0L -> {
                    incomeSourceDao.updateSyncStatus(source.id, SyncStatus.PENDING.value, created.remote.id.toString())
                    created.remote.id
                }
                is CreateOutcome.Created -> {
                    applyIncomeSourceServerSnapshot(item, source.id, created.remote)
                    return
                }
                is CreateOutcome.Recovered -> {
                    incomeSourceDao.updateSyncStatus(source.id, SyncStatus.PENDING.value, created.serverId.toString())
                    created.serverId
                }
            }

        val remote =
            apiClient.updateIncomeSource(
                id = serverId,
                request =
                    UpdateIncomeSourceRequest(
                        name = source.name,
                        isActive = activeFlagForUpdate(source.isActive, item),
                        isFixed = source.isFixed.toInt(),
                        fixedAmount = source.fixedAmount?.let { it.toDouble() / 100.0 },
                        autoDay = source.autoDay?.toInt(),
                        requireConfirm = source.requireConfirm.toInt(),
                    ),
                operationId = updateOperationId(item, hadServerId = knownServerId != null),
            )
        applyIncomeSourceServerSnapshot(item, source.id, remote)
    }

    /**
     * Синхронизация дохода на сервер
     */
    private suspend fun syncIncomeToServer(item: SyncQueueItem) {
        val operationId = getOperationId(item)
        if (item.operation == SyncOperation.DELETE.value) {
            getDeleteServerId(item)?.toIntOrNull()?.let { serverId ->
                apiClient.deleteIncome(serverId, operationId)
            }
            return
        }

        val income = incomeDao.getById(item.entityId)
        if (income == null) {
            // Income was deleted locally before sync - nothing to sync
            println("[SYNC] Income already deleted locally, skipping sync for entityId=${item.entityId}")
            return
        }

        val knownServerId = income.serverId?.toIntOrNull()
        val serverId =
            knownServerId ?: run {
                val monthServerId = resolveMonthServerId(income.monthId)
                val sourceServerId = resolveIncomeSourceServerId(income.incomeSourceId)

                if (monthServerId == null || sourceServerId == null) {
                    throw IllegalStateException(
                        "Cannot sync income: month serverId=$monthServerId, source serverId=$sourceServerId"
                    )
                }

                val source = incomeSourceDao.getById(income.incomeSourceId)
                    ?: throw IllegalStateException("Income source not found: id=${income.incomeSourceId}")

                val created =
                    createOnServer {
                        apiClient.addIncome(
                            AddIncomeRequest(
                                monthId = monthServerId.toInt(),
                                source = source.name,
                                amount = income.amount.toDouble(),
                                date = income.date,
                            ),
                            operationId,
                        )
                    }
                when (created) {
                    is CreateOutcome.Created -> {
                        applyIncomeServerSnapshot(item, income.id, created.remote, incomeSourceId = income.incomeSourceId)
                        return
                    }
                    is CreateOutcome.Recovered -> {
                        incomeDao.updateSyncStatus(income.id, SyncStatus.PENDING.value, created.serverId.toString())
                        created.serverId
                    }
                }
            }

        val remote =
            apiClient.updateIncome(serverId, income.amount.toDouble(), updateOperationId(item, hadServerId = knownServerId != null))
        applyIncomeServerSnapshot(item, income.id, remote, incomeSourceId = income.incomeSourceId)
    }

    /**
     * Синхронизация расхода на сервер
     */
    private suspend fun syncExpenseToServer(item: SyncQueueItem) {
        val operationId = getOperationId(item)
        if (item.operation == SyncOperation.DELETE.value) {
            getDeleteServerId(item)?.toIntOrNull()?.let { serverId ->
                apiClient.deleteExpense(serverId, operationId)
            }
            return
        }

        val expense = expenseDao.getById(item.entityId)
        if (expense == null) {
            // Expense was deleted locally before sync - nothing to sync
            println("[SYNC] Expense already deleted locally, skipping sync for entityId=${item.entityId}")
            return
        }

        val knownServerId = expense.serverId?.toIntOrNull()
        val serverId =
            knownServerId ?: run {
                val monthServerId = resolveMonthServerId(expense.monthId)
                val categoryServerId = resolveCategoryServerId(expense.categoryId)
                println("[SYNC] syncExpenseToServer create: expenseId=${expense.id}, monthId=${expense.monthId}, categoryId=${expense.categoryId}, month.serverId=$monthServerId, category.serverId=$categoryServerId")

                if (monthServerId == null || categoryServerId == null) {
                    throw IllegalStateException(
                        "Cannot sync expense: month serverId=$monthServerId, category serverId=$categoryServerId"
                    )
                }

                val created =
                    createOnServer {
                        apiClient.addExpense(
                            AddExpenseRequest(
                                monthId = monthServerId.toInt(),
                                categoryId = categoryServerId.toInt(),
                                amount = expense.amount.toDouble(),
                                comment = expense.description,
                                date = expense.date,
                            ),
                            operationId,
                        )
                    }
                when (created) {
                    is CreateOutcome.Created -> {
                        applyExpenseServerSnapshot(item, expense.id, created.remote, categoryId = expense.categoryId)
                        return
                    }
                    is CreateOutcome.Recovered -> {
                        expenseDao.updateSyncStatus(expense.id, SyncStatus.PENDING.value, created.serverId.toString())
                        created.serverId
                    }
                }
            }

        val remote =
            apiClient.updateExpense(
                serverId,
                expense.amount.toDouble(),
                expense.description,
                updateOperationId(item, hadServerId = knownServerId != null),
            )
        applyExpenseServerSnapshot(item, expense.id, remote, categoryId = expense.categoryId)
    }

    /**
     * Гарантирует наличие serverId у месяца.
     * Если serverId отсутствует, пытается создать/получить месяц на сервере через ensureMonth.
     */
    private suspend fun resolveMonthServerId(monthLocalId: Long): String? {
        val month = monthDao.getById(monthLocalId) ?: return null
        if (month.serverId != null) return month.serverId

        return try {
            val remoteMonth = apiClient.ensureMonth(month.year.toInt(), month.month.toInt())
            monthDao.updateServerId(month.id, remoteMonth.id.toString())
            remoteMonth.id.toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isConnectivityFailure()) throw e
            println("[SYNC] resolveMonthServerId failed: monthId=$monthLocalId, error=${e.message}")
            null
        }
    }

    /**
     * Сохраняет serverId родителя, созданного дочерней операцией. Создан этим запросом — запись
     * совпадает с серверной; восстановлен по ключу — локальные правки ещё не на сервере, их дошлёт
     * собственный элемент очереди записи.
     */
    private fun <T> rememberCreatedServerId(
        created: CreateOutcome<T>,
        remoteId: (T) -> Int,
        save: (syncStatus: String, serverId: String) -> Unit,
    ): String {
        val (status, serverId) =
            when (created) {
                is CreateOutcome.Created -> SyncStatus.SYNCED.value to remoteId(created.remote).toString()
                is CreateOutcome.Recovered -> SyncStatus.PENDING.value to created.serverId.toString()
            }
        save(status, serverId)
        return serverId
    }

    /**
     * Пытается получить serverId категории.
     * Если отсутствует, делает sync справочника категорий и повторяет lookup.
     */
    private suspend fun resolveCategoryServerId(categoryLocalId: Long): String? {
        val category = categoryDao.getById(categoryLocalId) ?: return null
        if (category.serverId != null) return category.serverId

        return try {
            categoryRepository.syncWithServer(currentUserId)
            val syncedServerId = categoryDao.getById(categoryLocalId)?.serverId
            if (syncedServerId != null) {
                syncedServerId
            } else {
                // Ключ собственного INSERT категории: когда очередь дойдёт до него, сервер узнает
                // повтор, и вторая категория не появится
                val created =
                    createOnServer {
                        apiClient.createCategory(
                            name = category.name,
                            isFixed = if (category.isFixed == 1L) 1 else null,
                            fixedAmount = category.fixedAmount?.let { it.toDouble() / 100.0 },
                            autoDay = category.autoDay?.toInt(),
                            requireConfirm = if (category.isFixed == 1L) category.requireConfirm.toInt() else null,
                            operationId = queue.openInsertOperationId(category.userId, EntityType.CATEGORY.value, category.id),
                        )
                    }
                rememberCreatedServerId(created, { it.id }) { status, serverId ->
                    categoryDao.updateSyncStatus(id = category.id, syncStatus = status, serverId = serverId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isConnectivityFailure()) throw e
            println("[SYNC] resolveCategoryServerId failed: categoryId=$categoryLocalId, error=${e.message}")
            null
        }
    }

    /**
     * Пытается получить serverId источника дохода.
     * Если отсутствует, делает sync справочника источников и повторяет lookup.
     */
    private suspend fun resolveIncomeSourceServerId(sourceLocalId: Long): String? {
        val source = incomeSourceDao.getById(sourceLocalId) ?: return null
        if (source.serverId != null) return source.serverId

        return try {
            incomeSourceRepository.syncWithServer(currentUserId)
            val syncedServerId = incomeSourceDao.getById(sourceLocalId)?.serverId
            if (syncedServerId != null) {
                syncedServerId
            } else {
                val created =
                    createOnServer {
                        apiClient.createIncomeSource(
                            name = source.name,
                            isFixed = if (source.isFixed == 1L) 1 else null,
                            fixedAmount = source.fixedAmount?.let { it.toDouble() / 100.0 },
                            autoDay = source.autoDay?.toInt(),
                            requireConfirm = if (source.isFixed == 1L) source.requireConfirm.toInt() else null,
                            operationId = queue.openInsertOperationId(source.userId, EntityType.INCOME_SOURCE.value, source.id),
                        )
                    }
                rememberCreatedServerId(created, { it.id }) { status, serverId ->
                    incomeSourceDao.updateSyncStatus(id = source.id, syncStatus = status, serverId = serverId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isConnectivityFailure()) throw e
            println("[SYNC] resolveIncomeSourceServerId failed: sourceId=$sourceLocalId, error=${e.message}")
            null
        }
    }

    /**
     * Синхронизация бюджета на сервер.
     * POST /budgets — upsert по паре (месяц, категория): одним запросом и создаём, и меняем лимит,
     * поэтому serverId бюджета для отправки не нужен.
     */
    private suspend fun syncBudgetToServer(item: SyncQueueItem) {
        if (item.operation == SyncOperation.DELETE.value) {
            // Удаления бюджета в API нет
            return
        }

        val budget = budgetDao.getById(item.entityId)
        if (budget == null) {
            println("[SYNC] syncBudgetToServer: budget not found for entityId=${item.entityId}")
            return
        }

        val monthServerId = resolveMonthServerId(budget.monthId)
        val categoryServerId = resolveCategoryServerId(budget.categoryId)
        if (monthServerId == null || categoryServerId == null) {
            throw IllegalStateException(
                "Cannot sync budget: month serverId=$monthServerId, category serverId=$categoryServerId"
            )
        }

        val remoteBudget =
            apiClient.setBudget(
                SetBudgetRequest(
                    monthId = monthServerId.toInt(),
                    categoryId = categoryServerId.toInt(),
                    limitAmount = budget.limitAmount.toDouble(),
                ),
                getOperationId(item),
            )
        println("[SYNC] syncBudgetToServer: ok, budgetId=${budget.id}, serverId=${remoteBudget.id}")
        applyBudgetServerSnapshot(item, budget.id, remoteBudget)
    }

    /**
     * Синхронизация цели накоплений на сервер
     */
    /**
     * Отправка исключения план-слоя (skip/override/reset) на сервер.
     * entityId кодирует ключ (тип, локальный id шаблона, локальный id месяца);
     * полное АКТУАЛЬНОЕ состояние читается из БД в момент отправки (last-write-wins),
     * отсутствие строки = дефолт (сервер удалит своё исключение).
     */
    private suspend fun syncPlannedOverrideToServer(item: SyncQueueItem) {
        val (templateType, templateId, monthId) = PlannedQueueKey.decode(item.entityId)

        val serverMonthId = resolveMonthServerId(monthId)?.toIntOrNull()
            ?: throw Exception("Month not synced yet for planned override (monthId=$monthId)")

        val serverTemplateId = if (templateType == "category") {
            categoryDao.getById(templateId)?.serverId?.toIntOrNull()
        } else {
            incomeSourceDao.getById(templateId)?.serverId?.toIntOrNull()
        } ?: throw Exception("Template not synced yet for planned override ($templateType:$templateId)")

        val row = plannedOverrideDao.get(item.userId, templateType, templateId, monthId)

        try {
            apiClient.putPlannedOverride(
                monthId = serverMonthId,
                templateType = templateType,
                templateId = serverTemplateId,
                request = PlannedOverrideRequest(
                    isSkipped = if (row?.isSkipped == 1L) 1 else 0,
                    overrideAmount = row?.overrideAmount?.let { it / 100.0 },
                    overrideDay = row?.overrideDay?.toInt(),
                ),
                operationId = getOperationId(item),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 409 = платёж уже материализован на сервере — исключение неактуально,
            // pull planned-state приведёт локальное состояние в порядок
            val alreadyMaterialized =
                e is ApiException && e.statusCode == 409 && e.code != ApiException.CODE_IDEMPOTENCY_KEY_REUSED
            if (!alreadyMaterialized) throw e
            println("[SYNC] syncPlannedOverrideToServer: 409 (already materialized), dropping override")
        }

        if (row != null) {
            // Помечаем SYNCED только если строку не изменили во время отправки:
            // иначе затёрли бы «грязное» состояние, которое ещё не доехало на сервер
            // (новое изменение породит свою queue-операцию и отправится отдельно).
            val current = plannedOverrideDao.get(item.userId, templateType, templateId, monthId)
            if (current != null && current.updatedAt == row.updatedAt) {
                plannedOverrideDao.updateSyncStatus(
                    userId = item.userId,
                    templateType = templateType,
                    templateId = templateId,
                    monthId = monthId,
                    syncStatus = SyncStatus.SYNCED.value,
                )
            }
        }
    }

    private suspend fun syncSavingsGoalToServer(item: SyncQueueItem) {
        val operationId = getOperationId(item)
        if (item.operation == SyncOperation.DELETE.value) {
            val serverId = getDeleteServerId(item)?.toIntOrNull() ?: return
            try {
                apiClient.deleteSavingsGoal(serverId, operationId)
            } catch (e: ApiException) {
                if (e.statusCode != 409 || e.code != ApiException.CODE_SAVINGS_GOAL_NOT_EMPTY) throw e
                // Локально копилка была пустой, но на сервере на ней есть деньги (пополнили с
                // другого устройства) — сервер удалять отказался. Удаление отменяется: полная
                // синхронизация вернёт копилку с её операциями
                println("[SYNC] savings goal serverId=$serverId is not empty on server, deletion cancelled")
                syncAll()
            }
            return
        }

        val goal = savingsGoalDao.getById(item.entityId)
        if (goal == null) {
            // Savings goal was deleted locally before sync - nothing to sync
            println("[SYNC] Savings goal already deleted locally, skipping sync for entityId=${item.entityId}")
            return
        }

        // Сумма уходит на сервер, только если пользователь задал её явно. Иначе сервер считает её
        // сам по транзакциям, а локальная сумма может включать ещё не отправленные пополнения
        val explicitCurrentAmount = decodeSyncQueuePayloadMetadata(item.payload)?.goalCurrentAmount
        val knownServerId = goal.serverId?.toIntOrNull()
        val serverId =
            knownServerId ?: when (
                val created =
                    createOnServer {
                        apiClient.createSavingsGoal(
                            CreateSavingsGoalRequest(
                                name = goal.name,
                                targetAmount = goal.targetAmount.toDouble(),
                            ),
                            operationId,
                        )
                    }
            ) {
                is CreateOutcome.Created -> {
                    if (explicitCurrentAmount == null) {
                        applySavingsGoalServerSnapshot(item, goal.id, created.remote)
                        return
                    }
                    savingsGoalDao.updateSyncStatus(goal.id, SyncStatus.PENDING.value, created.remote.id.toString())
                    created.remote.id
                }
                is CreateOutcome.Recovered -> {
                    savingsGoalDao.updateSyncStatus(goal.id, SyncStatus.PENDING.value, created.serverId.toString())
                    created.serverId
                }
            }

        println("[SYNC] syncSavingsGoalToServer: UPDATE serverId=$serverId, name=${goal.name}, targetAmount=${goal.targetAmount}, currentAmount=$explicitCurrentAmount")
        val remote =
            apiClient.updateSavingsGoal(
                id = serverId,
                request =
                    UpdateSavingsGoalRequest(
                        name = goal.name,
                        targetAmount = goal.targetAmount.toDouble(),
                        currentAmount = explicitCurrentAmount?.toDouble(),
                    ),
                operationId = updateOperationId(item, hadServerId = knownServerId != null),
            )
        applySavingsGoalServerSnapshot(item, goal.id, remote)
    }

    /**
     * Синхронизация транзакции накоплений на сервер
     */
    private suspend fun syncSavingsTransactionToServer(item: SyncQueueItem) {
        val operationId = getOperationId(item)
        if (item.operation == SyncOperation.DELETE.value) {
            getDeleteServerId(item)?.toIntOrNull()?.let { serverId ->
                apiClient.deleteSavingsTransaction(serverId, operationId)
            }
            return
        }

        val transaction = savingsTransactionDao.getById(item.entityId)
        if (transaction == null) {
            // Savings transaction was deleted locally before sync - nothing to sync
            println("[SYNC] Savings transaction already deleted locally, skipping sync for entityId=${item.entityId}")
            return
        }

        val goalServerId = resolveSavingsGoalServerId(transaction.savingsGoalId)
            ?: throw IllegalStateException("Cannot sync savings transaction: goal serverId is null for goalId=${transaction.savingsGoalId}")
        val monthServerId = resolveSavingsTransactionMonthServerId(transaction)
        val request =
            AddSavingsTransactionRequest(
                goalId = goalServerId.toInt(),
                amount = transaction.amount.toDouble(),
                date = transaction.date,
                monthId = monthServerId,
            )

        val knownServerId = transaction.serverId?.toIntOrNull()
        val serverId =
            knownServerId ?: when (val created = createOnServer { apiClient.addSavingsTransaction(request, operationId) }) {
                is CreateOutcome.Created -> {
                    println("[SYNC] syncSavingsTransactionToServer: created goalServerId=$goalServerId, monthServerId=$monthServerId, serverId=${created.remote.id}")
                    applySavingsTransactionServerSnapshot(item, transaction.id, created.remote)
                    return
                }
                is CreateOutcome.Recovered -> {
                    savingsTransactionDao.updateSyncStatus(transaction.id, SyncStatus.PENDING.value, created.serverId.toString())
                    created.serverId
                }
            }

        val remote =
            apiClient.updateSavingsTransaction(
                id = serverId,
                request = request,
                operationId = updateOperationId(item, hadServerId = knownServerId != null),
            )
        applySavingsTransactionServerSnapshot(item, transaction.id, remote)
    }

    // Без month_id сервер не создаёт скрытый расход «Пополнение копилки», и лимит месяца
    // не уменьшится никогда. Месяц, созданный офлайн, регистрируем на сервере; если не вышло —
    // бросаем, чтобы очередь повторила позже, а не отправила пополнение без месяца.
    private suspend fun resolveSavingsTransactionMonthServerId(
        transaction: ru.homebudget.finkeeper.data.local.dao.SavingsTransaction,
    ): Int? {
        val monthLocalId = transaction.monthId ?: return null
        // Снятию месяц на сервере не нужен (скрытый расход не создаётся) — не блокируем его очередь
        if (transaction.amount <= 0) return monthDao.getById(monthLocalId)?.serverId?.toIntOrNull()
        return resolveMonthServerId(monthLocalId)?.toIntOrNull()
            ?: throw IllegalStateException("Cannot sync savings transaction: month serverId is null for monthId=$monthLocalId")
    }

    /**
     * Пытается получить serverId цели накоплений.
     * Если отсутствует, делает sync целей и повторяет lookup,
     * затем пытается создать цель на сервере как fallback.
     */
    private suspend fun resolveSavingsGoalServerId(goalLocalId: Long): String? {
        val goal = savingsGoalDao.getById(goalLocalId) ?: return null
        if (goal.serverId != null) return goal.serverId

        return try {
            savingsGoalRepository.syncWithServer(currentUserId)
            val syncedServerId = savingsGoalDao.getById(goalLocalId)?.serverId
            if (syncedServerId != null) {
                syncedServerId
            } else {
                val created =
                    createOnServer {
                        apiClient.createSavingsGoal(
                            CreateSavingsGoalRequest(
                                name = goal.name,
                                targetAmount = goal.targetAmount.toDouble(),
                            ),
                            queue.openInsertOperationId(goal.userId, EntityType.SAVINGS_GOAL.value, goal.id),
                        )
                    }
                rememberCreatedServerId(created, { it.id }) { status, serverId ->
                    savingsGoalDao.updateSyncStatus(id = goal.id, syncStatus = status, serverId = serverId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isConnectivityFailure()) throw e
            println("[SYNC] resolveSavingsGoalServerId failed: goalId=$goalLocalId, error=${e.message}")
            null
        }
    }

    /**
     * Отправляет pending операции на сервер (только upload, без download).
     * Вызывается автоматически после enqueueSync для немедленной отправки изменений.
     * Использует отдельную корутину с задержкой, чтобы не конфликтовать с syncAll().
     */
    // Месяцы пополнений копилок, которые сейчас уйдут на сервер (снятия не нужны:
    // для них сервер скрытый расход не создаёт)
    private fun depositMonthIds(items: List<SyncQueueItem>): Set<Long> =
        items
            .filter { it.entityType == EntityType.SAVINGS_TRANSACTION.value && it.operation != SyncOperation.DELETE.value }
            .mapNotNull { savingsTransactionDao.getById(it.entityId) }
            .filter { it.amount > 0 }
            .mapNotNull { it.monthId }
            .toSet()

    // После выгрузки пополнение перестаёт считаться «невыгруженным», а его скрытый расход
    // создан сервером, но ещё не скачан — без этой догрузки остаток лимита на экране
    // подскочил бы обратно до следующей синхронизации
    private suspend fun pullHiddenSavingsExpenses(monthIds: Set<Long>): ServerPhaseResult {
        val phase = ServerPhase()
        for (monthId in monthIds) {
            phase.stepResult { expenseRepository.syncWithServer(currentUserId, monthId) }
        }
        return ServerPhaseResult(reachable = !phase.isUnreachable, serverError = phase.serverError)
    }

    // Один раз за жизнь процесса (вызов — только под захваченной синхронизацией): к этому моменту
    // отправку очереди ещё никто не вёл, значит все элементы в `syncing` остались от убитого
    // процесса и их надо вернуть в очередь
    private var staleSyncingReset = false

    private fun resetStaleSyncingOnce() {
        if (staleSyncingReset) return
        syncQueueDao.resetSyncingToPending()
        staleSyncingReset = true
    }

    private fun scheduleProcessQueue() {
        scope.launch {
            // Небольшая задержка, чтобы дать завершиться текущей транзакции
            delay(100)
            val token = tryBeginSync(SyncRequest.Queue) ?: return@launch
            println("[SYNC] scheduleProcessQueue: userId=$currentUserId")
            try {
                if (currentUserId <= 0L) {
                    println("[SYNC] scheduleProcessQueue: SKIPPED, userId=$currentUserId (not logged in)")
                    return@launch
                }
                val upload = uploadQueue()
                val pullError = upload.pullError
                when {
                    pullError != null -> {
                        pullFailed = true
                        _lastSyncError.value = pullError.toSyncErrorText()
                    }
                    upload.reachable -> markSyncSuccess(clearError = !upload.hasFailedItems && !pullFailed)
                    else -> println("[SYNC] scheduleProcessQueue: server unreachable, queue kept pending")
                }
                if (upload.syncedAny) _dataUpdated.tryEmit(Unit)
                println("[SYNC] scheduleProcessQueue DONE")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[SYNC] scheduleProcessQueue ERROR: ${e.message}")
                _lastSyncError.value = e.message ?: "scheduleProcessQueue error"
                e.printStackTrace()
            } finally {
                endSync(token)
            }
        }
    }

    /**
     * Возвращает набор serverId из незавершённых DELETE операций для указанного типа сущности.
     * Учитывает не только pending, но и failed/syncing элементы, чтобы remote merge
     * не восстанавливал локально удалённые записи, пока операция окончательно не закрыта.
     */
    fun getPendingDeleteServerIds(entityType: String): Set<String> {
        val queuedItems = syncQueueDao.getAllByUser(currentUserId)
        return queuedItems
            .filter {
                it.entityType == entityType &&
                    it.operation == SyncOperation.DELETE.value &&
                    it.status != SyncQueueStatus.COMPLETED.value
            }
            .mapNotNull { extractDeleteServerId(it.payload) }
            .toSet()
    }

    fun hasActiveQueueOperation(
        entityType: String,
        entityId: Long,
    ): Boolean {
        return syncQueueDao.getAllByUser(currentUserId).any {
            it.entityType == entityType &&
                it.entityId == entityId &&
                it.status != SyncQueueStatus.COMPLETED.value
        }
    }

    private fun getDeleteServerId(item: SyncQueueItem): String? = extractDeleteServerId(item.payload)

    private fun getOperationId(item: SyncQueueItem): String? = decodeSyncQueuePayloadMetadata(item.payload)?.opId

    private fun markEntityAfterSuccessfulSync(item: SyncQueueItem, serverId: String?) {
        val currentState = getEntitySyncState(item.entityType, item.entityId) ?: return

        val shouldKeepDirtyState =
            shouldPreserveDirtyStateAfterSuccessfulSync(
                operation = item.operation,
                payload = item.payload,
                currentUpdatedAt = currentState.updatedAt,
            )

        val targetStatus =
            if (shouldKeepDirtyState) {
                currentState.syncStatus
            } else {
                SyncStatus.SYNCED.value
            }

        when (item.entityType) {
            EntityType.CATEGORY.value ->
                categoryDao.updateSyncStatus(
                    id = item.entityId,
                    syncStatus = targetStatus,
                    serverId = serverId,
                )
            EntityType.INCOME_SOURCE.value ->
                incomeSourceDao.updateSyncStatus(
                    id = item.entityId,
                    syncStatus = targetStatus,
                    serverId = serverId,
                )
            EntityType.MONTH.value ->
                monthDao.updateSyncStatus(
                    id = item.entityId,
                    syncStatus = targetStatus,
                    serverId = serverId,
                )
            EntityType.INCOME.value ->
                incomeDao.updateSyncStatus(
                    id = item.entityId,
                    syncStatus = targetStatus,
                    serverId = serverId,
                )
            EntityType.EXPENSE.value ->
                expenseDao.updateSyncStatus(
                    id = item.entityId,
                    syncStatus = targetStatus,
                    serverId = serverId,
                )
            EntityType.BUDGET.value ->
                budgetDao.updateSyncStatus(
                    id = item.entityId,
                    syncStatus = targetStatus,
                    serverId = serverId,
                )
            EntityType.SAVINGS_GOAL.value ->
                savingsGoalDao.updateSyncStatus(
                    id = item.entityId,
                    syncStatus = targetStatus,
                    serverId = serverId,
                )
            EntityType.SAVINGS_TRANSACTION.value ->
                savingsTransactionDao.updateSyncStatus(
                    id = item.entityId,
                    syncStatus = targetStatus,
                    serverId = serverId,
                )
        }

        if (shouldKeepDirtyState) {
            println("[SYNC] markEntityAfterSuccessfulSync: preserving dirty state for ${item.entityType}#${item.entityId}, status=$targetStatus, serverId=$serverId")
        }
    }

    private fun shouldKeepDirtyStateAfterSuccessfulSync(item: SyncQueueItem): Boolean {
        val currentState = getEntitySyncState(item.entityType, item.entityId) ?: return false
        return shouldPreserveDirtyStateAfterSuccessfulSync(
            operation = item.operation,
            payload = item.payload,
            currentUpdatedAt = currentState.updatedAt,
        )
    }

    /**
     * Запись удалили на устройстве, пока шёл запрос: если сервер её только что создал, DELETE
     * в очередь не вставал (serverId ещё не было) — ставим его сами, иначе запись осталась бы
     * на сервере и вернулась при следующей синхронизации.
     */
    private fun deleteRemoteAfterLocalRemoval(
        item: SyncQueueItem,
        entityType: String,
        serverId: Int,
    ) {
        println("[SYNC] $entityType#${item.entityId} removed locally during sync, deleting server record $serverId")
        enqueueSync(item.userId, entityType, item.entityId, SyncOperation.DELETE.value, serverId.toString())
    }

    private fun applyCategoryServerSnapshot(
        item: SyncQueueItem,
        localId: Long,
        remote: ru.homebudget.finkeeper.data.model.Category,
    ) {
        if (shouldKeepDirtyStateAfterSuccessfulSync(item)) {
            markEntityAfterSuccessfulSync(item, remote.id.toString())
            return
        }
        val local = categoryDao.getById(localId) ?: return
        categoryDao.update(
            id = local.id,
            name = remote.name,
            type = local.type,
            icon = local.icon,
            color = local.color,
            // Ещё не отправленный порядок списка не перетираем серверным
            sortOrder = if (hasActiveQueueOperation(EntityType.CATEGORY_ORDER.value, 0L)) local.sortOrder else remote.sortOrder.toLong(),
            isActive = remote.isActive.toLong(),
            // Фиксированные поля обязательно из снапшота: у DAO дефолты 0/null,
            // и их пропуск стирал «фиксированность» локально после каждого push
            isFixed = remote.isFixed.toLong(),
            fixedAmount = remote.fixedAmount?.let { (it * 100).toLong() },
            autoDay = remote.autoDay?.toLong(),
            requireConfirm = remote.requireConfirm.toLong(),
            updatedAt = remote.updatedAt ?: local.updatedAt,
            serverId = remote.id.toString(),
            syncStatus = SyncStatus.SYNCED.value,
        )
    }

    private fun applyIncomeSourceServerSnapshot(
        item: SyncQueueItem,
        localId: Long,
        remote: ru.homebudget.finkeeper.data.model.IncomeSource,
    ) {
        if (shouldKeepDirtyStateAfterSuccessfulSync(item)) {
            markEntityAfterSuccessfulSync(item, remote.id.toString())
            return
        }
        val local = incomeSourceDao.getById(localId) ?: return
        incomeSourceDao.update(
            id = local.id,
            name = remote.name,
            sortOrder = if (hasActiveQueueOperation(EntityType.INCOME_SOURCE_ORDER.value, 0L)) local.sortOrder else remote.sortOrder.toLong(),
            isActive = remote.isActive.toLong(),
            // Фиксированные поля обязательно из снапшота (см. applyCategoryServerSnapshot)
            isFixed = remote.isFixed.toLong(),
            fixedAmount = remote.fixedAmount?.let { (it * 100).toLong() },
            autoDay = remote.autoDay?.toLong(),
            requireConfirm = (remote.requireConfirm ?: 0).toLong(),
            updatedAt = remote.updatedAt ?: local.updatedAt,
            serverId = remote.id.toString(),
            syncStatus = SyncStatus.SYNCED.value,
        )
    }

    private fun applyIncomeServerSnapshot(
        item: SyncQueueItem,
        localId: Long,
        remote: ru.homebudget.finkeeper.data.model.Income,
        incomeSourceId: Long,
    ) {
        if (shouldKeepDirtyStateAfterSuccessfulSync(item)) {
            markEntityAfterSuccessfulSync(item, remote.id.toString())
            return
        }
        val local = incomeDao.getById(localId) ?: return deleteRemoteAfterLocalRemoval(item, EntityType.INCOME.value, remote.id)
        incomeDao.update(
            id = local.id,
            monthId = local.monthId,
            incomeSourceId = incomeSourceId,
            amount = remote.amount.toLong(),
            description = remote.description,
            date = remote.date,
            updatedAt = remote.updatedAt ?: local.updatedAt,
            serverId = remote.id.toString(),
            syncStatus = SyncStatus.SYNCED.value,
        )
    }

    private fun applyExpenseServerSnapshot(
        item: SyncQueueItem,
        localId: Long,
        remote: ru.homebudget.finkeeper.data.model.Expense,
        categoryId: Long,
    ) {
        if (shouldKeepDirtyStateAfterSuccessfulSync(item)) {
            markEntityAfterSuccessfulSync(item, remote.id.toString())
            return
        }
        val local = expenseDao.getById(localId) ?: return deleteRemoteAfterLocalRemoval(item, EntityType.EXPENSE.value, remote.id)
        expenseDao.update(
            id = local.id,
            monthId = local.monthId,
            categoryId = categoryId,
            amount = remote.amount.toLong(),
            description = remote.comment,
            date = remote.date,
            updatedAt = remote.updatedAt ?: local.updatedAt,
            serverId = remote.id.toString(),
            syncStatus = SyncStatus.SYNCED.value,
            isHidden = local.isHidden,
        )
    }

    private fun applyBudgetServerSnapshot(
        item: SyncQueueItem,
        localId: Long,
        remote: ru.homebudget.finkeeper.data.model.Budget,
    ) {
        if (shouldKeepDirtyStateAfterSuccessfulSync(item)) {
            markEntityAfterSuccessfulSync(item, remote.id.toString())
            return
        }
        val local = budgetDao.getById(localId) ?: return
        budgetDao.update(
            id = local.id,
            monthId = local.monthId,
            categoryId = local.categoryId,
            limitAmount = remote.limitAmount.toLong(),
            updatedAt = remote.updatedAt ?: local.updatedAt,
            serverId = remote.id.toString(),
            syncStatus = SyncStatus.SYNCED.value,
        )
    }

    private fun applySavingsGoalServerSnapshot(
        item: SyncQueueItem,
        localId: Long,
        remote: ru.homebudget.finkeeper.data.model.SavingsGoal,
    ) {
        if (shouldKeepDirtyStateAfterSuccessfulSync(item)) {
            markEntityAfterSuccessfulSync(item, remote.id.toString())
            return
        }
        val local = savingsGoalDao.getById(localId) ?: return deleteRemoteAfterLocalRemoval(item, EntityType.SAVINGS_GOAL.value, remote.id)
        // Пока пополнения копилки ждут отправки, серверная сумма их ещё не включает —
        // локальную сумму не откатываем, её подтянет синхронизация после отправки транзакций
        val currentAmount =
            if (hasActiveSavingsGoalDependents(local.id)) local.currentAmount else remote.currentAmount.toLong()
        savingsGoalDao.update(
            id = local.id,
            name = remote.name,
            targetAmount = remote.targetAmount.toLong(),
            currentAmount = currentAmount,
            color = local.color,
            icon = local.icon,
            targetDate = local.targetDate,
            isAchieved = local.isAchieved,
            updatedAt = remote.updatedAt ?: local.updatedAt,
            serverId = remote.id.toString(),
            syncStatus = SyncStatus.SYNCED.value,
        )
    }

    private fun applySavingsTransactionServerSnapshot(
        item: SyncQueueItem,
        localId: Long,
        remote: ru.homebudget.finkeeper.data.model.SavingsTransaction,
    ) {
        if (shouldKeepDirtyStateAfterSuccessfulSync(item)) {
            markEntityAfterSuccessfulSync(item, remote.id.toString())
            return
        }
        val local = savingsTransactionDao.getById(localId) ?: return deleteRemoteAfterLocalRemoval(item, EntityType.SAVINGS_TRANSACTION.value, remote.id)
        savingsTransactionDao.update(
            id = local.id,
            savingsGoalId = local.savingsGoalId,
            monthId = local.monthId,
            amount = remote.amount.toLong(),
            type = local.type,
            description = local.description,
            date = remote.date,
            updatedAt = remote.updatedAt ?: local.updatedAt,
            serverId = remote.id.toString(),
            syncStatus = SyncStatus.SYNCED.value,
        )
    }

    private fun getEntityUpdatedAt(entityType: String, entityId: Long): String? =
        when (entityType) {
            EntityType.CATEGORY.value -> categoryDao.getById(entityId)?.updatedAt
            EntityType.INCOME_SOURCE.value -> incomeSourceDao.getById(entityId)?.updatedAt
            EntityType.MONTH.value -> monthDao.getById(entityId)?.updatedAt
            EntityType.INCOME.value -> incomeDao.getById(entityId)?.updatedAt
            EntityType.EXPENSE.value -> expenseDao.getById(entityId)?.updatedAt
            EntityType.BUDGET.value -> budgetDao.getById(entityId)?.updatedAt
            EntityType.SAVINGS_GOAL.value -> savingsGoalDao.getById(entityId)?.updatedAt
            EntityType.SAVINGS_TRANSACTION.value -> savingsTransactionDao.getById(entityId)?.updatedAt
            else -> null
        }

    private fun getEntitySyncState(entityType: String, entityId: Long): EntitySyncState? =
        when (entityType) {
            EntityType.CATEGORY.value ->
                categoryDao.getById(entityId)?.let { EntitySyncState(it.updatedAt, it.syncStatus) }
            EntityType.INCOME_SOURCE.value ->
                incomeSourceDao.getById(entityId)?.let { EntitySyncState(it.updatedAt, it.syncStatus) }
            EntityType.MONTH.value ->
                monthDao.getById(entityId)?.let { EntitySyncState(it.updatedAt, it.syncStatus) }
            EntityType.INCOME.value ->
                incomeDao.getById(entityId)?.let { EntitySyncState(it.updatedAt, it.syncStatus) }
            EntityType.EXPENSE.value ->
                expenseDao.getById(entityId)?.let { EntitySyncState(it.updatedAt, it.syncStatus) }
            EntityType.BUDGET.value ->
                budgetDao.getById(entityId)?.let { EntitySyncState(it.updatedAt, it.syncStatus) }
            EntityType.SAVINGS_GOAL.value ->
                savingsGoalDao.getById(entityId)?.let { EntitySyncState(it.updatedAt, it.syncStatus) }
            EntityType.SAVINGS_TRANSACTION.value ->
                savingsTransactionDao.getById(entityId)?.let { EntitySyncState(it.updatedAt, it.syncStatus) }
            else -> null
        }

    // Запись есть локально и не помечена синхронизированной
    private fun isEntityDirty(
        entityType: String,
        entityId: Long,
    ): Boolean {
        val state = getEntitySyncState(entityType, entityId) ?: return false
        return state.syncStatus != SyncStatus.SYNCED.value
    }

    private fun generateOperationId(): String =
        "${Clock.System.now().toEpochMilliseconds()}-${kotlin.random.Random.nextLong().toString(16)}"

    private data class EntitySyncState(
        val updatedAt: String?,
        val syncStatus: String,
    )

    /**
     * Обновляет количество ожидающих операций
     */
    private fun updatePendingCount() {
        scope.launch {
            _pendingCount.value = syncQueueDao.getPendingCount()
            val failedError = syncQueueDao.getFailedItems(limit = 1).firstOrNull()?.errorMessage
            if (!failedError.isNullOrBlank()) {
                _lastSyncError.value = failedError
            }
        }
    }

    /**
     * Очищает все завершённые операции
     */
    fun clearCompleted() {
        scope.launch {
            syncQueueDao.clearCompleted()
        }
    }

    /**
     * Повторяет неудачные операции
     */
    fun retryFailed() {
        scope.launch {
            syncQueueDao.retryFailed()
            updatePendingCount()
        }
    }
}

internal enum class QueueMergeAction {
    KEEP_EXISTING,
    REPLACE_WITH_NEW,
    DROP_BOTH,
}

internal fun resolveQueueMergeAction(existingOperation: String?, newOperation: String): QueueMergeAction {
    if (existingOperation == null) return QueueMergeAction.REPLACE_WITH_NEW

    return when {
        // Удаление побеждает: правка уже удалённой записи на сервер не уходит
        existingOperation == SyncOperation.DELETE.value -> QueueMergeAction.KEEP_EXISTING
        // Новая сущность уже ждёт INSERT; UPDATE просто меняет локальное состояние,
        // поэтому в очереди достаточно оставить INSERT.
        existingOperation == SyncOperation.INSERT.value && newOperation == SyncOperation.UPDATE.value -> {
            QueueMergeAction.KEEP_EXISTING
        }
        // Сущность создали и удалили до отправки на сервер — обе операции можно убрать.
        existingOperation == SyncOperation.INSERT.value && newOperation == SyncOperation.DELETE.value -> {
            QueueMergeAction.DROP_BOTH
        }
        else -> QueueMergeAction.REPLACE_WITH_NEW
    }
}

internal fun shouldApplyRemoteServerSnapshot(localUpdatedAt: String?, remoteUpdatedAt: String?): Boolean {
    if (remoteUpdatedAt.isNullOrBlank()) {
        return true
    }
    if (localUpdatedAt.isNullOrBlank()) {
        return true
    }
    return remoteUpdatedAt > localUpdatedAt
}

internal val syncQueuePayloadJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

@Serializable
internal data class SyncQueuePayloadMetadata(
    val opId: String? = null,
    val entityUpdatedAt: String? = null,
    val deleteServerId: String? = null,
    // Сумма копилки, заданная пользователем явно (абсолютная установка); null — сумму не менять
    val goalCurrentAmount: Long? = null,
    // UPDATE восстанавливает удалённую категорию или источник. Только такой UPDATE шлёт
    // is_active = 1: обычная правка не должна воскрешать запись, удалённую на другом устройстве
    val reactivate: Boolean = false,
)

internal fun encodeSyncQueuePayloadMetadata(metadata: SyncQueuePayloadMetadata): String =
    syncQueuePayloadJson.encodeToString(metadata)

internal fun decodeSyncQueuePayloadMetadata(payload: String?): SyncQueuePayloadMetadata? {
    if (payload.isNullOrBlank()) {
        return null
    }

    return try {
        syncQueuePayloadJson.decodeFromString<SyncQueuePayloadMetadata>(payload)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        SyncQueuePayloadMetadata(deleteServerId = payload)
    }
}

internal fun extractDeleteServerId(payload: String?): String? = decodeSyncQueuePayloadMetadata(payload)?.deleteServerId

/**
 * Пропустить можно только UPDATE, у которого в очереди есть более поздний элемент той же записи:
 * тот отправит актуальное состояние. INSERT и DELETE не пропускаются никогда (иначе запись
 * не создалась бы или не удалилась), а одиночный UPDATE всегда отправляет текущее состояние.
 * UPDATE с явной суммой копилки или с восстановлением записи тоже не пропускается: более поздний
 * элемент этих данных не несёт, и они не ушли бы на сервер.
 */
internal fun shouldSkipOutdatedQueueItem(
    operation: String,
    payload: String?,
    currentUpdatedAt: String?,
    hasLaterItem: Boolean,
): Boolean {
    if (operation != SyncOperation.UPDATE.value || !hasLaterItem) {
        return false
    }

    val meta = decodeSyncQueuePayloadMetadata(payload) ?: return false
    if (meta.goalCurrentAmount != null || meta.reactivate) return false
    val queuedUpdatedAt = meta.entityUpdatedAt ?: return false
    val resolvedCurrentUpdatedAt = currentUpdatedAt ?: return false
    return queuedUpdatedAt != resolvedCurrentUpdatedAt
}

internal fun shouldPreserveDirtyStateAfterSuccessfulSync(
    operation: String,
    payload: String?,
    currentUpdatedAt: String?,
): Boolean {
    if (operation == SyncOperation.DELETE.value) {
        return false
    }

    val queuedUpdatedAt = decodeSyncQueuePayloadMetadata(payload)?.entityUpdatedAt ?: return false
    val resolvedCurrentUpdatedAt = currentUpdatedAt ?: return false
    return queuedUpdatedAt != resolvedCurrentUpdatedAt
}

/**
 * Новый курсор tombstone. Сервер отдаёт записи с `deleted_at > since`, поэтому курсор — последняя
 * отметка времени перед первым отложенным tombstone (сам он и всё после него придут снова).
 */
internal fun nextDeletedRecordsCursor(
    since: String?,
    fetched: List<String>,
    deferred: List<String>,
): String? {
    val firstDeferred = deferred.minOrNull() ?: return fetched.maxOrNull() ?: since
    return fetched.filter { it < firstDeferred }.maxOrNull() ?: since
}

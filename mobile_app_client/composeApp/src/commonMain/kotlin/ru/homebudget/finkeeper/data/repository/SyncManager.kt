package ru.homebudget.finkeeper.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.homebudget.finkeeper.data.local.dao.*
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncQueueStatus
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.budget.BudgetRepository
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.expense.ExpenseRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeSourceRepository
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
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
    private val syncStateStorage: SyncStateStorage,
    private val tokenStorage: TokenStorage,
) {
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

    @kotlin.concurrent.Volatile
    private var syncStartedAt: Long = 0L

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

    private fun markSyncSuccess(timestamp: String = Clock.System.now().toString()) {
        syncStateStorage.lastSuccessfulSyncAt = timestamp
        _lastSuccessfulSyncAt.value = timestamp
        _lastSyncError.value = null
    }

    /**
     * Вызывается из WebSocketService при получении события об изменении данных на сервере.
     * Запускает синхронизацию с сервером и уведомляет ViewModels об обновлении.
     */
    fun notifyDataChanged() {
        scope.launch {
            syncAll()
            _dataUpdated.tryEmit(Unit)
        }
    }

    /**
     * Добавляет операцию в очередь синхронизации
     */
    fun enqueueSync(
        userId: Long,
        entityType: String,
        entityId: Long,
        operation: String,
        payload: String? = null,
    ) {
        scope.launch {
            try {
                if (userId <= 0L) {
                    val message = "Cannot enqueue sync: invalid userId=$userId for $entityType#$entityId"
                    println("[SYNC] $message")
                    _lastSyncError.value = message
                    return@launch
                }

                println("[SYNC] enqueueSync: type=$entityType, entityId=$entityId, op=$operation")
                val newPayload = buildQueuePayload(entityType, entityId, operation, payload)
                val existingQueuedItem =
                    syncQueueDao
                        .getAllByUser(userId)
                        .firstOrNull {
                            it.entityType == entityType &&
                                it.entityId == entityId &&
                                it.status != SyncQueueStatus.COMPLETED.value &&
                                it.status != SyncQueueStatus.SYNCING.value
                        }

                val operationToPersist =
                    when (resolveQueueMergeAction(existingQueuedItem?.operation, operation)) {
                    QueueMergeAction.KEEP_EXISTING -> {
                        existingQueuedItem?.operation ?: operation
                    }
                    QueueMergeAction.DROP_BOTH -> {
                        existingQueuedItem?.let { syncQueueDao.deleteById(it.id) }
                        updatePendingCount()
                        return@launch
                    }
                    QueueMergeAction.REPLACE_WITH_NEW -> {
                        operation
                    }
                }

                existingQueuedItem?.let { syncQueueDao.deleteById(it.id) }

                syncQueueDao.insert(
                    userId = userId,
                    entityType = entityType,
                    entityId = entityId,
                    operation = operationToPersist,
                    payload = newPayload,
                )
                updatePendingCount()

                // Немедленно отправляем на сервер (если доступен)
                scheduleProcessQueue()
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
        // Safety: force-reset _isSyncing if stuck for > 90 seconds
        if (_isSyncing.value) {
            val elapsed = Clock.System.now().toEpochMilliseconds() - syncStartedAt
            if (elapsed > 90_000L) {
                println("[SYNC] syncAll: force-resetting _isSyncing (stuck for ${elapsed}ms)")
                _isSyncing.value = false
            } else {
                println("[SYNC] syncAll: SKIPPED, already syncing for ${elapsed}ms")
                return
            }
        }

        scope.launch {
            _isSyncing.value = true
            syncStartedAt = Clock.System.now().toEpochMilliseconds()
            println("[SYNC] syncAll START: monthId=$monthId, userId=$currentUserId")
            try {
                if (currentUserId <= 0L) {
                    println("[SYNC] syncAll: SKIPPED, userId=$currentUserId (not logged in)")
                    return@launch
                }

                // Этап 1: Забираем данные с сервера (Download)
                syncFromServer(monthId)

                // Этап 2: Отправляем локальные изменения (Upload)
                syncQueueDao.retryFailed()
                val pendingItems = syncQueueDao.getPendingItems(limit = 50)
                println("[SYNC] syncAll upload: ${pendingItems.size} pending items")
                for (item in pendingItems) {
                    syncItemToServer(item)
                }

                // Очистка завершённых элементов
                syncQueueDao.clearCompleted()
                updatePendingCount()
                markSyncSuccess()

                // Уведомляем подписчиков об обновлении данных
                _dataUpdated.tryEmit(Unit)
                println("[SYNC] syncAll DONE")
            } catch (e: Exception) {
                println("[SYNC] syncAll ERROR: ${e.message}")
                _lastSyncError.value = e.message ?: "syncAll error"
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Синхронизация данных С СЕРВЕРА в локальную БД
     * Этот метод забирает актуальные данные с сервера и обновляет локальную БД
     */
    private suspend fun syncFromServer(monthId: Long?) {
        try {
            println("[SYNC] syncFromServer START: userId=$currentUserId, monthId=$monthId")
            // Синхронизируем справочники
            categoryRepository.syncWithServer(currentUserId)
            incomeSourceRepository.syncWithServer(currentUserId)
            savingsGoalRepository.syncWithServer(currentUserId)
            savingsGoalDao
                .getAllByUser(currentUserId)
                .filter { !it.serverId.isNullOrBlank() }
                .forEach { goal ->
                    savingsTransactionRepository.syncWithServer(currentUserId, goal.id)
                }

            // Синхронизируем данные за месяц (если указан)
            monthId?.let { id ->
                monthRepository.syncWithServer(currentUserId)
                incomeRepository.syncWithServer(currentUserId, id)
                expenseRepository.syncWithServer(currentUserId, id)
                budgetRepository.syncWithServer(currentUserId, id)
            }
            applyDeletedRecordsFromServer()
            println("[SYNC] syncFromServer DONE")
        } catch (e: Exception) {
            println("[SYNC] syncFromServer ERROR: ${e.message}")
            e.printStackTrace()
        }
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

        deletedRecords
            .sortedWith(
                compareBy<DeletedRecord> { priority[it.entityType] ?: 10 }
                    .thenBy { it.deletedAt },
            ).forEach { deletedRecord ->
                applyDeletedRecord(deletedRecord)
            }

        syncStateStorage.lastDeletedRecordsSyncAt = deletedRecords.maxOfOrNull { it.deletedAt }
    }

    private fun applyDeletedRecord(deletedRecord: DeletedRecord) {
        when (deletedRecord.entityType) {
            EntityType.CATEGORY.value -> {
                categoryDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.CATEGORY.value, local.id)
                    val hasDependentActiveQueue = hasActiveCategoryDependents(local.id)
                    if (!hasOwnActiveQueue && !hasDependentActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply category serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        categoryDao.deleteById(local.id)
                    } else {
                        println("[SYNC][TOMBSTONE] skip category serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue, dependentActive=$hasDependentActiveQueue")
                    }
                } ?: println("[SYNC][TOMBSTONE] skip category serverId=${deletedRecord.entityId}: local record not found")
            }

            EntityType.INCOME_SOURCE.value -> {
                incomeSourceDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.INCOME_SOURCE.value, local.id)
                    val hasDependentActiveQueue = hasActiveIncomeSourceDependents(local.id)
                    if (!hasOwnActiveQueue && !hasDependentActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply income_source serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        incomeSourceDao.deleteById(local.id)
                    } else {
                        println("[SYNC][TOMBSTONE] skip income_source serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue, dependentActive=$hasDependentActiveQueue")
                    }
                } ?: println("[SYNC][TOMBSTONE] skip income_source serverId=${deletedRecord.entityId}: local record not found")
            }

            EntityType.INCOME.value -> {
                incomeDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.INCOME.value, local.id)
                    if (!hasOwnActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply income serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        incomeDao.deleteById(local.id)
                    } else {
                        println("[SYNC][TOMBSTONE] skip income serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue")
                    }
                } ?: println("[SYNC][TOMBSTONE] skip income serverId=${deletedRecord.entityId}: local record not found")
            }

            EntityType.EXPENSE.value -> {
                expenseDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.EXPENSE.value, local.id)
                    if (!hasOwnActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply expense serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        expenseDao.deleteById(local.id)
                    } else {
                        println("[SYNC][TOMBSTONE] skip expense serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue")
                    }
                } ?: println("[SYNC][TOMBSTONE] skip expense serverId=${deletedRecord.entityId}: local record not found")
            }

            EntityType.SAVINGS_TRANSACTION.value -> {
                savingsTransactionDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.SAVINGS_TRANSACTION.value, local.id)
                    if (!hasOwnActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply savings_transaction serverId=${deletedRecord.entityId} -> localId=${local.id}")
                        savingsTransactionDao.deleteById(local.id)
                    } else {
                        println("[SYNC][TOMBSTONE] skip savings_transaction serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue")
                    }
                } ?: println("[SYNC][TOMBSTONE] skip savings_transaction serverId=${deletedRecord.entityId}: local record not found")
            }

            EntityType.SAVINGS_GOAL.value -> {
                savingsGoalDao.getByServerId(deletedRecord.entityId.toString())?.let { local ->
                    val hasOwnActiveQueue = hasActiveQueueOperation(EntityType.SAVINGS_GOAL.value, local.id)
                    val hasDependentActiveQueue = hasActiveSavingsGoalDependents(local.id)
                    if (!hasOwnActiveQueue && !hasDependentActiveQueue && local.syncStatus == SyncStatus.SYNCED.value) {
                        println("[SYNC][TOMBSTONE] apply savings_goal serverId=${deletedRecord.entityId} -> localId=${local.id} with child cleanup")
                        savingsTransactionDao.deleteAllByGoal(local.id)
                        savingsGoalDao.deleteById(local.id)
                    } else {
                        println("[SYNC][TOMBSTONE] skip savings_goal serverId=${deletedRecord.entityId} -> localId=${local.id}, syncStatus=${local.syncStatus}, ownActive=$hasOwnActiveQueue, dependentActive=$hasDependentActiveQueue")
                    }
                } ?: println("[SYNC][TOMBSTONE] skip savings_goal serverId=${deletedRecord.entityId}: local record not found")
            }
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
    private suspend fun syncItemToServer(item: SyncQueueItem) {
        try {
            if (shouldSkipOutdatedItem(item)) {
                println("[SYNC] syncItemToServer SKIPPED outdated item: type=${item.entityType}, entityId=${item.entityId}, op=${item.operation}")
                syncQueueDao.markCompleted(item.id)
                updatePendingCount()
                return
            }

            println("[SYNC] syncItemToServer START: type=${item.entityType}, entityId=${item.entityId}, op=${item.operation}, userId=${item.userId}")
            syncQueueDao.updateStatus(
                id = item.id,
                status = SyncQueueStatus.SYNCING.value,
                errorMessage = null,
            )

            when (item.entityType) {
                "category" -> syncCategoryToServer(item)
                "income_source" -> syncIncomeSourceToServer(item)
                "income" -> syncIncomeToServer(item)
                "expense" -> syncExpenseToServer(item)
                "budget" -> syncBudgetToServer(item)
                "savings_goal" -> syncSavingsGoalToServer(item)
                "savings_transaction" -> syncSavingsTransactionToServer(item)
                else -> println("[SYNC] syncItemToServer: UNKNOWN entityType=${item.entityType}")
            }

            println("[SYNC] syncItemToServer COMPLETED: type=${item.entityType}, entityId=${item.entityId}")
            syncQueueDao.updateStatus(
                id = item.id,
                status = SyncQueueStatus.COMPLETED.value,
                errorMessage = null,
            )
        } catch (e: Exception) {
            val errorMessage = e.message ?: e::class.simpleName ?: "Unknown sync error"
            println("[SYNC] syncItemToServer FAILED: type=${item.entityType}, entityId=${item.entityId}, error=$errorMessage")
            _lastSyncError.value = "Sync ${item.entityType}: $errorMessage"
            
            // Проверяем, является ли ошибка 404 (Not Found) - в этом случае повторные попытки бесполезны
            val isNotFoundError = errorMessage.contains("404") || 
                                  errorMessage.contains("Not Found", ignoreCase = true)
            
            if (isNotFoundError) {
                println("[SYNC] syncItemToServer: 404 error detected, marking as COMPLETED to avoid infinite retry")
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
        }
    }

    /**
     * Синхронизация категории на сервер
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

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val remote = apiClient.createCategory(
                    name = category.name,
                    isFixed = if (category.isFixed == 1L) 1 else null,
                    fixedAmount = category.fixedAmount?.let { it.toDouble() / 100.0 },
                    autoDay = category.autoDay?.toInt(),
                    operationId = operationId,
                )
                applyCategoryServerSnapshot(item, category.id, remote)
            }
            SyncOperation.UPDATE.value -> {
                category.serverId?.toIntOrNull()?.let { serverId ->
                    val remote =
                        apiClient.updateCategory(
                        id = serverId,
                        request =
                            UpdateCategoryRequest(
                                name = category.name,
                                isActive = if (category.isActive == 1L) 1 else 0,
                                isFixed = category.isFixed.toInt(),
                                fixedAmount = category.fixedAmount?.let { it.toDouble() / 100.0 },
                                autoDay = category.autoDay?.toInt(),
                            ),
                        operationId = operationId,
                    )
                    applyCategoryServerSnapshot(item, category.id, remote)
                }
            }
        }
    }

    /**
     * Синхронизация источника дохода на сервер
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

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val remote = apiClient.createIncomeSource(
                    name = source.name,
                    isFixed = if (source.isFixed == 1L) 1 else null,
                    fixedAmount = source.fixedAmount?.let { it.toDouble() / 100.0 },
                    autoDay = source.autoDay?.toInt(),
                    operationId = operationId,
                )
                applyIncomeSourceServerSnapshot(item, source.id, remote)
            }
            SyncOperation.UPDATE.value -> {
                source.serverId?.toIntOrNull()?.let { serverId ->
                    val remote =
                        apiClient.updateIncomeSource(
                        id = serverId,
                        request =
                            UpdateIncomeSourceRequest(
                                name = source.name,
                                isActive = if (source.isActive == 1L) 1 else 0,
                                isFixed = source.isFixed.toInt(),
                                fixedAmount = source.fixedAmount?.let { it.toDouble() / 100.0 },
                                autoDay = source.autoDay?.toInt(),
                            ),
                        operationId = operationId,
                    )
                    applyIncomeSourceServerSnapshot(item, source.id, remote)
                }
            }
        }
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

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val monthServerId = resolveMonthServerId(income.monthId)
                val sourceServerId = resolveIncomeSourceServerId(income.incomeSourceId)

                if (monthServerId == null || sourceServerId == null) {
                    throw IllegalStateException(
                        "Cannot sync income: month serverId=$monthServerId, source serverId=$sourceServerId"
                    )
                }

                val source = incomeSourceDao.getById(income.incomeSourceId)
                    ?: throw IllegalStateException("Income source not found: id=${income.incomeSourceId}")

                val remote =
                    apiClient.addIncome(
                        AddIncomeRequest(
                            monthId = monthServerId.toInt(),
                            source = source.name,
                            amount = income.amount.toDouble(),
                            date = income.date,
                        ),
                        operationId,
                    )
                applyIncomeServerSnapshot(item, income.id, remote, incomeSourceId = income.incomeSourceId)
            }
            SyncOperation.UPDATE.value -> {
                val serverId =
                    income.serverId?.toIntOrNull()
                        ?: throw IllegalStateException("Cannot sync income update: serverId is null for entityId=${item.entityId}")

                val remote = apiClient.updateIncome(serverId, income.amount.toDouble(), operationId)
                applyIncomeServerSnapshot(item, income.id, remote, incomeSourceId = income.incomeSourceId)
            }
        }
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

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val monthServerId = resolveMonthServerId(expense.monthId)
                val categoryServerId = resolveCategoryServerId(expense.categoryId)
                println("[SYNC] syncExpenseToServer INSERT: expenseId=${expense.id}, monthId=${expense.monthId}, categoryId=${expense.categoryId}, month.serverId=$monthServerId, category.serverId=$categoryServerId")

                if (monthServerId == null || categoryServerId == null) {
                    throw IllegalStateException(
                        "Cannot sync expense: month serverId=$monthServerId, category serverId=$categoryServerId"
                    )
                }

                val remote =
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
                applyExpenseServerSnapshot(item, expense.id, remote, categoryId = expense.categoryId)
            }
            SyncOperation.UPDATE.value -> {
                val serverId =
                    expense.serverId?.toIntOrNull()
                        ?: throw IllegalStateException("Cannot sync expense update: serverId is null for entityId=${item.entityId}")

                val remote = apiClient.updateExpense(serverId, expense.amount.toDouble(), operationId)
                applyExpenseServerSnapshot(item, expense.id, remote, categoryId = expense.categoryId)
            }
        }
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
        } catch (e: Exception) {
            println("[SYNC] resolveMonthServerId failed: monthId=$monthLocalId, error=${e.message}")
            null
        }
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
                val remote = apiClient.createCategory(
                    name = category.name,
                    isFixed = if (category.isFixed == 1L) 1 else null,
                    fixedAmount = category.fixedAmount?.let { it.toDouble() / 100.0 },
                    autoDay = category.autoDay?.toInt(),
                )
                categoryDao.updateSyncStatus(
                    id = category.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = remote.id.toString(),
                )
                remote.id.toString()
            }
        } catch (e: Exception) {
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
                val remote = apiClient.createIncomeSource(
                    name = source.name,
                    isFixed = if (source.isFixed == 1L) 1 else null,
                    fixedAmount = source.fixedAmount?.let { it.toDouble() / 100.0 },
                    autoDay = source.autoDay?.toInt(),
                )
                incomeSourceDao.updateSyncStatus(
                    id = source.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = remote.id.toString(),
                )
                remote.id.toString()
            }
        } catch (e: Exception) {
            println("[SYNC] resolveIncomeSourceServerId failed: sourceId=$sourceLocalId, error=${e.message}")
            null
        }
    }

    /**
     * Синхронизация бюджета на сервер
     */
    private suspend fun syncBudgetToServer(item: SyncQueueItem) {
        val operationId = getOperationId(item)
        println("[SYNC] syncBudgetToServer START: entityId=${item.entityId}, operation=${item.operation}")
        
        if (item.operation == SyncOperation.DELETE.value) {
            // В API нет метода удаления бюджета
            // item.payload?.toIntOrNull()?.let { serverId -> apiClient.deleteBudget(serverId) }
            return
        }

        val budget = budgetDao.getById(item.entityId)
        if (budget == null) {
            println("[SYNC] syncBudgetToServer: budget not found for entityId=${item.entityId}")
            return
        }
        println("[SYNC] syncBudgetToServer: budget found - id=${budget.id}, monthId=${budget.monthId}, categoryId=${budget.categoryId}, limitAmount=${budget.limitAmount}, serverId=${budget.serverId}, syncStatus=${budget.syncStatus}")

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val month = monthDao.getById(budget.monthId)
                val category = categoryDao.getById(budget.categoryId)
                println("[SYNC] syncBudgetToServer INSERT: month.serverId=${month?.serverId}, category.serverId=${category?.serverId}")

                if (month?.serverId == null || category?.serverId == null) {
                    throw IllegalStateException(
                        "Cannot sync budget: month serverId=${month?.serverId}, category serverId=${category?.serverId}"
                    )
                }

                println("[SYNC] syncBudgetToServer INSERT: calling setBudget...")
                val remoteBudget = apiClient.setBudget(
                    SetBudgetRequest(
                        monthId = month.serverId.toInt(),
                        categoryId = category.serverId.toInt(),
                        limitAmount = budget.limitAmount.toDouble(),
                    ),
                    operationId,
                )
                println("[SYNC] syncBudgetToServer INSERT: success, remoteBudget.id=${remoteBudget.id}")
                applyBudgetServerSnapshot(item, budget.id, remoteBudget)
                println("[SYNC] syncBudgetToServer INSERT: syncStatus updated to SYNCED")
            }
            SyncOperation.UPDATE.value -> {
                val month = monthDao.getById(budget.monthId)
                val category = categoryDao.getById(budget.categoryId)
                println("[SYNC] syncBudgetToServer UPDATE: month.serverId=${month?.serverId}, category.serverId=${category?.serverId}")

                if (month?.serverId == null || category?.serverId == null) {
                    throw IllegalStateException(
                        "Cannot sync budget update: month serverId=${month?.serverId}, category serverId=${category?.serverId}"
                    )
                }

                val serverId = budget.serverId?.toIntOrNull()
                if (serverId != null) {
                    // Обновляем существующий бюджет на сервере
                    try {
                        println("[SYNC] syncBudgetToServer UPDATE: calling updateBudget with serverId=$serverId...")
                        val remoteBudget =
                            apiClient.updateBudget(
                            id = serverId,
                            request =
                                SetBudgetRequest(
                                    monthId = month.serverId.toInt(),
                                    categoryId = category.serverId.toInt(),
                                    limitAmount = budget.limitAmount.toDouble(),
                                ),
                            operationId = operationId,
                        )
                        println("[SYNC] syncBudgetToServer UPDATE: updateBudget success")
                        applyBudgetServerSnapshot(item, budget.id, remoteBudget)
                    } catch (e: Exception) {
                        // Если получили 404 - бюджет не найден на сервере, создаём новый
                        val errorMessage = e.message ?: ""
                        if (errorMessage.contains("404") || errorMessage.contains("Not Found", ignoreCase = true)) {
                            println("[SYNC] syncBudgetToServer UPDATE: 404 error, falling back to CREATE")
                            val remoteBudget = apiClient.setBudget(
                                SetBudgetRequest(
                                    monthId = month.serverId.toInt(),
                                    categoryId = category.serverId.toInt(),
                                    limitAmount = budget.limitAmount.toDouble(),
                                ),
                                operationId,
                            )
                            println("[SYNC] syncBudgetToServer UPDATE: setBudget success, new id=${remoteBudget.id}")
                            applyBudgetServerSnapshot(item, budget.id, remoteBudget)
                        } else {
                            throw e // Пробрасываем другие ошибки
                        }
                    }
                } else {
                    // Бюджет ещё не на сервере - создаём его
                    println("[SYNC] syncBudgetToServer UPDATE: no serverId, creating new budget...")
                    val remoteBudget = apiClient.setBudget(
                        SetBudgetRequest(
                            monthId = month.serverId.toInt(),
                            categoryId = category.serverId.toInt(),
                            limitAmount = budget.limitAmount.toDouble(),
                        ),
                        operationId,
                    )
                    println("[SYNC] syncBudgetToServer UPDATE: setBudget success, id=${remoteBudget.id}")
                    applyBudgetServerSnapshot(item, budget.id, remoteBudget)
                }
            }
        }
    }

    /**
     * Синхронизация цели накоплений на сервер
     */
    private suspend fun syncSavingsGoalToServer(item: SyncQueueItem) {
        val operationId = getOperationId(item)
        if (item.operation == SyncOperation.DELETE.value) {
            getDeleteServerId(item)?.toIntOrNull()?.let { serverId ->
                apiClient.deleteSavingsGoal(serverId, operationId)
            }
            return
        }

        val goal = savingsGoalDao.getById(item.entityId)
        if (goal == null) {
            // Savings goal was deleted locally before sync - nothing to sync
            println("[SYNC] Savings goal already deleted locally, skipping sync for entityId=${item.entityId}")
            return
        }

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val remote =
                    apiClient.createSavingsGoal(
                        CreateSavingsGoalRequest(
                            name = goal.name,
                            targetAmount = goal.targetAmount.toDouble(),
                        ),
                        operationId,
                    )
                applySavingsGoalServerSnapshot(item, goal.id, remote)
            }
            SyncOperation.UPDATE.value -> {
                val serverId =
                    goal.serverId?.toIntOrNull()
                        ?: throw IllegalStateException("Cannot sync savings goal update: serverId is null for entityId=${item.entityId}")

                println("[SYNC] syncSavingsGoalToServer: UPDATE serverId=$serverId, name=${goal.name}, targetAmount=${goal.targetAmount}, currentAmount=${goal.currentAmount}")
                val remote =
                    apiClient.updateSavingsGoal(
                    id = serverId,
                    request =
                        UpdateSavingsGoalRequest(
                            name = goal.name,
                            targetAmount = goal.targetAmount.toDouble(),
                            currentAmount = goal.currentAmount.toDouble(),
                        ),
                    operationId = operationId,
                )
                println("[SYNC] syncSavingsGoalToServer: UPDATE OK")
                applySavingsGoalServerSnapshot(item, goal.id, remote)
            }
        }
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

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val goalServerId = resolveSavingsGoalServerId(transaction.savingsGoalId)
                    ?: throw IllegalStateException("Cannot sync savings transaction: goal serverId is null for goalId=${transaction.savingsGoalId}")

                // Resolve month serverId for proper expense creation on server
                val monthServerId = transaction.monthId?.let { localMonthId ->
                    monthDao.getById(localMonthId)?.serverId?.toIntOrNull()
                }

                println("[SYNC] syncSavingsTransactionToServer: INSERT goalServerId=$goalServerId, monthServerId=$monthServerId, amount=${transaction.amount}")
                val serverTransaction = apiClient.addSavingsTransaction(
                    AddSavingsTransactionRequest(
                        goalId = goalServerId.toInt(),
                        amount = transaction.amount.toDouble(),
                        date = transaction.date,
                        monthId = monthServerId,
                    ),
                    operationId,
                )
                println("[SYNC] syncSavingsTransactionToServer: INSERT OK, serverTransaction.id=${serverTransaction.id}")
                applySavingsTransactionServerSnapshot(item, transaction.id, serverTransaction)
            }
            SyncOperation.UPDATE.value -> {
                val goalServerId = resolveSavingsGoalServerId(transaction.savingsGoalId)
                    ?: throw IllegalStateException("Cannot sync savings transaction update: goal serverId is null for goalId=${transaction.savingsGoalId}")
                val serverId =
                    transaction.serverId?.toIntOrNull()
                        ?: throw IllegalStateException("Cannot sync savings transaction update: transaction serverId is null for entityId=${item.entityId}")

                // Resolve month serverId for proper expense creation on server
                val monthServerId = transaction.monthId?.let { localMonthId ->
                    monthDao.getById(localMonthId)?.serverId?.toIntOrNull()
                }

                val remote =
                    apiClient.updateSavingsTransaction(
                    id = serverId,
                    request =
                        AddSavingsTransactionRequest(
                            goalId = goalServerId.toInt(),
                            amount = transaction.amount.toDouble(),
                            date = transaction.date,
                            monthId = monthServerId,
                        ),
                    operationId = operationId,
                )
                applySavingsTransactionServerSnapshot(item, transaction.id, remote)
            }
        }
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
                val remote =
                    apiClient.createSavingsGoal(
                        CreateSavingsGoalRequest(
                            name = goal.name,
                            targetAmount = goal.targetAmount.toDouble(),
                        ),
                    )
                savingsGoalDao.updateSyncStatus(
                    id = goal.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = remote.id.toString(),
                )
                remote.id.toString()
            }
        } catch (e: Exception) {
            println("[SYNC] resolveSavingsGoalServerId failed: goalId=$goalLocalId, error=${e.message}")
            null
        }
    }

    /**
     * Отправляет pending операции на сервер (только upload, без download).
     * Вызывается автоматически после enqueueSync для немедленной отправки изменений.
     * Использует отдельную корутину с задержкой, чтобы не конфликтовать с syncAll().
     */
    private fun scheduleProcessQueue() {
        scope.launch {
            // Небольшая задержка, чтобы дать завершиться текущей транзакции
            kotlinx.coroutines.delay(100)

            // Safety: force-reset _isSyncing if stuck > 90s
            if (_isSyncing.value) {
                val elapsed = Clock.System.now().toEpochMilliseconds() - syncStartedAt
                if (elapsed > 90_000L) {
                    println("[SYNC] scheduleProcessQueue: force-resetting _isSyncing (stuck ${elapsed}ms)")
                    _isSyncing.value = false
                }
            }

            println("[SYNC] scheduleProcessQueue: isSyncing=${_isSyncing.value}, userId=$currentUserId")
            // Ждём, пока syncAll() завершится (если запущен)
            var attempts = 0
            while (_isSyncing.value && attempts < 60) {
                kotlinx.coroutines.delay(500)
                attempts++
            }

            if (_isSyncing.value) {
                println("[SYNC] scheduleProcessQueue: SKIPPED, still syncing after $attempts attempts")
                return@launch
            }

            if (currentUserId <= 0L) {
                println("[SYNC] scheduleProcessQueue: SKIPPED, userId=$currentUserId (not logged in)")
                return@launch
            }

            _isSyncing.value = true
            syncStartedAt = Clock.System.now().toEpochMilliseconds()
            try {
                val pendingItems = syncQueueDao.getPendingItems(limit = 50)
                println("[SYNC] scheduleProcessQueue: ${pendingItems.size} pending items")
                for (item in pendingItems) {
                    println("[SYNC] processing item: id=${item.id}, type=${item.entityType}, entityId=${item.entityId}, op=${item.operation}, status=${item.status}")
                    syncItemToServer(item)
                }
                syncQueueDao.clearCompleted()
                updatePendingCount()
                
                // Очищаем ошибку, если все операции успешны (нет FAILED элементов)
                val hasFailedItems = syncQueueDao.getFailedItems(limit = 1).isNotEmpty()
                if (!hasFailedItems) {
                    markSyncSuccess()
                }
                
                if (pendingItems.isNotEmpty()) {
                    _dataUpdated.tryEmit(Unit)
                }
                println("[SYNC] scheduleProcessQueue DONE")
            } catch (e: Exception) {
                println("[SYNC] scheduleProcessQueue ERROR: ${e.message}")
                _lastSyncError.value = e.message ?: "scheduleProcessQueue error"
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
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

    private fun buildQueuePayload(
        entityType: String,
        entityId: Long,
        operation: String,
        fallbackPayload: String?,
    ): String? {
        return encodeSyncQueuePayloadMetadata(
            SyncQueuePayloadMetadata(
                opId = generateOperationId(),
                entityUpdatedAt = getEntityUpdatedAt(entityType, entityId),
                deleteServerId =
                    if (operation == SyncOperation.DELETE.value) {
                        fallbackPayload
                    } else {
                        null
                    },
            ),
        )
    }

    private fun getDeleteServerId(item: SyncQueueItem): String? = extractDeleteServerId(item.payload)

    private fun getOperationId(item: SyncQueueItem): String? = decodeSyncQueuePayloadMetadata(item.payload)?.opId

    private fun shouldSkipOutdatedItem(item: SyncQueueItem): Boolean {
        return shouldSkipOutdatedQueueItem(
            operation = item.operation,
            payload = item.payload,
            currentUpdatedAt = getEntityUpdatedAt(item.entityType, item.entityId),
        )
    }

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
            sortOrder = remote.sortOrder.toLong(),
            isActive = remote.isActive.toLong(),
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
            sortOrder = remote.sortOrder.toLong(),
            isActive = remote.isActive.toLong(),
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
        val local = incomeDao.getById(localId) ?: return
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
        val local = expenseDao.getById(localId) ?: return
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
        val local = savingsGoalDao.getById(localId) ?: return
        savingsGoalDao.update(
            id = local.id,
            name = remote.name,
            targetAmount = remote.targetAmount.toLong(),
            currentAmount = remote.currentAmount.toLong(),
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
        val local = savingsTransactionDao.getById(localId) ?: return
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
)

internal fun encodeSyncQueuePayloadMetadata(metadata: SyncQueuePayloadMetadata): String =
    syncQueuePayloadJson.encodeToString(metadata)

internal fun decodeSyncQueuePayloadMetadata(payload: String?): SyncQueuePayloadMetadata? {
    if (payload.isNullOrBlank()) {
        return null
    }

    return try {
        syncQueuePayloadJson.decodeFromString<SyncQueuePayloadMetadata>(payload)
    } catch (_: Exception) {
        SyncQueuePayloadMetadata(deleteServerId = payload)
    }
}

internal fun extractDeleteServerId(payload: String?): String? = decodeSyncQueuePayloadMetadata(payload)?.deleteServerId

internal fun shouldSkipOutdatedQueueItem(
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

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
import ru.homebudget.finkeeper.data.local.dao.*
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncQueueStatus
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.expense.ExpenseRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeSourceRepository
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsGoalRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsTransactionRepository
import kotlinx.datetime.Clock

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
    private val savingsGoalRepository: SavingsGoalRepository,
    private val savingsTransactionRepository: SavingsTransactionRepository,
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

    private val _dataUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dataUpdated: SharedFlow<Unit> = _dataUpdated.asSharedFlow()

    @kotlin.concurrent.Volatile
    private var syncStartedAt: Long = 0L

    init {
        updatePendingCount()
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
                // Дедупликация: сохраняем корректный порядок операций для сущности.
                // Важно: UPDATE не должен затирать pending INSERT, иначе новая запись никогда не уходит на сервер.
                val existingPending =
                    syncQueueDao
                        .getPendingItems(limit = 1000)
                        .firstOrNull { it.entityType == entityType && it.entityId == entityId }

                when (resolveQueueMergeAction(existingPending?.operation, operation)) {
                    QueueMergeAction.KEEP_EXISTING -> {
                        updatePendingCount()
                        return@launch
                    }
                    QueueMergeAction.DROP_BOTH -> {
                        existingPending?.let { syncQueueDao.deleteById(it.id) }
                        updatePendingCount()
                        return@launch
                    }
                    QueueMergeAction.REPLACE_WITH_NEW -> {
                        existingPending?.let { syncQueueDao.deleteById(it.id) }
                    }
                }

                syncQueueDao.insert(
                    userId = userId,
                    entityType = entityType,
                    entityId = entityId,
                    operation = operation,
                    payload = payload,
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

            // Синхронизируем данные за месяц (если указан)
            monthId?.let { id ->
                monthRepository.syncWithServer(currentUserId)
                incomeRepository.syncWithServer(currentUserId, id)
                expenseRepository.syncWithServer(currentUserId, id)
            }
            println("[SYNC] syncFromServer DONE")
        } catch (e: Exception) {
            println("[SYNC] syncFromServer ERROR: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Отправляет локальные изменения на сервер
     */
    private suspend fun syncItemToServer(item: SyncQueueItem) {
        try {
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
            syncQueueDao.updateStatus(
                id = item.id,
                status = SyncQueueStatus.FAILED.value,
                errorMessage = errorMessage,
            )
            updatePendingCount()
        }
    }

    /**
     * Синхронизация категории на сервер
     */
    private suspend fun syncCategoryToServer(item: SyncQueueItem) {
        if (item.operation == SyncOperation.DELETE.value) {
            item.payload?.toIntOrNull()?.let { serverId ->
                apiClient.deleteCategory(serverId)
            }
            return
        }

        val category = categoryDao.getById(item.entityId) ?: return

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val remote = apiClient.createCategory(category.name)
                categoryDao.updateSyncStatus(
                    id = category.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = remote.id.toString(),
                )
            }
            SyncOperation.UPDATE.value -> {
                category.serverId?.toIntOrNull()?.let { serverId ->
                    apiClient.updateCategory(
                        id = serverId,
                        request =
                            UpdateCategoryRequest(
                                name = category.name,
                                isActive = if (category.isActive == 1L) 1 else 0,
                            ),
                    )
                    categoryDao.updateSyncStatus(
                        id = category.id,
                        syncStatus = SyncStatus.SYNCED.value,
                        serverId = serverId.toString(),
                    )
                }
            }
        }
    }

    /**
     * Синхронизация источника дохода на сервер
     */
    private suspend fun syncIncomeSourceToServer(item: SyncQueueItem) {
        if (item.operation == SyncOperation.DELETE.value) {
            item.payload?.toIntOrNull()?.let { serverId ->
                apiClient.deleteIncomeSource(serverId)
            }
            return
        }

        val source = incomeSourceDao.getById(item.entityId) ?: return

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val remote = apiClient.createIncomeSource(source.name)
                incomeSourceDao.updateSyncStatus(
                    id = source.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = remote.id.toString(),
                )
            }
            SyncOperation.UPDATE.value -> {
                source.serverId?.toIntOrNull()?.let { serverId ->
                    apiClient.updateIncomeSource(
                        id = serverId,
                        request =
                            UpdateIncomeSourceRequest(
                                name = source.name,
                                isActive = if (source.isActive == 1L) 1 else 0,
                            ),
                    )
                    incomeSourceDao.updateSyncStatus(
                        id = source.id,
                        syncStatus = SyncStatus.SYNCED.value,
                        serverId = serverId.toString(),
                    )
                }
            }
        }
    }

    /**
     * Синхронизация дохода на сервер
     */
    private suspend fun syncIncomeToServer(item: SyncQueueItem) {
        if (item.operation == SyncOperation.DELETE.value) {
            item.payload?.toIntOrNull()?.let { serverId ->
                apiClient.deleteIncome(serverId)
            }
            return
        }

        val income = incomeDao.getById(item.entityId)
            ?: throw IllegalStateException("Income not found for entityId=${item.entityId}")

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
                    )
                incomeDao.updateSyncStatus(
                    id = income.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = remote.id.toString(),
                )
            }
            SyncOperation.UPDATE.value -> {
                val serverId =
                    income.serverId?.toIntOrNull()
                        ?: throw IllegalStateException("Cannot sync income update: serverId is null for entityId=${item.entityId}")

                apiClient.updateIncome(serverId, income.amount.toDouble())
                incomeDao.updateSyncStatus(
                    id = income.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = serverId.toString(),
                )
            }
        }
    }

    /**
     * Синхронизация расхода на сервер
     */
    private suspend fun syncExpenseToServer(item: SyncQueueItem) {
        if (item.operation == SyncOperation.DELETE.value) {
            item.payload?.toIntOrNull()?.let { serverId ->
                apiClient.deleteExpense(serverId)
            }
            return
        }

        val expense = expenseDao.getById(item.entityId)
            ?: throw IllegalStateException("Expense not found for entityId=${item.entityId}")

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
                    )
                expenseDao.updateSyncStatus(
                    id = expense.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = remote.id.toString(),
                )
            }
            SyncOperation.UPDATE.value -> {
                val serverId =
                    expense.serverId?.toIntOrNull()
                        ?: throw IllegalStateException("Cannot sync expense update: serverId is null for entityId=${item.entityId}")

                apiClient.updateExpense(serverId, expense.amount.toDouble())
                expenseDao.updateSyncStatus(
                    id = expense.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = serverId.toString(),
                )
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
                val remote = apiClient.createCategory(category.name)
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
                val remote = apiClient.createIncomeSource(source.name)
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
        if (item.operation == SyncOperation.DELETE.value) {
            // В API нет метода удаления бюджета
            // item.payload?.toIntOrNull()?.let { serverId -> apiClient.deleteBudget(serverId) }
            return
        }

        val budget = budgetDao.getById(item.entityId) ?: return

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val month = monthDao.getById(budget.monthId)
                val category = categoryDao.getById(budget.categoryId)

                if (month?.serverId == null || category?.serverId == null) {
                    throw IllegalStateException(
                        "Cannot sync budget: month serverId=${month?.serverId}, category serverId=${category?.serverId}"
                    )
                }

                apiClient.setBudget(
                    SetBudgetRequest(
                        monthId = month.serverId.toInt(),
                        categoryId = category.serverId.toInt(),
                        limitAmount = budget.limitAmount.toDouble(),
                    ),
                )
                budgetDao.updateSyncStatus(
                    id = budget.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = null,
                )
            }
            SyncOperation.UPDATE.value -> {
                val month = monthDao.getById(budget.monthId)
                val category = categoryDao.getById(budget.categoryId)

                if (month?.serverId == null || category?.serverId == null) {
                    throw IllegalStateException(
                        "Cannot sync budget update: month serverId=${month?.serverId}, category serverId=${category?.serverId}"
                    )
                }

                budget.serverId?.toIntOrNull()?.let { serverId ->
                    apiClient.updateBudget(
                        id = serverId,
                        request =
                            SetBudgetRequest(
                                monthId = month.serverId.toInt(),
                                categoryId = category.serverId.toInt(),
                                limitAmount = budget.limitAmount.toDouble(),
                            ),
                    )
                    budgetDao.updateSyncStatus(
                        id = budget.id,
                        syncStatus = SyncStatus.SYNCED.value,
                        serverId = serverId.toString(),
                    )
                }
            }
        }
    }

    /**
     * Синхронизация цели накоплений на сервер
     */
    private suspend fun syncSavingsGoalToServer(item: SyncQueueItem) {
        if (item.operation == SyncOperation.DELETE.value) {
            item.payload?.toIntOrNull()?.let { serverId ->
                apiClient.deleteSavingsGoal(serverId)
            }
            return
        }

        val goal = savingsGoalDao.getById(item.entityId)
            ?: throw IllegalStateException("Savings goal not found for entityId=${item.entityId}")

        when (item.operation) {
            SyncOperation.INSERT.value -> {
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
            }
            SyncOperation.UPDATE.value -> {
                val serverId =
                    goal.serverId?.toIntOrNull()
                        ?: throw IllegalStateException("Cannot sync savings goal update: serverId is null for entityId=${item.entityId}")

                println("[SYNC] syncSavingsGoalToServer: UPDATE serverId=$serverId, name=${goal.name}, targetAmount=${goal.targetAmount}, currentAmount=${goal.currentAmount}")
                apiClient.updateSavingsGoal(
                    id = serverId,
                    request =
                        UpdateSavingsGoalRequest(
                            name = goal.name,
                            targetAmount = goal.targetAmount.toDouble(),
                            currentAmount = goal.currentAmount.toDouble(),
                        ),
                )
                println("[SYNC] syncSavingsGoalToServer: UPDATE OK")
                savingsGoalDao.updateSyncStatus(
                    id = goal.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = serverId.toString(),
                )
            }
        }
    }

    /**
     * Синхронизация транзакции накоплений на сервер
     */
    private suspend fun syncSavingsTransactionToServer(item: SyncQueueItem) {
        if (item.operation == SyncOperation.DELETE.value) {
            item.payload?.toIntOrNull()?.let { serverId ->
                apiClient.deleteSavingsTransaction(serverId)
            }
            return
        }

        val transaction = savingsTransactionDao.getById(item.entityId)
            ?: throw IllegalStateException("Savings transaction not found for entityId=${item.entityId}")

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val goalServerId = resolveSavingsGoalServerId(transaction.savingsGoalId)
                    ?: throw IllegalStateException("Cannot sync savings transaction: goal serverId is null for goalId=${transaction.savingsGoalId}")

                println("[SYNC] syncSavingsTransactionToServer: INSERT goalServerId=$goalServerId, amount=${transaction.amount}")
                val serverTransaction = apiClient.addSavingsTransaction(
                    AddSavingsTransactionRequest(
                        goalId = goalServerId.toInt(),
                        amount = transaction.amount.toDouble(),
                        date = transaction.date,
                    ),
                )
                println("[SYNC] syncSavingsTransactionToServer: INSERT OK, serverTransaction.id=${serverTransaction.id}")
                savingsTransactionDao.updateSyncStatus(
                    id = transaction.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = serverTransaction.id.toString(),
                )
            }
            SyncOperation.UPDATE.value -> {
                val goalServerId = resolveSavingsGoalServerId(transaction.savingsGoalId)
                    ?: throw IllegalStateException("Cannot sync savings transaction update: goal serverId is null for goalId=${transaction.savingsGoalId}")
                val serverId =
                    transaction.serverId?.toIntOrNull()
                        ?: throw IllegalStateException("Cannot sync savings transaction update: transaction serverId is null for entityId=${item.entityId}")

                apiClient.updateSavingsTransaction(
                    id = serverId,
                    request =
                        AddSavingsTransactionRequest(
                            goalId = goalServerId.toInt(),
                            amount = transaction.amount.toDouble(),
                            date = transaction.date,
                        ),
                )
                savingsTransactionDao.updateSyncStatus(
                    id = transaction.id,
                    syncStatus = SyncStatus.SYNCED.value,
                    serverId = serverId.toString(),
                )
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
                // Повторяем ранее неудачные операции перед новой отправкой
                syncQueueDao.retryFailed()
                val pendingItems = syncQueueDao.getPendingItems(limit = 50)
                println("[SYNC] scheduleProcessQueue: ${pendingItems.size} pending items")
                for (item in pendingItems) {
                    println("[SYNC] processing item: id=${item.id}, type=${item.entityType}, entityId=${item.entityId}, op=${item.operation}, status=${item.status}")
                    syncItemToServer(item)
                }
                syncQueueDao.clearCompleted()
                updatePendingCount()
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
     * Возвращает набор serverId из pending DELETE операций для указанного типа сущности.
     * Используется в syncWithServer, чтобы не восстанавливать удалённые записи.
     */
    fun getPendingDeleteServerIds(entityType: String): Set<String> {
        val pendingItems = syncQueueDao.getPendingItems(limit = 1000)
        return pendingItems
            .filter { it.entityType == entityType && it.operation == SyncOperation.DELETE.value && it.payload != null }
            .mapNotNull { it.payload }
            .toSet()
    }

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

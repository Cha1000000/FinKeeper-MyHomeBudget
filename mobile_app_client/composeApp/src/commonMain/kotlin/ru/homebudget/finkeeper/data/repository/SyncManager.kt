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

    private val _dataUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dataUpdated: SharedFlow<Unit> = _dataUpdated.asSharedFlow()

    init {
        updatePendingCount()
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
                // Дедупликация: удаляем предыдущие pending-записи для этой сущности
                syncQueueDao.deleteByTypeAndId(entityType, entityId)

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
        if (_isSyncing.value) return

        scope.launch {
            _isSyncing.value = true
            try {
                // Этап 1: Забираем данные с сервера (Download)
                // Это важно для случая, когда данные обновлены на другом устройстве
                syncFromServer(monthId)

                // Этап 2: Отправляем локальные изменения (Upload)
                val pendingItems = syncQueueDao.getPendingItems(limit = 50)
                for (item in pendingItems) {
                    syncItemToServer(item)
                }

                // Очистка завершённых элементов
                syncQueueDao.clearCompleted()
                updatePendingCount()

                // Уведомляем подписчиков об обновлении данных
                _dataUpdated.tryEmit(Unit)
            } catch (e: Exception) {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Отправляет локальные изменения на сервер
     */
    private suspend fun syncItemToServer(item: SyncQueueItem) {
        try {
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
            }

            syncQueueDao.updateStatus(
                id = item.id,
                status = SyncQueueStatus.COMPLETED.value,
                errorMessage = null,
            )
        } catch (e: Exception) {
            syncQueueDao.updateStatus(
                id = item.id,
                status = SyncQueueStatus.FAILED.value,
                errorMessage = e.message,
            )
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

        val income = incomeDao.getById(item.entityId) ?: return

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val month = monthDao.getById(income.monthId)
                val source = incomeSourceDao.getById(income.incomeSourceId)

                if (month?.serverId != null && source?.serverId != null) {
                    val remote =
                        apiClient.addIncome(
                            AddIncomeRequest(
                                monthId = month.serverId.toInt(),
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
            }
            SyncOperation.UPDATE.value -> {
                income.serverId?.toIntOrNull()?.let { serverId ->
                    apiClient.updateIncome(serverId, income.amount.toDouble())
                    incomeDao.updateSyncStatus(
                        id = income.id,
                        syncStatus = SyncStatus.SYNCED.value,
                        serverId = serverId.toString(),
                    )
                }
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

        val expense = expenseDao.getById(item.entityId) ?: return

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val month = monthDao.getById(expense.monthId)
                val category = categoryDao.getById(expense.categoryId)

                if (month?.serverId != null && category?.serverId != null) {
                    val remote =
                        apiClient.addExpense(
                            AddExpenseRequest(
                                monthId = month.serverId.toInt(),
                                categoryId = category.serverId.toInt(),
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
            }
            SyncOperation.UPDATE.value -> {
                expense.serverId?.toIntOrNull()?.let { serverId ->
                    apiClient.updateExpense(serverId, expense.amount.toDouble())
                    expenseDao.updateSyncStatus(
                        id = expense.id,
                        syncStatus = SyncStatus.SYNCED.value,
                        serverId = serverId.toString(),
                    )
                }
            }
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

                if (month?.serverId != null && category?.serverId != null) {
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
            }
            SyncOperation.UPDATE.value -> {
                budget.serverId?.toIntOrNull()?.let { serverId ->
                    val month = monthDao.getById(budget.monthId)
                    val category = categoryDao.getById(budget.categoryId)
                    if (month?.serverId != null && category?.serverId != null) {
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

        val goal = savingsGoalDao.getById(item.entityId) ?: return

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
                goal.serverId?.toIntOrNull()?.let { serverId ->
                    apiClient.updateSavingsGoal(
                        id = serverId,
                        request =
                            UpdateSavingsGoalRequest(
                                name = goal.name,
                                targetAmount = goal.targetAmount.toDouble(),
                                currentAmount = goal.currentAmount.toDouble(),
                            ),
                    )
                    savingsGoalDao.updateSyncStatus(
                        id = goal.id,
                        syncStatus = SyncStatus.SYNCED.value,
                        serverId = serverId.toString(),
                    )
                }
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

        val transaction = savingsTransactionDao.getById(item.entityId) ?: return

        when (item.operation) {
            SyncOperation.INSERT.value -> {
                val goal = savingsGoalDao.getById(transaction.savingsGoalId)
                if (goal?.serverId != null) {
                    apiClient.addSavingsTransaction(
                        AddSavingsTransactionRequest(
                            goalId = goal.serverId.toInt(),
                            amount = transaction.amount.toDouble(),
                            date = transaction.date,
                        ),
                    )
                    savingsTransactionDao.updateSyncStatus(
                        id = transaction.id,
                        syncStatus = SyncStatus.SYNCED.value,
                        serverId = null,
                    )
                }
            }
            SyncOperation.UPDATE.value -> {
                val goal = savingsGoalDao.getById(transaction.savingsGoalId)
                if (goal?.serverId != null) {
                    transaction.serverId?.let { serverId ->
                        apiClient.updateSavingsTransaction(
                            id = serverId.toInt(),
                            request =
                                AddSavingsTransactionRequest(
                                    goalId = goal.serverId.toInt(),
                                    amount = transaction.amount.toDouble(),
                                    date = transaction.date,
                                ),
                        )
                        savingsTransactionDao.updateSyncStatus(
                            id = transaction.id,
                            syncStatus = SyncStatus.SYNCED.value,
                            serverId = serverId,
                        )
                    }
                }
            }
        }
    }

    /**
     * Отправляет pending операции на сервер (только upload, без download).
     * Вызывается автоматически после enqueueSync для немедленной отправки изменений.
     * Использует отдельную корутину с задержкой, чтобы не конфликтовать с syncAll().
     */
    private fun scheduleProcessQueue() {
        scope.launch {
            // Ждём, пока syncAll() завершится (если запущен)
            var attempts = 0
            while (_isSyncing.value && attempts < 10) {
                kotlinx.coroutines.delay(500)
                attempts++
            }

            if (_isSyncing.value) return@launch

            _isSyncing.value = true
            try {
                val pendingItems = syncQueueDao.getPendingItems(limit = 50)
                for (item in pendingItems) {
                    syncItemToServer(item)
                }
                syncQueueDao.clearCompleted()
                updatePendingCount()
                if (pendingItems.isNotEmpty()) {
                    _dataUpdated.tryEmit(Unit)
                }
            } catch (e: Exception) {
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

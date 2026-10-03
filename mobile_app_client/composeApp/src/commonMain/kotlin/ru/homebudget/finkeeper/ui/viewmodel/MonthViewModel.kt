package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.repository.SAVINGS_EXPENSE_CATEGORY_NAME
import ru.homebudget.finkeeper.data.repository.budget.BudgetRepository
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.expense.ExpenseRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeRepository
import ru.homebudget.finkeeper.data.network.ServerLinkState
import ru.homebudget.finkeeper.data.network.isConnectivityFailure
import ru.homebudget.finkeeper.data.network.runServerPhase
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.income.IncomeSourceRepository
import ru.homebudget.finkeeper.data.repository.month.MonthData
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
import ru.homebudget.finkeeper.data.repository.planned.PlannedRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsTransactionRepository
import ru.homebudget.finkeeper.data.planned.toAutoExpense
import ru.homebudget.finkeeper.data.planned.toAutoIncome
import ru.homebudget.finkeeper.data.planned.withoutDownloadedRecords
import ru.homebudget.finkeeper.data.planned.PlannedItem
import ru.homebudget.finkeeper.data.planned.PlannedResult
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.util.currentIsoDate

data class GroupedExpense(
    val categoryId: Int,
    val categoryName: String,
    val items: List<Expense>,
    val total: Double,
    val limit: Double,
    val isOverLimit: Boolean,
)

/** Плановый платёж для UI: суммы в рублях */
data class PlannedUiItem(
    val templateType: String,
    val templateId: Long,
    val name: String,
    val amount: Double,
    val originalAmount: Double,
    val dueDay: Int,
    val requireConfirm: Boolean,
    val isSkipped: Boolean,
    val isOverridden: Boolean,
    val isOverdue: Boolean,
)

private fun PlannedItem.toUi() = PlannedUiItem(
    templateType = templateType,
    templateId = templateId,
    name = name,
    amount = amountCents / 100.0,
    originalAmount = originalAmountCents / 100.0,
    dueDay = dueDay,
    requireConfirm = requireConfirm,
    isSkipped = isSkipped,
    isOverridden = isOverridden,
    isOverdue = isOverdue,
)

data class IncomeWithSource(
    val id: Int,
    val sourceName: String,
    val amount: Double,
    val date: String,
)

data class MonthViewState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val year: Int = 0,
    val month: Int = 0,
    val monthData: MonthData? = null,
    val incomes: List<Income> = emptyList(),
    val incomesWithSources: List<IncomeWithSource> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val groupedExpenses: List<GroupedExpense> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalAllExpenses: Double = 0.0,
    val totalLimit: Double = 0.0,
    val plannedExpenses: List<PlannedUiItem> = emptyList(),
    val plannedIncomes: List<PlannedUiItem> = emptyList(),
    val plannedExpensesTotal: Double = 0.0,
    val plannedIncomesTotal: Double = 0.0,
    // id записей, «отмеченных автоматически» (ещё не пришли с сервера): у них нет строки в БД,
    // поэтому экран вместо изменения/удаления показывает пояснение
    val autoAppliedExpenseIds: Set<Int> = emptySet(),
    val autoAppliedIncomeIds: Set<Int> = emptySet(),
    val activeTab: Int = 0, // 0 = expenses, 1 = incomes
    /** Итог последней загрузки: его заменяет следующая загрузка. */
    val loadError: String? = null,
    /** Ошибка действия пользователя: живёт до «Скрыть» или следующего действия. */
    val actionError: String? = null,
    val isOffline: Boolean = false,
    /** Идёт серверная фаза загрузки (для индикатора на кнопке «Повторить»). */
    val isSyncing: Boolean = false,
    // Pending source confirmation state
    val pendingSourceName: String? = null,
    val pendingSourceAmount: Double? = null,
    val showSourceConfirm: Boolean = false,
) {
    /** Текст баннера ошибки экрана: ошибка действия важнее итога загрузки. */
    val error: String? get() = actionError ?: loadError
}

class MonthViewModel(
    private val monthRepository: MonthRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val incomeSourceRepository: IncomeSourceRepository,
    private val plannedRepository: PlannedRepository,
    private val tokenStorage: TokenStorage,
    private val syncManager: SyncManager,
    private val savingsTransactionRepository: SavingsTransactionRepository,
    private val serverLinkState: ServerLinkState,
) : ViewModel() {
    private val currentUserId: Long get() = tokenStorage.userId
    private val _state = MutableStateFlow(MonthViewState())
    val state: StateFlow<MonthViewState> = _state.asStateFlow()

    init {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        // Restore saved month or use current
        val savedYear = tokenStorage.monthViewYear
        val savedMonth = tokenStorage.monthViewMonth
        val year = if (savedYear > 0 && savedMonth > 0) savedYear else now.year
        val month = if (savedYear > 0 && savedMonth > 0) savedMonth else now.monthNumber
        _state.value = _state.value.copy(year = year, month = month)
        loadData()
        observeSyncUpdates()
    }

    private fun observeSyncUpdates() {
        viewModelScope.launch {
            syncManager.dataUpdated.collect {
                loadData(syncFromServer = false, showLoader = false)
            }
        }
    }

    fun refreshData() {
        startServerLoad(refreshing = true)
    }

    fun clearError() {
        _state.value = _state.value.copy(loadError = null, actionError = null)
    }

    fun setActiveTab(tab: Int) {
        _state.value = _state.value.copy(activeTab = tab)
    }

    fun prevMonth() {
        val s = _state.value
        var y = s.year
        var m = s.month - 1
        if (m < 1) {
            m = 12
            y--
        }
        _state.value = s.copy(year = y, month = m)
        saveSelectedMonth(y, m)
        loadData()
    }

    fun nextMonth() {
        val s = _state.value
        var y = s.year
        var m = s.month + 1
        if (m > 12) {
            m = 1
            y++
        }
        _state.value = s.copy(year = y, month = m)
        saveSelectedMonth(y, m)
        loadData()
    }

    private fun saveSelectedMonth(year: Int, month: Int) {
        tokenStorage.monthViewYear = year
        tokenStorage.monthViewMonth = month
    }

    private var loadedMonthKey: Int? = null
    private var serverLoadJob: Job? = null
    private var localLoadJob: Job? = null

    /**
     * [syncFromServer] = `false` — только перечитать Room (после локальных правок и синка в фоне):
     * так не перезаписываются ещё не отправленные изменения, а идущая серверная загрузка не
     * прерывается.
     */
    fun loadData(
        syncFromServer: Boolean = true,
        showLoader: Boolean = true,
    ) {
        val s = _state.value
        // Полноэкранный лоадер — только если для этого месяца ещё нечего показать
        val needLoader = showLoader && loadedMonthKey != s.year * 100 + s.month
        if (needLoader && !s.isLoading) _state.value = s.copy(isLoading = true)
        if (syncFromServer) startServerLoad(refreshing = false) else startLocalLoad()
    }

    // Новая серверная загрузка отменяет предыдущую: иначе устаревшая (другой месяц, долгая сеть)
    // перезаписала бы более свежий результат
    private fun startServerLoad(refreshing: Boolean) {
        val previous = serverLoadJob
        serverLoadJob =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                if (refreshing) _state.value = _state.value.copy(isRefreshing = true)
                try {
                    loadFromServer()
                } finally {
                    if (refreshing) _state.value = _state.value.copy(isRefreshing = false)
                }
            }
    }

    private fun startLocalLoad() {
        val previous = localLoadJob
        localLoadJob =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                try {
                    val s = _state.value
                    publishLocalData(localMonth(s.year, s.month), s.year, s.month)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportLoadFailure(e)
                }
            }
    }

    private suspend fun localMonth(
        year: Int,
        month: Int,
    ): MonthData = monthRepository.getOrCreateMonth(currentUserId, year, month, registerOnServer = false).getOrThrow()

    /**
     * Local-first: сначала показываем данные из локальной БД, затем подтягиваем сервер в рамках
     * общего бюджета времени и перечитываем Room. Без связи экран остаётся на локальных данных
     * с isOffline, а не висит на лоадере.
     */
    private suspend fun loadFromServer() {
        val s = _state.value
        val year = s.year
        val month = s.month
        try {
            var monthData = localMonth(year, month)
            publishLocalData(monthData, year, month)

            _state.value = _state.value.copy(isSyncing = true)
            val phase =
                try {
                    runServerPhase(serverLinkState) {
                        stepResult { categoryRepository.syncWithServer(currentUserId) }
                        stepResult { incomeSourceRepository.syncWithServer(currentUserId) }
                        // До скачивания: сервер создаст регулярные записи, у которых наступил день
                        step { monthRepository.ensureOnServerChecked(monthData.localId) }
                        monthData = localMonth(year, month)
                        if (monthData.serverId != null) {
                            stepResult { incomeRepository.syncWithServer(currentUserId, monthData.localId) }
                            stepResult { expenseRepository.syncWithServer(currentUserId, monthData.localId) }
                            stepResult { budgetRepository.syncWithServer(currentUserId, monthData.localId) }
                            // Старый сервер без planned-state не должен ломать загрузку месяца
                            optionalStep { plannedRepository.syncWithServer(currentUserId, monthData.localId) }
                        }
                    }
                } finally {
                    _state.value = _state.value.copy(isSyncing = false)
                }

            // Пока шла серверная фаза, пользователь мог переключить месяц — не затираем его данные
            val current = _state.value
            if (current.year != year || current.month != month) return
            publishLocalData(monthData, year, month)
            _state.value =
                _state.value.copy(
                    isOffline = !phase.reachable,
                    loadError = phase.serverError?.let { Strings.SERVER_REFRESH_FAILED },
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportLoadFailure(e)
        }
    }

    private fun reportLoadFailure(e: Exception) {
        println("[LOAD] month load failed: ${e::class.simpleName}: ${e.message}")
        _state.value =
            _state.value.copy(
                isLoading = false,
                // Подробности — в лог: текст исключения Room пользователю ничего не скажет
                loadError = Strings.LOADING_ERROR,
                isOffline = e.isConnectivityFailure() || _state.value.isOffline,
            )
    }

    private suspend fun publishLocalData(
        monthData: MonthData,
        year: Int,
        month: Int,
    ) {
        // Читаем актуальные данные из локальной БД. Сбой чтения — ошибка экрана, а не пустой месяц
        var incomes: List<Income> = incomeRepository.getIncomesByMonth(monthData.localId).getOrThrow()
        var allExpenses: List<Expense> = expenseRepository.getExpensesByMonth(monthData.localId).getOrThrow()
        val categories: List<Category> = categoryRepository.getAllCategories(currentUserId).getOrThrow()
        val budgets: List<Budget> = budgetRepository.getBudgetsByMonth(monthData.localId).getOrThrow()
        val incomeSources: List<IncomeSource> = incomeSourceRepository.getAllIncomeSources(currentUserId).getOrThrow()

        // Виртуальный план-слой: вычисляется локально, работает оффлайн
        val planned: PlannedResult = plannedRepository.getPlanned(monthData.localId)
            .withoutDownloadedRecords(allExpenses, incomes)

        // Регулярные записи, у которых наступил день, а с сервера они ещё не пришли —
        // учитываем как факт (тем же видом, что создаст сервер), чтобы суммы не скакали
        val autoExpenses = planned.autoAppliedExpenses.map { it.toAutoExpense(monthData.localId) }
        val autoIncomes = planned.autoAppliedIncomes.map { it.toAutoIncome(monthData.localId) }
        allExpenses = allExpenses + autoExpenses
        incomes = incomes + autoIncomes

        // Фильтруем расходы - исключаем категорию "Пополнение копилки"
        val piggyBankCategoryId = categories.find { it.name == SAVINGS_EXPENSE_CATEGORY_NAME }?.id
        val visibleExpenses =
            if (piggyBankCategoryId != null) {
                allExpenses.filter { it.categoryId != piggyBankCategoryId }
            } else {
                allExpenses
            }

        // Присоединяем названия источников к доходам
        val incomesWithSources =
            incomes.map { income ->
                val sourceId = income.source.toLongOrNull()
                val matchedSource = if (sourceId != null && sourceId != 0L) {
                    incomeSources.find { it.id.toLong() == sourceId }
                } else null
                val sourceName = matchedSource?.name
                    ?: income.description
                    ?: income.source
                IncomeWithSource(
                    id = income.id,
                    sourceName = sourceName,
                    amount = income.amount,
                    date = income.date,
                )
            }

        val totalIncome = incomes.sumOf { it.amount }
        val totalExpense = visibleExpenses.sumOf { it.amount }
        // + ещё не выгруженные пополнения копилок: их скрытый расход создаст сервер позже,
        // а остаток лимита должен уменьшиться сразу, как и онлайн
        val totalAllExpenses = allExpenses.sumOf { it.amount } +
            savingsTransactionRepository.getUnsyncedDepositsTotal(currentUserId, monthData.localId)
        val totalLimit = budgets.sumOf { it.limitAmount }

        val grouped = buildGroupedExpenses(visibleExpenses, categories, budgets)

        // Пока читали Room, пользователь мог переключить месяц — чужие данные не показываем
        val current = _state.value
        if (current.year != year || current.month != month) return
        _state.value =
            _state.value.copy(
                isLoading = false,
                // Room прочитан — прежний сбой загрузки больше не актуален
                loadError = current.loadError.withoutLocalLoadError(),
                monthData = monthData,
                incomes = incomes,
                incomesWithSources = incomesWithSources,
                expenses = visibleExpenses,
                categories = categories,
                incomeSources = incomeSources,
                budgets = budgets,
                groupedExpenses = grouped,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                totalAllExpenses = totalAllExpenses,
                totalLimit = totalLimit,
                plannedExpenses = planned.expenses.map { it.toUi() },
                plannedIncomes = planned.incomes.map { it.toUi() },
                plannedExpensesTotal = planned.plannedExpensesCents / 100.0,
                plannedIncomesTotal = planned.plannedIncomesCents / 100.0,
                autoAppliedExpenseIds = autoExpenses.map { it.id }.toSet(),
                autoAppliedIncomeIds = autoIncomes.map { it.id }.toSet(),
            )
        loadedMonthKey = year * 100 + month
    }

    fun addIncome(
        source: String,
        amount: Double,
    ) {
        val md = _state.value.monthData
        if (md == null) {
            println("[MONTH-VM] addIncome: SKIPPED, monthData is null")
            return
        }
        println("[MONTH-VM] addIncome: source=$source, amount=$amount, monthId=${md.localId}, userId=$currentUserId")
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                val sourceId =
                    _state.value.incomeSources
                        .find { it.name.equals(source, ignoreCase = true) }
                        ?.id
                        ?.toLong() ?: 0L
                println("[MONTH-VM] addIncome: sourceId=$sourceId")

                // Имя источника задано, но в списке его нет — создаём источник,
                // чтобы не получить доход с income_source_id=0 (он вечно падает в синке)
                if (sourceId == 0L && source.isNotBlank()) {
                    createSourceAndIncome(md.localId, source, amount)
                    return@launch
                }

                incomeRepository.createIncome(
                    userId = currentUserId,
                    monthId = md.localId,
                    incomeSourceId = sourceId,
                    amount = amount,
                    date = currentIsoDate(),
                ).getOrThrow()
                println("[MONTH-VM] addIncome: createIncome completed")

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError(Strings.ERROR_ADDING_INCOME))
            }
        }
    }

    /**
     * Последовательно создаёт источник дохода и доход с ним. id нового источника
     * берётся прямо из результата создания, а не из перечитанного state — иначе
     * доход создаётся с income_source_id=0 (гонка двух параллельных корутин).
     */
    private suspend fun createSourceAndIncome(monthLocalId: Long, source: String, amount: Double) {
        val newSourceId =
            incomeSourceRepository.createIncomeSource(
                userId = currentUserId,
                name = source,
            ).getOrThrow().id.toLong()
        incomeRepository.createIncome(
            userId = currentUserId,
            monthId = monthLocalId,
            incomeSourceId = newSourceId,
            amount = amount,
            date = currentIsoDate(),
        ).getOrThrow()
        loadData(syncFromServer = false, showLoader = false)
    }

    fun addIncomeWithSourceCheck(
        source: String,
        amount: Double,
    ) {
        if (_state.value.monthData == null) return
        val incomeSources = _state.value.incomeSources

        // Check if source exists
        val sourceExists = incomeSources.any { it.name.equals(source, ignoreCase = true) }

        if (!sourceExists && source.isNotBlank()) {
            // Source doesn't exist - show confirmation dialog
            _state.value =
                _state.value.copy(
                    pendingSourceName = source,
                    pendingSourceAmount = amount,
                    showSourceConfirm = true,
                )
        } else {
            // Source exists or empty - add income directly
            addIncome(source, amount)
        }
    }

    fun confirmAddIncomeSource() {
        val source = _state.value.pendingSourceName ?: return
        val amount = _state.value.pendingSourceAmount ?: return
        val md = _state.value.monthData ?: return

        // Очищаем pending-состояние сразу (диалог закрывается)
        _state.value =
            _state.value.copy(
                pendingSourceName = null,
                pendingSourceAmount = null,
                showSourceConfirm = false,
            )

        // Создаём источник и доход последовательно (см. createSourceAndIncome)
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                createSourceAndIncome(md.localId, source, amount)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError(Strings.ERROR_ADDING_INCOME))
            }
        }
    }

    fun cancelAddIncomeSource() {
        _state.value =
            _state.value.copy(
                pendingSourceName = null,
                pendingSourceAmount = null,
                showSourceConfirm = false,
            )
    }

    fun addExpense(
        categoryId: Int,
        amount: Double,
        comment: String?,
    ) {
        val md = _state.value.monthData
        if (md == null) {
            println("[MONTH-VM] addExpense: SKIPPED, monthData is null")
            return
        }
        println("[MONTH-VM] addExpense: categoryId=$categoryId, amount=$amount, monthId=${md.localId}, userId=$currentUserId")
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                expenseRepository.createExpense(
                    userId = currentUserId,
                    monthId = md.localId,
                    categoryId = categoryId.toLong(),
                    amount = amount,
                    description = comment,
                    date = currentIsoDate(),
                ).getOrThrow()
                println("[MONTH-VM] addExpense: createExpense completed")

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    fun updateIncome(
        id: Int,
        amount: Double,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                // Обновляем локально через репозиторий (у доходов правится только сумма)
                incomeRepository.updateIncome(
                    id = id.toLong(),
                    amount = amount,
                ).getOrThrow()

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    fun updateExpense(
        id: Int,
        amount: Double,
        description: String? = null,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                // Обновляем локально через репозиторий (расход: сумма + комментарий)
                expenseRepository.updateExpense(
                    id = id.toLong(),
                    amount = amount,
                    description = description,
                    updateDescription = true,
                ).getOrThrow()

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    fun deleteIncome(id: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                // Удаляем локально через репозиторий
                incomeRepository.deleteIncome(id.toLong()).getOrThrow()

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                // Удаляем локально через репозиторий
                expenseRepository.deleteExpense(id.toLong()).getOrThrow()

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    fun setBudget(
        categoryId: Int,
        limit: Double,
    ) {
        val md = _state.value.monthData ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                budgetRepository.setBudget(
                    userId = currentUserId,
                    monthId = md.localId,
                    categoryId = categoryId.toLong(),
                    limitAmount = limit,
                ).getOrThrow()

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    fun addIncomeSource(name: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                // Сначала сохраняем локально через репозиторий
                incomeSourceRepository.createIncomeSource(
                    userId = currentUserId,
                    name = name,
                ).getOrThrow()

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError(Strings.ERROR_ADDING_SOURCE))
            }
        }
    }

    fun reorderExpenseGroups(newOrder: List<GroupedExpense>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                _state.value = _state.value.copy(groupedExpenses = newOrder)
                // Переставлены только категории с расходами в месяце — остальные остаются на местах
                categoryRepository.reorderCategories(currentUserId, newOrder.map { it.categoryId.toLong() }).getOrThrow()
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError(Strings.ERROR_REORDERING))
            }
        }
    }

    private fun buildGroupedExpenses(
        expenses: List<Expense>,
        categories: List<Category>,
        budgets: List<Budget>,
    ): List<GroupedExpense> {
        val expenseMap = expenses.groupBy { it.categoryId }
        val categoryOrder = categories.mapIndexed { index, cat -> cat.id to index }.toMap()

        return expenseMap
            .map { (catId, items) ->
                // Ищем название категории из списка категорий по categoryId
                val catName = categories.find { cat -> cat.id == catId }?.name ?: "Без категории"
                val total = items.sumOf { it.amount }
                val budget = budgets.find { it.categoryId == catId }
                val limit = budget?.limitAmount ?: 0.0
                GroupedExpense(
                    categoryId = catId.toInt(),
                    categoryName = catName,
                    items = items.sortedByDescending { it.date },
                    total = total,
                    limit = limit,
                    isOverLimit = limit > 0 && total > limit,
                )
            }.sortedBy { categoryOrder[it.categoryId] ?: Int.MAX_VALUE }
    }

    // ── Действия план-слоя ──

    /** «Оплачено/Получено»: только при сети — сервер создаёт реальную запись */
    fun confirmPlanned(item: PlannedUiItem, amount: Double? = null) {
        val md = _state.value.monthData ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            val result = plannedRepository.confirm(
                monthId = md.localId,
                templateType = item.templateType,
                templateId = item.templateId,
                amountCents = amount?.let { (it * 100).toLong() },
            )
            if (result.isSuccess) {
                loadData(syncFromServer = true, showLoader = false)
            } else {
                val error = (result as? ru.homebudget.finkeeper.data.repository.Result.Error)?.exception
                _state.value = _state.value.copy(
                    actionError = error?.toActionError(Strings.ERROR_CONFIRMING_PLANNED) ?: Strings.ERROR_CONFIRMING_PLANNED,
                )
            }
        }
    }

    /** Пропустить платёж в этом месяце / вернуть в план (offline-first) */
    fun skipPlanned(item: PlannedUiItem, skipped: Boolean) {
        val md = _state.value.monthData ?: return
        runPlannedAction {
            plannedRepository.setSkipped(md.localId, item.templateType, item.templateId, skipped)
        }
    }

    /** Изменить сумму/день только на этот месяц (offline-first) */
    fun overridePlanned(item: PlannedUiItem, amount: Double, day: Int) {
        val md = _state.value.monthData ?: return
        runPlannedAction {
            plannedRepository.setOverride(
                monthId = md.localId,
                templateType = item.templateType,
                templateId = item.templateId,
                amountCents = if (amount == item.originalAmount) null else (amount * 100).toLong(),
                day = day,
            )
        }
    }

    /** Сбросить изменения месяца к шаблону (offline-first) */
    fun resetPlanned(item: PlannedUiItem) {
        val md = _state.value.monthData ?: return
        runPlannedAction {
            plannedRepository.resetOverride(md.localId, item.templateType, item.templateId)
        }
    }

    private fun runPlannedAction(action: suspend () -> ru.homebudget.finkeeper.data.repository.Result<Unit>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                action().getOrThrow()
                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }
}

package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.budget.BudgetRepository
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.expense.ExpenseRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeRepository
import ru.homebudget.finkeeper.data.repository.month.MonthData
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
import ru.homebudget.finkeeper.data.repository.planned.PlannedRepository
import ru.homebudget.finkeeper.data.repository.onSuccess
import ru.homebudget.finkeeper.data.repository.savings.SavingsGoalRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsTransactionRepository
import ru.homebudget.finkeeper.data.planned.toAutoExpense
import ru.homebudget.finkeeper.data.planned.withoutDownloadedRecords
import ru.homebudget.finkeeper.util.RetryConfig
import ru.homebudget.finkeeper.util.withRetry

data class ExpenseCategoryBreakdown(
    val name: String,
    val amount: Double,
    val percentage: Double,
)

private const val PIGGY_BANK_CATEGORY_NAME = "Пополнение копилки"

data class DashboardState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val year: Int = 0,
    val month: Int = 0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalAllExpenses: Double = 0.0,
    val totalSavings: Double = 0.0,
    val savingsPercent: Double = 0.0,
    val available: Double = 0.0,
    val availableWithoutSavings: Double = 0.0,
    val totalAssets: Double = 0.0,
    val trendData: List<TrendItem> = emptyList(),
    val expenseBreakdown: List<ExpenseCategoryBreakdown> = emptyList(),
    // План-слой: суммы плановых платежей и прогноз месяца
    val plannedExpensesTotal: Double = 0.0,
    val plannedIncomesTotal: Double = 0.0,
    val forecastExpenses: Double = 0.0,
    val forecastFree: Double = 0.0,
    val error: String? = null,
    val isOffline: Boolean = false,
)

class DashboardViewModel(
    private val monthRepository: MonthRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val plannedRepository: PlannedRepository,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
    private val syncManager: SyncManager,
    private val savingsTransactionRepository: SavingsTransactionRepository,
) : ViewModel() {
    private val currentUserId: Long get() = tokenStorage.userId
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        // Restore saved month or use current
        val savedYear = tokenStorage.dashboardYear
        val savedMonth = tokenStorage.dashboardMonth
        val year = if (savedYear > 0 && savedMonth > 0) savedYear else now.year
        val month = if (savedYear > 0 && savedMonth > 0) savedMonth else now.monthNumber
        _state.value = _state.value.copy(year = year, month = month)
        observeSyncUpdates()
    }

    private fun observeSyncUpdates() {
        viewModelScope.launch {
            syncManager.dataUpdated.collect {
                loadData(showLoader = false)
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            loadDataSuspend()
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    fun loadData(showLoader: Boolean = true) {
        viewModelScope.launch {
            if (showLoader) {
                _state.value = _state.value.copy(isLoading = true, error = null)
            } else {
                _state.value = _state.value.copy(error = null)
            }
            loadDataSuspend()
        }
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
        tokenStorage.dashboardYear = year
        tokenStorage.dashboardMonth = month
    }

    private suspend fun loadDataSuspend() {
        try {
            val s = _state.value
            val year = s.year
            val month = s.month

            // Получаем или создаём месяц через репозиторий
            val monthResult = monthRepository.getOrCreateMonth(currentUserId, year, month)

            val monthData =
                if (monthResult.isSuccess) {
                    monthResult.getOrNull()!!
                } else {
                    throw Exception("Failed to get or create month")
                }

            // Фаза 1: Синхронизация с сервером
            var isOffline = false
            var summary: MonthSummary? = null
            var trend: List<TrendItem> = emptyList()
            var ensured = false

            try {
                categoryRepository.syncWithServer(currentUserId)
                savingsGoalRepository.syncWithServer(currentUserId)
                // До скачивания: сервер создаст регулярные записи, у которых наступил день
                ensured = monthRepository.ensureOnServer(monthData.localId)
                if (monthData.serverId != null) {
                    expenseRepository.syncWithServer(currentUserId, monthData.localId)
                    incomeRepository.syncWithServer(currentUserId, monthData.localId)
                    try {
                        plannedRepository.syncWithServer(currentUserId, monthData.localId)
                    } catch (_: Exception) {
                        // Старый сервер без planned-state не должен ломать загрузку
                    }
                    summary = withRetry(config = RetryConfig(maxAttempts = 2)) {
                        apiClient.getMonthSummary(monthData.serverId)
                    }
                    trend = withRetry(config = RetryConfig(maxAttempts = 2)) {
                        apiClient.getTrend()
                    }
                }
            } catch (e: Exception) {
                isOffline = true
            }

            // Фаза 2: Читаем актуальные данные из локальной БД
            var savingsGoals: List<SavingsGoal> = emptyList()
            savingsGoalRepository
                .getAllSavingsGoals(currentUserId)
                .onSuccess { goals -> savingsGoals = goals }

            var expenses: List<Expense> = emptyList()
            expenseRepository
                .getExpensesByMonth(monthData.localId)
                .onSuccess { expenseList -> expenses = expenseList }

            var incomes: List<Income> = emptyList()
            incomeRepository
                .getIncomesByMonth(monthData.localId)
                .onSuccess { incomeList -> incomes = incomeList }

            var categories: List<Category> = emptyList()
            categoryRepository
                .getAllCategories(currentUserId)
                .onSuccess { cats -> categories = cats }

            var budgets: List<Budget> = emptyList()
            budgetRepository
                .getBudgetsByMonth(monthData.localId)
                .onSuccess { budgetList -> budgets = budgetList }

            val totalSavings = savingsGoals.sumOf { it.currentAmount }

            // План-слой: локальное вычисление (оффлайн)
            val planned = plannedRepository.getPlanned(monthData.localId)
                .withoutDownloadedRecords(expenses, incomes)
            // Регулярные записи, у которых наступил день, а сервер их ещё не создал (или они не
            // пришли): их нет ни в summary, ни в локальных записях — учитываем как факт
            val autoExpenses = planned.autoAppliedExpenses.map { it.toAutoExpense(monthData.localId) }
            // Если ensure прошёл, сервер уже создал все наступившие записи и они есть в summary —
            // прибавлять виртуальные к серверным итогам нельзя (двойной счёт)
            val addAutoToTotals = summary == null || !ensured
            val autoIncomesTotal = if (addAutoToTotals) planned.autoAppliedIncomes.sumOf { it.amountCents } / 100.0 else 0.0
            val autoExpensesTotal = if (addAutoToTotals) autoExpenses.sumOf { it.amount } else 0.0

            // Используем серверные данные если есть, иначе локальные
            val totalIncome = (summary?.income ?: incomes.sumOf { it.amount }) + autoIncomesTotal
            // + ещё не выгруженные пополнения копилок (их нет ни в summary, ни в локальных
            // расходах: скрытый расход создаст сервер после выгрузки)
            val totalAllExpense = (summary?.expenses ?: expenses.sumOf { it.amount }) +
                savingsTransactionRepository.getUnsyncedDepositsTotal(currentUserId, monthData.localId) +
                autoExpensesTotal
            val totalLimit = budgets.sumOf { it.limitAmount }

            // Всего активов = кумулятивный баланс до выбранного месяца включительно
            val cumulativeBalance = try {
                withRetry(config = RetryConfig(maxAttempts = 2)) {
                    apiClient.getCumulativeBalance(year, month)
                }.cumulativeBalance
            } catch (_: Exception) {
                maxOf(0.0, totalLimit - totalAllExpense) // fallback: только текущий месяц если офлайн
            }
            val totalAssets = cumulativeBalance + totalSavings
            val savingsPercent =
                if (totalIncome > 0) {
                    ((summary?.savings ?: 0.0) / totalIncome) * 100
                } else {
                    0.0
                }

            // Фильтруем по имени категории, не по ID
            val piggyBankCategoryId = categories.find { it.name == PIGGY_BANK_CATEGORY_NAME }?.id
            val realVisibleExpenses = if (piggyBankCategoryId != null) {
                expenses.filter { it.categoryId != piggyBankCategoryId }
            } else {
                expenses
            }
            val visibleExpenses = realVisibleExpenses + autoExpenses
            val totalVisibleExpense = visibleExpenses.sumOf { it.amount }
            val available = maxOf(0.0, totalLimit - totalAllExpense)
            val breakdown = buildExpenseBreakdown(visibleExpenses, categories)

            // Прогноз — из видимых цифр: скрытые расходы копилки уменьшают свободное
            // (totalAllExpense), savings отдельно не вычитаем — иначе двойной счёт (как в веб-клиенте)
            val plannedExpensesTotal = planned.plannedExpensesCents / 100.0
            val plannedIncomesTotal = planned.plannedIncomesCents / 100.0
            val forecastExpenses = totalVisibleExpense + plannedExpensesTotal
            val forecastFree = (totalIncome + plannedIncomesTotal) - (totalAllExpense + plannedExpensesTotal)

            _state.value =
                _state.value.copy(
                    isLoading = false,
                    totalIncome = totalIncome,
                    totalExpense = totalVisibleExpense,
                    totalAllExpenses = totalAllExpense,
                    totalSavings = totalSavings,
                    savingsPercent = savingsPercent,
                    available = available,
                    availableWithoutSavings = totalAssets - totalSavings,
                    totalAssets = totalAssets,
                    trendData = trend,
                    expenseBreakdown = breakdown,
                    plannedExpensesTotal = plannedExpensesTotal,
                    plannedIncomesTotal = plannedIncomesTotal,
                    forecastExpenses = forecastExpenses,
                    forecastFree = forecastFree,
                    isOffline = isOffline,
                )
        } catch (e: Exception) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки",
                    isOffline = true,
                )
        }
    }

    private fun buildExpenseBreakdown(
        expenses: List<Expense>,
        categories: List<Category>,
    ): List<ExpenseCategoryBreakdown> {
        val totalVisibleExpense = expenses.sumOf { it.amount }
        val expensesWithCategoryNames =
            expenses.map { expense ->
                val categoryName = categories.find { cat -> cat.id == expense.categoryId }?.name ?: "Без категории"
                expense to categoryName
            }
        val grouped = expensesWithCategoryNames.groupBy { it.second }
        return grouped
            .map { (categoryName, items) ->
                val amount = items.sumOf { it.first.amount }
                ExpenseCategoryBreakdown(
                    name = categoryName,
                    amount = amount,
                    percentage = if (totalVisibleExpense > 0) (amount / totalVisibleExpense) * 100 else 0.0,
                )
            }.sortedByDescending { it.amount }
    }
}

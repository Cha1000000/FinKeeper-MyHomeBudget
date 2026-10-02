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
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.network.ServerLinkState
import ru.homebudget.finkeeper.data.network.isConnectivityFailure
import ru.homebudget.finkeeper.data.network.runServerPhase
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.budget.BudgetRepository
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.expense.ExpenseRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeRepository
import ru.homebudget.finkeeper.data.repository.month.MonthData
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
import ru.homebudget.finkeeper.data.repository.planned.PlannedRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsGoalRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsTransactionRepository
import ru.homebudget.finkeeper.data.planned.toAutoExpense
import ru.homebudget.finkeeper.data.planned.withoutDownloadedRecords
import ru.homebudget.finkeeper.ui.Strings
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
    /** `null` — неизвестно (сервер ещё не ответил для этого месяца). */
    val savingsPercent: Double? = 0.0,
    val available: Double = 0.0,
    /** `null` — серверного баланса ещё не было ни разу: честная цифра неизвестна. */
    val availableWithoutSavings: Double? = null,
    val totalAssets: Double? = null,
    val trendData: List<TrendItem> = emptyList(),
    val expenseBreakdown: List<ExpenseCategoryBreakdown> = emptyList(),
    // План-слой: суммы плановых платежей и прогноз месяца
    val plannedExpensesTotal: Double = 0.0,
    val plannedIncomesTotal: Double = 0.0,
    val forecastExpenses: Double = 0.0,
    val forecastFree: Double = 0.0,
    val error: String? = null,
    val isOffline: Boolean = false,
    /** Идёт серверная фаза загрузки (для индикатора на кнопке «Повторить»). */
    val isSyncing: Boolean = false,
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
    private val serverLinkState: ServerLinkState,
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
                // Идущую загрузку (в т.ч. pull-to-refresh) не обрываем: после неё сделаем одну дозагрузку
                if (loadJob?.isActive == true) pendingReload = true else loadData(showLoader = false)
            }
        }
    }

    private var loadJob: Job? = null
    private var loadedMonthKey: Int? = null
    private var pendingReload = false

    fun refreshData() {
        startLoad(refreshing = true)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun loadData(showLoader: Boolean = true) {
        val s = _state.value
        // Полноэкранный лоадер — только если для этого месяца ещё нечего показать:
        // повторный вход на экран и обновление после синка не должны его мигать
        val needLoader = showLoader && loadedMonthKey != s.year * 100 + s.month
        _state.value = s.copy(isLoading = needLoader || s.isLoading, error = null)
        startLoad(refreshing = false)
    }

    // Новая загрузка отменяет предыдущую: иначе длинная сетевая фаза устаревшего запроса
    // могла бы перезаписать более свежий результат
    private fun startLoad(refreshing: Boolean) {
        val previous = loadJob
        loadJob =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                if (refreshing) _state.value = _state.value.copy(isRefreshing = true)
                try {
                    loadDataSuspend()
                } finally {
                    if (refreshing) _state.value = _state.value.copy(isRefreshing = false)
                }
                // Только при штатном завершении: после отмены дозагрузку сделает новая загрузка
                if (pendingReload) {
                    pendingReload = false
                    startLoad(refreshing = false)
                }
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

    /**
     * Local-first: сначала показываем данные из локальной БД (без сети), затем подтягиваем сервер
     * в рамках общего бюджета времени и публикуем уточнённые цифры. На «зависшей» сети экран
     * остаётся на локальных данных с пометкой isOffline, а не висит на лоадере.
     */
    private suspend fun loadDataSuspend() {
        try {
            val s = _state.value
            val year = s.year
            val month = s.month

            suspend fun localMonth(): MonthData =
                monthRepository.getOrCreateMonth(currentUserId, year, month, registerOnServer = false)
                    .getOrThrow()

            var monthData = localMonth()

            // Фаза 1: мгновенно, только Room
            publishState(
                monthData, year, month, summary = null, trend = _state.value.trendData, ensured = false,
                serverCumulative = null, isOffline = _state.value.isOffline, error = _state.value.error,
            )

            // Фаза 2: сервер, с бюджетом и коротким замыканием после первой сетевой неудачи
            var summary: MonthSummary? = null
            var trend: List<TrendItem> = emptyList()
            var ensured = false
            var serverCumulative: CumulativeSnapshot? = null

            _state.value = _state.value.copy(isSyncing = true)
            val phase =
                runServerPhase(serverLinkState) {
                    stepResult { categoryRepository.syncWithServer(currentUserId) }
                    stepResult { savingsGoalRepository.syncWithServer(currentUserId) }
                    // До скачивания: сервер создаст регулярные записи, у которых наступил день
                    ensured = step { monthRepository.ensureOnServerChecked(monthData.localId) } ?: false
                    monthData = localMonth()
                    val serverMonthId = monthData.serverId
                    if (serverMonthId != null) {
                        stepResult { expenseRepository.syncWithServer(currentUserId, monthData.localId) }
                        stepResult { incomeRepository.syncWithServer(currentUserId, monthData.localId) }
                        // Старый сервер без planned-state не должен ломать загрузку
                        optionalStep { plannedRepository.syncWithServer(currentUserId, monthData.localId) }
                        summary = step {
                            withRetry(config = RetryConfig(maxAttempts = 2)) { apiClient.getMonthSummary(serverMonthId) }
                        }
                        trend = step {
                            withRetry(config = RetryConfig(maxAttempts = 2)) { apiClient.getTrend() }
                        } ?: emptyList()
                    }
                    serverCumulative = step {
                        // База оффлайн-оценки — непосредственно перед запросом: правки, сделанные
                        // пока он идёт, сервер не учтёт, а оценка учтёт
                        val baseline = localMonthBalance(monthData.localId)
                        val response =
                            withRetry(config = RetryConfig(maxAttempts = 2)) { apiClient.getCumulativeBalance(year, month) }
                        CumulativeSnapshot(server = response.cumulativeBalance, localBaseline = baseline)
                    }
                }

            // Фаза 3: перечитываем Room — pull мог обновить данные
            publishState(
                monthData, year, month, summary, trend, ensured,
                // Базу для оффлайн-оценки «Всего активов» запоминаем, только если локальные данные
                // месяца пришли целиком: иначе последующий pull сдвинул бы оценку дважды
                serverCumulative = serverCumulative.takeIf { phase.serverError == null },
                isOffline = !phase.reachable,
                error = phase.serverError?.let { Strings.SERVER_REFRESH_FAILED },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[LOAD] dashboard load failed: ${e::class.simpleName}: ${e.message}")
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    // Подробности — в лог: текст исключения Room/сериализации пользователю ничего не скажет
                    error = Strings.LOADING_ERROR,
                    isOffline = e.isConnectivityFailure() || _state.value.isOffline,
                    isSyncing = false,
                )
        }
    }

    /** Локальный баланс месяца: база оффлайн-оценки «Всего активов». */
    private suspend fun localMonthBalance(monthLocalId: Long): Double {
        val expenses = expenseRepository.getExpensesByMonth(monthLocalId).getOrThrow()
        val incomes = incomeRepository.getIncomesByMonth(monthLocalId).getOrThrow()
        val unsyncedDeposits = savingsTransactionRepository.getUnsyncedDepositsTotal(currentUserId, monthLocalId)
        return incomes.sumOf { it.amount } - expenses.sumOf { it.amount } - unsyncedDeposits
    }

    /** Ответ сервера о кумулятивном балансе и локальный баланс месяца на момент запроса. */
    private class CumulativeSnapshot(
        val server: Double,
        val localBaseline: Double,
    )

    private suspend fun publishState(
        monthData: MonthData,
        year: Int,
        month: Int,
        summary: MonthSummary?,
        trend: List<TrendItem>,
        ensured: Boolean,
        serverCumulative: CumulativeSnapshot?,
        isOffline: Boolean,
        error: String?,
    ) {
        // Читаем актуальные данные из локальной БД. Сбой чтения — ошибка экрана, а не нули
        val savingsGoals = savingsGoalRepository.getAllSavingsGoals(currentUserId).getOrThrow()
        val expenses = expenseRepository.getExpensesByMonth(monthData.localId).getOrThrow()
        val incomes = incomeRepository.getIncomesByMonth(monthData.localId).getOrThrow()
        val categories = categoryRepository.getAllCategories(currentUserId).getOrThrow()
        val budgets = budgetRepository.getBudgetsByMonth(monthData.localId).getOrThrow()

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
        val unsyncedDeposits = savingsTransactionRepository.getUnsyncedDepositsTotal(currentUserId, monthData.localId)
        val totalAllExpense = (summary?.expenses ?: expenses.sumOf { it.amount }) +
            unsyncedDeposits +
            autoExpensesTotal
        val totalLimit = budgets.sumOf { it.limitAmount }

        // Всего активов = кумулятивный баланс до выбранного месяца включительно (на сервере:
        // Σ доходов − Σ расходов по месяцам ≤ выбранного). Без ответа сервера берём последнее
        // известное значение и сдвигаем на изменение локального баланса месяца с тех пор.
        val localBalance = incomes.sumOf { it.amount } - expenses.sumOf { it.amount } - unsyncedDeposits
        val cumulativeBalance =
            if (serverCumulative != null) {
                tokenStorage.saveCumulativeBalance(
                    currentUserId, year, month, serverCumulative.server, serverCumulative.localBaseline,
                )
                serverCumulative.server + (localBalance - serverCumulative.localBaseline)
            } else {
                // Без кэша честной цифры нет: прошлые месяцы локально могут быть неполными
                tokenStorage.loadCumulativeBalance(currentUserId, year, month)
                    ?.let { (server, baseline) -> server + (localBalance - baseline) }
            }
        val totalAssets = cumulativeBalance?.plus(totalSavings)
        val savingsPercent =
            when {
                totalIncome <= 0 -> 0.0
                summary != null -> summary.savings / totalIncome * 100
                // Без ответа сервера процент неизвестен: оставляем прежний для этого месяца, а не «0%»
                loadedMonthKey == year * 100 + month -> _state.value.savingsPercent
                else -> null
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
                availableWithoutSavings = cumulativeBalance,
                totalAssets = totalAssets,
                trendData = trend.ifEmpty { _state.value.trendData },
                expenseBreakdown = breakdown,
                plannedExpensesTotal = plannedExpensesTotal,
                plannedIncomesTotal = plannedIncomesTotal,
                forecastExpenses = forecastExpenses,
                forecastFree = forecastFree,
                isOffline = isOffline,
                isSyncing = false,
                error = error,
            )
        loadedMonthKey = year * 100 + month
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

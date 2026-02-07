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

data class ExpenseCategoryBreakdown(
    val name: String,
    val amount: Double,
    val percentage: Double
)

data class DashboardState(
    val isLoading: Boolean = true,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalSavings: Double = 0.0,
    val savingsPercent: Double = 0.0,
    val available: Double = 0.0,
    val totalAssets: Double = 0.0,
    val trendData: List<TrendItem> = emptyList(),
    val expenseBreakdown: List<ExpenseCategoryBreakdown> = emptyList(),
    val error: String? = null
)

class DashboardViewModel(
    private val apiClient: ApiClient
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val year = now.year
                val month = now.monthNumber

                val monthData = apiClient.ensureMonth(year, month)
                val summary = apiClient.getMonthSummary(monthData.id)
                val trend = apiClient.getTrend()
                val savingsGoals = apiClient.getSavingsGoals()
                val expenses = apiClient.getExpenses(monthData.id)

                val totalSavings = savingsGoals.sumOf { it.currentAmount }
                val available = summary.income - summary.expenses
                val totalAssets = available + totalSavings
                val savingsPercent = if (summary.income > 0) {
                    (summary.savings / summary.income) * 100
                } else 0.0

                // Build expense breakdown (excluding hidden savings category)
                val visibleExpenses = expenses.filter { it.categoryName != "Пополнение копилки" }
                val totalVisibleExpense = visibleExpenses.sumOf { it.amount }
                val grouped = visibleExpenses.groupBy { it.categoryName ?: "Без категории" }
                val breakdown = grouped.map { (name, items) ->
                    val amount = items.sumOf { it.amount }
                    ExpenseCategoryBreakdown(
                        name = name,
                        amount = amount,
                        percentage = if (totalVisibleExpense > 0) (amount / totalVisibleExpense) * 100 else 0.0
                    )
                }.sortedByDescending { it.amount }

                _state.value = DashboardState(
                    isLoading = false,
                    totalIncome = summary.income,
                    totalExpense = summary.expenses,
                    totalSavings = totalSavings,
                    savingsPercent = savingsPercent,
                    available = available,
                    totalAssets = totalAssets,
                    trendData = trend,
                    expenseBreakdown = breakdown
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки данных"
                )
            }
        }
    }
}

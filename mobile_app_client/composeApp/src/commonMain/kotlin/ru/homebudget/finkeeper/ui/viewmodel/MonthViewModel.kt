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
import ru.homebudget.finkeeper.util.currentIsoDate

data class GroupedExpense(
    val categoryId: Int,
    val categoryName: String,
    val items: List<Expense>,
    val total: Double,
    val limit: Double,
    val isOverLimit: Boolean
)

data class MonthViewState(
    val isLoading: Boolean = true,
    val year: Int = 0,
    val month: Int = 0,
    val monthData: Month? = null,
    val incomes: List<Income> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val groupedExpenses: List<GroupedExpense> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalLimit: Double = 0.0,
    val activeTab: Int = 0, // 0 = expenses, 1 = incomes
    val error: String? = null,
    // Pending source confirmation state
    val pendingSourceName: String? = null,
    val pendingSourceAmount: Double? = null,
    val showSourceConfirm: Boolean = false
)

class MonthViewModel(
    private val apiClient: ApiClient
) : ViewModel() {

    private val _state = MutableStateFlow(MonthViewState())
    val state: StateFlow<MonthViewState> = _state.asStateFlow()

    init {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        _state.value = _state.value.copy(year = now.year, month = now.monthNumber)
        loadData()
    }

    fun setActiveTab(tab: Int) {
        _state.value = _state.value.copy(activeTab = tab)
    }

    fun prevMonth() {
        val s = _state.value
        var y = s.year
        var m = s.month - 1
        if (m < 1) { m = 12; y-- }
        _state.value = s.copy(year = y, month = m)
        loadData()
    }

    fun nextMonth() {
        val s = _state.value
        var y = s.year
        var m = s.month + 1
        if (m > 12) { m = 1; y++ }
        _state.value = s.copy(year = y, month = m)
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val s = _state.value
                val monthData = apiClient.ensureMonth(s.year, s.month)
                val incomes = apiClient.getIncomes(monthData.id)
                val allExpenses = apiClient.getExpenses(monthData.id)
                val categories = apiClient.getCategories()
                val budgets = apiClient.getBudgets(monthData.id)
                val incomeSources = apiClient.getIncomeSources()

                val visibleExpenses = allExpenses.filter { it.categoryName != "Пополнение копилки" }
                val totalIncome = incomes.sumOf { it.amount }
                val totalExpense = visibleExpenses.sumOf { it.amount }
                val totalLimit = budgets.sumOf { it.limitAmount }

                val grouped = buildGroupedExpenses(visibleExpenses, categories, budgets)

                _state.value = _state.value.copy(
                    isLoading = false,
                    monthData = monthData,
                    incomes = incomes,
                    expenses = visibleExpenses,
                    categories = categories,
                    incomeSources = incomeSources,
                    budgets = budgets,
                    groupedExpenses = grouped,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    totalLimit = totalLimit
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки"
                )
            }
        }
    }

    fun addIncome(source: String, amount: Double) {
        val monthData = _state.value.monthData ?: return
        viewModelScope.launch {
            try {
                apiClient.addIncome(AddIncomeRequest(
                    monthId = monthData.id,
                    source = source,
                    amount = amount,
                    date = currentIsoDate()
                ))
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addIncomeWithSourceCheck(source: String, amount: Double) {
        val monthData = _state.value.monthData ?: return
        val incomeSources = _state.value.incomeSources
        
        // Check if source exists
        val sourceExists = incomeSources.any { it.name.equals(source, ignoreCase = true) }
        
        if (!sourceExists && source.isNotBlank()) {
            // Source doesn't exist - show confirmation dialog
            _state.value = _state.value.copy(
                pendingSourceName = source,
                pendingSourceAmount = amount,
                showSourceConfirm = true
            )
        } else {
            // Source exists or empty - add income directly
            addIncome(source, amount)
        }
    }

    fun confirmAddIncomeSource() {
        val source = _state.value.pendingSourceName ?: return
        val amount = _state.value.pendingSourceAmount ?: return
        
        // First add the source
        addIncomeSource(source)
        
        // Then add income with the source
        addIncome(source, amount)
        
        // Clear pending state
        _state.value = _state.value.copy(
            pendingSourceName = null,
            pendingSourceAmount = null,
            showSourceConfirm = false
        )
    }

    fun cancelAddIncomeSource() {
        _state.value = _state.value.copy(
            pendingSourceName = null,
            pendingSourceAmount = null,
            showSourceConfirm = false
        )
    }

    fun addExpense(categoryId: Int, amount: Double, comment: String?) {
        val monthData = _state.value.monthData ?: return
        viewModelScope.launch {
            try {
                apiClient.addExpense(AddExpenseRequest(
                    monthId = monthData.id,
                    categoryId = categoryId,
                    amount = amount,
                    date = currentIsoDate(),
                    comment = comment
                ))
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateIncome(id: Int, amount: Double) {
        viewModelScope.launch {
            try {
                apiClient.updateIncome(id, amount)
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateExpense(id: Int, amount: Double) {
        viewModelScope.launch {
            try {
                apiClient.updateExpense(id, amount)
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteIncome(id: Int) {
        viewModelScope.launch {
            try {
                apiClient.deleteIncome(id)
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            try {
                apiClient.deleteExpense(id)
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun setBudget(categoryId: Int, limit: Double) {
        val monthData = _state.value.monthData ?: return
        viewModelScope.launch {
            try {
                apiClient.setBudget(SetBudgetRequest(
                    monthId = monthData.id,
                    categoryId = categoryId,
                    limitAmount = limit
                ))
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addIncomeSource(name: String) {
        viewModelScope.launch {
            try {
                apiClient.createIncomeSource(name)
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Ошибка добавления источника")
            }
        }
    }

    private fun buildGroupedExpenses(
        expenses: List<Expense>,
        categories: List<Category>,
        budgets: List<Budget>
    ): List<GroupedExpense> {
        val expenseMap = expenses.groupBy { it.categoryId }
        val categoryOrder = categories.mapIndexed { index, cat -> cat.id to index }.toMap()

        return expenseMap.map { (catId, items) ->
            val catName = items.firstOrNull()?.categoryName ?: "Без категории"
            val total = items.sumOf { it.amount }
            val budget = budgets.find { it.categoryId == catId }
            val limit = budget?.limitAmount ?: 0.0
            GroupedExpense(
                categoryId = catId,
                categoryName = catName,
                items = items.sortedByDescending { it.date },
                total = total,
                limit = limit,
                isOverLimit = limit > 0 && total > limit
            )
        }.sortedBy { categoryOrder[it.categoryId] ?: Int.MAX_VALUE }
    }
}

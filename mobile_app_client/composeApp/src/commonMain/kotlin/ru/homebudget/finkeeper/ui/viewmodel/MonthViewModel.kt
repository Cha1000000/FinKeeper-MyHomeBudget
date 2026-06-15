package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.repository.budget.BudgetRepository
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.expense.ExpenseRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeRepository
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.income.IncomeSourceRepository
import ru.homebudget.finkeeper.data.repository.month.MonthData
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
import ru.homebudget.finkeeper.data.repository.onSuccess
import ru.homebudget.finkeeper.data.repository.planned.PlannedRepository
import ru.homebudget.finkeeper.data.planned.PlannedItem
import ru.homebudget.finkeeper.data.planned.PlannedResult
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
    val activeTab: Int = 0, // 0 = expenses, 1 = incomes
    val error: String? = null,
    val isOffline: Boolean = false,
    // Pending source confirmation state
    val pendingSourceName: String? = null,
    val pendingSourceAmount: Double? = null,
    val showSourceConfirm: Boolean = false,
)

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
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            loadDataSuspend()
            _state.value = _state.value.copy(isRefreshing = false)
        }
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

    fun loadData(
        syncFromServer: Boolean = true,
        showLoader: Boolean = true,
    ) {
        viewModelScope.launch {
            if (showLoader) {
                _state.value = _state.value.copy(isLoading = true, error = null)
            } else {
                _state.value = _state.value.copy(error = null)
            }
            loadDataSuspend(syncFromServer)
        }
    }

    private suspend fun loadDataSuspend(syncFromServer: Boolean = true) {
        try {
            val s = _state.value

            // Получаем или создаём месяц через репозиторий
            val monthResult = monthRepository.getOrCreateMonth(currentUserId, s.year, s.month)
            val monthData =
                if (monthResult.isSuccess) {
                    monthResult.getOrNull()!!
                } else {
                    throw Exception("Failed to get or create month")
                }

            // Фаза 1: Синхронизируем данные с сервером (если онлайн)
            // Пропускаем синхронизацию после локальных мутаций (delete/update),
            // чтобы не перезаписать ещё не отправленные изменения
            var isOffline = _state.value.isOffline
            if (syncFromServer) {
                isOffline = false
                try {
                    categoryRepository.syncWithServer(currentUserId)
                    incomeSourceRepository.syncWithServer(currentUserId)
                    if (monthData.serverId != null) {
                        incomeRepository.syncWithServer(currentUserId, monthData.localId)
                        expenseRepository.syncWithServer(currentUserId, monthData.localId)
                        budgetRepository.syncWithServer(currentUserId, monthData.localId)
                        try {
                            plannedRepository.syncWithServer(currentUserId, monthData.localId)
                        } catch (_: Exception) {
                            // Старый сервер без planned-state не должен ломать загрузку месяца
                        }
                    }
                } catch (e: Exception) {
                    isOffline = true
                }
            }

            // Фаза 2: Читаем актуальные данные из локальной БД
            var incomes: List<Income> = emptyList()
            incomeRepository
                .getIncomesByMonth(monthData.localId)
                .onSuccess { incomeList ->
                    incomes = incomeList
                }

            var allExpenses: List<Expense> = emptyList()
            expenseRepository
                .getExpensesByMonth(monthData.localId)
                .onSuccess { expenseList ->
                    allExpenses = expenseList
                }

            var categories: List<Category> = emptyList()
            categoryRepository
                .getAllCategories(currentUserId)
                .onSuccess { categoryList ->
                    categories = categoryList
                }

            var budgets: List<Budget> = emptyList()
            budgetRepository
                .getBudgetsByMonth(monthData.localId)
                .onSuccess { budgetList ->
                    budgets = budgetList
                }

            var incomeSources: List<IncomeSource> = emptyList()
            incomeSourceRepository
                .getAllIncomeSources(currentUserId)
                .onSuccess { sourceList ->
                    incomeSources = sourceList
                }

            // Фильтруем расходы - исключаем категорию "Пополнение копилки"
            val piggyBankCategoryId = categories.find { it.name == "Пополнение копилки" }?.id
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
            val totalAllExpenses = allExpenses.sumOf { it.amount }
            val totalLimit = budgets.sumOf { it.limitAmount }

            val grouped = buildGroupedExpenses(visibleExpenses, categories, budgets)

            // Виртуальный план-слой: вычисляется локально, работает оффлайн
            val planned: PlannedResult = plannedRepository.getPlanned(monthData.localId)

            _state.value =
                _state.value.copy(
                    isLoading = false,
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
                )
                println("[MONTH-VM] addIncome: createIncome completed")

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                println("[MONTH-VM] addIncome ERROR: ${e.message}")
                e.printStackTrace()
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /**
     * Последовательно создаёт источник дохода и доход с ним. id нового источника
     * берётся прямо из результата создания, а не из перечитанного state — иначе
     * доход создаётся с income_source_id=0 (гонка двух параллельных корутин).
     */
    private suspend fun createSourceAndIncome(monthLocalId: Long, source: String, amount: Double) {
        val created = incomeSourceRepository.createIncomeSource(
            userId = currentUserId,
            name = source,
        )
        val newSourceId = (created as? ru.homebudget.finkeeper.data.repository.Result.Success)
            ?.data?.id?.toLong()
        if (newSourceId == null) {
            _state.value = _state.value.copy(error = "Не удалось создать источник дохода")
            return
        }
        incomeRepository.createIncome(
            userId = currentUserId,
            monthId = monthLocalId,
            incomeSourceId = newSourceId,
            amount = amount,
            date = currentIsoDate(),
        )
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
            try {
                createSourceAndIncome(md.localId, source, amount)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Ошибка добавления дохода")
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
            try {
                expenseRepository.createExpense(
                    userId = currentUserId,
                    monthId = md.localId,
                    categoryId = categoryId.toLong(),
                    amount = amount,
                    description = comment,
                    date = currentIsoDate(),
                )
                println("[MONTH-VM] addExpense: createExpense completed")

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                println("[MONTH-VM] addExpense ERROR: ${e.message}")
                e.printStackTrace()
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateIncome(
        id: Int,
        amount: Double,
    ) {
        viewModelScope.launch {
            try {
                // Обновляем локально через репозиторий (у доходов правится только сумма)
                incomeRepository.updateIncome(
                    id = id.toLong(),
                    amount = amount,
                )

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateExpense(
        id: Int,
        amount: Double,
        description: String? = null,
    ) {
        viewModelScope.launch {
            try {
                // Обновляем локально через репозиторий (расход: сумма + комментарий)
                expenseRepository.updateExpense(
                    id = id.toLong(),
                    amount = amount,
                    description = description,
                    updateDescription = true,
                )

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteIncome(id: Int) {
        viewModelScope.launch {
            try {
                // Удаляем локально через репозиторий
                incomeRepository.deleteIncome(id.toLong())

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            try {
                // Удаляем локально через репозиторий
                expenseRepository.deleteExpense(id.toLong())

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun setBudget(
        categoryId: Int,
        limit: Double,
    ) {
        val md = _state.value.monthData ?: return
        viewModelScope.launch {
            try {
                budgetRepository.setBudget(
                    userId = currentUserId,
                    monthId = md.localId,
                    categoryId = categoryId.toLong(),
                    limitAmount = limit,
                )

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addIncomeSource(name: String) {
        viewModelScope.launch {
            try {
                // Сначала сохраняем локально через репозиторий
                incomeSourceRepository.createIncomeSource(
                    userId = currentUserId,
                    name = name,
                )

                loadData(syncFromServer = false, showLoader = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Ошибка добавления источника")
            }
        }
    }

    fun reorderExpenseGroups(newOrder: List<GroupedExpense>) {
        viewModelScope.launch {
            try {
                newOrder.forEachIndexed { index, group ->
                    categoryRepository.updateCategorySortOrder(group.categoryId.toLong(), index.toLong())
                }
                _state.value = _state.value.copy(groupedExpenses = newOrder)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Ошибка сортировки")
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
            val result = plannedRepository.confirm(
                monthId = md.localId,
                templateType = item.templateType,
                templateId = item.templateId,
                amountCents = amount?.let { (it * 100).toLong() },
            )
            if (result.isSuccess) {
                loadData(syncFromServer = true, showLoader = false)
            } else {
                val message = (result as? ru.homebudget.finkeeper.data.repository.Result.Error)?.exception?.message
                _state.value = _state.value.copy(error = message ?: "Не удалось подтвердить платёж")
            }
        }
    }

    /** Пропустить платёж в этом месяце / вернуть в план (offline-first) */
    fun skipPlanned(item: PlannedUiItem, skipped: Boolean) {
        val md = _state.value.monthData ?: return
        viewModelScope.launch {
            plannedRepository.setSkipped(md.localId, item.templateType, item.templateId, skipped)
            loadData(syncFromServer = false, showLoader = false)
        }
    }

    /** Изменить сумму/день только на этот месяц (offline-first) */
    fun overridePlanned(item: PlannedUiItem, amount: Double, day: Int) {
        val md = _state.value.monthData ?: return
        viewModelScope.launch {
            plannedRepository.setOverride(
                monthId = md.localId,
                templateType = item.templateType,
                templateId = item.templateId,
                amountCents = if (amount == item.originalAmount) null else (amount * 100).toLong(),
                day = day,
            )
            loadData(syncFromServer = false, showLoader = false)
        }
    }

    /** Сбросить изменения месяца к шаблону (offline-first) */
    fun resetPlanned(item: PlannedUiItem) {
        val md = _state.value.monthData ?: return
        viewModelScope.launch {
            plannedRepository.resetOverride(md.localId, item.templateType, item.templateId)
            loadData(syncFromServer = false, showLoader = false)
        }
    }
}

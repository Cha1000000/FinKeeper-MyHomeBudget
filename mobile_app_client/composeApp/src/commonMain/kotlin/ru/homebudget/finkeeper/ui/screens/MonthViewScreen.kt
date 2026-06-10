package ru.homebudget.finkeeper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.components.AppButton
import ru.homebudget.finkeeper.ui.components.DesktopAddButton
import ru.homebudget.finkeeper.ui.components.AppTextField
import ru.homebudget.finkeeper.ui.components.ConfirmDialog
import ru.homebudget.finkeeper.ui.components.EmptyState
import ru.homebudget.finkeeper.ui.components.GlassyButtonStyle
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.components.LoadingScreen
import ru.homebudget.finkeeper.ui.components.ProgressBar
import ru.homebudget.finkeeper.ui.components.ScreenHeader
import ru.homebudget.finkeeper.ui.components.SummaryCard
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.GroupedExpense
import ru.homebudget.finkeeper.ui.viewmodel.MonthViewState
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.formatDate
import ru.homebudget.finkeeper.util.isDesktop
import ru.homebudget.finkeeper.util.monthName
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MonthViewScreen(
    state: MonthViewState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetActiveTab: (Int) -> Unit,
    onAddIncome: (String, Double) -> Unit,
    onAddIncomeWithSourceCheck: (String, Double) -> Unit,
    onAddExpense: (Int, Double, String?) -> Unit,
    onUpdateIncome: (Int, Double) -> Unit,
    onUpdateExpense: (Int, Double) -> Unit,
    onDeleteIncome: (Int) -> Unit,
    onDeleteExpense: (Int) -> Unit,
    onSetBudget: (Int, Double) -> Unit,
    onAddIncomeSource: (String) -> Unit,
    onConfirmAddIncomeSource: () -> Unit,
    onCancelAddIncomeSource: () -> Unit,
    onReorderExpenseGroups: (List<GroupedExpense>) -> Unit,
    onRefresh: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddExpenseForCategory by remember { mutableStateOf<Int?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var deleteIncomeId by remember { mutableStateOf<Int?>(null) }
    var deleteExpenseId by remember { mutableStateOf<Int?>(null) }
    var editingExpense by remember { mutableStateOf<Pair<Int, Double>?>(null) }

    var localGroupedExpenses by remember { mutableStateOf(state.groupedExpenses) }
    LaunchedEffect(state.groupedExpenses) {
        localGroupedExpenses = state.groupedExpenses
    }

    LaunchedEffect(Unit) { onRefresh() }

    val semantic = AppTheme.semanticColors
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val useFloatingAddButton = !isDesktop
    val floatingActionButtonBottomPadding = if (useFloatingAddButton) 20.dp else 24.dp
    val listBottomPadding = if (useFloatingAddButton) 104.dp else 8.dp
    val floatingActionShape = RoundedCornerShape(percent = 50)
    val floatingActionBlurTint =
        if (isDarkTheme) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.64f)
        }
    val floatingActionPlateTint =
        MaterialTheme.colorScheme.primaryContainer.copy(
            alpha = if (isDarkTheme) 0.22f else 0.18f
        )

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    Column(modifier = Modifier.fillMaxSize().then(
        if (ru.homebudget.finkeeper.util.isDesktop) Modifier.padding(top = 16.dp) else Modifier
    )) {
        ScreenHeader(title = Strings.MONTH_TITLE)

        // Month navigation header
        GlassyCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            baseColor = MaterialTheme.colorScheme.surface,
            highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrevMonth) {
                    Text(Strings.PREV_MONTH_ICON, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    text = "${monthName(state.month)} ${state.year}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onNextMonth) {
                    Text(Strings.NEXT_MONTH_ICON, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // Summary cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryCard(
                title = Strings.INCOMES_TAB,
                value = formatCurrency(state.totalIncome),
                backgroundColor = semantic.incomeCardBg,
                contentColor = semantic.incomeColor,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = Strings.EXPENSES_TAB,
                value = formatCurrency(state.totalExpense),
                backgroundColor = semantic.expenseCardBg,
                contentColor = semantic.expenseColor,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.totalLimit > 0) {
            GlassyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                baseColor = semantic.warningCardBg,
                highlightColor = semantic.warningColor.copy(alpha = 0.2f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(Strings.SPENDING_LIMIT, style = MaterialTheme.typography.titleSmall, color = semantic.warningColor)
                        Text(
                            "${formatCurrency(state.totalAllExpenses)} / ${formatCurrency(state.totalLimit)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.warningColor,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ProgressBar(
                        progress = if (state.totalLimit > 0) (state.totalAllExpenses / state.totalLimit).toFloat() else 0f,
                        color = if (state.totalAllExpenses > state.totalLimit) MaterialTheme.colorScheme.error else semantic.warningColor,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${Strings.REMAINDER} ${formatCurrency(maxOf(0.0, state.totalLimit - state.totalAllExpenses))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.warningColor.copy(alpha = 0.8f),
                    )
                }
            }
        }

        val budgetButtonVerticalPadding = if (state.totalLimit > 0) 2.dp else 4.dp
        // Кнопка Лимиты
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = budgetButtonVerticalPadding).padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(
                text = Strings.BUDGET,
                onClick = { showBudgetDialog = true },
                containerColor = Color(0xFF0D47A1),
                contentColor = Color.White,
                style = GlassyButtonStyle.Glassy,
                modifier = Modifier.height(32.dp).widthIn(min = 90.dp),
                textStyle = MaterialTheme.typography.labelLarge
            )
        }

        // Tabs
        TabRow(
            selectedTabIndex = state.activeTab,
            modifier = Modifier.padding(horizontal = 16.dp),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(selected = state.activeTab == 0, onClick = { onSetActiveTab(0) }) {
                Text(Strings.EXPENSES_TAB, modifier = Modifier.padding(12.dp))
            }
            Tab(selected = state.activeTab == 1, onClick = { onSetActiveTab(1) }) {
                Text(Strings.INCOMES_TAB, modifier = Modifier.padding(12.dp))
            }
        }

        // Content
        val lazyListState = rememberLazyListState()
        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = from.index - 1
            val toIndex = to.index - 1
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < localGroupedExpenses.size && toIndex < localGroupedExpenses.size) {
                localGroupedExpenses = localGroupedExpenses.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                state = lazyListState,
                contentPadding = PaddingValues(top = 8.dp, bottom = listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.activeTab == 0) {
                    // Expenses tab
                    item {
                        if (useFloatingAddButton) {
                            Text(
                                text = Strings.EXPENSES_BY_CATEGORIES,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    Strings.EXPENSES_BY_CATEGORIES,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                DesktopAddButton(onClick = { showAddDialog = true })
                            }
                        }
                    }

                    if (localGroupedExpenses.isEmpty()) {
                        item { EmptyState(Strings.NO_EXPENSES_THIS_MONTH) }
                    } else {
                        itemsIndexed(localGroupedExpenses, key = { _, group -> group.categoryId }) { _, group ->
                            ReorderableItem(reorderableLazyListState, key = group.categoryId) { isDragging ->
                                val elevation = if (isDragging) 8.dp else 0.dp
                                ExpenseGroupCard(
                                    group = group,
                                    onDeleteExpense = { deleteExpenseId = it },
                                    onEditExpense = { id, amount -> editingExpense = Pair(id, amount) },
                                    onAddExpenseInCategory = { showAddExpenseForCategory = group.categoryId },
                                    modifier = Modifier
                                        .longPressDraggableHandle(
                                            onDragStopped = {
                                                onReorderExpenseGroups(localGroupedExpenses)
                                            },
                                        ),
                                    elevation = elevation,
                                )
                            }
                        }
                    }
                } else {
                    // Incomes tab
                    item {
                        if (useFloatingAddButton) {
                            Text(
                                text = Strings.INCOMES_TAB,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    Strings.INCOMES_TAB,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface

                                )
                                DesktopAddButton(onClick = { showAddDialog = true })
                            }
                        }
                    }

                    if (state.incomesWithSources.isEmpty()) {
                        item { EmptyState(Strings.NO_INCOMES_THIS_MONTH) }
                    } else {
                        items(state.incomesWithSources, key = { it.id }) { income ->
                            IncomeItemCard(
                                source = income.sourceName,
                                amount = formatCurrency(income.amount),
                                date = formatDate(income.date),
                                onDelete = { deleteIncomeId = income.id },
                            )
                        }
                    }
                }
            }

            if (useFloatingAddButton) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = floatingActionButtonBottomPadding)
                        .height(56.dp)
                        .sizeIn(minWidth = 116.dp, maxWidth = 122.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .blur(26.dp)
                            .clip(floatingActionShape)
                            .background(floatingActionBlurTint),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .clip(floatingActionShape)
                            .background(floatingActionPlateTint),
                    )
                    AppButton(
                        text = Strings.ADD,
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = GlassyButtonStyle.Glassy,
                        textStyle = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        AddEntryDialog(
            isExpense = state.activeTab == 0,
            categories = state.categories.filter { it.isActive == 1 },
            incomeSources = state.incomeSources.filter { it.isActive == 1 },
            onDismiss = { showAddDialog = false },
            onAddExpense = { catId, amount, comment ->
                onAddExpense(catId, amount, comment)
                showAddDialog = false
            },
            onAddIncome = { source, amount ->
                onAddIncomeWithSourceCheck(source, amount)
                showAddDialog = false
            },
            onAddIncomeSource = onAddIncomeSource,
        )
    }

    // Add expense for specific category dialog
    showAddExpenseForCategory?.let { categoryId ->
        val catName = state.categories.find { it.id == categoryId }?.name ?: ""
        AddExpenseForCategoryDialog(
            categoryName = catName,
            onDismiss = { showAddExpenseForCategory = null },
            onAdd = { amount, comment ->
                onAddExpense(categoryId, amount, comment)
                showAddExpenseForCategory = null
            },
        )
    }

    // Edit expense dialog
    editingExpense?.let { (expenseId, currentAmount) ->
        EditExpenseDialog(
            currentAmount = currentAmount,
            onDismiss = { editingExpense = null },
            onSave = { newAmount ->
                onUpdateExpense(expenseId, newAmount)
                editingExpense = null
            },
        )
    }

    // Budget dialog
    if (showBudgetDialog) {
        BudgetDialog(
            categories = state.categories.filter { it.isActive == 1 },
            budgets = state.budgets,
            onDismiss = { showBudgetDialog = false },
            onSetBudget = onSetBudget,
        )
    }

    // Delete confirmations
    deleteIncomeId?.let { id ->
        ConfirmDialog(
            title = Strings.DELETE_INCOME,
            message = Strings.CONFIRM_DELETE,
            onConfirm = {
                onDeleteIncome(id)
                deleteIncomeId = null
            },
            onDismiss = { deleteIncomeId = null },
            isDestructive = true,
        )
    }

    deleteExpenseId?.let { id ->
        ConfirmDialog(
            title = Strings.DELETE_EXPENSE,
            message = Strings.CONFIRM_DELETE_EXPENSE,
            onConfirm = {
                onDeleteExpense(id)
                deleteExpenseId = null
            },
            onDismiss = { deleteExpenseId = null },
            isDestructive = true,
        )
    }

    // Confirm new income source dialog from ViewModel state
    if (state.showSourceConfirm) {
        ConfirmDialog(
            title = Strings.CONFIRMATION,
            message = Strings.SOURCE_NOT_FOUND.replace("%1\$s", state.pendingSourceName ?: ""),
            confirmText = Strings.YES,
            dismissText = Strings.CANCEL,
            onConfirm = {
                onConfirmAddIncomeSource()
                showAddDialog = false
            },
            onDismiss = {
                onCancelAddIncomeSource()
                showAddDialog = false
            },
            isDestructive = false,
            isLoading = false,
        )
    }
}

@Composable
private fun ExpenseGroupCard(
    group: GroupedExpense,
    onDeleteExpense: (Int) -> Unit,
    onEditExpense: (Int, Double) -> Unit,
    onAddExpenseInCategory: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val semantic = AppTheme.semanticColors
    
    GlassyCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        baseColor = if (group.isOverLimit) semantic.expenseCardBg else MaterialTheme.colorScheme.surface,
        highlightColor = (if (group.isOverLimit) semantic.expenseColor else MaterialTheme.colorScheme.primary).copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (group.limit > 0) {
                        Text(
                            text = "${formatCurrency(group.total)} / ${formatCurrency(group.limit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (group.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = formatCurrency(group.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (group.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (expanded) Strings.COLLAPSE_ICON else Strings.EXPAND_ICON,
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (group.limit > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                ProgressBar(
                    progress = (group.total / group.limit).toFloat(),
                    color = if (group.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    height = 3,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(
                        onClick = onAddExpenseInCategory,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = Strings.ADD_EXPENSE_FOR_CATEGORY.replace("%1\$s", group.categoryName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    group.items.forEach { expense ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatCurrency(expense.amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!expense.comment.isNullOrBlank()) {
                                    Text(
                                        text = expense.comment,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = formatDate(expense.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                TextButton(
                                    onClick = { onEditExpense(expense.id, expense.amount) },
                                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp),
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                ) {
                                    Text(Strings.EDIT_ICON, style = MaterialTheme.typography.bodyLarge)
                                }
                                TextButton(
                                    onClick = { onDeleteExpense(expense.id) },
                                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp),
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Text(Strings.DELETE_ICON, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        if (expense != group.items.last()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeItemCard(
    source: String,
    amount: String,
    date: String,
    onDelete: () -> Unit,
) {
    GlassyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = AppTheme.semanticColors.incomeColor.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.semanticColors.incomeColor,
            )
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(Strings.DELETE_ICON)
            }
        }
    }
}

@Composable
private fun AddEntryDialog(
    isExpense: Boolean,
    categories: List<ru.homebudget.finkeeper.data.model.Category>,
    incomeSources: List<ru.homebudget.finkeeper.data.model.IncomeSource>,
    onDismiss: () -> Unit,
    onAddExpense: (Int, Double, String?) -> Unit,
    onAddIncome: (String, Double) -> Unit,
    onAddIncomeSource: (String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0) }
    var selectedSource by remember { mutableStateOf(incomeSources.firstOrNull()?.name ?: "") }
    var customSource by remember { mutableStateOf("") }
    var useCustomSource by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isExpense) Strings.ADD_EXPENSE else Strings.ADD_INCOME) },
        text = {
            Column(
                modifier = Modifier
                    .then(if (isDesktop) Modifier.padding(vertical = 32.dp) else Modifier)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isExpense) {
                    Text(Strings.CATEGORY, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    ) {
                        LazyColumn(modifier = Modifier.padding(4.dp)) {
                            items(categories, key = { it.id }) { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCategoryId = cat.id }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selectedCategoryId == cat.id,
                                        onClick = { selectedCategoryId = cat.id },
                                    )
                                    Text(cat.name, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                } else {
                    Text(Strings.SOURCE, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    ) {
                        LazyColumn(modifier = Modifier.padding(4.dp)) {
                            items(incomeSources, key = { it.name }) { src ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSource = src.name
                                            useCustomSource = false
                                        }.padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = !useCustomSource && selectedSource == src.name,
                                        onClick = {
                                            selectedSource = src.name
                                            useCustomSource = false
                                        },
                                    )
                                    Text(src.name, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { useCustomSource = true }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = useCustomSource,
                                        onClick = { useCustomSource = true },
                                    )
                                    Text("${Strings.OTHER}:", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                    if (useCustomSource) {
                        AppTextField(
                            value = customSource,
                            onValueChange = { customSource = it },
                            label = Strings.NEW_SOURCE,
                        )
                    }
                }

                AppTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = Strings.AMOUNT,
                    keyboardType = KeyboardType.Decimal,
                )

                if (isExpense) {
                    AppTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = Strings.COMMENT_OPTIONAL,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountVal = amount.toDoubleOrNull() ?: return@TextButton
                    if (isExpense) {
                        onAddExpense(selectedCategoryId, amountVal, comment.ifBlank { null })
                    } else {
                        val src = if (useCustomSource) customSource else selectedSource
                        if (src.isNotBlank()) onAddIncome(src, amountVal)
                    }
                },
                enabled = amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0,
            ) {
                Text(Strings.ADD)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

@Composable
private fun BudgetDialog(
    categories: List<ru.homebudget.finkeeper.data.model.Category>,
    budgets: List<ru.homebudget.finkeeper.data.model.Budget>,
    onDismiss: () -> Unit,
    onSetBudget: (Int, Double) -> Unit,
) {
    val budgetValues = remember {
        mutableStateMapOf<Int, String>().apply {
            categories.forEach { cat ->
                val budget = budgets.find { it.categoryId == cat.id }
                put(cat.id, budget?.limitAmount?.let { if (it > 0) it.toLong().toString() else "" } ?: "")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.BUDGET_SETTINGS) },
        text = {
            Column(
                modifier = Modifier
                    .then(if (isDesktop) Modifier.padding(vertical = 32.dp) else Modifier)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { cat ->
                    AppTextField(
                        value = budgetValues[cat.id] ?: "",
                        onValueChange = { budgetValues[cat.id] = it },
                        label = cat.name,
                        keyboardType = KeyboardType.Decimal,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                budgetValues.forEach { (catId, value) ->
                    val limit = value.toDoubleOrNull() ?: 0.0
                    onSetBudget(catId, limit)
                }
                onDismiss()
            }) {
                Text(Strings.SAVE)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

@Composable
private fun AddExpenseForCategoryDialog(
    categoryName: String,
    onDismiss: () -> Unit,
    onAdd: (Double, String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Strings.ADD_EXPENSE} в \"$categoryName\"") },
        text = {
            Column(
                modifier = Modifier.then(if (isDesktop) Modifier.padding(vertical = 32.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = Strings.AMOUNT,
                    keyboardType = KeyboardType.Decimal,
                )
                AppTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = Strings.COMMENT_OPTIONAL,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountVal = amount.toDoubleOrNull() ?: return@TextButton
                    onAdd(amountVal, comment.ifBlank { null })
                },
                enabled = amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0,
            ) {
                Text(Strings.ADD)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

@Composable
private fun EditExpenseDialog(
    currentAmount: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var amount by remember { mutableStateOf(currentAmount.toLong().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.EDIT_EXPENSE) },
        text = {
            Column(
                modifier = Modifier.then(if (isDesktop) Modifier.padding(vertical = 32.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = Strings.AMOUNT,
                    keyboardType = KeyboardType.Decimal,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountVal = amount.toDoubleOrNull() ?: return@TextButton
                    onSave(amountVal)
                },
                enabled = amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0,
            ) {
                Text(Strings.SAVE)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

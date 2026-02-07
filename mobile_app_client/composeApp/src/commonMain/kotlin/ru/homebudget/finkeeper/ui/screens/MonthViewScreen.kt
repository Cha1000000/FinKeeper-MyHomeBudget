package ru.homebudget.finkeeper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.components.*
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.GroupedExpense
import ru.homebudget.finkeeper.ui.viewmodel.MonthViewState
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.formatDate
import ru.homebudget.finkeeper.util.monthName

@Composable
fun MonthViewScreen(
    state: MonthViewState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetActiveTab: (Int) -> Unit,
    onAddIncome: (String, Double) -> Unit,
    onAddExpense: (Int, Double, String?) -> Unit,
    onUpdateIncome: (Int, Double) -> Unit,
    onUpdateExpense: (Int, Double) -> Unit,
    onDeleteIncome: (Int) -> Unit,
    onDeleteExpense: (Int) -> Unit,
    onSetBudget: (Int, Double) -> Unit,
    onAddIncomeSource: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var deleteIncomeId by remember { mutableStateOf<Int?>(null) }
    var deleteExpenseId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

    val semantic = AppTheme.semanticColors

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Month navigation header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPrevMonth) { Text("◀") }
                Text(
                    text = "${monthName(state.month)} ${state.year}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onNextMonth) { Text("▶") }
            }
        }

        // Summary cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCard(
                title = "Доходы",
                value = formatCurrency(state.totalIncome),
                backgroundColor = semantic.incomeCardBg,
                contentColor = semantic.incomeColor,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Расходы",
                value = formatCurrency(state.totalExpense),
                backgroundColor = semantic.expenseCardBg,
                contentColor = semantic.expenseColor,
                modifier = Modifier.weight(1f)
            )
        }

        if (state.totalLimit > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = semantic.warningCardBg)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Лимит трат", style = MaterialTheme.typography.labelMedium, color = semantic.warningColor)
                        Text(
                            "${formatCurrency(state.totalExpense)} / ${formatCurrency(state.totalLimit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.warningColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ProgressBar(
                        progress = if (state.totalLimit > 0) (state.totalExpense / state.totalLimit).toFloat() else 0f,
                        color = if (state.totalExpense > state.totalLimit) MaterialTheme.colorScheme.error else semantic.warningColor
                    )
                }
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = state.activeTab,
            modifier = Modifier.padding(horizontal = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = state.activeTab == 0, onClick = { onSetActiveTab(0) }) {
                Text("Расходы", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = state.activeTab == 1, onClick = { onSetActiveTab(1) }) {
                Text("Доходы", modifier = Modifier.padding(12.dp))
            }
        }

        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.activeTab == 0) {
                // Expenses tab
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Расходы по категориям", style = MaterialTheme.typography.titleMedium)
                        Row {
                            TextButton(onClick = { showBudgetDialog = true }) {
                                Text("Бюджет", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = { showAddDialog = true }) {
                                Text("+ Добавить", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                if (state.groupedExpenses.isEmpty()) {
                    item { EmptyState("Нет расходов за этот месяц") }
                } else {
                    items(state.groupedExpenses, key = { it.categoryId }) { group ->
                        ExpenseGroupCard(
                            group = group,
                            onDeleteExpense = { deleteExpenseId = it },
                            onUpdateExpense = onUpdateExpense
                        )
                    }
                }
            } else {
                // Incomes tab
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Доходы", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showAddDialog = true }) {
                            Text("+ Добавить", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (state.incomes.isEmpty()) {
                    item { EmptyState("Нет доходов за этот месяц") }
                } else {
                    items(state.incomes, key = { it.id }) { income ->
                        IncomeItemCard(
                            source = income.source,
                            amount = formatCurrency(income.amount),
                            date = formatDate(income.date),
                            onDelete = { deleteIncomeId = income.id }
                        )
                    }
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
                onAddIncome(source, amount)
                showAddDialog = false
            },
            onAddIncomeSource = onAddIncomeSource
        )
    }

    // Budget dialog
    if (showBudgetDialog) {
        BudgetDialog(
            categories = state.categories.filter { it.isActive == 1 },
            budgets = state.budgets,
            onDismiss = { showBudgetDialog = false },
            onSetBudget = onSetBudget
        )
    }

    // Delete confirmations
    deleteIncomeId?.let { id ->
        ConfirmDialog(
            title = "Удалить доход",
            message = "Вы уверены, что хотите удалить этот доход?",
            onConfirm = { onDeleteIncome(id); deleteIncomeId = null },
            onDismiss = { deleteIncomeId = null },
            isDestructive = true
        )
    }

    deleteExpenseId?.let { id ->
        ConfirmDialog(
            title = "Удалить расход",
            message = "Вы уверены, что хотите удалить этот расход?",
            onConfirm = { onDeleteExpense(id); deleteExpenseId = null },
            onDismiss = { deleteExpenseId = null },
            isDestructive = true
        )
    }
}

@Composable
private fun ExpenseGroupCard(
    group: GroupedExpense,
    onDeleteExpense: (Int) -> Unit,
    onUpdateExpense: (Int, Double) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (group.isOverLimit)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (group.limit > 0) {
                        Text(
                            text = "${formatCurrency(group.total)} / ${formatCurrency(group.limit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (group.isOverLimit) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = formatCurrency(group.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (group.isOverLimit) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (group.limit > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                ProgressBar(
                    progress = (group.total / group.limit).toFloat(),
                    color = if (group.isOverLimit) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    height = 3
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    group.items.forEach { expense ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatCurrency(expense.amount),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (!expense.comment.isNullOrBlank()) {
                                    Text(
                                        text = expense.comment,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatDate(expense.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { onDeleteExpense(expense.id) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("✕", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (expense != group.items.last()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = source, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.semanticColors.incomeColor
            )
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("✕")
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
    onAddIncomeSource: (String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0) }
    var selectedSource by remember { mutableStateOf(incomeSources.firstOrNull()?.name ?: "") }
    var customSource by remember { mutableStateOf("") }
    var useCustomSource by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isExpense) "Добавить расход" else "Добавить доход") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isExpense) {
                    // Category selector
                    Text("Категория", style = MaterialTheme.typography.labelMedium)
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategoryId = cat.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id }
                            )
                            Text(cat.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                } else {
                    // Source selector
                    Text("Источник", style = MaterialTheme.typography.labelMedium)
                    incomeSources.forEach { src ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSource = src.name; useCustomSource = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !useCustomSource && selectedSource == src.name,
                                onClick = { selectedSource = src.name; useCustomSource = false }
                            )
                            Text(src.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useCustomSource = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useCustomSource,
                            onClick = { useCustomSource = true }
                        )
                        Text("Другой:", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (useCustomSource) {
                        AppTextField(
                            value = customSource,
                            onValueChange = { customSource = it },
                            label = "Новый источник"
                        )
                    }
                }

                AppTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Сумма",
                    keyboardType = KeyboardType.Decimal
                )

                if (isExpense) {
                    AppTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = "Комментарий (необязательно)"
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
                        val src = if (useCustomSource) {
                            if (customSource.isNotBlank()) {
                                onAddIncomeSource(customSource)
                            }
                            customSource
                        } else selectedSource
                        if (src.isNotBlank()) onAddIncome(src, amountVal)
                    }
                },
                enabled = amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun BudgetDialog(
    categories: List<ru.homebudget.finkeeper.data.model.Category>,
    budgets: List<ru.homebudget.finkeeper.data.model.Budget>,
    onDismiss: () -> Unit,
    onSetBudget: (Int, Double) -> Unit
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
        title = { Text("Настройка бюджета") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    AppTextField(
                        value = budgetValues[cat.id] ?: "",
                        onValueChange = { budgetValues[cat.id] = it },
                        label = cat.name,
                        keyboardType = KeyboardType.Decimal
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
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

package ru.homebudget.finkeeper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import ru.homebudget.finkeeper.ui.components.PlannedSectionCard
import ru.homebudget.finkeeper.ui.components.ScreenHeader
import ru.homebudget.finkeeper.ui.components.ServerUnreachableBanner
import ru.homebudget.finkeeper.ui.viewmodel.PlannedUiItem
import ru.homebudget.finkeeper.ui.components.SummaryCard
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.GroupedExpense
import ru.homebudget.finkeeper.ui.viewmodel.MonthViewState
import ru.homebudget.finkeeper.util.evalAmount
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.isAmountExpression
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
    onUpdateIncome: (Int, Double, String?) -> Unit,
    onUpdateExpense: (Int, Double, String?) -> Unit,
    onDeleteIncome: (Int) -> Unit,
    onDeleteExpense: (Int) -> Unit,
    onSetBudget: (Int, Double) -> Unit,
    onAddIncomeSource: (String) -> Unit,
    onConfirmAddIncomeSource: () -> Unit,
    onCancelAddIncomeSource: () -> Unit,
    onReorderExpenseGroups: (List<GroupedExpense>) -> Unit,
    onRefresh: () -> Unit,
    onConfirmPlanned: (PlannedUiItem, Double?) -> Unit = { _, _ -> },
    onSkipPlanned: (PlannedUiItem, Boolean) -> Unit = { _, _ -> },
    onOverridePlanned: (PlannedUiItem, Double, Int) -> Unit = { _, _, _ -> },
    onResetPlanned: (PlannedUiItem) -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddExpenseForCategory by remember { mutableStateOf<Int?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var deleteIncomeId by remember { mutableStateOf<Int?>(null) }
    var autoAppliedInfo by remember { mutableStateOf<String?>(null) }
    var deleteExpenseId by remember { mutableStateOf<Int?>(null) }
    var editingEntry by remember { mutableStateOf<EditingEntry?>(null) }
    var confirmPlannedItem by remember { mutableStateOf<PlannedUiItem?>(null) }
    var overridePlannedItem by remember { mutableStateOf<PlannedUiItem?>(null) }

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

        if (state.isOffline) {
            ServerUnreachableBanner(
                onRetry = onRefresh,
                isRetrying = state.isSyncing,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

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
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryCard(
                title = Strings.INCOMES_TAB,
                value = formatCurrency(state.totalIncome),
                backgroundColor = semantic.incomeCardBg,
                contentColor = semantic.incomeColor,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                subtitle = if (state.plannedIncomesTotal > 0) {
                    "🕐 " + Strings.PLANNED_AWAITING_INCOME.replace("%s", formatCurrency(state.plannedIncomesTotal))
                } else null,
            )
            SummaryCard(
                title = Strings.EXPENSES_TAB,
                value = formatCurrency(state.totalExpense),
                backgroundColor = semantic.expenseCardBg,
                contentColor = semantic.expenseColor,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                subtitle = if (state.plannedExpensesTotal > 0) {
                    "🕐 " + Strings.PLANNED_BY_PLAN.replace("%s", formatCurrency(state.plannedExpensesTotal))
                } else null,
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
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Strings.SPENDING_LIMIT, style = MaterialTheme.typography.titleSmall, color = semantic.warningColor)
                            LimitInfoButton(
                                expenses = state.totalExpense,
                                allExpenses = state.totalAllExpenses,
                                accentColor = semantic.warningColor,
                            )
                        }
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

                    if (state.plannedExpenses.isNotEmpty()) {
                        item(key = "planned-expenses") {
                            PlannedSectionCard(
                                title = Strings.PLANNED_EXPENSES_SECTION,
                                items = state.plannedExpenses,
                                total = state.plannedExpensesTotal,
                                isIncome = false,
                                isOffline = state.isOffline,
                                onConfirm = { confirmPlannedItem = it },
                                onSkip = onSkipPlanned,
                                onOverride = { overridePlannedItem = it },
                                onReset = onResetPlanned,
                            )
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
                                    onEditExpense = { id, amount, comment -> editingEntry = EditingEntry(id, amount, comment, isIncome = false) },
                                    onAddExpenseInCategory = { showAddExpenseForCategory = group.categoryId },
                                    autoAppliedIds = state.autoAppliedExpenseIds,
                                    onAutoAppliedClick = {
                                        autoAppliedInfo = Strings.AUTO_APPLIED_TEXT_EXPENSE.replace("%s", group.categoryName)
                                    },
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

                    if (state.plannedIncomes.isNotEmpty()) {
                        item(key = "planned-incomes") {
                            PlannedSectionCard(
                                title = Strings.PLANNED_INCOMES_SECTION,
                                items = state.plannedIncomes,
                                total = state.plannedIncomesTotal,
                                isIncome = true,
                                isOffline = state.isOffline,
                                onConfirm = { confirmPlannedItem = it },
                                onSkip = onSkipPlanned,
                                onOverride = { overridePlannedItem = it },
                                onReset = onResetPlanned,
                            )
                        }
                    }

                    if (state.incomesWithSources.isEmpty()) {
                        item { EmptyState(Strings.NO_INCOMES_THIS_MONTH) }
                    } else {
                        items(state.incomesWithSources, key = { it.id }) { income ->
                            val isAutoApplied = income.id in state.autoAppliedIncomeIds
                            val showAutoInfo = { autoAppliedInfo = Strings.AUTO_APPLIED_TEXT_INCOME.replace("%s", income.sourceName) }
                            IncomeItemCard(
                                source = income.sourceName,
                                amount = formatCurrency(income.amount),
                                date = formatDate(income.date),
                                isAutoApplied = isAutoApplied,
                                onEdit = {
                                    if (isAutoApplied) showAutoInfo()
                                    else editingEntry = EditingEntry(income.id, income.amount, null, isIncome = true)
                                },
                                onDelete = { if (isAutoApplied) showAutoInfo() else deleteIncomeId = income.id },
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

    // Диалог редактирования записи (расход/доход): сумма + комментарий
    editingEntry?.let { entry ->
        EditEntryDialog(
            isIncome = entry.isIncome,
            currentAmount = entry.amount,
            currentComment = entry.comment,
            onDismiss = { editingEntry = null },
            onSave = { newAmount, newComment ->
                if (entry.isIncome) onUpdateIncome(entry.id, newAmount, newComment)
                else onUpdateExpense(entry.id, newAmount, newComment)
                editingEntry = null
            },
        )
    }

    // Подтверждение оплаты/получения планового платежа
    confirmPlannedItem?.let { item ->
        ConfirmPlannedDialog(
            item = item,
            onDismiss = { confirmPlannedItem = null },
            onConfirm = { amount ->
                onConfirmPlanned(item, amount)
                confirmPlannedItem = null
            },
        )
    }

    // Изменение планового платежа на этот месяц (override)
    overridePlannedItem?.let { item ->
        OverridePlannedDialog(
            item = item,
            onDismiss = { overridePlannedItem = null },
            onSave = { amount, day ->
                onOverridePlanned(item, amount, day)
                overridePlannedItem = null
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

    autoAppliedInfo?.let { text ->
        AlertDialog(
            onDismissRequest = { autoAppliedInfo = null },
            title = { Text(Strings.AUTO_APPLIED_TITLE) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { autoAppliedInfo = null }) { Text(Strings.AUTO_APPLIED_OK) }
            },
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
    onEditExpense: (Int, Double, String?) -> Unit,
    onAddExpenseInCategory: () -> Unit,
    autoAppliedIds: Set<Int>,
    onAutoAppliedClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val semantic = AppTheme.semanticColors
    val baseColor = if (group.isOverLimit) semantic.expenseCardBg else MaterialTheme.colorScheme.surface
    val accentColor = if (group.isOverLimit) semantic.expenseColor else MaterialTheme.colorScheme.primary

    // Поворот шеврона — анимация через transform (дёшево), вместо смены глифа.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "chevronRotation",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Заголовок — «стеклянная» карточка фикс. высоты. Дорогой градиент/обводка НЕ
        // перерисовываются по растущей площади при раскрытии (строки вынесены ниже отдельным блоком).
        GlassyCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            shape = if (expanded) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp) else RoundedCornerShape(12.dp),
            baseColor = baseColor,
            highlightColor = accentColor.copy(alpha = 0.1f),
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
                        text = Strings.EXPAND_ICON,
                        modifier = Modifier.padding(start = 8.dp).rotate(chevronRotation),
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
            }
        }

        // Раскрывающийся блок строк — сплошной фон (без дорогого градиента), лёгкие строки.
        // Анимация: fade (opacity) + expand/shrink с предсказуемым tween — плавно и не медленно.
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(160)) +
                expandVertically(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
                fadeOut(animationSpec = tween(140)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(baseColor)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
            ) {
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
                    val isAutoApplied = expense.id in autoAppliedIds
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
                                color = MaterialTheme.colorScheme.onSurface,
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
                            if (isAutoApplied) {
                                Text(
                                    text = Strings.AUTO_APPLIED_LABEL,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        // Лёгкие кнопки-иконки вместо Material TextButton — дешевле компоновка/измерение.
                        // У записи, отмеченной автоматически, строки в БД ещё нет — вместо правки пояснение
                        RowGlyphButton(glyph = Strings.EDIT_ICON, tint = MaterialTheme.colorScheme.primary) {
                            if (isAutoApplied) onAutoAppliedClick() else onEditExpense(expense.id, expense.amount, expense.comment)
                        }
                        RowGlyphButton(glyph = Strings.DELETE_ICON, tint = MaterialTheme.colorScheme.error) {
                            if (isAutoApplied) onAutoAppliedClick() else onDeleteExpense(expense.id)
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

/** Лёгкая «кнопка-иконка» из глифа: без оверхеда Material TextButton (ripple/min-target/colors). */
@Composable
private fun RowGlyphButton(
    glyph: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun IncomeItemCard(
    source: String,
    amount: String,
    date: String,
    isAutoApplied: Boolean,
    onEdit: () -> Unit,
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
                if (isAutoApplied) {
                    Text(
                        text = Strings.AUTO_APPLIED_LABEL,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.semanticColors.incomeColor,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(Strings.EDIT_ICON, style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(Strings.DELETE_ICON, style = MaterialTheme.typography.bodyLarge)
                }
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

    // Сумма может быть арифметическим выражением (=523+1275+104). null — выражение невалидно.
    val parsedAmount: Double? = evalAmount(amount)
    val submit: () -> Unit = submit@{
        val amountVal = parsedAmount ?: return@submit
        if (amountVal <= 0) return@submit
        if (isExpense) {
            onAddExpense(selectedCategoryId, amountVal, comment.ifBlank { null })
        } else {
            val src = if (useCustomSource) customSource else selectedSource
            if (src.isNotBlank()) onAddIncome(src, amountVal)
        }
    }

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
                            items(incomeSources, key = { it.id }) { src ->
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

                AmountInputWithExpression(
                    amount = amount,
                    onAmountChange = { amount = it },
                    parsed = parsedAmount,
                    onSubmit = submit,
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
                onClick = submit,
                enabled = parsedAmount != null && parsedAmount > 0,
            ) {
                Text(Strings.ADD)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

/**
 * Поле ввода суммы с поддержкой арифметических выражений (Excel-стиль):
 * на мобиле — ряд операторов НАД полем (клавиатура не перекрывает), под полем — live-превью.
 * На десктопе ряд операторов не нужен (есть физическая клавиатура), остаётся только превью под полем.
 * Внутренний отступ 6dp (вдвое меньше межблочного 12dp).
 */
@Composable
private fun AmountInputWithExpression(
    amount: String,
    onAmountChange: (String) -> Unit,
    parsed: Double?,
    onSubmit: () -> Unit,
) {
    // Поле работает на TextFieldValue, чтобы управлять позицией курсора: при вставке
    // оператора кнопкой он добавляется в позицию курсора, а курсор сдвигается за него.
    var fieldValue by remember { mutableStateOf(TextFieldValue(amount, TextRange(amount.length))) }
    // Синхронизация при внешнем изменении amount (например, программный сброс формы).
    if (fieldValue.text != amount) {
        fieldValue = TextFieldValue(amount, TextRange(amount.length))
    }

    fun applyText(newText: String, cursor: Int) {
        fieldValue = TextFieldValue(newText, TextRange(cursor))
        onAmountChange(newText)
    }

    fun insert(op: String) {
        val sel = fieldValue.selection
        val text = fieldValue.text
        val newText = text.substring(0, sel.start) + op + text.substring(sel.end)
        applyText(newText, sel.start + op.length)
    }

    fun backspace() {
        val sel = fieldValue.selection
        val text = fieldValue.text
        when {
            sel.start != sel.end -> applyText(text.substring(0, sel.start) + text.substring(sel.end), sel.start)
            sel.start > 0 -> applyText(text.substring(0, sel.start - 1) + text.substring(sel.start), sel.start - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!isDesktop) {
            AmountOperatorRow(
                onInsert = { insert(it) },
                onBackspace = { backspace() },
            )
        }
        AppTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onAmountChange(it.text)
            },
            label = Strings.AMOUNT,
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        AmountExpressionPreview(amount = amount, parsed = parsed)
    }
}

/**
 * Ряд кнопок-операторов для ввода выражений на мобильной клавиатуре (нет +, −, ×, ÷ на Decimal).
 */
@Composable
private fun AmountOperatorRow(
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val operators = listOf(
        "+" to "+",
        "−" to "-",
        "×" to "*",
        "÷" to "/",
        "(" to "(",
        ")" to ")",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        operators.forEach { (label, value) ->
            OperatorChip(label = label, modifier = Modifier.weight(1f)) { onInsert(value) }
        }
        OperatorChip(label = "⌫", modifier = Modifier.weight(1f), onClick = onBackspace)
    }
}

/**
 * Live-превью вычисленного результата выражения (Excel-стиль). Показывается, только если
 * ввод похож на выражение.
 */
@Composable
private fun AmountExpressionPreview(
    amount: String,
    parsed: Double?,
) {
    if (!isAmountExpression(amount)) return

    if (parsed != null && parsed > 0) {
        Text(
            text = "= ${formatCurrency(parsed)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Text(
            text = Strings.AMOUNT_EXPRESSION_INVALID,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun OperatorChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(40.dp).clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// Значок «i» на плашке лимита: по нажатию — пузырь с пояснением, что пополнения копилок
// тоже расходуют лимит месяца (они приходят скрытыми расходами «Пополнение копилки»)
@Composable
private fun LimitInfoButton(
    expenses: Double,
    allExpenses: Double,
    accentColor: Color,
) {
    var showInfo by remember { mutableStateOf(false) }
    val marginPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val positionProvider = remember(marginPx) { BelowAnchorClampedPositionProvider(marginPx) }

    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { showInfo = !showInfo },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = Strings.LIMIT_INFO_TITLE,
                tint = accentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
        if (showInfo) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { showInfo = false },
                properties = PopupProperties(focusable = true),
            ) {
                LimitInfoContent(expenses = expenses, allExpenses = allExpenses, accentColor = accentColor)
            }
        }
    }
}

@Composable
private fun LimitInfoContent(
    expenses: Double,
    allExpenses: Double,
    accentColor: Color,
) {
    val savings = allExpenses - expenses
    Surface(
        modifier = Modifier.widthIn(max = 300.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(Strings.LIMIT_INFO_TITLE, style = MaterialTheme.typography.titleSmall, color = accentColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Strings.LIMIT_INFO_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (savings > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = accentColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                LimitInfoRow(Strings.LIMIT_INFO_EXPENSES, expenses)
                LimitInfoRow(Strings.LIMIT_INFO_SAVINGS, savings)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = accentColor.copy(alpha = 0.2f),
                )
                LimitInfoRow(Strings.LIMIT_INFO_USED, allExpenses, emphasized = true)
            }
        }
    }
}

@Composable
private fun LimitInfoRow(label: String, amount: Double, emphasized: Boolean = false) {
    val color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = weight)
        Spacer(modifier = Modifier.size(12.dp))
        Text(formatCurrency(amount), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = weight)
    }
}

// Пузырь под значком; по горизонтали прижимается к краям окна, чтобы не уехать
// за экран на узком телефоне; если снизу не влезает — открывается над значком
private class BelowAnchorClampedPositionProvider(private val marginPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = anchorBounds.left.coerceIn(marginPx, maxX)
        val fitsBelow = anchorBounds.bottom + popupContentSize.height <= windowSize.height - marginPx
        val y = if (fitsBelow) {
            anchorBounds.bottom
        } else {
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
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
            val semantic = AppTheme.semanticColors
            // Сумма всех введённых лимитов — обновляется на лету при вводе.
            val totalLimit = budgetValues.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
            Column(
                modifier = if (isDesktop) Modifier.padding(vertical = 32.dp) else Modifier,
            ) {
                // Зафиксированная сверху плашка «Общий лимит на месяц» — не уезжает при прокрутке.
                GlassyCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    baseColor = semantic.savingsCardBg,
                    highlightColor = semantic.savingsColor.copy(alpha = 0.2f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = Strings.TOTAL_MONTH_LIMIT,
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.savingsColor,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatCurrency(totalLimit),
                            style = MaterialTheme.typography.titleLarge,
                            color = semantic.savingsColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Скроллится только список полей ввода лимитов по категориям.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
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

    val parsedAmount: Double? = evalAmount(amount)
    val submit: () -> Unit = submit@{
        val amountVal = parsedAmount ?: return@submit
        if (amountVal <= 0) return@submit
        onAdd(amountVal, comment.ifBlank { null })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Strings.ADD_EXPENSE} в \"$categoryName\"") },
        text = {
            Column(
                modifier = Modifier.then(if (isDesktop) Modifier.padding(vertical = 32.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AmountInputWithExpression(
                    amount = amount,
                    onAmountChange = { amount = it },
                    parsed = parsedAmount,
                    onSubmit = submit,
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
                onClick = submit,
                enabled = parsedAmount != null && parsedAmount > 0,
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
private fun EditEntryDialog(
    isIncome: Boolean,
    currentAmount: Double,
    currentComment: String?,
    onDismiss: () -> Unit,
    onSave: (Double, String?) -> Unit,
) {
    var amount by remember { mutableStateOf(currentAmount.toLong().toString()) }
    var comment by remember { mutableStateOf(currentComment ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isIncome) Strings.EDIT_INCOME else Strings.EDIT_EXPENSE) },
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
                // Комментарий — только у расходов (у доходов на сервере его негде хранить)
                if (!isIncome) {
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
                    val amountVal = evalAmount(amount) ?: return@TextButton
                    onSave(amountVal, if (isIncome) null else comment.ifBlank { null })
                },
                enabled = evalAmount(amount)?.let { it > 0 } == true,
            ) {
                Text(Strings.SAVE)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

/** Состояние редактируемой записи месяца (расход или доход) */
private data class EditingEntry(
    val id: Int,
    val amount: Double,
    val comment: String?,
    val isIncome: Boolean,
)


@Composable
private fun ConfirmPlannedDialog(
    item: PlannedUiItem,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit,
) {
    val isIncome = item.templateType == "income_source"
    var amount by remember { mutableStateOf(item.amount.toLong().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isIncome) Strings.PLANNED_CONFIRM_TITLE_INCOME else Strings.PLANNED_CONFIRM_TITLE_EXPENSE) },
        text = {
            Column(
                modifier = Modifier.then(if (isDesktop) Modifier.padding(vertical = 16.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = (if (isIncome) Strings.PLANNED_CONFIRM_TEXT_INCOME else Strings.PLANNED_CONFIRM_TEXT_EXPENSE)
                        .replace("%s", item.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
                    val amountVal = evalAmount(amount) ?: return@TextButton
                    onConfirm(if (amountVal == item.amount) null else amountVal)
                },
                enabled = (evalAmount(amount) ?: 0.0) > 0,
            ) {
                Text(if (isIncome) Strings.PLANNED_RECEIVE_BUTTON else Strings.PLANNED_PAY_BUTTON)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

@Composable
private fun OverridePlannedDialog(
    item: PlannedUiItem,
    onDismiss: () -> Unit,
    onSave: (Double, Int) -> Unit,
) {
    var amount by remember { mutableStateOf(item.amount.toLong().toString()) }
    var day by remember { mutableStateOf(item.dueDay.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.PLANNED_OVERRIDE_TITLE) },
        text = {
            Column(
                modifier = Modifier.then(if (isDesktop) Modifier.padding(vertical = 16.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${item.name}: ${Strings.PLANNED_OVERRIDE_HINT}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = Strings.AMOUNT + " (" + Strings.PLANNED_OVERRIDE_ORIGINAL.replace("%s", formatCurrency(item.originalAmount)) + ")",
                    keyboardType = KeyboardType.Decimal,
                )
                AppTextField(
                    value = day,
                    onValueChange = { day = it },
                    label = Strings.AUTO_DAY,
                    keyboardType = KeyboardType.Number,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountVal = evalAmount(amount) ?: return@TextButton
                    val dayVal = day.toIntOrNull() ?: return@TextButton
                    onSave(amountVal, dayVal)
                },
                enabled = (evalAmount(amount) ?: 0.0) > 0 && (day.toIntOrNull() ?: 0) in 1..31,
            ) {
                Text(Strings.SAVE)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

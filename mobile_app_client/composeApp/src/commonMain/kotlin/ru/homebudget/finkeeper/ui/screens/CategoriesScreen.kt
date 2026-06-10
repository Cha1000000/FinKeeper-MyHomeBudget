package ru.homebudget.finkeeper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.components.AppButton
import ru.homebudget.finkeeper.ui.components.DesktopAddButton
import ru.homebudget.finkeeper.ui.components.GlassyButtonStyle
import ru.homebudget.finkeeper.ui.components.AppTextField
import ru.homebudget.finkeeper.ui.components.ConfirmDialog
import ru.homebudget.finkeeper.ui.components.EmptyState
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.components.LoadingScreen
import ru.homebudget.finkeeper.ui.components.ScreenHeader
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.viewmodel.CategoriesState
import ru.homebudget.finkeeper.data.model.Category
import ru.homebudget.finkeeper.data.model.IncomeSource
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.isDesktop
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CategoriesScreen(
    state: CategoriesState,
    onSetActiveTab: (Int) -> Unit,
    onAddCategory: (String) -> Unit,
    onUpdateCategory: (Int, String) -> Unit,
    onDeactivateCategory: (Int) -> Unit,
    onAddIncomeSource: (String) -> Unit,
    onUpdateIncomeSource: (Int, String) -> Unit,
    onDeactivateIncomeSource: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleReorderMode: () -> Unit,
    onToggleIncomeSourceReorderMode: () -> Unit,
    onUpdateCategoriesOrder: (List<Category>) -> Unit,
    onUpdateIncomeSourcesOrder: (List<IncomeSource>) -> Unit,
    onReorderCategories: (List<Category>) -> Unit,
    onReorderIncomeSources: (List<IncomeSource>) -> Unit,
    // Фиксированные категории
    onAddFixedCategory: (String, Double, Int) -> Unit,
    onUpdateFixedCategory: (Int, String?, Double?, Int?) -> Unit,
    onDeactivateFixedCategory: (Int) -> Unit,
    // Фиксированные источники дохода
    onAddFixedIncomeSource: (String, Double, Int) -> Unit,
    onUpdateFixedIncomeSource: (Int, String?, Double?, Int?) -> Unit,
    onDeactivateFixedIncomeSource: (Int) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Int?>(null) }

    // Состояние для фиксированных элементов
    var showAddFixedDialog by remember { mutableStateOf(false) }
    var editingFixed by remember { mutableStateOf<EditingFixedItem?>(null) }
    var deleteFixedId by remember { mutableStateOf<Int?>(null) }
    
    // localCategories используется ТОЛЬКО во время режима сортировки для drag-and-drop
    var localCategories by remember { mutableStateOf(state.categories.filter { it.isActive == 1 }) }
    var localIncomeSources by remember { mutableStateOf(state.incomeSources.filter { it.isActive == 1 }) }
    
    // При входе в режим сортировки — копируем текущее состояние
    LaunchedEffect(state.isReorderMode) {
        if (state.isReorderMode) {
            localCategories = state.categories.filter { it.isActive == 1 }
        }
    }
    
    LaunchedEffect(state.isIncomeSourceReorderMode) {
        if (state.isIncomeSourceReorderMode) {
            localIncomeSources = state.incomeSources.filter { it.isActive == 1 }
        }
    }

    LaunchedEffect(Unit) { onRefresh() }

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
        if (isDesktop) Modifier.padding(top = 16.dp) else Modifier
    )) {
        ScreenHeader(title = Strings.REFERENCE_BOOKS)

        TabRow(
            selectedTabIndex = state.activeTab,
            modifier = Modifier.padding(horizontal = 16.dp),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = state.activeTab == 0, onClick = { onSetActiveTab(0) }) {
                Text(Strings.CATEGORIES_TAB, modifier = Modifier.padding(12.dp))
            }
            Tab(selected = state.activeTab == 1, onClick = { onSetActiveTab(1) }) {
                Text(Strings.INCOME_SOURCES_TAB, modifier = Modifier.padding(12.dp))
            }
        }

        val lazyListState = rememberLazyListState()
        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = from.index - 2
            val toIndex = to.index - 2
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < localCategories.size && toIndex < localCategories.size) {
                localCategories = localCategories.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        }

        val incomeListState = rememberLazyListState()
        val reorderableIncomeListState = rememberReorderableLazyListState(incomeListState) { from, to ->
            val fromIndex = from.index - 2
            val toIndex = to.index - 2
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < localIncomeSources.size && toIndex < localIncomeSources.size) {
                localIncomeSources = localIncomeSources.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        }

        val activeListState = if (state.activeTab == 0) lazyListState else incomeListState

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                state = activeListState,
                contentPadding = PaddingValues(top = 8.dp, bottom = listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- Секция "Фиксированные": выделенный блок над кнопкой сортировки ---
                item {
                    if (state.activeTab == 0) {
                        FixedSection(
                            title = Strings.FIXED_SECTION,
                            isEmpty = state.fixedCategories.isEmpty(),
                            emptyText = Strings.NO_FIXED_CATEGORIES,
                            onAdd = { showAddFixedDialog = true },
                        ) {
                            state.fixedCategories.forEach { cat ->
                                FixedItemCard(
                                    name = cat.name,
                                    amount = cat.fixedAmount ?: 0.0,
                                    autoDay = cat.autoDay ?: 1,
                                    onEdit = {
                                        editingFixed = EditingFixedItem(
                                            id = cat.id,
                                            name = cat.name,
                                            amount = cat.fixedAmount ?: 0.0,
                                            autoDay = cat.autoDay ?: 1,
                                        )
                                    },
                                    onDelete = { deleteFixedId = cat.id },
                                )
                            }
                        }
                    } else {
                        FixedSection(
                            title = Strings.FIXED_SECTION,
                            isEmpty = state.fixedIncomeSources.isEmpty(),
                            emptyText = Strings.NO_FIXED_INCOME_SOURCES,
                            onAdd = { showAddFixedDialog = true },
                        ) {
                            state.fixedIncomeSources.forEach { src ->
                                FixedItemCard(
                                    name = src.name,
                                    amount = src.fixedAmount ?: 0.0,
                                    autoDay = src.autoDay ?: 1,
                                    onEdit = {
                                        editingFixed = EditingFixedItem(
                                            id = src.id,
                                            name = src.name,
                                            amount = src.fixedAmount ?: 0.0,
                                            autoDay = src.autoDay ?: 1,
                                        )
                                    },
                                    onDelete = { deleteFixedId = src.id },
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.activeTab == 0) {
                            TextButton(onClick = {
                                if (state.isReorderMode) {
                                    onReorderCategories(localCategories)
                                }
                                onToggleReorderMode()
                            }) {
                                Text(
                                    if (state.isReorderMode) Strings.DONE else Strings.REORDER_MODE,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            TextButton(onClick = {
                                if (state.isIncomeSourceReorderMode) {
                                    onReorderIncomeSources(localIncomeSources)
                                }
                                onToggleIncomeSourceReorderMode()
                            }) {
                                Text(
                                    if (state.isIncomeSourceReorderMode) Strings.DONE else Strings.REORDER_MODE,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (!useFloatingAddButton) {
                            DesktopAddButton(onClick = { showAddDialog = true })
                        }
                    }
                }

                if (state.activeTab == 0) {
                    // --- Обычные категории ---
                    val activeCategories = state.categories.filter { it.isActive == 1 }
                    if (activeCategories.isEmpty()) {
                        item { EmptyState(Strings.NO_CATEGORIES) }
                    } else {
                        if (state.isReorderMode) {
                            itemsIndexed(localCategories, key = { _, cat -> cat.id }) { _, cat ->
                                ReorderableItem(reorderableLazyListState, key = cat.id) { isDragging ->
                                    val elevation = if (isDragging) 8.dp else 0.dp
                                    ReorderableCategoryItem(
                                        name = cat.name,
                                        modifier = Modifier.longPressDraggableHandle(),
                                        elevation = elevation
                                    )
                                }
                            }
                        } else {
                            items(activeCategories, key = { it.id }) { cat ->
                                EditableItemCard(
                                    name = cat.name,
                                    isEditing = editingId == cat.id,
                                    editingName = editingName,
                                    onEditingNameChange = { editingName = it },
                                    onStartEdit = { editingId = cat.id; editingName = cat.name },
                                    onSaveEdit = {
                                        onUpdateCategory(cat.id, editingName)
                                        editingId = null
                                    },
                                    onCancelEdit = { editingId = null },
                                    onDelete = { deleteId = cat.id }
                                )
                            }
                        }
                    }
                } else {
                    // --- Обычные источники дохода ---
                    val activeIncomeSources = state.incomeSources.filter { it.isActive == 1 }
                    if (activeIncomeSources.isEmpty()) {
                        item { EmptyState(Strings.NO_INCOME_SOURCES) }
                    } else {
                        if (state.isIncomeSourceReorderMode) {
                            itemsIndexed(localIncomeSources, key = { _, src -> src.id }) { _, src ->
                                ReorderableItem(reorderableIncomeListState, key = src.id) { isDragging ->
                                    val elevation = if (isDragging) 8.dp else 0.dp
                                    ReorderableCategoryItem(
                                        name = src.name,
                                        modifier = Modifier.longPressDraggableHandle(),
                                        elevation = elevation
                                    )
                                }
                            }
                        } else {
                            items(activeIncomeSources, key = { it.id }) { src ->
                                EditableItemCard(
                                    name = src.name,
                                    isEditing = editingId == src.id,
                                    editingName = editingName,
                                    onEditingNameChange = { editingName = it },
                                    onStartEdit = { editingId = src.id; editingName = src.name },
                                    onSaveEdit = {
                                        onUpdateIncomeSource(src.id, editingName)
                                        editingId = null
                                    },
                                    onCancelEdit = { editingId = null },
                                    onDelete = { deleteId = src.id }
                                )
                            }
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

    if (showAddDialog) {
        AddNameDialog(
            title = if (state.activeTab == 0) Strings.NEW_CATEGORY else Strings.NEW_INCOME_SOURCE,
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                if (state.activeTab == 0) onAddCategory(name) else onAddIncomeSource(name)
                showAddDialog = false
            }
        )
    }

    deleteId?.let { id ->
        ConfirmDialog(
            title = Strings.DELETE,
            message = Strings.DELETE_CONFIRMATION,
            onConfirm = {
                if (state.activeTab == 0) onDeactivateCategory(id) else onDeactivateIncomeSource(id)
                deleteId = null
            },
            onDismiss = { deleteId = null },
            isDestructive = true
        )
    }

    // --- Диалоги для фиксированных элементов ---
    if (showAddFixedDialog) {
        AddFixedDialog(
            title = if (state.activeTab == 0) Strings.NEW_FIXED_CATEGORY else Strings.NEW_FIXED_INCOME_SOURCE,
            onDismiss = { showAddFixedDialog = false },
            onConfirm = { name, amount, day ->
                if (state.activeTab == 0) {
                    onAddFixedCategory(name, amount, day)
                } else {
                    onAddFixedIncomeSource(name, amount, day)
                }
                showAddFixedDialog = false
            },
        )
    }

    editingFixed?.let { item ->
        AddFixedDialog(
            title = if (state.activeTab == 0) Strings.EDIT_FIXED_CATEGORY else Strings.EDIT_FIXED_INCOME_SOURCE,
            initialName = item.name,
            initialAmount = item.amount,
            initialDay = item.autoDay,
            onDismiss = { editingFixed = null },
            onConfirm = { name, amount, day ->
                if (state.activeTab == 0) {
                    onUpdateFixedCategory(item.id, name, amount, day)
                } else {
                    onUpdateFixedIncomeSource(item.id, name, amount, day)
                }
                editingFixed = null
            },
        )
    }

    deleteFixedId?.let { id ->
        ConfirmDialog(
            title = Strings.DELETE,
            message = Strings.FIXED_DELETE_CONFIRMATION,
            onConfirm = {
                if (state.activeTab == 0) onDeactivateFixedCategory(id) else onDeactivateFixedIncomeSource(id)
                deleteFixedId = null
            },
            onDismiss = { deleteFixedId = null },
            isDestructive = true,
        )
    }

    LaunchedEffect(!state.isReorderMode) {
        if (!state.isReorderMode && localCategories != state.categories) {
            onReorderCategories(localCategories)
        }
    }
}

@Composable
private fun EditableItemCard(
    name: String,
    isEditing: Boolean,
    editingName: String,
    onEditingNameChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassyCard(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        if (isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextField(
                    value = editingName,
                    onValueChange = onEditingNameChange,
                    label = Strings.NAME,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSaveEdit) { Text(Strings.CHECK, style = MaterialTheme.typography.bodyLarge) }
                TextButton(onClick = onCancelEdit) { Text(Strings.DELETE_ICON, style = MaterialTheme.typography.bodyLarge) }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    TextButton(onClick = onStartEdit) {
                        Text(Strings.EDIT_ICON, style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(Strings.DELETE_ICON, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddNameDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = Strings.NAME
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(Strings.ADD)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        }
    )
}

@Composable
private fun ReorderableCategoryItem(
    name: String,
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 0.dp
) {
    GlassyCard(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Strings.DRAG_HANDLE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// --- Фиксированные элементы ---

private data class EditingFixedItem(
    val id: Int,
    val name: String,
    val amount: Double,
    val autoDay: Int,
)

/**
 * Выделенный блок «Фиксированные»: рамка + тонированный фон, заголовок с иконкой,
 * кнопка добавления и содержимое (список фиксированных элементов или текст-заглушка).
 * Визуально отделяет фиксированные категории/источники от обычных, как в веб-версии.
 */
@Composable
private fun FixedSection(
    title: String,
    isEmpty: Boolean,
    emptyText: String,
    onAdd: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${Strings.FIXED_SECTION_ICON}  $title",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    Strings.ADD_FIXED,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (isEmpty) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        } else {
            content()
        }
    }
}

@Composable
private fun FixedItemCard(
    name: String,
    amount: Double,
    autoDay: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatCurrency(amount)}  ·  ${autoDay}${Strings.DAY_OF_MONTH_SUFFIX}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                TextButton(onClick = onEdit) {
                    Text(Strings.EDIT_ICON, style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(Strings.DELETE_ICON, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun AddFixedDialog(
    title: String,
    initialName: String = "",
    initialAmount: Double = 0.0,
    initialDay: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, day: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var amountText by remember {
        mutableStateOf(if (initialAmount > 0.0) initialAmount.toBigDecimal().stripTrailingZeros().toPlainString() else "")
    }
    var dayText by remember { mutableStateOf(if (initialDay > 0) initialDay.toString() else "") }

    val amount = amountText.toDoubleOrNull()
    val day = dayText.toIntOrNull()
    val isValid = name.isNotBlank() && amount != null && amount > 0 && day != null && day in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = Strings.NAME,
                )
                AppTextField(
                    value = amountText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                            amountText = newValue
                        }
                    },
                    label = Strings.FIXED_AMOUNT,
                )
                AppTextField(
                    value = dayText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || (newValue.all { it.isDigit() } && newValue.length <= 2)) {
                            dayText = newValue
                        }
                    },
                    label = "${Strings.AUTO_DAY} (1–31)",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) onConfirm(name, amount!!, day!!)
                },
                enabled = isValid,
            ) {
                Text(Strings.SAVE)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

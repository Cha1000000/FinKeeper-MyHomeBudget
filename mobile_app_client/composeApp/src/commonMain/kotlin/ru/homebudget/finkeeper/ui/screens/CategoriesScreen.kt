package ru.homebudget.finkeeper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.components.*
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.viewmodel.CategoriesState

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
    onRefresh: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = Strings.REFERENCE_BOOKS,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text(
                            Strings.ADD_NEW,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (state.activeTab == 0) {
                if (state.categories.isEmpty()) {
                    item { EmptyState(Strings.NO_CATEGORIES) }
                } else {
                    items(state.categories.filter { it.isActive == 1 }, key = { it.id }) { cat ->
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
            } else {
                if (state.incomeSources.isEmpty()) {
                    item { EmptyState(Strings.NO_INCOME_SOURCES) }
                } else {
                    items(state.incomeSources.filter { it.isActive == 1 }, key = { it.id }) { src ->
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
            title = Strings.DELETE_TITLE,
            message = Strings.DELETE_CONFIRMATION,
            onConfirm = {
                if (state.activeTab == 0) onDeactivateCategory(id) else onDeactivateIncomeSource(id)
                deleteId = null
            },
            onDismiss = { deleteId = null },
            isDestructive = true
        )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .neonGlow(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), radius = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                TextButton(onClick = onSaveEdit) { Text(Strings.CHECK) }
                TextButton(onClick = onCancelEdit) { Text(Strings.DELETE) }
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
                        Text(Strings.EDIT, style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(Strings.DELETE, style = MaterialTheme.typography.bodyLarge)
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


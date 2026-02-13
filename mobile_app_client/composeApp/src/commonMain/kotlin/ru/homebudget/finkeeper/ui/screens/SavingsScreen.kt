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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.data.model.SavingsGoal
import ru.homebudget.finkeeper.ui.components.*
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.SavingsState
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.util.formatCurrency

@Composable
fun SavingsScreen(
    state: SavingsState,
    onCreateGoal: (String, Double) -> Unit,
    onUpdateGoal: (Int, String?, Double?, Double?) -> Unit,
    onDeleteGoal: (Int) -> Unit,
    onAddTransaction: (Int, Double) -> Unit,
    onRefresh: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var transactionGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var isDeposit by remember { mutableStateOf(true) }
    var deleteGoalId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    val semantic = AppTheme.semanticColors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = Strings.PIGGY_BANKS,
                modifier = Modifier.padding(horizontal = 0.dp),
                actions = {
                    TextButton(onClick = { showCreateDialog = true }) {
                        Text(Strings.CREATE, style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        }

        if (state.goals.isEmpty()) {
            item { EmptyState(Strings.NO_PIGGY_BANKS) }
        } else {
            items(state.goals, key = { it.id }) { goal ->
                SavingsGoalCard(
                    goal = goal,
                    onEdit = { editingGoal = goal },
                    onDeposit = { transactionGoal = goal; isDeposit = true },
                    onWithdraw = { transactionGoal = goal; isDeposit = false }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateGoalDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, target ->
                onCreateGoal(name, target)
                showCreateDialog = false
            }
        )
    }

    editingGoal?.let { goal ->
        EditGoalDialog(
            goal = goal,
            onDismiss = { editingGoal = null },
            onSave = { name, target, current ->
                onUpdateGoal(goal.id, name, target, current)
                editingGoal = null
            },
            onDelete = {
                deleteGoalId = goal.id
                editingGoal = null
            }
        )
    }

    transactionGoal?.let { goal ->
        TransactionDialog(
            goalName = goal.name,
            isDeposit = isDeposit,
            onDismiss = { transactionGoal = null },
            onConfirm = { amount ->
                val finalAmount = if (isDeposit) amount else -amount
                onAddTransaction(goal.id, finalAmount)
                transactionGoal = null
            }
        )
    }

    deleteGoalId?.let { id ->
        ConfirmDialog(
            title = Strings.DELETE_PIGGY_BANK,
            message = Strings.DELETE_PIGGY_BANK_CONFIRM,
            onConfirm = { onDeleteGoal(id); deleteGoalId = null },
            onDismiss = { deleteGoalId = null },
            isDestructive = true
        )
    }
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoal,
    onEdit: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit
) {
    val semantic = AppTheme.semanticColors
    val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat() else 0f

    GlassyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = MaterialTheme.colorScheme.primary
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onEdit) {
                    Text(Strings.EDIT, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatCurrency(goal.currentAmount),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${Strings.FROM} ${formatCurrency(goal.targetAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ProgressBar(
                progress = progress,
                color = MaterialTheme.colorScheme.primary,
                height = 8
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassyButton(
                    onClick = onDeposit,
                    text = Strings.DEPOSIT,
                    modifier = Modifier.weight(1f),
                    color = semantic.incomeColor,
                    textColor = Color.White,
                    style = GlassyButtonStyle.Solid
                )
                GlassyButton(
                    onClick = onWithdraw,
                    text = Strings.WITHDRAW,
                    modifier = Modifier.weight(1f),
                    color = semantic.expenseColor,
                    textColor = Color.White,
                    style = GlassyButtonStyle.Glassy
                )
            }
        }
    }
}

@Composable
private fun CreateGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.NEW_PIGGY_BANK) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(value = name, onValueChange = { name = it }, label = "Название")
                AppTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = "Целевая сумма",
                    keyboardType = KeyboardType.Decimal
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = target.toDoubleOrNull() ?: return@TextButton
                    if (name.isNotBlank() && t > 0) onConfirm(name, t)
                },
                enabled = name.isNotBlank() && (target.toDoubleOrNull() ?: 0.0) > 0
            ) { Text(Strings.CREATE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun EditGoalDialog(
    goal: SavingsGoal,
    onDismiss: () -> Unit,
    onSave: (String?, Double?, Double?) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(goal.name) }
    var target by remember { mutableStateOf(goal.targetAmount.toLong().toString()) }
    var current by remember { mutableStateOf(goal.currentAmount.toLong().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.EDIT_PIGGY_BANK) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(value = name, onValueChange = { name = it }, label = Strings.NAME)
                AppTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = Strings.TARGET_AMOUNT,
                    keyboardType = KeyboardType.Decimal
                )
                AppTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = Strings.CURRENT_AMOUNT,
                    keyboardType = KeyboardType.Decimal
                )
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(Strings.DELETE_PIGGY_BANK)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    name.takeIf { it != goal.name },
                    target.toDoubleOrNull()?.takeIf { it != goal.targetAmount },
                    current.toDoubleOrNull()?.takeIf { it != goal.currentAmount }
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun TransactionDialog(
    goalName: String,
    isDeposit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDeposit) Strings.DEPOSIT else Strings.WITHDRAW) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${Strings.PIGGY_BANK_NAME}", style = MaterialTheme.typography.bodyMedium)
                AppTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = Strings.AMOUNT,
                    keyboardType = KeyboardType.Decimal
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = amount.toDoubleOrNull() ?: return@TextButton
                    if (a > 0) onConfirm(a)
                },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text(if (isDeposit) Strings.DEPOSIT else Strings.WITHDRAW) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

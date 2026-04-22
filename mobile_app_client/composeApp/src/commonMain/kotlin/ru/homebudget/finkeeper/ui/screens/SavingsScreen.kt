package ru.homebudget.finkeeper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.data.model.SavingsGoal
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.components.AppButton
import ru.homebudget.finkeeper.ui.components.AppTextField
import ru.homebudget.finkeeper.ui.components.ConfirmDialog
import ru.homebudget.finkeeper.ui.components.EmptyState
import ru.homebudget.finkeeper.ui.components.GlassyButton
import ru.homebudget.finkeeper.ui.components.GlassyButtonStyle
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.components.LoadingScreen
import ru.homebudget.finkeeper.ui.components.ProgressBar
import ru.homebudget.finkeeper.ui.components.ScreenHeader
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.SavingsState
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.isDesktop

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
    val sortedGoals = remember(state.goals) { 
        state.goals.sortedByDescending { it.currentAmount } 
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val columns = when {
            !isDesktop -> 1
            screenWidth < 800.dp -> 2
            screenWidth < 1200.dp -> 3
            else -> 4
        }
        val horizontalPadding = if (isDesktop) 24.dp else 16.dp
        val spacing = if (isDesktop) 24.dp else 16.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing)
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

            if (state.totalSavings > 0) {
                item {
                    GlassyCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        baseColor = MaterialTheme.colorScheme.primary,
                        highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Strings.TOTAL_SAVINGS,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = formatCurrency(state.totalSavings),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }

            if (sortedGoals.isEmpty()) {
                item { EmptyState(Strings.NO_PIGGY_BANKS) }
            } else {
                val chunked = sortedGoals.chunked(columns)
                items(chunked.size) { index ->
                    val rowItems = chunked[index]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        rowItems.forEach { goal ->
                            Box(modifier = Modifier.weight(1f)) {
                                SavingsGoalCard(
                                    goal = goal,
                                    onEdit = { editingGoal = goal },
                                    onDeposit = { transactionGoal = goal; isDeposit = true },
                                    onWithdraw = { transactionGoal = goal; isDeposit = false }
                                )
                            }
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
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
        highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
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
                    Text(Strings.EDIT, style = MaterialTheme.typography.bodyMedium)
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
                AppButton(
                    onClick = onDeposit,
                    text = Strings.DEPOSIT,
                    modifier = Modifier.weight(1f).height(48.dp),
                    containerColor = semantic.incomeColor,
                    contentColor = Color.White,
                    style = GlassyButtonStyle.Glassy
                )
                AppButton(
                    onClick = onWithdraw,
                    text = Strings.WITHDRAW,
                    modifier = Modifier.weight(1f).height(48.dp),
                    containerColor = semantic.expenseColor,
                    contentColor = Color.White,
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
                AppTextField(value = name, onValueChange = { name = it }, label = Strings.NAME)
                AppTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = Strings.TARGET_AMOUNT,
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } }
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
            }) { Text(Strings.SAVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } }
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
                Text(
                    text = Strings.PIGGY_BANK_NAME.replace("%1\$s", "\"$goalName\""),
                    style = MaterialTheme.typography.bodyMedium
                )
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } }
    )
}

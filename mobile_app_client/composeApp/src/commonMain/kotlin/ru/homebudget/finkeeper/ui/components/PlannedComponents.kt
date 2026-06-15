package ru.homebudget.finkeeper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.data.planned.TEMPLATE_TYPE_INCOME_SOURCE
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.viewmodel.PlannedUiItem
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.isDesktop

/**
 * Сворачиваемая секция плановых платежей/поступлений месяца.
 * Desktop: inline-кнопка подтверждения + меню [⋯]; mobile: те же действия,
 * но секция по умолчанию свёрнута при большом списке, touch-таргеты крупнее.
 */
@Composable
fun PlannedSectionCard(
    title: String,
    items: List<PlannedUiItem>,
    total: Double,
    isIncome: Boolean,
    isOffline: Boolean,
    onConfirm: (PlannedUiItem) -> Unit,
    onSkip: (PlannedUiItem, Boolean) -> Unit,
    onOverride: (PlannedUiItem) -> Unit,
    onReset: (PlannedUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    // На мобильном длинный план по умолчанию свёрнут, на desktop всегда раскрыт
    var expanded by rememberSaveable(title) { mutableStateOf(isDesktop || items.size <= 3) }
    val semantic = AppTheme.semanticColors

    GlassyCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (expanded) "▾" else "▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.sizeIn(minWidth = 8.dp))
                    Text(
                        text = "🕐 $title",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatCurrency(total),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                        PlannedItemRow(
                            item = item,
                            isIncome = isIncome,
                            isOffline = isOffline,
                            onConfirm = { onConfirm(item) },
                            onSkip = { skip -> onSkip(item, skip) },
                            onOverride = { onOverride(item) },
                            onReset = { onReset(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedItemRow(
    item: PlannedUiItem,
    isIncome: Boolean,
    isOffline: Boolean,
    onConfirm: () -> Unit,
    onSkip: (Boolean) -> Unit,
    onOverride: () -> Unit,
    onReset: () -> Unit,
) {
    val semantic = AppTheme.semanticColors
    var menuOpen by remember { mutableStateOf(false) }

    val statusText = when {
        item.isSkipped -> Strings.PLANNED_SKIPPED
        item.isOverdue -> Strings.PLANNED_AWAITING_CONFIRM.replace("%d", item.dueDay.toString())
        isIncome -> Strings.PLANNED_DUE_INCOME.replace("%d", item.dueDay.toString())
        else -> Strings.PLANNED_DUE_EXPENSE.replace("%d", item.dueDay.toString())
    }
    val statusColor = when {
        item.isSkipped -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        item.isOverdue -> semantic.warningColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val mutedDecoration = if (item.isSkipped) TextDecoration.LineThrough else TextDecoration.None

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.isSkipped) 0.5f else 0.85f),
                textDecoration = mutedDecoration,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (item.isOverridden && !item.isSkipped) "$statusText · ${Strings.PLANNED_OVERRIDDEN}" else statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
            )
        }

        Text(
            text = formatCurrency(item.amount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = mutedDecoration,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        if (item.isSkipped) {
            TextButton(onClick = { onSkip(false) }) {
                Text(Strings.PLANNED_RETURN, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            TextButton(
                onClick = onConfirm,
                enabled = !isOffline,
            ) {
                Text(
                    text = if (isIncome) Strings.PLANNED_RECEIVE_BUTTON else Strings.PLANNED_PAY_BUTTON,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOffline) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Text("⋯", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(Strings.PLANNED_MENU_OVERRIDE) },
                        onClick = { menuOpen = false; onOverride() },
                    )
                    DropdownMenuItem(
                        text = { Text(Strings.PLANNED_MENU_SKIP) },
                        onClick = { menuOpen = false; onSkip(true) },
                    )
                    if (item.isOverridden) {
                        DropdownMenuItem(
                            text = { Text(Strings.PLANNED_MENU_RESET) },
                            onClick = { menuOpen = false; onReset() },
                        )
                    }
                }
            }
        }
    }
}

val PlannedUiItem.isIncomeTemplate: Boolean
    get() = templateType == TEMPLATE_TYPE_INCOME_SOURCE

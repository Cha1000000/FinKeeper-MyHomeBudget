package ru.homebudget.finkeeper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.components.LoadingScreen
import ru.homebudget.finkeeper.ui.components.ProgressBar
import ru.homebudget.finkeeper.ui.components.SummaryCard
import ru.homebudget.finkeeper.ui.components.neonGlow
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.theme.ChartColors
import ru.homebudget.finkeeper.ui.viewmodel.DashboardState
import ru.homebudget.finkeeper.ui.viewmodel.ExpenseCategoryBreakdown
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.shortMonthName

@Composable
fun DashboardScreen(
    state: DashboardState,
    onRefresh: () -> Unit
) {
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
            Text(
                text = "Обзор",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Summary cards - 2 per row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    title = "Накопления",
                    value = formatCurrency(state.totalSavings),
                    backgroundColor = semantic.tealCardBg,
                    contentColor = semantic.tealColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "% в копилку",
                    value = "${kotlin.math.round(state.savingsPercent).toInt()}%",
                    backgroundColor = semantic.savingsCardBg,
                    contentColor = semantic.savingsColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    title = "Доступно",
                    value = formatCurrency(state.available),
                    backgroundColor = if (state.available >= 0) semantic.availableCardBg
                        else semantic.expenseCardBg,
                    contentColor = if (state.available >= 0) semantic.availableColor
                        else semantic.expenseColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Всего активов",
                    value = formatCurrency(state.totalAssets),
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Trend chart (simplified bar representation)
        if (state.trendData.isNotEmpty()) {
            item {
                TrendCard(state)
            }
        }

        // Expense breakdown
        if (state.expenseBreakdown.isNotEmpty()) {
            item {
                ExpenseBreakdownCard(state.expenseBreakdown)
            }
        }
    }
}

@Composable
private fun TrendCard(state: DashboardState) {
    val semantic = AppTheme.semanticColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .neonGlow(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), radius = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Динамика финансов",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            val maxValue = state.trendData.maxOfOrNull {
                maxOf(it.income, it.expense, it.savings)
            } ?: 1.0

            state.trendData.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val parts = item.month.split("/")
                    val monthNum = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val yearNum = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    Text(
                        text = "${shortMonthName(monthNum)}\n${yearNum % 100}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        ProgressBar(
                            progress = (item.income / maxValue).toFloat(),
                            color = semantic.incomeColor,
                            height = 4
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        ProgressBar(
                            progress = (item.expense / maxValue).toFloat(),
                            color = semantic.expenseColor,
                            height = 4
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        ProgressBar(
                            progress = (item.savings / maxValue).toFloat(),
                            color = semantic.savingsColor,
                            height = 4
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem("Доходы", semantic.incomeColor)
                LegendItem("Расходы", semantic.expenseColor)
                LegendItem("Накопления", semantic.savingsColor)
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
                .neonGlow(color, radius = 4.dp, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpenseBreakdownCard(breakdown: List<ExpenseCategoryBreakdown>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .neonGlow(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), radius = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Структура расходов",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            breakdown.forEachIndexed { index, item ->
                val color = ChartColors[index % ChartColors.size]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(color)
                            .neonGlow(color, radius = 6.dp, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${kotlin.math.round(item.percentage).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Text(
                        text = formatCurrency(item.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

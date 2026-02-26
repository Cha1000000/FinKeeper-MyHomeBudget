package ru.homebudget.finkeeper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.components.ExpensePieChart
import ru.homebudget.finkeeper.ui.components.FinancialDynamicsChart
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.components.LoadingScreen
import ru.homebudget.finkeeper.ui.components.ScreenHeader
import ru.homebudget.finkeeper.ui.components.SummaryCard
import androidx.compose.ui.graphics.luminance
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.DashboardState
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.isDesktop
import ru.homebudget.finkeeper.util.monthName

@Composable
fun DashboardScreen(
    state: DashboardState,
    onRefresh: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    val semantic = AppTheme.semanticColors
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val assetsTextColor = if (isDarkTheme) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = Strings.DASHBOARD_TITLE,
                modifier = Modifier.padding(horizontal = 0.dp),
            )
        }

        // Month navigation header
        item {
            GlassyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
        }

        // Total Assets top card
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
                        text = Strings.TOTAL_ASSETS,
                        style = MaterialTheme.typography.titleMedium,
                        color = assetsTextColor,
                    )
                    Text(
                        text = formatCurrency(state.totalAssets),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        if (isDesktop) {
            // Desktop: 3 cards per row (like web version)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        title = Strings.INCOMES,
                        value = formatCurrency(state.totalIncome),
                        backgroundColor = semantic.incomeCardBg,
                        contentColor = semantic.incomeColor,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = Strings.EXPENSES,
                        value = formatCurrency(state.totalExpense),
                        backgroundColor = semantic.expenseCardBg,
                        contentColor = semantic.expenseColor,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = Strings.SAVINGS,
                        value = formatCurrency(state.totalSavings),
                        backgroundColor = semantic.tealCardBg,
                        contentColor = semantic.tealColor,
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
                        title = Strings.SAVINGS_PERCENT,
                        value = "${kotlin.math.round(state.savingsPercent).toInt()}%",
                        backgroundColor = semantic.savingsCardBg,
                        contentColor = semantic.savingsColor,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = Strings.AVAILABLE,
                        value = formatCurrency(state.available),
                        backgroundColor = if (state.available >= 0) semantic.availableCardBg
                            else semantic.expenseCardBg,
                        contentColor = if (state.available >= 0) semantic.availableColor
                            else semantic.expenseColor,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = Strings.AVAILABLE_WITHOUT_SAVINGS,
                        value = formatCurrency(state.availableWithoutSavings),
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                        titleStyle = MaterialTheme.typography.titleSmall
                    )
                }
            }
        } else {
            // Mobile: 2 cards per row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryCard(
                        title = Strings.INCOMES,
                        value = formatCurrency(state.totalIncome),
                        backgroundColor = semantic.incomeCardBg,
                        contentColor = semantic.incomeColor,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = Strings.EXPENSES,
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryCard(
                        title = Strings.SAVINGS,
                        value = formatCurrency(state.totalSavings),
                        backgroundColor = semantic.tealCardBg,
                        contentColor = semantic.tealColor,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = Strings.SAVINGS_PERCENT,
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryCard(
                        title = Strings.AVAILABLE,
                        value = formatCurrency(state.available),
                        backgroundColor = if (state.available >= 0) semantic.availableCardBg
                            else semantic.expenseCardBg,
                        contentColor = if (state.available >= 0) semantic.availableColor
                            else semantic.expenseColor,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = Strings.AVAILABLE_WITHOUT_SAVINGS,
                        value = formatCurrency(state.availableWithoutSavings),
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                        titleStyle = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }

        // Charts: side-by-side on desktop, stacked on mobile
        if (isDesktop && state.trendData.isNotEmpty() && state.expenseBreakdown.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FinancialDynamicsChart(
                        trendData = state.trendData,
                        modifier = Modifier.weight(1f)
                    )
                    ExpensePieChart(
                        breakdown = state.expenseBreakdown,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            if (state.trendData.isNotEmpty()) {
                item {
                    FinancialDynamicsChart(state.trendData)
                }
            }
            if (state.expenseBreakdown.isNotEmpty()) {
                item {
                    ExpensePieChart(state.expenseBreakdown)
                }
            }
        }
    }
}

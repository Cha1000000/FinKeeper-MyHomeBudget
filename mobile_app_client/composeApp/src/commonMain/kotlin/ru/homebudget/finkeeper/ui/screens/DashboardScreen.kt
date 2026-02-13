package ru.homebudget.finkeeper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.components.ExpensePieChart
import ru.homebudget.finkeeper.ui.components.ScreenHeader
import ru.homebudget.finkeeper.ui.components.FinancialDynamicsChart
import ru.homebudget.finkeeper.ui.components.LoadingScreen
import ru.homebudget.finkeeper.ui.components.SummaryCard
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.DashboardState
import ru.homebudget.finkeeper.util.formatCurrency

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
            ScreenHeader(
                title = Strings.DASHBOARD_TITLE,
                modifier = Modifier.padding(horizontal = 0.dp),
            )
        }

        // Summary cards - 2 per row
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
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    title = Strings.TOTAL_ASSETS,
                    value = formatCurrency(state.totalAssets),
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Trend line chart
        if (state.trendData.isNotEmpty()) {
            item {
                FinancialDynamicsChart(state.trendData)
            }
        }

        // Expense pie chart
        if (state.expenseBreakdown.isNotEmpty()) {
            item {
                ExpensePieChart(state.expenseBreakdown)
            }
        }
    }
}

package ru.homebudget.finkeeper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.data.model.AnalyticsInsightsResponse
import ru.homebudget.finkeeper.data.model.Entitlements
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.components.AppButton
import ru.homebudget.finkeeper.ui.components.ExpensePieChart
import ru.homebudget.finkeeper.ui.components.ExportReportDialog
import ru.homebudget.finkeeper.ui.components.FinancialDynamicsChart
import ru.homebudget.finkeeper.ui.components.GlassyButtonStyle
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.components.IconBurnFlameGlass
import ru.homebudget.finkeeper.ui.components.IconChevronLeft
import ru.homebudget.finkeeper.ui.components.IconChevronRight
import ru.homebudget.finkeeper.ui.components.IconExportSheetGlass
import ru.homebudget.finkeeper.ui.components.IconProCrownGlass
import ru.homebudget.finkeeper.ui.components.IconProLockGlass
import ru.homebudget.finkeeper.ui.components.IconScheduleGlass
import ru.homebudget.finkeeper.ui.components.LoadingScreen
import ru.homebudget.finkeeper.ui.components.ProPillBadge
import ru.homebudget.finkeeper.ui.components.ScreenErrorBanner
import ru.homebudget.finkeeper.ui.components.ScreenHeader
import ru.homebudget.finkeeper.ui.components.ServerUnreachableBanner
import ru.homebudget.finkeeper.ui.components.SummaryCard
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.DashboardState
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.isDesktop
import ru.homebudget.finkeeper.util.monthName

@Composable
fun DashboardScreen(
    state: DashboardState,
    entitlements: Entitlements = Entitlements.FREE_DEFAULT,
    onRefresh: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDismissError: () -> Unit,
    onSelectTrendPeriod: (Int) -> Unit = {},
    onExportReport: (format: String, period: String) -> Unit = { _, _ -> },
    onOpenPaywall: (featureKey: String?) -> Unit = {},
    topBanner: (@Composable () -> Unit)? = null,
    onOpenMonthExpenses: () -> Unit = {},
    onOpenMonthIncomes: () -> Unit = {},
    onOpenSavings: () -> Unit = {},
) {
    var showExportDialog by remember { mutableStateOf(false) }
    val emeraldAccent = Color(0xFF10B981)

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
                actions = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(emeraldAccent.copy(alpha = 0.14f))
                            .border(1.dp, emeraldAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable {
                                if (entitlements.features.exportReports) {
                                    showExportDialog = true
                                } else {
                                    onOpenPaywall("exportReports")
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        IconExportSheetGlass(size = 15.dp)
                        Text(
                            text = if (isDesktop) Strings.PRO_EXPORT_BUTTON else Strings.PRO_EXPORT_SHORT,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = emeraldAccent,
                            maxLines = 1,
                            softWrap = false,
                        )
                        if (!entitlements.features.exportReports) {
                            IconProLockGlass(color = emeraldAccent, size = 11.dp)
                        }
                    }
                },
            )
        }

        state.exportMessage?.let { msg ->
            item {
                GlassyCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    baseColor = MaterialTheme.colorScheme.primaryContainer,
                    highlightColor = emeraldAccent,
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        if (topBanner != null) {
            item { topBanner() }
        }

        if (state.isOffline) {
            item { ServerUnreachableBanner(onRetry = onRefresh, isRetrying = state.isSyncing) }
        }

        state.error?.let { error ->
            item { ScreenErrorBanner(message = error, onDismiss = onDismissError) }
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
                        IconChevronLeft(color = MaterialTheme.colorScheme.primary, size = 26.dp)
                    }
                    Text(
                        text = "${monthName(state.month)} ${state.year}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = onNextMonth) {
                        IconChevronRight(color = MaterialTheme.colorScheme.primary, size = 26.dp)
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
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = state.totalAssets?.let(::formatCurrency) ?: Strings.NO_VALUE,
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
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        title = Strings.INCOMES,
                        value = formatCurrency(state.totalIncome),
                        backgroundColor = semantic.incomeCardBg,
                        contentColor = semantic.incomeColor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        subtitle = if (state.plannedIncomesTotal > 0) {
                            Strings.PLANNED_AWAITING_INCOME.replace("%s", formatCurrency(state.plannedIncomesTotal))
                        } else null,
                        subtitleIcon = if (state.plannedIncomesTotal > 0) {
                            { IconScheduleGlass(size = 14.dp) }
                        } else null,
                        onClick = onOpenMonthIncomes,
                    )
                    SummaryCard(
                        title = Strings.EXPENSES,
                        value = formatCurrency(state.totalExpense),
                        backgroundColor = semantic.expenseCardBg,
                        contentColor = semantic.expenseColor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        subtitle = if (state.plannedExpensesTotal > 0) {
                            Strings.PLANNED_BY_PLAN.replace("%s", formatCurrency(state.plannedExpensesTotal))
                        } else null,
                        subtitleIcon = if (state.plannedExpensesTotal > 0) {
                            { IconScheduleGlass(size = 14.dp) }
                        } else null,
                        onClick = onOpenMonthExpenses,
                    )
                    SummaryCard(
                        title = Strings.SAVINGS,
                        value = formatCurrency(state.totalSavings),
                        backgroundColor = semantic.tealCardBg,
                        contentColor = semantic.tealColor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        subtitle = Strings.SAVINGS_PERCENT_SUBTITLE.replace("%s", formatSavingsPercent(state.savingsPercent)),
                        onClick = onOpenSavings,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        title = Strings.AVAILABLE,
                        value = formatCurrency(state.available),
                        backgroundColor = if (state.available >= 0) semantic.availableCardBg
                            else semantic.expenseCardBg,
                        contentColor = if (state.available >= 0) semantic.availableColor
                            else semantic.expenseColor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = onOpenMonthExpenses,
                    )
                    SummaryCard(
                        title = Strings.AVAILABLE_WITHOUT_SAVINGS,
                        value = state.availableWithoutSavings?.let(::formatCurrency) ?: Strings.NO_VALUE,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        titleStyle = MaterialTheme.typography.titleSmall
                    )
                }
            }
        } else {
            // Mobile: 2 cards per row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryCard(
                        title = Strings.INCOMES,
                        value = formatCurrency(state.totalIncome),
                        backgroundColor = semantic.incomeCardBg,
                        contentColor = semantic.incomeColor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        subtitle = if (state.plannedIncomesTotal > 0) {
                            Strings.PLANNED_AWAITING_INCOME.replace("%s", formatCurrency(state.plannedIncomesTotal))
                        } else null,
                        subtitleIcon = if (state.plannedIncomesTotal > 0) {
                            { IconScheduleGlass(size = 14.dp) }
                        } else null,
                        onClick = onOpenMonthIncomes,
                    )
                    SummaryCard(
                        title = Strings.EXPENSES,
                        value = formatCurrency(state.totalExpense),
                        backgroundColor = semantic.expenseCardBg,
                        contentColor = semantic.expenseColor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        subtitle = if (state.plannedExpensesTotal > 0) {
                            Strings.PLANNED_BY_PLAN.replace("%s", formatCurrency(state.plannedExpensesTotal))
                        } else null,
                        subtitleIcon = if (state.plannedExpensesTotal > 0) {
                            { IconScheduleGlass(size = 14.dp) }
                        } else null,
                        onClick = onOpenMonthExpenses,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryCard(
                        title = Strings.SAVINGS,
                        value = formatCurrency(state.totalSavings),
                        backgroundColor = semantic.tealCardBg,
                        contentColor = semantic.tealColor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        subtitle = Strings.SAVINGS_PERCENT_SUBTITLE.replace("%s", formatSavingsPercent(state.savingsPercent)),
                        onClick = onOpenSavings,
                    )
                    SummaryCard(
                        title = Strings.AVAILABLE,
                        value = formatCurrency(state.available),
                        backgroundColor = if (state.available >= 0) semantic.availableCardBg
                            else semantic.expenseCardBg,
                        contentColor = if (state.available >= 0) semantic.availableColor
                            else semantic.expenseColor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = onOpenMonthExpenses,
                    )
                }
            }

            item {
                SummaryCard(
                    title = Strings.AVAILABLE_WITHOUT_SAVINGS,
                    value = state.availableWithoutSavings?.let(::formatCurrency) ?: Strings.NO_VALUE,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                    titleStyle = MaterialTheme.typography.titleSmall
                )
            }
        }

        // Прогноз на месяц (показываем только когда есть план)
        if (state.plannedExpensesTotal > 0 || state.plannedIncomesTotal > 0) {
            item {
                SummaryCard(
                    title = Strings.FORECAST_CARD_TITLE,
                    value = "${formatCurrency(state.forecastFree)} ${Strings.FORECAST_FREE}",
                    backgroundColor = semantic.savingsCardBg,
                    contentColor = if (state.forecastFree >= 0) semantic.savingsColor else semantic.expenseColor,
                    modifier = Modifier.fillMaxWidth(),
                    subtitle = Strings.FORECAST_EXPENSES.replace("%s", formatCurrency(state.forecastExpenses)),
                    onClick = onOpenMonthExpenses,
                )
            }
        }

        // Charts: side-by-side on desktop, stacked on mobile
        val handlePeriodClick: (Int) -> Unit = { months ->
            if (months > 6 && !entitlements.features.extendedAnalytics) {
                onOpenPaywall("extendedAnalytics")
            } else {
                onSelectTrendPeriod(months)
            }
        }

        if (isDesktop && state.trendData.isNotEmpty() && state.expenseBreakdown.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FinancialDynamicsChart(
                            trendData = state.trendData,
                            selectedPeriodMonths = if (!entitlements.features.extendedAnalytics && state.selectedTrendMonths > 6) 6 else state.selectedTrendMonths,
                            isExtendedAnalyticsUnlocked = entitlements.features.extendedAnalytics,
                            onSelectPeriodMonths = handlePeriodClick,
                        )
                        if (entitlements.features.extendedAnalytics && state.insights != null) {
                            ProInsightsCard(insights = state.insights)
                        } else if (!entitlements.features.extendedAnalytics) {
                            ProAnalyticsTeaserCard(
                                canStartTrial = entitlements.canStartTrial,
                                onClick = { onOpenPaywall("extendedAnalytics") },
                            )
                        }
                    }
                    ExpensePieChart(
                        breakdown = state.expenseBreakdown,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            if (state.trendData.isNotEmpty()) {
                item {
                    FinancialDynamicsChart(
                        trendData = state.trendData,
                        selectedPeriodMonths = if (!entitlements.features.extendedAnalytics && state.selectedTrendMonths > 6) 6 else state.selectedTrendMonths,
                        isExtendedAnalyticsUnlocked = entitlements.features.extendedAnalytics,
                        onSelectPeriodMonths = handlePeriodClick,
                    )
                }
            }
            if (state.expenseBreakdown.isNotEmpty()) {
                item {
                    ExpensePieChart(state.expenseBreakdown)
                }
            }
            // Карточка PRO-инсайтов: Скорость трат + Сравнение категорий с прошлым месяцем
            item {
                if (entitlements.features.extendedAnalytics && state.insights != null) {
                    ProInsightsCard(insights = state.insights)
                } else if (!entitlements.features.extendedAnalytics) {
                    ProAnalyticsTeaserCard(
                        canStartTrial = entitlements.canStartTrial,
                        onClick = { onOpenPaywall("extendedAnalytics") },
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        ExportReportDialog(
            isExporting = state.isExporting,
            onExport = { fmt, period ->
                onExportReport(fmt, period)
                showExportDialog = false
            },
            onDismiss = { showExportDialog = false },
        )
    }
}

@Composable
private fun ProInsightsCard(insights: AnalyticsInsightsResponse) {
    val emeraldAccent = Color(0xFF10B981)
    val amberAccent = Color(0xFFF59E0B)

    GlassyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = emeraldAccent.copy(alpha = 0.22f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(amberAccent.copy(alpha = 0.14f))
                            .border(1.dp, amberAccent.copy(alpha = 0.30f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconBurnFlameGlass(size = 20.dp)
                    }
                    Column {
                        Text(
                            text = Strings.PRO_BURN_RATE_TITLE,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = Strings.PRO_BURN_RATE_DAYS_FORMAT
                                .replace("%1\$d", insights.burnRate.elapsedDays.toString())
                                .replace("%2\$d", insights.burnRate.daysInMonth.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ProPillBadge(text = Strings.PRO_BADGE, accentColor = emeraldAccent)
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(12.dp),
                ) {
                    Column {
                        Text(
                            text = Strings.PRO_BURN_RATE_DAILY_LABEL,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatCurrency(insights.burnRate.dailyBurnRate) + Strings.PRO_BURN_RATE_PER_DAY_SUFFIX,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(12.dp),
                ) {
                    Column {
                        Text(
                            text = Strings.PRO_BURN_RATE_PROJECTED_LABEL,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatCurrency(insights.burnRate.projectedWithPlannedExpenses),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = Strings.PRO_BURN_RATE_PREV_MONTH_FORMAT.replace(
                                "%1\$s",
                                formatCurrency(insights.comparison.previousMonthExpenses),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (insights.comparison.categories.isNotEmpty()) {
                Text(
                    text = Strings.PRO_MOM_COMPARISON_TITLE,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    insights.comparison.categories.take(4).forEach { cat ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = cat.categoryName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                cat.deltaPercent?.let { dp ->
                                    val sign = if (dp > 0) "+" else ""
                                    ProPillBadge(
                                        text = "$sign${dp}%",
                                        accentColor = if (dp <= 0) emeraldAccent else amberAccent,
                                    )
                                }
                            }
                            Text(
                                text = "${formatCurrency(cat.previousAmount)} → ${formatCurrency(cat.currentAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = Strings.PRO_MOM_EMPTY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProAnalyticsTeaserCard(
    canStartTrial: Boolean,
    onClick: () -> Unit,
) {
    val emeraldAccent = Color(0xFF10B981)
    val amberAccent = Color(0xFFF59E0B)
    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = emeraldAccent.copy(alpha = 0.25f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(amberAccent.copy(alpha = 0.14f))
                        .border(1.dp, amberAccent.copy(alpha = 0.30f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconBurnFlameGlass(size = 20.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = Strings.PRO_FEATURE_ANALYTICS_TITLE,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        ProPillBadge(text = Strings.PRO_BADGE, accentColor = emeraldAccent)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Strings.PRO_FEATURE_ANALYTICS_DESC,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppButton(
                text = if (canStartTrial) Strings.PRO_TRY_TRIAL_SHORT else Strings.PRO_OPEN_PAYWALL,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                containerColor = emeraldAccent,
                contentColor = Color.White,
                style = GlassyButtonStyle.Solid,
                textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}

private fun formatSavingsPercent(percent: Double?): String =
    percent?.let { "${kotlin.math.round(it).toInt()}%" } ?: Strings.NO_VALUE

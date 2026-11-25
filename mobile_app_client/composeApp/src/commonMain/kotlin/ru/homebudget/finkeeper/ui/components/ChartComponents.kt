package ru.homebudget.finkeeper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.data.model.TrendItem
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.theme.ChartColors
import ru.homebudget.finkeeper.ui.viewmodel.ExpenseCategoryBreakdown
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.monthName
import ru.homebudget.finkeeper.util.shortMonthName
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.round

/**
 * Линейный график трендов расходов за 6 месяцев
 */
@Composable
fun TrendLineChart(
    trendData: List<TrendItem>,
    modifier: Modifier = Modifier
) {
    val semantic = AppTheme.semanticColors

    GlassyCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = semantic.incomeColor.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Финансовая динамика",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (trendData.isNotEmpty()) {
                LineChartCanvas(
                    data = trendData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendItem("Доходы", semantic.incomeColor)
                    LegendItem("Расходы", semantic.expenseColor)
                    LegendItem("Сбережения", semantic.savingsColor)
                }
            } else {
                Text(
                    text = "Нет данных для отображения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Canvas для отрисовки линейного графика
 */
@Composable
private fun LineChartCanvas(
    data: List<TrendItem>,
    modifier: Modifier = Modifier
) {
    val semantic = AppTheme.semanticColors
    val gridColorVal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val onSurfaceVariantColorVal = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val padding = 40f
        val chartWidth = canvasWidth - padding * 2
        val chartHeight = canvasHeight - padding * 2

        // Находим максимальное значение для масштабирования
        val maxValue = data.maxOfOrNull { maxOf(it.income, it.expense, it.savings) } ?: 1.0
        val safeMaxValue = if (maxValue == 0.0) 1.0 else maxValue

        // Рисуем сетку
        val gridLines = 5
        repeat(gridLines) { i ->
            val y = padding + (chartHeight / gridLines) * i
            drawLine(
                color = gridColorVal,
                start = Offset(padding, y),
                end = Offset(canvasWidth - padding, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            )
        }

        // Функция для получения координат точки
        fun getPoint(index: Int, value: Double): Offset {
            val x = padding + (chartWidth / (data.size - 1).coerceAtLeast(1)) * index
            val y = padding + chartHeight - (value / safeMaxValue).toFloat() * chartHeight
            return Offset(x, y)
        }

        // Рисуем линии для доходов
        if (data.size > 1) {
            val incomePath = Path().apply {
                data.forEachIndexed { index, item ->
                    val point = getPoint(index, item.income)
                    if (index == 0) {
                        moveTo(point.x, point.y)
                    } else {
                        lineTo(point.x, point.y)
                    }
                }
            }
            drawPath(
                path = incomePath,
                color = semantic.incomeColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Рисуем точки для доходов
            data.forEachIndexed { index, item ->
                val point = getPoint(index, item.income)
                drawCircle(
                    color = semantic.incomeColor,
                    radius = 4.dp.toPx(),
                    center = point
                )
            }
        }

        // Рисуем линии для расходов
        if (data.size > 1) {
            val expensePath = Path().apply {
                data.forEachIndexed { index, item ->
                    val point = getPoint(index, item.expense)
                    if (index == 0) {
                        moveTo(point.x, point.y)
                    } else {
                        lineTo(point.x, point.y)
                    }
                }
            }
            drawPath(
                path = expensePath,
                color = semantic.expenseColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Рисуем точки для расходов
            data.forEachIndexed { index, item ->
                val point = getPoint(index, item.expense)
                drawCircle(
                    color = semantic.expenseColor,
                    radius = 4.dp.toPx(),
                    center = point
                )
            }
        }

        // Рисуем линии для сбережений
        if (data.size > 1) {
            val savingsPath = Path().apply {
                data.forEachIndexed { index, item ->
                    val point = getPoint(index, item.savings)
                    if (index == 0) {
                        moveTo(point.x, point.y)
                    } else {
                        lineTo(point.x, point.y)
                    }
                }
            }
            drawPath(
                path = savingsPath,
                color = semantic.savingsColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Рисуем точки для сбережений
            data.forEachIndexed { index, item ->
                val point = getPoint(index, item.savings)
                drawCircle(
                    color = semantic.savingsColor,
                    radius = 4.dp.toPx(),
                    center = point
                )
            }
        }

        // Рисуем подписи месяцев
        data.forEachIndexed { index, _ ->
            val x = padding + (chartWidth / (data.size - 1).coerceAtLeast(1)) * index
            
            // Рисуем текст подписи (упрощенно)
            val textY = canvasHeight - 10f
            // Текст рисуется через drawText, но для простоты используем точки
            drawCircle(
                color = onSurfaceVariantColorVal,
                radius = 2.dp.toPx(),
                center = Offset(x, textY)
            )
        }
    }
}

/**
 * Круговая диаграмма структуры расходов (прозрачный бублик с итогом/выбранным сектором в центре).
 */
@Composable
fun ExpensePieChart(
    breakdown: List<ExpenseCategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(breakdown) { mutableStateOf<Int?>(null) }
    val selectedItem = selectedIndex?.let { breakdown.getOrNull(it) }
    val selectedColor = selectedIndex?.let { ChartColors[it % ChartColors.size] }
    val totalAmount = remember(breakdown) { breakdown.sumOf { it.amount } }

    GlassyCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = ChartColors.firstOrNull()?.copy(alpha = 0.1f) ?: Color.White.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = Strings.EXPENSE_STRUCTURE,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (breakdown.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PieChartCanvas(
                        data = breakdown,
                        selectedIndex = selectedIndex,
                        onSelect = { idx ->
                            selectedIndex = if (idx == null || selectedIndex == idx) null else idx
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(max = 112.dp).padding(horizontal = 4.dp),
                    ) {
                        Text(
                            text = selectedItem?.name ?: "Всего",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = formatCurrency(selectedItem?.amount ?: totalAmount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = selectedColor ?: MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (selectedItem != null) {
                            Text(
                                text = "${round(selectedItem.percentage).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = selectedColor ?: MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Интерактивная легенда: тап подсвечивает сектор и показывает детали в центре бублика
                breakdown.forEachIndexed { index, item ->
                    val color = ChartColors[index % ChartColors.size]
                    val isSelected = selectedIndex == index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { selectedIndex = if (isSelected) null else index }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 13.dp else 11.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${round(item.percentage).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text(
                            text = formatCurrency(item.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Text(
                    text = "Нет данных для отображения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Canvas для отрисовки кольцевой диаграммы через Stroke (центр остаётся прозрачным,
 * не перекрывает радиальный градиент GlassyCard плоской заплаткой).
 */
@Composable
private fun PieChartCanvas(
    data: List<ExpenseCategoryBreakdown>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(data) {
            detectTapGestures { offset ->
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val outerR = minOf(centerX, centerY) * 0.86f
                val innerR = outerR * 0.54f
                val dx = offset.x - centerX
                val dy = offset.y - centerY
                val dist = hypot(dx, dy)
                if (dist < innerR * 0.85f) {
                    onSelect(null)
                    return@detectTapGestures
                }
                if (dist > outerR * 1.12f) return@detectTapGestures

                var angle = (atan2(dy, dx) * 180.0 / PI).toFloat() + 90f
                if (angle < 0f) angle += 360f
                var acc = 0f
                data.forEachIndexed { index, item ->
                    acc += ((item.percentage / 100.0) * 360.0).toFloat()
                    if (angle <= acc || index == data.lastIndex) {
                        onSelect(index)
                        return@detectTapGestures
                    }
                }
            }
        }
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val outerR = minOf(centerX, centerY) * 0.86f
        val ringThickness = outerR * 0.42f
        val ringRadius = outerR - ringThickness / 2f

        var startAngle = -90f
        data.forEachIndexed { index, item ->
            val rawSweep = ((item.percentage / 100.0) * 360.0).toFloat()
            val gap = if (data.size > 1 && rawSweep > 4f) 1.6f else 0f
            val sweep = (rawSweep - gap).coerceAtLeast(1f)
            val isSelected = selectedIndex == index
            val alpha = if (selectedIndex == null || isSelected) 1f else 0.45f
            val strokeW = if (isSelected) ringThickness + 6.dp.toPx() else ringThickness
            val color = ChartColors[index % ChartColors.size].copy(alpha = alpha)

            drawArc(
                color = color,
                startAngle = startAngle + gap / 2f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = strokeW, cap = StrokeCap.Butt),
            )
            startAngle += rawSweep
        }
    }
}

/**
 * Элемент легенды
 */
@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Столбчатая диаграмма с группировкой и выбором месяца по тапу для просмотра точных сумм.
 */
@Composable
fun FinancialDynamicsChart(
    trendData: List<TrendItem>,
    modifier: Modifier = Modifier
) {
    val semantic = AppTheme.semanticColors
    var selectedIndex by remember(trendData) { mutableStateOf<Int?>(null) }
    val selectedItem = selectedIndex?.let { trendData.getOrNull(it) }

    GlassyCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = Strings.FINANCIAL_DYNAMICS,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (trendData.isNotEmpty()) {
                AnimatedVisibility(visible = selectedItem != null) {
                    selectedItem?.let { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = formatFullMonthLabel(item.month),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "+${formatCurrency(item.income)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = semantic.incomeColor,
                                    )
                                    Text(
                                        text = "−${formatCurrency(item.expense)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = semantic.expenseColor,
                                    )
                                    Text(
                                        text = formatCurrency(item.savings),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = semantic.savingsColor,
                                    )
                                }
                            }
                        }
                    }
                }

                BarChartCanvas(
                    data = trendData,
                    selectedIndex = selectedIndex,
                    onSelect = { idx ->
                        selectedIndex = if (selectedIndex == idx) null else idx
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                MonthLabelsRow(
                    trendData = trendData,
                    selectedIndex = selectedIndex,
                    onSelect = { idx ->
                        selectedIndex = if (selectedIndex == idx) null else idx
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendItem(Strings.INCOMES, semantic.incomeColor)
                    LegendItem(Strings.EXPENSES, semantic.expenseColor)
                    LegendItem(Strings.SAVINGS, semantic.savingsColor)
                }
            } else {
                Text(
                    text = "Нет данных для отображения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Canvas для отрисовки столбчатой диаграммы с группировкой и тапом по месяцу.
 */
@Composable
private fun BarChartCanvas(
    data: List<TrendItem>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = AppTheme.semanticColors
    val gridColorVal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val highlightPillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    Canvas(
        modifier = modifier.pointerInput(data) {
            detectTapGestures { offset ->
                val padding = 16.dp.toPx()
                val chartWidth = size.width - padding * 2
                val barWidth = 12.dp.toPx()
                val barGap = 4.dp.toPx()
                val groupGap = 14.dp.toPx()
                val groupWidth = barWidth * 3 + barGap * 2
                val totalGroupsWidth = groupWidth * data.size + groupGap * (data.size - 1).coerceAtLeast(0)
                val firstX = padding + (chartWidth - totalGroupsWidth) / 2f

                data.indices.forEach { index ->
                    val startX = firstX + index * (groupWidth + groupGap)
                    if (offset.x in (startX - groupGap / 2f)..(startX + groupWidth + groupGap / 2f)) {
                        onSelect(index)
                        return@detectTapGestures
                    }
                }
            }
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val padding = 16.dp.toPx()
        val verticalPad = 14.dp.toPx()
        val chartWidth = canvasWidth - padding * 2
        val chartHeight = canvasHeight - verticalPad * 2

        val maxValue = data.maxOfOrNull { maxOf(it.income, it.expense, it.savings) } ?: 1.0
        val safeMaxValue = if (maxValue == 0.0) 1.0 else maxValue

        val barWidth = 12.dp.toPx()
        val barGap = 4.dp.toPx()
        val groupGap = 14.dp.toPx()
        val groupWidth = barWidth * 3 + barGap * 2

        val gridLines = 5
        repeat(gridLines) { i ->
            val y = verticalPad + (chartHeight / gridLines) * i
            drawLine(
                color = gridColorVal,
                start = Offset(padding, y),
                end = Offset(canvasWidth - padding, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            )
        }

        val totalGroupsWidth = groupWidth * data.size + groupGap * (data.size - 1).coerceAtLeast(0)
        val firstX = padding + (chartWidth - totalGroupsWidth) / 2f

        data.forEachIndexed { index, item ->
            val startX = firstX + index * (groupWidth + groupGap)
            val isSelected = selectedIndex == index
            val alpha = if (selectedIndex == null || isSelected) 1f else 0.45f

            if (isSelected) {
                drawRoundRect(
                    color = highlightPillColor,
                    topLeft = Offset(startX - 4.dp.toPx(), verticalPad / 2f),
                    size = Size(groupWidth + 8.dp.toPx(), canvasHeight - verticalPad),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
            }

            val incomeHeight = (item.income / safeMaxValue).toFloat() * chartHeight
            drawRoundRect(
                color = semantic.incomeColor.copy(alpha = alpha),
                topLeft = Offset(startX, verticalPad + chartHeight - incomeHeight),
                size = Size(barWidth, incomeHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            val expenseHeight = (item.expense / safeMaxValue).toFloat() * chartHeight
            drawRoundRect(
                color = semantic.expenseColor.copy(alpha = alpha),
                topLeft = Offset(startX + barWidth + barGap, verticalPad + chartHeight - expenseHeight),
                size = Size(barWidth, expenseHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            val savingsHeight = (item.savings / safeMaxValue).toFloat() * chartHeight
            drawRoundRect(
                color = semantic.savingsColor.copy(alpha = alpha),
                topLeft = Offset(startX + barWidth * 2 + barGap * 2, verticalPad + chartHeight - savingsHeight),
                size = Size(barWidth, savingsHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

@Composable
private fun MonthLabelsRow(
    trendData: List<TrendItem>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
) {
    val barWidth = 12.dp
    val barGap = 4.dp
    val groupGap = 14.dp
    val groupWidth = barWidth * 3 + barGap * 2
    val totalGroupsWidth = groupWidth * trendData.size + groupGap * (trendData.size - 1).coerceAtLeast(0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.width(totalGroupsWidth),
            horizontalArrangement = Arrangement.spacedBy(groupGap)
        ) {
            trendData.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                Box(
                    modifier = Modifier
                        .width(groupWidth)
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatTrendMonthLabel(item.month),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatTrendMonthLabel(rawMonth: String): String {
    val parts = rawMonth.split("/")
    if (parts.size != 2) return rawMonth

    val month = parts[0].toIntOrNull() ?: return rawMonth
    val year = parts[1].toIntOrNull() ?: return rawMonth
    val shortYear = (year % 100).toString().padStart(2, '0')
    return "${shortMonthName(month).lowercase()}.$shortYear"
}

private fun formatFullMonthLabel(rawMonth: String): String {
    val parts = rawMonth.split("/")
    if (parts.size != 2) return rawMonth

    val month = parts[0].toIntOrNull() ?: return rawMonth
    val year = parts[1].toIntOrNull() ?: return rawMonth
    return "${monthName(month)} $year"
}

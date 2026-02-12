package ru.homebudget.finkeeper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.data.model.TrendItem
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.theme.ChartColors
import ru.homebudget.finkeeper.ui.viewmodel.ExpenseCategoryBreakdown
import ru.homebudget.finkeeper.util.formatCurrency
import ru.homebudget.finkeeper.util.shortMonthName
import kotlin.math.max
import kotlin.math.min
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
        data.forEachIndexed { index, item ->
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
 * Круговая диаграмма структуры расходов
 */
@Composable
fun ExpensePieChart(
    breakdown: List<ExpenseCategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Структура расходов",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (breakdown.isNotEmpty()) {
                // Круговая диаграмма с использованием Canvas
                PieChartCanvas(
                    data = breakdown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Легенда с деталями
                breakdown.forEachIndexed { index, item ->
                    val color = ChartColors[index % ChartColors.size]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color)
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
                            text = "${round(item.percentage).toInt()}%",
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
 * Canvas для отрисовки круговой диаграммы
 */
@Composable
private fun PieChartCanvas(
    data: List<ExpenseCategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    val surfaceColorVal = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2
        val radius = minOf(centerX, centerY) * 0.8f

        var startAngle = -90f // Начинаем с верха

        data.forEachIndexed { index, item ->
            val sweepAngle = (item.percentage / 100.0) * 360.0f
            val color = ChartColors[index % ChartColors.size]

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle.toFloat(),
                useCenter = true,
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(
                    centerX - radius,
                    centerY - radius
                )
            )

            startAngle += sweepAngle.toFloat()
        }

        // Рисуем центральный круг для создания эффекта "donut chart"
        drawCircle(
            color = surfaceColorVal,
            radius = radius * 0.5f,
            center = Offset(centerX, centerY)
        )
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
 * Столбчатая диаграмма с группировкой для отображения финансовой динамики
 */
@Composable
fun FinancialDynamicsChart(
    trendData: List<TrendItem>,
    modifier: Modifier = Modifier
) {
    val semantic = AppTheme.semanticColors

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = Strings.FINANCIAL_DYNAMICS,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (trendData.isNotEmpty()) {
                BarChartCanvas(
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
 * Canvas для отрисовки столбчатой диаграммы с группировкой
 */
@Composable
private fun BarChartCanvas(
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

        // Размеры столбцов
        val barWidth = 12.dp.toPx()
        val barGap = 4.dp.toPx()
        val groupGap = 8.dp.toPx()
        val groupWidth = barWidth * 3 + barGap * 2

        // Рисуем сетку из 5 горизонтальных линий
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

        // Рисуем столбцы для каждого месяца
        data.forEachIndexed { index, item ->
            // Вычисляем позицию группы
            val totalGroupsWidth = groupWidth * data.size + groupGap * (data.size - 1)
            val startX = padding + (chartWidth - totalGroupsWidth) / 2 + index * (groupWidth + groupGap)

            // Рисуем столбец дохода
            val incomeHeight = (item.income / safeMaxValue).toFloat() * chartHeight
            val incomeX = startX
            val incomeY = padding + chartHeight - incomeHeight
            drawRoundRect(
                color = semantic.incomeColor,
                topLeft = Offset(incomeX, incomeY),
                size = Size(barWidth, incomeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Рисуем столбец расхода
            val expenseHeight = (item.expense / safeMaxValue).toFloat() * chartHeight
            val expenseX = startX + barWidth + barGap
            val expenseY = padding + chartHeight - expenseHeight
            drawRoundRect(
                color = semantic.expenseColor,
                topLeft = Offset(expenseX, expenseY),
                size = Size(barWidth, expenseHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Рисуем столбец накоплений
            val savingsHeight = (item.savings / safeMaxValue).toFloat() * chartHeight
            val savingsX = startX + barWidth * 2 + barGap * 2
            val savingsY = padding + chartHeight - savingsHeight
            drawRoundRect(
                color = semantic.savingsColor,
                topLeft = Offset(savingsX, savingsY),
                size = Size(barWidth, savingsHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Рисуем подпись месяца под группой столбцов
            val monthLabelX = startX + groupWidth / 2
            val monthLabelY = canvasHeight - 10f
            drawCircle(
                color = onSurfaceVariantColorVal,
                radius = 2.dp.toPx(),
                center = Offset(monthLabelX, monthLabelY)
            )
        }
    }
}

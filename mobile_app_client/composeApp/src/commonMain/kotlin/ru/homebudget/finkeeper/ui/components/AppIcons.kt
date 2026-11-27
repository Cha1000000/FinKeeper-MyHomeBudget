package ru.homebudget.finkeeper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =====================================================================
// Авторский набор иконок FinKeeper24 (стиль Liquid Glass, 100% Canvas KMP).
// Дополняет навигационные иконки из NavIcons.kt и полностью заменяет
// эмодзи, текстовые спецсимволы и стандартные Material Icons.
// =====================================================================

// ── Уровень 1: Объёмные стеклянные иконки с градиентами и бликами ───

/** Стеклянный циферблат со стрелками (вместо «🕐» и «⏳» для плановых/авто-записей). */
@Composable
fun IconScheduleGlass(
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val r = s * 0.43f
        val cx = s * 0.5f
        val cy = s * 0.5f

        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0x55FDE68A), Color(0x38D97706)),
                start = Offset(0f, 0f),
                end = Offset(s, s),
            ),
            radius = r,
            center = Offset(cx, cy),
        )
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)),
                start = Offset(0f, 0f),
                end = Offset(s, s),
            ),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = s * 0.08f),
        )
        // Блик верхнего левого сектора
        rotate(degrees = -24f, pivot = Offset(s * 0.36f, s * 0.28f)) {
            drawOval(
                color = Color.White.copy(alpha = 0.42f),
                topLeft = Offset(s * 0.22f, s * 0.21f),
                size = Size(s * 0.28f, s * 0.14f),
            )
        }
        // Стрелки
        val handBrush = Brush.linearGradient(
            colors = listOf(Color(0xFFFEF3C7), Color(0xFFF59E0B)),
        )
        val hands = Path().apply {
            moveTo(cx, cy - s * 0.20f)
            lineTo(cx, cy + s * 0.02f)
            lineTo(cx + s * 0.16f, cy + s * 0.10f)
        }
        drawPath(
            path = hands,
            brush = handBrush,
            style = Stroke(width = s * 0.095f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color = Color(0xFFFFFBEB), radius = s * 0.055f, center = Offset(cx, cy))
    }
}

/** Стеклянный календарь со стрелкой цикла (вместо «📅» в секции «Фиксированные»). */
@Composable
fun IconFixedCalendarGlass(
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val left = s * 0.12f
        val top = s * 0.20f
        val bodyW = s * 0.76f
        val bodyH = s * 0.68f
        val cr = CornerRadius(s * 0.18f, s * 0.18f)

        val emeraldGrad = Brush.linearGradient(
            colors = listOf(Color(0xFF6EE7B7), Color(0xFF10B981), Color(0xFF047857)),
            start = Offset(left, top),
            end = Offset(left + bodyW, top + bodyH),
        )

        // Полупрозрачное тело календаря
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0x66A7F3D0), Color(0x30059669)),
                start = Offset(left, top),
                end = Offset(left + bodyW, top + bodyH),
            ),
            topLeft = Offset(left, top),
            size = Size(bodyW, bodyH),
            cornerRadius = cr,
        )
        drawRoundRect(
            brush = emeraldGrad,
            topLeft = Offset(left, top),
            size = Size(bodyW, bodyH),
            cornerRadius = cr,
            style = Stroke(width = s * 0.065f),
        )

        // Изумрудная шапка календаря
        val headerPath = Path().apply {
            moveTo(left, top + s * 0.18f)
            quadraticBezierTo(left, top, left + s * 0.18f, top)
            lineTo(left + bodyW - s * 0.18f, top)
            quadraticBezierTo(left + bodyW, top, left + bodyW, top + s * 0.18f)
            lineTo(left + bodyW, top + s * 0.24f)
            lineTo(left, top + s * 0.24f)
            close()
        }
        drawPath(headerPath, brush = emeraldGrad)

        // Блик на шапке
        drawOval(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(s * 0.20f, s * 0.25f),
            size = Size(s * 0.28f, s * 0.09f),
        )

        // Верхние крепления
        val pinBrush = Color(0xFFD1FAE5)
        val pinStroke = Stroke(width = s * 0.04f)
        drawRoundRect(pinBrush, Offset(s * 0.29f, s * 0.11f), Size(s * 0.09f, s * 0.16f), CornerRadius(s * 0.045f))
        drawRoundRect(Color(0xFF047857), Offset(s * 0.29f, s * 0.11f), Size(s * 0.09f, s * 0.16f), CornerRadius(s * 0.045f), style = pinStroke)
        drawRoundRect(pinBrush, Offset(s * 0.62f, s * 0.11f), Size(s * 0.09f, s * 0.16f), CornerRadius(s * 0.045f))
        drawRoundRect(Color(0xFF047857), Offset(s * 0.62f, s * 0.11f), Size(s * 0.09f, s * 0.16f), CornerRadius(s * 0.045f), style = pinStroke)

        // Круговая стрелка регулярности внизу
        val arcRect = Rect(s * 0.38f, s * 0.52f, s * 0.63f, s * 0.77f)
        drawArc(
            brush = emeraldGrad,
            startAngle = 160f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = s * 0.07f, cap = StrokeCap.Round),
        )
        val tip = Path().apply {
            moveTo(s * 0.61f, s * 0.56f)
            lineTo(s * 0.65f, s * 0.65f)
            lineTo(s * 0.55f, s * 0.65f)
        }
        drawPath(
            tip,
            brush = emeraldGrad,
            style = Stroke(width = s * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Янтарный жетон ручного подтверждения (вместо «✋» в бейдже «вручную»). */
@Composable
fun IconManualConfirmGlass(
    size: Dp = 15.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val r = s * 0.44f
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0x66FDE68A), Color(0x38D97706)),
            ),
            radius = r,
        )
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)),
            ),
            radius = r,
            style = Stroke(width = s * 0.075f),
        )
        // Мини-силуэт ладони/нажатия
        val palm = Path().apply {
            moveTo(s * 0.42f, s * 0.62f)
            lineTo(s * 0.42f, s * 0.34f)
            quadraticBezierTo(s * 0.42f, s * 0.27f, s * 0.48f, s * 0.27f)
            quadraticBezierTo(s * 0.54f, s * 0.27f, s * 0.54f, s * 0.34f)
            lineTo(s * 0.54f, s * 0.47f)
            lineTo(s * 0.66f, s * 0.52f)
            quadraticBezierTo(s * 0.72f, s * 0.55f, s * 0.69f, s * 0.66f)
            lineTo(s * 0.64f, s * 0.75f)
            lineTo(s * 0.43f, s * 0.75f)
            lineTo(s * 0.32f, s * 0.58f)
            quadraticBezierTo(s * 0.34f, s * 0.51f, s * 0.39f, s * 0.55f)
            close()
        }
        drawPath(palm, color = Color(0xFFFFFBEB).copy(alpha = 0.9f))
        drawPath(
            palm,
            color = Color(0xFFB45309),
            style = Stroke(width = s * 0.055f, join = StrokeJoin.Round),
        )
    }
}

/** Стеклянные серверные модули со статус-индикаторами (вместо «⚙» в настройках сервера). */
@Composable
fun IconServerNodeGlass(
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val left = s * 0.14f
        val w = s * 0.72f
        val h = s * 0.28f
        val cr = CornerRadius(s * 0.12f, s * 0.12f)
        val emerald = Brush.linearGradient(listOf(Color(0xFF6EE7B7), Color(0xFF059669)))
        val glass = Brush.linearGradient(listOf(Color(0x66A7F3D0), Color(0x2E059669)))

        // Верхний модуль
        drawRoundRect(glass, Offset(left, s * 0.17f), Size(w, h), cr)
        drawRoundRect(emerald, Offset(left, s * 0.17f), Size(w, h), cr, style = Stroke(s * 0.06f))
        drawCircle(Color(0xFF10B981), radius = s * 0.055f, center = Offset(s * 0.30f, s * 0.31f))
        drawLine(emerald, Offset(s * 0.43f, s * 0.31f), Offset(s * 0.73f, s * 0.31f), s * 0.06f, StrokeCap.Round)

        // Нижний модуль
        drawRoundRect(glass, Offset(left, s * 0.55f), Size(w, h), cr)
        drawRoundRect(emerald, Offset(left, s * 0.55f), Size(w, h), cr, style = Stroke(s * 0.06f))
        drawCircle(Color(0xFFF59E0B), radius = s * 0.055f, center = Offset(s * 0.30f, s * 0.69f))
        drawLine(emerald, Offset(s * 0.43f, s * 0.69f), Offset(s * 0.73f, s * 0.69f), s * 0.06f, StrokeCap.Round)
    }
}

/** Стеклянный жетон пояснения «i» (вместо Material Icons.Outlined.Info). */
@Composable
fun IconInfoGlass(
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        drawCircle(color = color.copy(alpha = 0.15f), radius = s * 0.42f)
        drawCircle(
            color = color,
            radius = s * 0.42f,
            style = Stroke(width = s * 0.08f),
        )
        rotate(-25f, Offset(s * 0.36f, s * 0.28f)) {
            drawOval(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(s * 0.23f, s * 0.22f),
                size = Size(s * 0.25f, s * 0.11f),
            )
        }
        drawCircle(color = color, radius = s * 0.06f, center = Offset(s * 0.5f, s * 0.33f))
        drawLine(
            color = color,
            start = Offset(s * 0.5f, s * 0.46f),
            end = Offset(s * 0.5f, s * 0.70f),
            strokeWidth = s * 0.095f,
            cap = StrokeCap.Round,
        )
    }
}

/** Две шкалы с бегунками для кнопки настройки лимитов. */
@Composable
fun IconLimitsGaugeGlass(
    color: Color,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val y1 = s * 0.33f
        val y2 = s * 0.68f
        val startX = s * 0.14f
        val endX = s * 0.86f

        drawLine(color.copy(alpha = 0.3f), Offset(startX, y1), Offset(endX, y1), s * 0.085f, StrokeCap.Round)
        drawLine(Color(0xFF10B981), Offset(startX, y1), Offset(s * 0.62f, y1), s * 0.095f, StrokeCap.Round)
        drawCircle(Color(0xFFA7F3D0), radius = s * 0.125f, center = Offset(s * 0.64f, y1))
        drawCircle(Color(0xFF059669), radius = s * 0.125f, center = Offset(s * 0.64f, y1), style = Stroke(s * 0.06f))

        drawLine(color.copy(alpha = 0.3f), Offset(startX, y2), Offset(endX, y2), s * 0.085f, StrokeCap.Round)
        drawLine(Color(0xFFF59E0B), Offset(startX, y2), Offset(s * 0.38f, y2), s * 0.095f, StrokeCap.Round)
        drawCircle(Color(0xFFFDE68A), radius = s * 0.125f, center = Offset(s * 0.38f, y2))
        drawCircle(Color(0xFFD97706), radius = s * 0.125f, center = Offset(s * 0.38f, y2), style = Stroke(s * 0.06f))
    }
}

// ── Уровень 2: Авторские компактные иконки действий (в геометрии NavIcons) ──

/** Наклонённое перо со штрихом (вместо «✍︎»). */
@Composable
fun IconEditPen(
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.082f
        val penBody = Path().apply {
            moveTo(s * 0.64f, s * 0.18f)
            lineTo(s * 0.82f, s * 0.36f)
            lineTo(s * 0.37f, s * 0.80f)
            lineTo(s * 0.17f, s * 0.83f)
            lineTo(s * 0.20f, s * 0.63f)
            close()
        }
        drawPath(penBody, color = color.copy(alpha = 0.16f))
        drawPath(penBody, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(color, Offset(s * 0.54f, s * 0.28f), Offset(s * 0.72f, s * 0.46f), sw * 0.9f, StrokeCap.Round)
        drawLine(color.copy(alpha = 0.42f), Offset(s * 0.17f, s * 0.90f), Offset(s * 0.80f, s * 0.90f), sw * 0.85f, StrokeCap.Round)
    }
}

/** Стеклянная корзина удаления (вместо универсального «✕» на деструктивных кнопках). */
@Composable
fun IconTrash(
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.08f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Дужка крышки
        val handle = Path().apply {
            moveTo(s * 0.37f, s * 0.24f)
            quadraticBezierTo(s * 0.37f, s * 0.15f, s * 0.46f, s * 0.15f)
            lineTo(s * 0.54f, s * 0.15f)
            quadraticBezierTo(s * 0.63f, s * 0.15f, s * 0.63f, s * 0.24f)
        }
        drawPath(handle, color = color, style = stroke)
        // Крышка
        drawLine(color, Offset(s * 0.18f, s * 0.27f), Offset(s * 0.82f, s * 0.27f), sw, StrokeCap.Round)

        // Чаша
        val bin = Path().apply {
            moveTo(s * 0.27f, s * 0.27f)
            lineTo(s * 0.32f, s * 0.78f)
            quadraticBezierTo(s * 0.33f, s * 0.86f, s * 0.41f, s * 0.86f)
            lineTo(s * 0.59f, s * 0.86f)
            quadraticBezierTo(s * 0.67f, s * 0.86f, s * 0.68f, s * 0.78f)
            lineTo(s * 0.73f, s * 0.27f)
            close()
        }
        drawPath(bin, color = color.copy(alpha = 0.14f))
        drawPath(bin, color = color, style = stroke)
        // Внутренние рёбра
        drawLine(color, Offset(s * 0.42f, s * 0.43f), Offset(s * 0.44f, s * 0.71f), sw * 0.85f, StrokeCap.Round)
        drawLine(color, Offset(s * 0.58f, s * 0.43f), Offset(s * 0.56f, s * 0.71f), sw * 0.85f, StrokeCap.Round)
    }
}

/** Закруглённый крестик в мягкой подложке (для отмены/закрытия вместо «✕»). */
@Composable
fun IconCloseCross(
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.095f
        drawCircle(color = color.copy(alpha = 0.10f), radius = s * 0.42f)
        drawLine(color, Offset(s * 0.33f, s * 0.33f), Offset(s * 0.67f, s * 0.67f), sw, StrokeCap.Round)
        drawLine(color, Offset(s * 0.67f, s * 0.33f), Offset(s * 0.33f, s * 0.67f), sw, StrokeCap.Round)
    }
}

/** Кнопка-стрелка «Предыдущий месяц» (вместо «◀»). */
@Composable
fun IconChevronLeft(
    color: Color,
    size: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.095f
        drawCircle(color = color.copy(alpha = 0.10f), radius = s * 0.42f)
        drawCircle(color = color.copy(alpha = 0.22f), radius = s * 0.42f, style = Stroke(s * 0.04f))
        val path = Path().apply {
            moveTo(s * 0.58f, s * 0.30f)
            lineTo(s * 0.37f, s * 0.50f)
            lineTo(s * 0.58f, s * 0.70f)
        }
        drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Кнопка-стрелка «Следующий месяц» (вместо «▶»). */
@Composable
fun IconChevronRight(
    color: Color,
    size: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.095f
        drawCircle(color = color.copy(alpha = 0.10f), radius = s * 0.42f)
        drawCircle(color = color.copy(alpha = 0.22f), radius = s * 0.42f, style = Stroke(s * 0.04f))
        val path = Path().apply {
            moveTo(s * 0.42f, s * 0.30f)
            lineTo(s * 0.63f, s * 0.50f)
            lineTo(s * 0.42f, s * 0.70f)
        }
        drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Плавный шеврон раскрытия (по умолчанию смотрит вниз; поворачивается через Modifier.rotate). */
@Composable
fun IconExpandChevron(
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.11f
        val path = Path().apply {
            moveTo(s * 0.24f, s * 0.37f)
            lineTo(s * 0.50f, s * 0.64f)
            lineTo(s * 0.76f, s * 0.37f)
        }
        drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Тактильная стеклянная рукоятка перетаскивания 2×3 (вместо текста «Перетащить»). */
@Composable
fun IconDragGrip(
    color: Color,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val cr = CornerRadius(s * 0.22f, s * 0.22f)
        drawRoundRect(color = color.copy(alpha = 0.09f), topLeft = Offset(s * 0.14f, s * 0.12f), size = Size(s * 0.72f, s * 0.76f), cornerRadius = cr)
        drawRoundRect(color = color.copy(alpha = 0.22f), topLeft = Offset(s * 0.14f, s * 0.12f), size = Size(s * 0.72f, s * 0.76f), cornerRadius = cr, style = Stroke(s * 0.04f))

        val r = s * 0.062f
        val xs = listOf(s * 0.37f, s * 0.63f)
        val ys = listOf(s * 0.30f, s * 0.50f, s * 0.70f)
        ys.forEach { y ->
            drawCircle(color = color, radius = r, center = Offset(xs[0], y))
            drawCircle(color = color.copy(alpha = 0.72f), radius = r, center = Offset(xs[1], y))
        }
    }
}

/** Фирменный плюс с мягким ореолом (для FAB «Добавить»). */
@Composable
fun IconPlus(
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.115f
        drawCircle(color = color.copy(alpha = 0.18f), radius = s * 0.42f)
        drawLine(color, Offset(s * 0.50f, s * 0.26f), Offset(s * 0.50f, s * 0.74f), sw, StrokeCap.Round)
        drawLine(color, Offset(s * 0.26f, s * 0.50f), Offset(s * 0.74f, s * 0.50f), sw, StrokeCap.Round)
    }
}

/** Переключатель видимости пароля со стеклянной радужкой. */
@Composable
fun IconEyeToggle(
    visible: Boolean,
    color: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.08f
        val contour = Path().apply {
            moveTo(s * 0.10f, s * 0.50f)
            cubicTo(s * 0.24f, s * 0.24f, s * 0.76f, s * 0.24f, s * 0.90f, s * 0.50f)
            cubicTo(s * 0.76f, s * 0.76f, s * 0.24f, s * 0.76f, s * 0.10f, s * 0.50f)
            close()
        }
        drawPath(contour, color = color.copy(alpha = 0.12f))
        drawPath(contour, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
        drawCircle(color = color.copy(alpha = 0.25f), radius = s * 0.135f, center = Offset(s * 0.50f, s * 0.50f))
        drawCircle(color = color, radius = s * 0.135f, center = Offset(s * 0.50f, s * 0.50f), style = Stroke(width = sw * 0.9f))
        drawCircle(color = Color.White.copy(alpha = 0.85f), radius = s * 0.042f, center = Offset(s * 0.54f, s * 0.45f))

        if (!visible) {
            drawLine(color, Offset(s * 0.18f, s * 0.82f), Offset(s * 0.82f, s * 0.18f), sw * 1.15f, StrokeCap.Round)
        }
    }
}

/** Галочка подтверждения в стеклянном жетоне (вместо «✓»). */
@Composable
fun IconCheckMark(
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.10f
        drawCircle(color = color.copy(alpha = 0.14f), radius = s * 0.42f)
        drawCircle(color = color.copy(alpha = 0.35f), radius = s * 0.42f, style = Stroke(s * 0.05f))
        val path = Path().apply {
            moveTo(s * 0.29f, s * 0.51f)
            lineTo(s * 0.44f, s * 0.66f)
            lineTo(s * 0.72f, s * 0.35f)
        }
        drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Троеточие для меню плановых операций (вместо текстового «⋯»). */
@Composable
fun IconMoreDots(
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val r = s * 0.075f
        val y = s * 0.50f
        drawCircle(color = color, radius = r, center = Offset(s * 0.25f, y))
        drawCircle(color = color, radius = r, center = Offset(s * 0.50f, y))
        drawCircle(color = color, radius = r, center = Offset(s * 0.75f, y))
    }
}

// ── Уровень 3: Объёмные иконки Семейный PRO (Liquid Glass) ─────────────

/** Объёмная корона Семейный PRO с изумрудно-золотым градиентом и бликами. */
@Composable
fun IconProCrownGlass(
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val crownGrad = Brush.linearGradient(
            colors = listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFFF59E0B)),
            start = Offset(s * 0.1f, s * 0.15f),
            end = Offset(s * 0.9f, s * 0.85f),
        )
        val crownPath = Path().apply {
            moveTo(s * 0.16f, s * 0.74f)
            lineTo(s * 0.10f, s * 0.34f)
            lineTo(s * 0.33f, s * 0.50f)
            lineTo(s * 0.50f, s * 0.20f)
            lineTo(s * 0.67f, s * 0.50f)
            lineTo(s * 0.90f, s * 0.34f)
            lineTo(s * 0.84f, s * 0.74f)
            close()
        }
        drawPath(crownPath, brush = crownGrad)
        drawPath(
            crownPath,
            color = Color.White.copy(alpha = 0.45f),
            style = Stroke(width = s * 0.05f, join = StrokeJoin.Round),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFFD97706))),
            topLeft = Offset(s * 0.16f, s * 0.78f),
            size = Size(s * 0.68f, s * 0.11f),
            cornerRadius = CornerRadius(s * 0.05f, s * 0.05f),
        )
        drawCircle(color = Color(0xFFFEF3C7), radius = s * 0.055f, center = Offset(s * 0.50f, s * 0.54f))
        drawCircle(color = Color(0xFFA7F3D0), radius = s * 0.045f, center = Offset(s * 0.30f, s * 0.60f))
        drawCircle(color = Color(0xFFFDE68A), radius = s * 0.045f, center = Offset(s * 0.70f, s * 0.60f))
    }
}

/** Стеклянный замок PRO-функции. */
@Composable
fun IconProLockGlass(
    color: Color = Color(0xFF10B981),
    size: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.11f
        val shackle = Path().apply {
            moveTo(s * 0.32f, s * 0.45f)
            lineTo(s * 0.32f, s * 0.30f)
            cubicTo(s * 0.32f, s * 0.14f, s * 0.68f, s * 0.14f, s * 0.68f, s * 0.30f)
            lineTo(s * 0.68f, s * 0.45f)
        }
        drawPath(shackle, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
        drawRoundRect(
            color = color.copy(alpha = 0.22f),
            topLeft = Offset(s * 0.20f, s * 0.44f),
            size = Size(s * 0.60f, s * 0.44f),
            cornerRadius = CornerRadius(s * 0.14f, s * 0.14f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(s * 0.20f, s * 0.44f),
            size = Size(s * 0.60f, s * 0.44f),
            cornerRadius = CornerRadius(s * 0.14f, s * 0.14f),
            style = Stroke(width = sw * 0.85f),
        )
        drawCircle(color = color, radius = s * 0.065f, center = Offset(s * 0.50f, s * 0.66f))
    }
}

/** Объёмное пламя для карточки Burn Rate (скорость трат). */
@Composable
fun IconBurnFlameGlass(
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val outerGrad = Brush.linearGradient(
            colors = listOf(Color(0xFFFBBF24), Color(0xFFF97316), Color(0xFFE11D48)),
            start = Offset(s * 0.3f, s * 0.1f),
            end = Offset(s * 0.7f, s * 0.9f),
        )
        val outer = Path().apply {
            moveTo(s * 0.53f, s * 0.10f)
            cubicTo(s * 0.56f, s * 0.24f, s * 0.44f, s * 0.32f, s * 0.38f, s * 0.42f)
            cubicTo(s * 0.32f, s * 0.49f, s * 0.36f, s * 0.56f, s * 0.36f, s * 0.56f)
            cubicTo(s * 0.30f, s * 0.52f, s * 0.29f, s * 0.44f, s * 0.29f, s * 0.44f)
            cubicTo(s * 0.20f, s * 0.53f, s * 0.18f, s * 0.64f, s * 0.22f, s * 0.75f)
            cubicTo(s * 0.26f, s * 0.86f, s * 0.37f, s * 0.92f, s * 0.50f, s * 0.92f)
            cubicTo(s * 0.68f, s * 0.92f, s * 0.80f, s * 0.78f, s * 0.80f, s * 0.60f)
            cubicTo(s * 0.80f, s * 0.40f, s * 0.66f, s * 0.26f, s * 0.53f, s * 0.10f)
            close()
        }
        drawPath(outer, brush = outerGrad)
        val inner = Path().apply {
            moveTo(s * 0.51f, s * 0.46f)
            cubicTo(s * 0.53f, s * 0.54f, s * 0.44f, s * 0.60f, s * 0.44f, s * 0.70f)
            cubicTo(s * 0.44f, s * 0.78f, s * 0.49f, s * 0.83f, s * 0.55f, s * 0.80f)
            cubicTo(s * 0.62f, s * 0.77f, s * 0.65f, s * 0.69f, s * 0.64f, s * 0.61f)
            cubicTo(s * 0.63f, s * 0.54f, s * 0.56f, s * 0.50f, s * 0.51f, s * 0.46f)
            close()
        }
        drawPath(
            inner,
            brush = Brush.verticalGradient(listOf(Color(0xFFFEF08A), Color(0xFFFB923C))),
        )
    }
}

/** Стеклянная таблица Excel/CSV экспорта. */
@Composable
fun IconExportSheetGlass(
    color: Color = Color(0xFF10B981),
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.085f
        val cr = CornerRadius(s * 0.15f, s * 0.15f)
        drawRoundRect(
            color = color.copy(alpha = 0.18f),
            topLeft = Offset(s * 0.16f, s * 0.12f),
            size = Size(s * 0.68f, s * 0.76f),
            cornerRadius = cr,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(s * 0.16f, s * 0.12f),
            size = Size(s * 0.68f, s * 0.76f),
            cornerRadius = cr,
            style = Stroke(width = sw),
        )
        drawLine(color, Offset(s * 0.30f, s * 0.35f), Offset(s * 0.70f, s * 0.35f), sw, StrokeCap.Round)
        drawLine(color, Offset(s * 0.30f, s * 0.52f), Offset(s * 0.70f, s * 0.52f), sw, StrokeCap.Round)
        drawLine(color, Offset(s * 0.30f, s * 0.69f), Offset(s * 0.54f, s * 0.69f), sw, StrokeCap.Round)
        drawCircle(color = color, radius = s * 0.06f, center = Offset(s * 0.68f, s * 0.69f))
    }
}

/** Стеклянная булавка закрепления бэкапа (Pin). */
@Composable
fun IconPinGlass(
    active: Boolean,
    color: Color = if (active) Color(0xFFF59E0B) else Color(0xFF94A3B8),
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.095f
        val body = Path().apply {
            moveTo(s * 0.36f, s * 0.18f)
            lineTo(s * 0.64f, s * 0.18f)
            lineTo(s * 0.60f, s * 0.46f)
            lineTo(s * 0.72f, s * 0.58f)
            lineTo(s * 0.72f, s * 0.64f)
            lineTo(s * 0.28f, s * 0.64f)
            lineTo(s * 0.28f, s * 0.58f)
            lineTo(s * 0.40f, s * 0.46f)
            close()
        }
        if (active) {
            drawPath(body, color = color.copy(alpha = 0.28f))
        }
        drawPath(body, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
        drawLine(color, Offset(s * 0.50f, s * 0.64f), Offset(s * 0.50f, s * 0.88f), sw, StrokeCap.Round)
    }
}

/** Стеклянная иконка устройств (ноутбук + смартфон). */
@Composable
fun IconDevicesGlass(
    color: Color = Color(0xFF10B981),
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.08f
        drawRoundRect(
            color = color.copy(alpha = 0.16f),
            topLeft = Offset(s * 0.10f, s * 0.20f),
            size = Size(s * 0.58f, s * 0.42f),
            cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(s * 0.10f, s * 0.20f),
            size = Size(s * 0.58f, s * 0.42f),
            cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
            style = Stroke(width = sw),
        )
        drawLine(color, Offset(s * 0.24f, s * 0.74f), Offset(s * 0.52f, s * 0.74f), sw, StrokeCap.Round)
        drawRoundRect(
            color = color.copy(alpha = 0.26f),
            topLeft = Offset(s * 0.64f, s * 0.38f),
            size = Size(s * 0.26f, s * 0.44f),
            cornerRadius = CornerRadius(s * 0.07f, s * 0.07f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(s * 0.64f, s * 0.38f),
            size = Size(s * 0.26f, s * 0.44f),
            cornerRadius = CornerRadius(s * 0.07f, s * 0.07f),
            style = Stroke(width = sw),
        )
    }
}

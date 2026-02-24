package ru.homebudget.finkeeper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dashboard Icon - "Bento" layout style
 * More associative with "Overview" and "Structure"
 */
@Composable
fun IconDashboard(color: Color, isSelected: Boolean = false, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = if (isSelected) s * 0.09f else s * 0.075f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = s * 0.12f
        val gap = s * 0.1f
        val cr = CornerRadius(s * 0.06f)
        
        val leftW = (s - pad * 2 - gap) * 0.45f
        val rightW = (s - pad * 2 - gap) * 0.55f
        val topH = (s - pad * 2 - gap) * 0.45f
        val bottomH = (s - pad * 2 - gap) * 0.55f

        // Left vertical column
        drawRoundRect(color, Offset(pad, pad), Size(leftW, s - pad * 2), cr, style = stroke)
        
        // Right top square
        drawRoundRect(color, Offset(pad + leftW + gap, pad), Size(rightW, topH), cr, style = stroke)
        
        // Right bottom rectangle 
        drawRoundRect(color, Offset(pad + leftW + gap, pad + topH + gap), Size(rightW, bottomH), cr, style = stroke)

        if (isSelected) {
            // Add a small detail to show it's active
            drawCircle(color, sw, Offset(pad + leftW / 2, pad + s * 0.3f))
        }
    }
}

/**
 * Calendar Icon - Modern with an event indicator
 */
@Composable
fun IconCalendar(color: Color, isSelected: Boolean = false, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = if (isSelected) s * 0.09f else s * 0.075f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = s * 0.12f
        val headerH = s * 0.22f
        val cr = CornerRadius(s * 0.1f)

        // Wrapper
        drawRoundRect(color, Offset(pad, pad + sw), Size(s - pad * 2, s - pad * 2 - sw), cr, style = stroke)

        // Header line
        drawLine(color, Offset(pad, pad + sw + headerH), Offset(s - pad, pad + sw + headerH), sw, StrokeCap.Round)

        // Date markers (vertical tabs)
        val tab1x = pad + (s - pad * 2) * 0.25f
        val tab2x = pad + (s - pad * 2) * 0.75f
        drawLine(color, Offset(tab1x, pad - sw * 0.5f), Offset(tab1x, pad + sw * 1.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(tab2x, pad - sw * 0.5f), Offset(tab2x, pad + sw * 1.5f), sw, StrokeCap.Round)

        // "Selected day" circle inside
        if (isSelected) {
            drawCircle(color, s * 0.06f, Offset(s * 0.65f, s * 0.7f))
        } else {
            // Small dot
            drawCircle(color, sw * 0.6f, Offset(s * 0.65f, s * 0.7f))
        }
    }
}

/**
 * Categories Icon - "Tag" style
 * More associative with labeling/organizing
 */
@Composable
fun IconReceipt(color: Color, isSelected: Boolean = false, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = if (isSelected) s * 0.09f else s * 0.075f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        
        val tagPath = Path().apply {
            moveTo(s * 0.15f, s * 0.4f) // Left side start
            lineTo(s * 0.15f, s * 0.8f) // Left down
            quadraticBezierTo(s * 0.15f, s * 0.9f, s * 0.25f, s * 0.9f) // corner
            lineTo(s * 0.75f, s * 0.9f) // bottom
            quadraticBezierTo(s * 0.85f, s * 0.9f, s * 0.85f, s * 0.8f) // corner
            lineTo(s * 0.85f, s * 0.4f) // Right side
            lineTo(s * 0.5f, s * 0.1f) // point top
            lineTo(s * 0.15f, s * 0.4f) // back to start
            close()
        }
        
        drawPath(tagPath, color, style = stroke)
        
        // Hole in the tag
        drawCircle(color, sw * 1.2f, Offset(s * 0.5f, s * 0.35f), style = if (isSelected) Stroke(sw) else stroke)
        
        // Detail line
        drawLine(color, Offset(s * 0.35f, s * 0.65f), Offset(s * 0.65f, s * 0.65f), sw * 0.8f, StrokeCap.Round)
    }
}

/**
 * Piggy Bank Icon - Elegant, minimalist design
 */
@Composable
fun IconPiggyBank(color: Color, isSelected: Boolean = false, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = if (isSelected) s * 0.09f else s * 0.075f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Body
        val bodyPath = Path().apply {
            moveTo(s * 0.2f, s * 0.7f)
            // Belly
            quadraticBezierTo(s * 0.5f, s * 0.85f, s * 0.8f, s * 0.7f)
            // Head/Front
            quadraticBezierTo(s * 0.95f, s * 0.5f, s * 0.8f, s * 0.25f)
            // Back/Top
            quadraticBezierTo(s * 0.5f, s * 0.1f, s * 0.2f, s * 0.25f)
            // Back
            quadraticBezierTo(s * 0.05f, s * 0.5f, s * 0.2f, s * 0.7f)
            close()
        }
        drawPath(bodyPath, color, style = stroke)

        // Snout
        val snout = Path().apply {
            moveTo(s * 0.88f, s * 0.4f)
            lineTo(s * 0.95f, s * 0.4f)
            lineTo(s * 0.95f, s * 0.55f)
            lineTo(s * 0.88f, s * 0.55f)
        }
        drawPath(snout, color, style = stroke)

        // Ear
        val ear = Path().apply {
            moveTo(s * 0.65f, s * 0.18f)
            lineTo(s * 0.75f, s * 0.08f)
            lineTo(s * 0.8f, s * 0.25f)
        }
        drawPath(ear, color, style = stroke)

        // Leg front
        drawLine(color, Offset(s * 0.65f, s * 0.78f), Offset(s * 0.65f, s * 0.9f), sw, StrokeCap.Round)
        // Leg back
        drawLine(color, Offset(s * 0.35f, s * 0.78f), Offset(s * 0.35f, s * 0.9f), sw, StrokeCap.Round)

        // Coin dropping detail
        if (isSelected) {
            drawCircle(color, sw * 1.5f, Offset(s * 0.5f, s * 0.4f))
        } else {
            drawLine(color, Offset(s * 0.45f, s * 0.2f), Offset(s * 0.55f, s * 0.2f), sw, StrokeCap.Round)
        }
    }
}

/**
 * Settings Icon - Precise gear design
 */
@Composable
fun IconSettings(color: Color, isSelected: Boolean = false, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = if (isSelected) s * 0.09f else s * 0.075f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cx = s / 2f
        val cy = s / 2f

        // Central circle
        drawCircle(color, s * 0.15f, Offset(cx, cy), style = stroke)

        val innerR = s * 0.26f
        val outerR = s * 0.42f
        val teethCount = 8
        val path = Path()

        for (i in 0 until teethCount) {
            val angle = i * (2 * PI.toFloat() / teethCount)
            val nextAngle = (i + 1) * (2 * PI.toFloat() / teethCount)
            val midAngle = (angle + nextAngle) / 2f
            
            val cosA = cos(angle.toDouble()).toFloat()
            val sinA = sin(angle.toDouble()).toFloat()
            val cosM = cos(midAngle.toDouble()).toFloat()
            val sinM = sin(midAngle.toDouble()).toFloat()
            val cosN = cos(nextAngle.toDouble()).toFloat()
            val sinN = sin(nextAngle.toDouble()).toFloat()

            if (i == 0) path.moveTo(cx + innerR * cosA, cy + innerR * sinA)
            
            path.quadraticBezierTo(
                cx + outerR * 1.1f * cosM, cy + outerR * 1.1f * sinM,
                cx + innerR * cosN, cy + innerR * sinN
            )
        }
        path.close()
        drawPath(path, color, style = stroke)
    }
}

/**
 * Logout Icon - More dynamic outgoing style
 */
@Composable
fun IconLogOut(color: Color, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.075f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = s * 0.15f

        // Bracket door
        val path = Path().apply {
            moveTo(s * 0.45f, pad)
            lineTo(pad, pad)
            quadraticBezierTo(pad * 0.7f, pad, pad * 0.7f, pad * 1.5f)
            lineTo(pad * 0.7f, s - pad * 1.5f)
            quadraticBezierTo(pad * 0.7f, s - pad, pad, s - pad)
            lineTo(s * 0.45f, s - pad)
        }
        drawPath(path, color, style = stroke)

        // Arrow
        val arrowY = s * 0.5f
        val arrowStart = s * 0.35f
        val arrowEnd = s * 0.9f
        drawLine(color, Offset(arrowStart, arrowY), Offset(arrowEnd, arrowY), sw, StrokeCap.Round)
        
        // Arrow head
        val headSize = s * 0.2f
        drawLine(color, Offset(arrowEnd - headSize, arrowY - headSize), Offset(arrowEnd, arrowY), sw, StrokeCap.Round)
        drawLine(color, Offset(arrowEnd - headSize, arrowY + headSize), Offset(arrowEnd, arrowY), sw, StrokeCap.Round)
    }
}

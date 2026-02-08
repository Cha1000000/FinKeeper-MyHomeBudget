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

// Lucide LayoutDashboard — 4 rounded squares in a 2x2 grid
@Composable
fun IconDashboard(color: Color, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val stroke = Stroke(width = s * 0.083f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = s * 0.125f
        val gap = s * 0.083f
        val cellW = (s - pad * 2 - gap) / 2
        val cellH = (s - pad * 2 - gap) / 2
        val cr = CornerRadius(s * 0.06f)

        // Top-left
        drawRoundRect(color, Offset(pad, pad), Size(cellW, cellH), cr, style = stroke)
        // Top-right
        drawRoundRect(color, Offset(pad + cellW + gap, pad), Size(cellW, cellH), cr, style = stroke)
        // Bottom-left
        drawRoundRect(color, Offset(pad, pad + cellH + gap), Size(cellW, cellH), cr, style = stroke)
        // Bottom-right
        drawRoundRect(color, Offset(pad + cellW + gap, pad + cellH + gap), Size(cellW, cellH), cr, style = stroke)
    }
}

// Lucide Calendar — rectangle with top tabs and grid lines
@Composable
fun IconCalendar(color: Color, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.083f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = s * 0.125f
        val top = s * 0.21f
        val cr = CornerRadius(s * 0.08f)

        // Main rect
        drawRoundRect(color, Offset(pad, top), Size(s - pad * 2, s - top - pad), cr, style = stroke)

        // Two top tabs
        val tab1x = s * 0.33f
        val tab2x = s * 0.67f
        drawLine(color, Offset(tab1x, pad), Offset(tab1x, top + sw), sw, StrokeCap.Round)
        drawLine(color, Offset(tab2x, pad), Offset(tab2x, top + sw), sw, StrokeCap.Round)

        // Horizontal line separating header
        val lineY = top + (s - top - pad) * 0.3f
        drawLine(color, Offset(pad, lineY), Offset(s - pad, lineY), sw, StrokeCap.Round)
    }
}

// Lucide Receipt — rectangle with dollar sign inside
@Composable
fun IconReceipt(color: Color, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.083f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = s * 0.125f
        val cr = CornerRadius(s * 0.08f)

        // Main rect
        drawRoundRect(color, Offset(pad, pad), Size(s - pad * 2, s - pad * 2), cr, style = stroke)

        // Dollar sign — S curve
        val cx = s * 0.5f
        val cy = s * 0.5f
        val dh = s * 0.25f

        // Vertical line of $
        drawLine(color, Offset(cx, cy - dh), Offset(cx, cy + dh), sw * 0.8f, StrokeCap.Round)

        // Top arc of S
        val path = Path().apply {
            moveTo(cx + s * 0.1f, cy - dh * 0.6f)
            cubicTo(cx + s * 0.1f, cy - dh * 0.9f, cx - s * 0.12f, cy - dh * 0.9f, cx - s * 0.12f, cy - dh * 0.3f)
            cubicTo(cx - s * 0.12f, cy + dh * 0.1f, cx + s * 0.12f, cy + dh * 0.1f, cx + s * 0.12f, cy + dh * 0.5f)
            cubicTo(cx + s * 0.12f, cy + dh * 0.85f, cx - s * 0.1f, cy + dh * 0.85f, cx - s * 0.1f, cy + dh * 0.55f)
        }
        drawPath(path, color, style = Stroke(width = sw * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Lucide PiggyBank reference — facing right
@Composable
fun IconPiggyBank(color: Color, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.08f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Main body - rounded shape
        val bodyPath = Path().apply {
            // Start from bottom left
            moveTo(s * 0.25f, s * 0.75f)
            // Left side (rump)
            cubicTo(s * 0.05f, s * 0.75f, s * 0.05f, s * 0.35f, s * 0.25f, s * 0.25f)
            // Top (back)
            cubicTo(s * 0.45f, s * 0.15f, s * 0.65f, s * 0.15f, s * 0.75f, s * 0.25f)
            // Right side (neck/face)
            cubicTo(s * 0.85f, s * 0.35f, s * 0.85f, s * 0.45f, s * 0.80f, s * 0.55f)
            // Bottom (belly)
            cubicTo(s * 0.75f, s * 0.75f, s * 0.45f, s * 0.75f, s * 0.25f, s * 0.75f)
        }
        drawPath(bodyPath, color, style = stroke)

        // Snout
        val snoutPath = Path().apply {
            moveTo(s * 0.82f, s * 0.42f)
            lineTo(s * 0.92f, s * 0.42f)
            lineTo(s * 0.92f, s * 0.52f)
            lineTo(s * 0.82f, s * 0.52f)
        }
        drawPath(snoutPath, color, style = stroke)

        // Ear
        val earPath = Path().apply {
            moveTo(s * 0.65f, s * 0.18f)
            lineTo(s * 0.72f, s * 0.08f)
            lineTo(s * 0.78f, s * 0.22f)
        }
        drawPath(earPath, color, style = stroke)

        // Eye
        drawCircle(color, s * 0.03f, Offset(s * 0.72f, s * 0.38f))

        // Tail (on the left)
        val tailPath = Path().apply {
            moveTo(s * 0.15f, s * 0.45f)
            cubicTo(s * 0.05f, s * 0.35f, s * 0.15f, s * 0.25f, s * 0.10f, s * 0.35f)
        }
        drawPath(tailPath, color, style = stroke)

        // Legs (two U-shaped legs at the bottom)
        // Back leg
        val leg1 = Path().apply {
            moveTo(s * 0.35f, s * 0.75f)
            lineTo(s * 0.35f, s * 0.88f)
            lineTo(s * 0.45f, s * 0.88f)
            lineTo(s * 0.45f, s * 0.75f)
        }
        drawPath(leg1, color, style = stroke)
        // Front leg
        val leg2 = Path().apply {
            moveTo(s * 0.60f, s * 0.75f)
            lineTo(s * 0.60f, s * 0.88f)
            lineTo(s * 0.70f, s * 0.88f)
            lineTo(s * 0.70f, s * 0.75f)
        }
        drawPath(leg2, color, style = stroke)

        // Coin slot
        drawLine(color, Offset(s * 0.40f, s * 0.22f), Offset(s * 0.55f, s * 0.22f), sw, StrokeCap.Round)
    }
}

// Lucide Settings (gear) — 8 rounded teeth
@Composable
fun IconSettings(color: Color, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.08f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cx = s / 2f
        val cy = s / 2f

        // Central circle
        drawCircle(color, s * 0.12f, Offset(cx, cy), style = stroke)

        // Gear body with 8 rounded teeth
        val gearPath = Path()
        val teethCount = 8
        val innerR = s * 0.25f
        val outerR = s * 0.40f
        
        for (i in 0 until teethCount) {
            val angle = (i.toFloat() / teethCount) * 2f * kotlin.math.PI.toFloat()
            val nextAngle = ((i + 1).toFloat() / teethCount) * 2f * kotlin.math.PI.toFloat()
            val midAngle = (angle + nextAngle) / 2f
            
            val xInner = cx + innerR * kotlin.math.cos(angle.toDouble()).toFloat()
            val yInner = cy + innerR * kotlin.math.sin(angle.toDouble()).toFloat()
            
            val xTooth = cx + outerR * kotlin.math.cos(midAngle.toDouble()).toFloat()
            val yTooth = cy + outerR * kotlin.math.sin(midAngle.toDouble()).toFloat()

            if (i == 0) {
                gearPath.moveTo(xInner, yInner)
            }
            
            // Draw tooth as a rounded curve
            gearPath.quadraticTo(xTooth, yTooth, 
                cx + innerR * kotlin.math.cos(nextAngle.toDouble()).toFloat(),
                cy + innerR * kotlin.math.sin(nextAngle.toDouble()).toFloat()
            )
        }
        gearPath.close()
        drawPath(gearPath, color, style = stroke)
    }
}

// Lucide LogOut — box with arrow pointing right
@Composable
fun IconLogOut(color: Color, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val sw = s * 0.083f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = s * 0.15f

        // Door frame (3 sides of rectangle — left, top, bottom)
        val path = Path().apply {
            moveTo(s * 0.55f, pad)
            lineTo(pad, pad)
            lineTo(pad, s - pad)
            lineTo(s * 0.55f, s - pad)
        }
        drawPath(path, color, style = stroke)

        // Arrow shaft
        val arrowY = s * 0.5f
        drawLine(color, Offset(s * 0.35f, arrowY), Offset(s - pad, arrowY), sw, StrokeCap.Round)

        // Arrow head
        drawLine(color, Offset(s * 0.7f, s * 0.33f), Offset(s - pad, arrowY), sw, StrokeCap.Round)
        drawLine(color, Offset(s * 0.7f, s * 0.67f), Offset(s - pad, arrowY), sw, StrokeCap.Round)
    }
}

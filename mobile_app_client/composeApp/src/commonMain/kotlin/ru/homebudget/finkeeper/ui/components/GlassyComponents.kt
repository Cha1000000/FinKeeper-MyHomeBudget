package ru.homebudget.finkeeper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
fun GlassyCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    baseColor: Color = MaterialTheme.colorScheme.surface,
    highlightColor: Color = Color.White.copy(alpha = 0.2f),
    content: @Composable () -> Unit
) {
    // Градиент фона: радиальный градиент из левого верхнего угла (светлое пятно) в основной цвет
    val bgBrush = Brush.radialGradient(
        colors = listOf(
            highlightColor.copy(alpha = 0.15f),
            baseColor.copy(alpha = 0.8f),
            baseColor
        ),
        center = Offset(0f, 0f),
        radius = 500f
    )

    // Градиент для обводки: светлый угол
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            highlightColor.copy(alpha = 0.4f),
            baseColor.copy(alpha = 0.05f)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
    ) {
        content()
    }
}

@Composable
fun GlassyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: @Composable() (() -> Unit)? = null,
    // Стиль: Solid (как About) или Glassy (как Review)
    style: GlassyButtonStyle = GlassyButtonStyle.Glassy,
    color: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = Color.White
) {
    val shape = RoundedCornerShape(percent = 50) // Полностью овальная форма (Pill)

    val buttonModifier = if (style == GlassyButtonStyle.Glassy) {
        // Glassy стиль
        val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        val gradientBottom = if (isLightTheme) color.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.2f)

        val bgBrush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0.15f),
                gradientBottom
            )
        )
        val borderBrush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0.4f),
                color.copy(alpha = 0.05f)
            )
        )
        
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .clickable(enabled = enabled && !isLoading, onClick = onClick)
    } else {
        modifier
            .fillMaxWidth()
            .height(56.dp)
    }

    if (style == GlassyButtonStyle.Solid) {
        Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().height(56.dp),
            enabled = enabled && !isLoading,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = textColor,
                disabledContainerColor = color.copy(alpha = 0.5f),
                disabledContentColor = textColor.copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 2.dp,
                hoveredElevation = 8.dp
            )
        ) {
            ButtonContent(text, isLoading, textColor, icon)
        }
    } else {
        Box(
            modifier = buttonModifier,
            contentAlignment = Alignment.Center
        ) {
            ButtonContent(text, isLoading, textColor, icon)
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    textColor: Color,
    icon: (@Composable () -> Unit)?
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = textColor,
            strokeWidth = 2.dp
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
        }
    }
}

enum class GlassyButtonStyle {
    Solid, // Яркий, заполненный
    Glassy // Полупрозрачный, стеклянный
}

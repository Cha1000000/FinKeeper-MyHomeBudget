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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    
    // Градиент фона: радиальный градиент из левого верхнего угла
    val bgBrush = Brush.radialGradient(
        colors = listOf(
            highlightColor.copy(alpha = if (isDark) 0.1f else 0.15f),
            baseColor.copy(alpha = if (isDark) 0.7f else 0.8f),
            baseColor.copy(alpha = if (isDark) 0.85f else 0.95f)
        ),
        center = Offset(0f, 0f),
        radius = 800f
    )

    // Градиент для обводки
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.3f else 0.2f),
            Color.White.copy(alpha = 0.05f),
            baseColor.copy(alpha = 0.1f)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = modifier
            .drawBehind {
                // Тонкий блик по верхнему краю для эффекта "жидкого стекла"
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1f)
                )
            }
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
    style: GlassyButtonStyle = GlassyButtonStyle.Glassy,
    color: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = Color.White,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    val shape = RoundedCornerShape(percent = 50)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    if (style == GlassyButtonStyle.Solid) {
        Button(
            onClick = onClick,
            modifier = modifier
                .then(
                    if (isDark) Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)
                        ),
                        shape = shape
                    ) else Modifier
                ),
            enabled = enabled && !isLoading,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = textColor,
                disabledContainerColor = color.copy(alpha = 0.5f),
                disabledContentColor = textColor.copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 2.dp
            )
        ) {
            ButtonContent(text, isLoading, textColor, icon, textStyle)
        }
    } else {
        // Улучшенный Glassmorphism для Glassy стиля
        val bgAlpha = if (isDark) 0.15f else 0.35f
        val borderAlpha = if (isDark) 0.4f else 0.5f
        
        val bgBrush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = bgAlpha + 0.1f),
                color.copy(alpha = bgAlpha)
            )
        )
        
        val borderBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = borderAlpha),
                color.copy(alpha = 0.15f)
            )
        )

        Box(
            modifier = modifier
                .drawBehind {
                    // Эффект внутреннего свечения (блеска)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                        style = Stroke(width = 2f)
                    )
                }
                .clip(shape)
                .background(bgBrush)
                .border(1.dp, borderBrush, shape)
                .clickable(enabled = enabled && !isLoading, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            ButtonContent(text, isLoading, textColor, icon, textStyle)
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    textColor: Color,
    icon: (@Composable () -> Unit)?,
    textStyle: androidx.compose.ui.text.TextStyle
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
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                text = text,
                style = textStyle,
                color = textColor
            )
        }
    }
}

enum class GlassyButtonStyle {
    Solid, // Яркий, заполненный
    Glassy // Полупрозрачный, стеклянный
}

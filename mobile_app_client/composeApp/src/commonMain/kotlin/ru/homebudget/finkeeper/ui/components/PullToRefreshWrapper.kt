package ru.homebudget.finkeeper.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val REFRESH_TRIGGER_DP = 80.dp
private val MAX_DRAG_DP = 120.dp

@Composable
fun PullToRefreshWrapper(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val refreshTriggerPx = with(density) { REFRESH_TRIGGER_DP.toPx() }
    val maxDragPx = with(density) { MAX_DRAG_DP.toPx() }

    var dragOffset by remember { mutableStateOf(0f) }
    var isTriggered by remember { mutableStateOf(false) }

    // Анимация возврата после завершения обновления
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && isTriggered) {
            isTriggered = false
            animate(dragOffset, 0f) { value, _ ->
                dragOffset = value
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Если тянем вверх и есть offset — уменьшаем offset
                if (available.y < 0 && dragOffset > 0) {
                    val consumed = available.y.coerceAtLeast(-dragOffset)
                    dragOffset += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Если контент уже наверху и тянем вниз — увеличиваем offset
                if (available.y > 0 && !isTriggered) {
                    val newOffset = (dragOffset + available.y * 0.5f).coerceAtMost(maxDragPx)
                    dragOffset = newOffset
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffset >= refreshTriggerPx && !isTriggered) {
                    isTriggered = true
                    onRefresh()
                    // Анимируем к позиции индикатора
                    animate(dragOffset, refreshTriggerPx * 0.5f) { value, _ ->
                        dragOffset = value
                    }
                } else if (!isTriggered) {
                    // Не достигли порога — возвращаем назад
                    animate(dragOffset, 0f) { value, _ ->
                        dragOffset = value
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        // Индикатор обновления
        if (dragOffset > 0 || isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                )
            }
        }

        // Контент со смещением
        Box(
            modifier = Modifier.offset {
                IntOffset(0, dragOffset.roundToInt())
            },
        ) {
            content()
        }
    }
}

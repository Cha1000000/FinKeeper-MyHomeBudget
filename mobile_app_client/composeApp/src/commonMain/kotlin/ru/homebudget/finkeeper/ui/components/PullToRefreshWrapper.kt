package ru.homebudget.finkeeper.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.homebudget.finkeeper.util.isDesktop
import kotlin.math.roundToInt

private val REFRESH_TRIGGER_DP = 72.dp
private val REFRESH_RESTING_DP = 58.dp
private val MAX_DRAG_DP = 108.dp
private const val DRAG_RESISTANCE = 0.58f

/**
 * Модификатор для верхней статичной области экрана (например, шапки «Месяца»),
 * позволяющий вызывать pull-to-refresh свайпом по ней, не затрагивая скролл списка ниже.
 */
val LocalHeaderPullToRefreshModifier = compositionLocalOf<Modifier> { Modifier }

@Composable
fun PullToRefreshWrapper(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enableNestedScroll: Boolean = true,
    content: @Composable () -> Unit,
) {
    // На Desktop pull-to-refresh не используется (колёсико мыши не должно тянуть индикатор)
    if (isDesktop) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
        return
    }

    val density = LocalDensity.current
    val refreshTriggerPx = with(density) { REFRESH_TRIGGER_DP.toPx() }
    val refreshRestingPx = with(density) { REFRESH_RESTING_DP.toPx() }
    val maxDragPx = with(density) { MAX_DRAG_DP.toPx() }
    val hiddenOffsetPx = with(density) { (-48).dp.toPx() }

    val coroutineScope = rememberCoroutineScope()
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)

    val dragOffset = remember { Animatable(0f) }
    var isTriggered by remember { mutableStateOf(false) }
    var sawRefreshing by remember { mutableStateOf(false) }
    // Блокируем pull-to-refresh в рамках текущего жеста, если список реально крутился к началу
    // (докрутка до верха списка останавливается у края и не запускает случайный refresh).
    var gestureConsumedUpwardScroll by remember { mutableStateOf(false) }

    fun finishPullGesture() {
        if (currentIsRefreshing || isTriggered) return
        if (dragOffset.value >= refreshTriggerPx) {
            isTriggered = true
            sawRefreshing = false
            currentOnRefresh()
            coroutineScope.launch {
                dragOffset.animateTo(refreshRestingPx)
                // Страховка на случай мгновенного/офлайн onRefresh(), не поднявшего isRefreshing
                delay(400)
                if (!currentIsRefreshing && isTriggered) {
                    isTriggered = false
                    dragOffset.animateTo(0f)
                }
            }
        } else if (dragOffset.value > 0f) {
            coroutineScope.launch { dragOffset.animateTo(0f) }
        }
    }

    // Ключ строго `isRefreshing`: сброс `isTriggered = false` НЕ отменяет анимацию возврата!
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            sawRefreshing = true
            dragOffset.animateTo(refreshRestingPx)
        } else if (sawRefreshing || isTriggered || dragOffset.value > 0f) {
            sawRefreshing = false
            isTriggered = false
            dragOffset.animateTo(0f)
        }
    }

    val nestedScrollConnection = remember(refreshTriggerPx, refreshRestingPx, maxDragPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y < 0f && dragOffset.value > 0f && !currentIsRefreshing && !isTriggered) {
                    val consumed = available.y.coerceAtLeast(-dragOffset.value)
                    val target = (dragOffset.value + consumed).coerceAtLeast(0f)
                    coroutineScope.launch { dragOffset.snapTo(target) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                if (consumed.y > 0.5f) {
                    gestureConsumedUpwardScroll = true
                    return Offset.Zero
                }

                if (gestureConsumedUpwardScroll || currentIsRefreshing || isTriggered) {
                    return Offset.Zero
                }

                if (available.y > 0f) {
                    val target = (dragOffset.value + available.y * DRAG_RESISTANCE).coerceAtMost(maxDragPx)
                    coroutineScope.launch { dragOffset.snapTo(target) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                gestureConsumedUpwardScroll = false
                finishPullGesture()
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                gestureConsumedUpwardScroll = false
                return Velocity.Zero
            }
        }
    }

    // Жест вытягивания для верхней статичной шапки (используется на экране «Месяц»)
    val headerPullModifier = remember(refreshTriggerPx, maxDragPx) {
        Modifier.pointerInput(refreshTriggerPx, maxDragPx) {
            detectVerticalDragGestures(
                onVerticalDrag = { _, dragAmount ->
                    if (currentIsRefreshing || isTriggered) return@detectVerticalDragGestures
                    if (dragAmount > 0f || dragOffset.value > 0f) {
                        val delta = if (dragAmount > 0f) dragAmount * DRAG_RESISTANCE else dragAmount
                        val target = (dragOffset.value + delta).coerceIn(0f, maxDragPx)
                        coroutineScope.launch { dragOffset.snapTo(target) }
                    }
                },
                onDragEnd = { finishPullGesture() },
                onDragCancel = {
                    if (!currentIsRefreshing && !isTriggered && dragOffset.value > 0f) {
                        coroutineScope.launch { dragOffset.animateTo(0f) }
                    }
                },
            )
        }
    }

    val currentOffset = dragOffset.value
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val progress = (currentOffset / refreshTriggerPx).coerceIn(0f, 1f)
    val isReady = progress >= 1f
    val showIndicator = currentOffset > 2f || isRefreshing || isTriggered

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (enableNestedScroll) Modifier.nestedScroll(nestedScrollConnection) else Modifier),
    ) {
        CompositionLocalProvider(LocalHeaderPullToRefreshModifier provides headerPullModifier) {
            // Контент остаётся на месте — поверх плавно выезжает стеклянная капсула индикатора
            content()
        }

        if (showIndicator) {
            val primary = MaterialTheme.colorScheme.primary
            val surface = MaterialTheme.colorScheme.surface
            val indicatorBase = if (isReady || isRefreshing) {
                primary.copy(alpha = if (isDark) 0.22f else 0.14f).compositeOver(surface)
            } else {
                surface
            }
            val indicatorBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.16f else 0.55f).compositeOver(indicatorBase).copy(alpha = 0.96f),
                    indicatorBase.copy(alpha = 0.94f),
                ),
            )
            val borderBrush = Brush.linearGradient(
                colors = listOf(
                    if (isReady || isRefreshing) primary.copy(alpha = 0.85f) else Color.White.copy(alpha = if (isDark) 0.35f else 0.7f),
                    primary.copy(alpha = if (isReady || isRefreshing) 0.35f else 0.18f),
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            )

            val yOffset = (hiddenOffsetPx + currentOffset).roundToInt()
            val indicatorAlpha = if (isRefreshing || isTriggered) 1f else (progress * 1.45f).coerceIn(0f, 1f)
            val indicatorScale = if (isRefreshing || isTriggered) 1f else (0.72f + 0.28f * progress)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, yOffset) }
                    .alpha(indicatorAlpha)
                    .scale(indicatorScale)
                    .size(44.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = primary.copy(alpha = if (isReady || isRefreshing) 0.45f else 0.2f),
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                    )
                    .clip(CircleShape)
                    .background(indicatorBrush)
                    .border(width = 1.2.dp, brush = borderBrush, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isRefreshing || isTriggered) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = primary,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { (progress * 0.85f).coerceIn(0.05f, 0.85f) },
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(progress * 240f),
                        color = if (isReady) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        strokeWidth = 2.5.dp,
                    )
                }
            }
        }
    }
}

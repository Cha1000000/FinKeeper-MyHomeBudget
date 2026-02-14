package ru.homebudget.finkeeper.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

data class WindowControlActions(
    val onClose: () -> Unit = {},
    val onMinimize: () -> Unit = {},
    val onToggleFullscreen: () -> Unit = {},
)

val LocalWindowControls = staticCompositionLocalOf { WindowControlActions() }

@Composable
expect fun DraggableArea(content: @Composable () -> Unit)

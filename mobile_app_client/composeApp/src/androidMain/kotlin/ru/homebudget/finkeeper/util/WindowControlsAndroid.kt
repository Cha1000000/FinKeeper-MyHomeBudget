package ru.homebudget.finkeeper.util

import androidx.compose.runtime.Composable

@Composable
actual fun DraggableArea(content: @Composable () -> Unit) {
    content()
}

package ru.homebudget.finkeeper.util

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.MouseInfo
import java.awt.Window

var desktopAwtWindow: Window? = null

@Composable
actual fun DraggableArea(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.pointerInput(Unit) {
            var startX = 0
            var startY = 0
            detectDragGestures(
                onDragStart = {
                    val mouseLocation = MouseInfo.getPointerInfo().location
                    val windowLocation = desktopAwtWindow?.location ?: return@detectDragGestures
                    startX = mouseLocation.x - windowLocation.x
                    startY = mouseLocation.y - windowLocation.y
                },
                onDrag = { change, _ ->
                    change.consume()
                    val mouseLocation = MouseInfo.getPointerInfo().location
                    desktopAwtWindow?.setLocation(
                        mouseLocation.x - startX,
                        mouseLocation.y - startY,
                    )
                },
            )
        }
    ) {
        content()
    }
}

package ru.homebudget.finkeeper

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import ru.homebudget.finkeeper.data.repository.SyncService
import ru.homebudget.finkeeper.data.repository.WebSocketService
import ru.homebudget.finkeeper.di.appModule
import ru.homebudget.finkeeper.di.desktopAppModule
import ru.homebudget.finkeeper.util.LocalWindowControls
import ru.homebudget.finkeeper.util.WindowControlActions
import ru.homebudget.finkeeper.util.desktopAwtWindow
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.prefs.Preferences

private const val PREF_WINDOW_WIDTH = "window_width"
private const val PREF_WINDOW_HEIGHT = "window_height"
private const val PREF_WINDOW_X = "window_x"
private const val PREF_WINDOW_Y = "window_y"
private const val PREF_IS_MAXIMIZED = "window_maximized"

private fun loadWindowState(): Triple<DpSize, WindowPosition, Boolean> {
    val prefs = Preferences.userRoot().node("ru/homebudget/finkeeper")
    val width = prefs.getFloat(PREF_WINDOW_WIDTH, 1200f)
    val height = prefs.getFloat(PREF_WINDOW_HEIGHT, 800f)
    val x = prefs.getFloat(PREF_WINDOW_X, Float.NaN)
    val y = prefs.getFloat(PREF_WINDOW_Y, Float.NaN)
    val isMaximized = prefs.getBoolean(PREF_IS_MAXIMIZED, false)

    val size = DpSize(width.dp, height.dp)
    val position = if (x.isNaN() || y.isNaN()) {
        WindowPosition.PlatformDefault
    } else {
        WindowPosition(x.dp, y.dp)
    }
    return Triple(size, position, isMaximized)
}

private fun saveWindowState(
    size: DpSize,
    position: WindowPosition,
    isMaximized: Boolean,
) {
    val prefs = Preferences.userRoot().node("ru/homebudget/finkeeper")
    prefs.putFloat(PREF_WINDOW_WIDTH, size.width.value)
    prefs.putFloat(PREF_WINDOW_HEIGHT, size.height.value)
    if (position is WindowPosition.Absolute) {
        prefs.putFloat(PREF_WINDOW_X, position.x.value)
        prefs.putFloat(PREF_WINDOW_Y, position.y.value)
    }
    prefs.putBoolean(PREF_IS_MAXIMIZED, isMaximized)
    prefs.flush()
}

/**
 * Desktop entry point for FinKeeper24 application
 * Supports macOS, Windows, and Linux
 */
fun main() {
    // Redirect stdout/stderr to log file for diagnostics
    try {
        val logDir = File(System.getProperty("user.home"), ".finkeeper")
        if (!logDir.exists()) logDir.mkdirs()
        val logFile = File(logDir, "sync_debug.log")
        val logStream = PrintStream(FileOutputStream(logFile, true), true)
        val originalOut = System.out
        val originalErr = System.err
        // Tee: write to both console and file
        System.setOut(PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) { originalOut.write(b); logStream.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { originalOut.write(b, off, len); logStream.write(b, off, len) }
            override fun flush() { originalOut.flush(); logStream.flush() }
        }, true))
        System.setErr(PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) { originalErr.write(b); logStream.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { originalErr.write(b, off, len); logStream.write(b, off, len) }
            override fun flush() { originalErr.flush(); logStream.flush() }
        }, true))
        println("[DESKTOP] Log file: ${logFile.absolutePath}")
    } catch (e: Exception) {
        println("[DESKTOP] Failed to set up log file: ${e.message}")
    }

    // Initialize Koin DI
    startKoin {
        modules(appModule, desktopAppModule)
    }

    // Start sync services (same as Android's FinKeeper24App)
    try {
        val syncService = GlobalContext.get().getOrNull<SyncService>()
        syncService?.start()
        val webSocketService = GlobalContext.get().getOrNull<WebSocketService>()
        webSocketService?.start()
        println("[DESKTOP] SyncService and WebSocketService started")
    } catch (e: Exception) {
        println("[DESKTOP] Failed to start sync services: ${e.message}")
    }

    val (savedSize, savedPosition, wasMaximized) = loadWindowState()

    application {
        val windowState = rememberWindowState(
            size = savedSize,
            position = savedPosition,
            placement = if (wasMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        )

        Window(
            onCloseRequest = {
                saveWindowState(
                    size = windowState.size,
                    position = windowState.position,
                    isMaximized = windowState.placement == WindowPlacement.Maximized,
                )
                exitApplication()
            },
            title = "FinKeeper24",
            state = windowState,
            resizable = true,
            undecorated = true,
            transparent = true,
            icon = painterResource("icon.png"),
        ) {
            desktopAwtWindow = this.window
            this.window.minimumSize = java.awt.Dimension(900, 600)

            // Периодически сохраняем состояние окна (на случай краша)
            DisposableEffect(windowState) {
                onDispose {
                    saveWindowState(
                        size = windowState.size,
                        position = windowState.position,
                        isMaximized = windowState.placement == WindowPlacement.Maximized,
                    )
                }
            }

            val windowControls = WindowControlActions(
                onClose = {
                    saveWindowState(
                        size = windowState.size,
                        position = windowState.position,
                        isMaximized = windowState.placement == WindowPlacement.Maximized,
                    )
                    exitApplication()
                },
                onMinimize = { windowState.isMinimized = true },
                onToggleFullscreen = {
                    windowState.placement =
                        if (windowState.placement == WindowPlacement.Maximized)
                            WindowPlacement.Floating
                        else
                            WindowPlacement.Maximized
                },
            )

            CompositionLocalProvider(LocalWindowControls provides windowControls) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    App()
                }
            }
        }
    }
}

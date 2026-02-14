package ru.homebudget.finkeeper

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.startKoin
import ru.homebudget.finkeeper.di.appModule
import ru.homebudget.finkeeper.di.desktopAppModule
import ru.homebudget.finkeeper.util.LocalWindowControls
import ru.homebudget.finkeeper.util.WindowControlActions
import ru.homebudget.finkeeper.util.desktopAwtWindow

/**
 * Desktop entry point for FinKeeper application
 * Supports macOS, Windows, and Linux
 */
fun main() {
    // Initialize Koin DI
    startKoin {
        modules(appModule, desktopAppModule)
    }

    application {
        val windowState = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "FinKeeper",
            state = windowState,
            resizable = true,
            undecorated = true,
            transparent = false,
            icon = painterResource("icon.png"),
        ) {
            desktopAwtWindow = this.window

            val windowControls = WindowControlActions(
                onClose = ::exitApplication,
                onMinimize = { windowState.isMinimized = true },
                onToggleFullscreen = {
                    windowState.placement =
                        if (windowState.placement == WindowPlacement.Fullscreen)
                            WindowPlacement.Floating
                        else
                            WindowPlacement.Fullscreen
                },
            )

            CompositionLocalProvider(LocalWindowControls provides windowControls) {
                App()
            }
        }
    }
}

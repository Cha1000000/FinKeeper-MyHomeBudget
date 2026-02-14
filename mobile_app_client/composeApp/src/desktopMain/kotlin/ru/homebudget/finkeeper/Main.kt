package ru.homebudget.finkeeper

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.startKoin
import ru.homebudget.finkeeper.di.appModule
import ru.homebudget.finkeeper.di.desktopAppModule

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
            icon = painterResource("icon.png"),
        ) {
            App()
        }
    }
}

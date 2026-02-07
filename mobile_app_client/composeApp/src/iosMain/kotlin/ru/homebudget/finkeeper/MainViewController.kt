package ru.homebudget.finkeeper

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import ru.homebudget.finkeeper.di.appModule

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}

fun MainViewController() = ComposeUIViewController { App() }
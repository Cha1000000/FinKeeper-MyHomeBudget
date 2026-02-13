package ru.homebudget.finkeeper

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import ru.homebudget.finkeeper.data.repository.SyncService
import ru.homebudget.finkeeper.data.repository.WebSocketService
import ru.homebudget.finkeeper.di.appModule
import ru.homebudget.finkeeper.di.iosAppModule

/**
 * Инициализация Koin для iOS
 * Регистрирует модули зависимостей: общий + iOS-специфичный
 */
fun initKoin() {
    startKoin {
        modules(appModule, iosAppModule)
    }
}

/**
 * Главный ViewController для iOS приложения
 */
fun MainViewController() = ComposeUIViewController { App() }

/**
 * Инициализация сервисов синхронизации для iOS
 * Должен быть вызван из iOSApp.swift после initKoin()
 */
class SyncServiceInitializer : KoinComponent {
    private val syncService: SyncService by inject()
    private val webSocketService: WebSocketService by inject()

    fun start() {
        syncService.start()
        webSocketService.start()
    }

    fun stop() {
        syncService.stop()
        webSocketService.stop()
    }
}

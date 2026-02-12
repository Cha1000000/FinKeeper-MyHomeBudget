package ru.homebudget.finkeeper.data.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Монитор состояния сети
 * expect/actual класс для отслеживания online/offline статуса
 */
expect class NetworkMonitor {
    /**
     * Flow с текущим состоянием сети
     * true - есть интернет, false - офлайн
     */
    val isOnline: StateFlow<Boolean>

    /**
     * Текущее состояние сети
     */
    val isNetworkAvailable: Boolean

    /**
     * Запуск мониторинга сети
     */
    fun startMonitoring()

    /**
     * Остановка мониторинга сети
     */
    fun stopMonitoring()
}

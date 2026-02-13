package ru.homebudget.finkeeper.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Desktop (JVM) implementation of NetworkMonitor.
 * Periodically checks internet connectivity by attempting to reach a well-known host.
 * Supports macOS, Windows, and Linux.
 */
actual class NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    actual val isNetworkAvailable: Boolean
        get() = _isOnline.value

    private var scheduler: ScheduledExecutorService? = null

    actual fun startMonitoring() {
        // Initial check
        checkNetworkState()

        // Start periodic checks every 5 seconds
        scheduler = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay(
                { checkNetworkState() },
                5,
                5,
                TimeUnit.SECONDS
            )
        }
    }

    actual fun stopMonitoring() {
        scheduler?.shutdown()
        scheduler = null
    }

    private fun checkNetworkState() {
        try {
            // Try to reach Google's DNS server (8.8.8.8) or any reliable host
            val reachable = InetAddress.getByName("8.8.8.8").isReachable(3000)
            _isOnline.value = reachable
        } catch (e: Exception) {
            _isOnline.value = false
        }
    }
}

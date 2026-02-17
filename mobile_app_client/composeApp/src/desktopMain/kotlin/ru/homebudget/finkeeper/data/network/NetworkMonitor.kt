package ru.homebudget.finkeeper.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Desktop (JVM) implementation of NetworkMonitor.
 * Periodically checks internet connectivity by attempting HTTP connection.
 * Supports macOS, Windows, and Linux.
 * 
 * Note: Uses HTTP connection instead of ICMP ping (InetAddress.isReachable)
 * because ICMP often fails on macOS due to firewall/permissions.
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

        // Start periodic checks every 10 seconds
        scheduler = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay(
                { checkNetworkState() },
                10,
                10,
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
            // Use HTTP HEAD request to Google - more reliable than ICMP ping
            val url = URL("http://217.114.8.82:3002/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.useCaches = false
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            _isOnline.value = (responseCode in 200..399)
        } catch (e: Exception) {
            // Fallback: try connecting to any DNS
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("8.8.8.8", 53), 3000)
                socket.close()
                _isOnline.value = true
            } catch (e2: Exception) {
                _isOnline.value = false
            }
        }
    }
}

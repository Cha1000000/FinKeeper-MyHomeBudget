package ru.homebudget.finkeeper.data.repository

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.homebudget.finkeeper.data.remote.TokenStorage

@Serializable
data class DataChangedEvent(
    val type: String,
    val entity: String,
    val action: String,
)

/**
 * WebSocket сервис для получения real-time уведомлений об изменениях данных на сервере.
 * При получении события data_changed эмитит в dataChanged flow,
 * на который подписываются ViewModels для обновления данных.
 */
class WebSocketService(
    private val tokenStorage: TokenStorage,
    private val syncManager: SyncManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isStarted = false
    private var reconnectDelay = 1000L

    private val json = Json { ignoreUnknownKeys = true }

    private val _dataChanged = MutableSharedFlow<DataChangedEvent>(extraBufferCapacity = 10)
    val dataChanged: SharedFlow<DataChangedEvent> = _dataChanged

    private val wsClient = HttpClient {
        install(WebSockets)
    }

    fun start() {
        if (isStarted) return
        isStarted = true
        connectLoop()
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        scope.cancel()
    }

    private fun connectLoop() {
        scope.launch {
            while (isActive && isStarted) {
                try {
                    val token = tokenStorage.token ?: run {
                        delay(5000)
                        return@launch
                    }

                    val serverUrl = tokenStorage.serverUrl
                    // Convert http(s)://host:port to ws(s)://host:port
                    val wsUrl = serverUrl
                        .replace("https://", "wss://")
                        .replace("http://", "ws://")

                    val host = wsUrl.substringAfter("://").substringBefore(":")
                    val port = wsUrl.substringAfterLast(":").toIntOrNull() ?: 3002
                    val isSecure = wsUrl.startsWith("wss://")

                    println("WebSocket connecting to $host:$port")

                    wsClient.webSocket(
                        host = host,
                        port = port,
                        path = "/?token=$token",
                        request = {
                            if (isSecure) {
                                url.protocol = io.ktor.http.URLProtocol.WSS
                            }
                        }
                    ) {
                        println("WebSocket connected")
                        reconnectDelay = 1000L

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val event = json.decodeFromString<DataChangedEvent>(text)
                                    if (event.type == "data_changed") {
                                        println("WebSocket event: ${event.entity} ${event.action}")
                                        _dataChanged.emit(event)
                                        syncManager.notifyDataChanged()
                                    }
                                } catch (e: Exception) {
                                    // Ignore non-JSON frames (pings etc.)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("WebSocket error: ${e.message}")
                }

                // Reconnect with exponential backoff (max 30s)
                if (isStarted) {
                    delay(reconnectDelay)
                    reconnectDelay = (reconnectDelay * 2).coerceAtMost(30000L)
                }
            }
        }
    }
}

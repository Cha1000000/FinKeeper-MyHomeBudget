package ru.homebudget.finkeeper.data.remote

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.runBlocking
import ru.homebudget.finkeeper.data.network.ServerLinkState
import ru.homebudget.finkeeper.data.network.isConnectivityFailure
import ru.homebudget.finkeeper.data.network.isTimeoutOrUnresolvable
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ApiClient на реальном HTTP-движке платформы (CIO на desktop, OkHttp в Android unit-тестах):
 * индикатор связи и классификация ошибок, на которых держится local-first.
 */
class ApiClientServerLinkTest {
    private val fastTimeouts = ApiTimeouts(requestMillis = 5_000, connectMillis = 2_000, socketMillis = 1_000)

    @Test
    fun connectionRefused_marksServerUnreachable() {
        val linkState = ServerLinkState()
        val client = ApiClient(tokenStorageFor(closedPort()), linkState, fastTimeouts)

        val error = assertFailsWith<Exception> { runBlocking { client.getCategories() } }

        assertTrue(error.isConnectivityFailure())
        assertTrue(linkState.isUnreachable.value)
    }

    @Test
    fun silentServer_failsFastAsTimeoutAndMarksUnreachable() {
        val linkState = ServerLinkState()
        // Соединение принимается (backlog), но ответа нет — как «зависший» сервер на мобильной сети
        ServerSocket(0).use { silent ->
            val client = ApiClient(tokenStorageFor(silent.localPort), linkState, fastTimeouts)

            val error = assertFailsWith<Exception> { runBlocking { client.getCategories() } }

            assertTrue(error.isConnectivityFailure(), "таймаут должен считаться сетевой ошибкой: $error")
            assertTrue(error.isTimeoutOrUnresolvable(), "таймаут не должен ретраиться: $error")
        }
        assertTrue(linkState.isUnreachable.value)
    }

    @Test
    fun errorResponse_meansServerIsReachable() {
        val linkState = ServerLinkState()
        runBlocking { linkState.reportUnreachable() }
        withHttpServer(Response("500 Internal Server Error", """{"error":"boom"}""")) { port ->
            val client = ApiClient(tokenStorageFor(port), linkState, fastTimeouts)

            val error = assertFailsWith<ApiException> { runBlocking { client.getCategories() } }
            assertFalse(error.isConnectivityFailure())
        }

        assertFalse(linkState.isUnreachable.value)
    }

    @Test
    fun badGatewayFromProxy_meansServerIsUnreachable() {
        val linkState = ServerLinkState()
        withHttpServer(Response("502 Bad Gateway", """{"error":"upstream"}""")) { port ->
            val client = ApiClient(tokenStorageFor(port), linkState, fastTimeouts)

            val error = assertFailsWith<ApiException> { runBlocking { client.getCategories() } }
            assertEquals(502, error.statusCode)
            assertTrue(error.isConnectivityFailure())
        }

        assertTrue(linkState.isUnreachable.value)
    }

    @Test
    fun successAfterFailure_clearsUnreachable() {
        val linkState = ServerLinkState()
        val client = ApiClient(tokenStorageFor(closedPort()), linkState, fastTimeouts)
        assertFailsWith<Exception> { runBlocking { client.getCategories() } }
        assertTrue(linkState.isUnreachable.value)

        withHttpServer(Response("200 OK", "[]")) { port ->
            val recovered = ApiClient(tokenStorageFor(port), linkState, fastTimeouts)
            runBlocking { recovered.getCategories() }
        }

        assertFalse(linkState.isUnreachable.value)
    }

    @Test
    fun refreshHangsAfter401_surfacesNetworkErrorAndKeepsSession() {
        val linkState = ServerLinkState()
        // Сервер ответил 401 на первый запрос и «завис» на refresh (второе соединение никто не принимает)
        withHttpServer(Response("401 Unauthorized", """{"error":"expired"}""")) { port ->
            val storage =
                tokenStorageFor(port).apply {
                    accessToken = "expired-access"
                    refreshToken = "refresh"
                }
            val client = ApiClient(storage, linkState, fastTimeouts)

            val error = assertFailsWith<Exception> { runBlocking { client.getCategories() } }

            assertTrue(error.isConnectivityFailure(), "ожидалась сетевая ошибка, а не 401: $error")
            assertEquals("refresh", storage.refreshToken, "сетевой сбой refresh не должен разлогинивать")
        }
        assertTrue(linkState.isUnreachable.value)
    }

    private fun tokenStorageFor(port: Int): TokenStorage =
        TokenStorage(
            settings = MapSettings(),
            secureTokenStorage =
                object : SecureTokenStorage {
                    override var accessToken: String? = null
                    override var refreshToken: String? = null

                    override fun clear() {
                        accessToken = null
                        refreshToken = null
                    }
                },
        ).apply { serverUrl = "http://127.0.0.1:$port" }

    /** Порт, на котором гарантированно никто не слушает. */
    private fun closedPort(): Int = ServerSocket(0).use { it.localPort }

    private class Response(
        val status: String,
        val body: String,
    )

    /** Отвечает на одно соединение заданным статусом и телом; следующие соединения не принимает. */
    private fun withHttpServer(
        response: Response,
        block: (port: Int) -> Unit,
    ) {
        ServerSocket(0).use { server ->
            val worker =
                thread(isDaemon = true) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (reader.readLine()?.isNotEmpty() == true) {
                            // читаем заголовки до пустой строки
                        }
                        val bytes = response.body.toByteArray()
                        socket.getOutputStream().apply {
                            write(
                                (
                                    "HTTP/1.1 ${response.status}\r\nContent-Type: application/json\r\n" +
                                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                                ).toByteArray(),
                            )
                            write(bytes)
                            flush()
                        }
                    }
                }
            block(server.localPort)
            worker.join(5_000)
        }
    }
}

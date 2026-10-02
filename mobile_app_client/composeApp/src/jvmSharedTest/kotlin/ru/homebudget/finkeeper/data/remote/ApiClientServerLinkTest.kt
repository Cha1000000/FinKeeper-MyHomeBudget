package ru.homebudget.finkeeper.data.remote

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.runBlocking
import ru.homebudget.finkeeper.data.network.ServerLinkState
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ApiClient сообщает индикатору, отвечает ли сервер: ответ с любым кодом — да, сетевая неудача — нет. */
class ApiClientServerLinkTest {
    @Test
    fun connectionRefused_marksServerUnreachable() {
        val linkState = ServerLinkState()
        val client = ApiClient(tokenStorageFor(closedPort()), linkState)

        assertFailsWith<Exception> { runBlocking { client.getCategories() } }

        assertTrue(linkState.isUnreachable.value)
    }

    @Test
    fun errorResponse_meansServerIsReachable() {
        val linkState = ServerLinkState()
        linkState.reportUnreachable()
        withHttpServer(status = "500 Internal Server Error", body = """{"error":"boom"}""") { port ->
            val client = ApiClient(tokenStorageFor(port), linkState)

            assertFailsWith<Exception> { runBlocking { client.getCategories() } }
        }

        assertFalse(linkState.isUnreachable.value)
    }

    @Test
    fun successAfterFailure_clearsUnreachable() {
        val linkState = ServerLinkState()
        val client = ApiClient(tokenStorageFor(closedPort()), linkState)
        assertFailsWith<Exception> { runBlocking { client.getCategories() } }
        assertTrue(linkState.isUnreachable.value)

        withHttpServer(status = "200 OK", body = "[]") { port ->
            val recovered = ApiClient(tokenStorageFor(port), linkState)
            runBlocking { recovered.getCategories() }
        }

        assertFalse(linkState.isUnreachable.value)
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

    /** Отвечает на один запрос заданным статусом и телом, затем закрывается. */
    private fun withHttpServer(
        status: String,
        body: String,
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
                        val bytes = body.toByteArray()
                        socket.getOutputStream().apply {
                            write(
                                (
                                    "HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
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

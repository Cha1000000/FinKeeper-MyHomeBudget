package ru.homebudget.finkeeper.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Доступность сервера по итогам HTTP-запросов: сеть на устройстве может быть, а сервер — молчать.
 * Любой ответ (кроме 502/503/504 от прокси) означает, что сервер жив; сетевая неудача
 * (таймаут, обрыв, DNS) — что нет.
 *
 * Запросы идут параллельно, поэтому учитывается только итог запроса, начатого не раньше
 * уже учтённого: медленный запрос, упавший по таймауту, не перетирает более свежий успех.
 */
class ServerLinkState {
    private val mutex = Mutex()
    private var lastStartedRequest = 0L
    private var lastAppliedRequest = 0L

    private val _isUnreachable = MutableStateFlow(false)
    val isUnreachable: StateFlow<Boolean> = _isUnreachable.asStateFlow()

    /** Регистрирует начало запроса; номер передаётся в [reportResult]. */
    suspend fun beginRequest(): Long = mutex.withLock { ++lastStartedRequest }

    suspend fun reportResult(
        requestId: Long,
        reachable: Boolean,
    ) {
        mutex.withLock {
            if (requestId < lastAppliedRequest) return
            lastAppliedRequest = requestId
            _isUnreachable.value = !reachable
        }
    }

    /** Отказ вне конкретного запроса (истёк бюджет фазы) — свежее всех уже начатых запросов. */
    suspend fun reportUnreachable() {
        reportResult(beginRequest(), reachable = false)
    }
}

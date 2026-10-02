package ru.homebudget.finkeeper.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Что запросили запустить: полную синхронизацию или только отправку очереди. */
internal sealed interface SyncRequest {
    data class Full(val monthId: Long?) : SyncRequest

    data object Queue : SyncRequest
}

/**
 * Сериализует запуски синхронизации: проверка «уже идёт» и захват — под одним замком.
 * Запрос, пришедший во время синхронизации, не теряется, а возвращается из [end] как
 * следующий к запуску (например, «сервер снова ответил», пока шёл синк, упавший по таймауту).
 * Синхронизация дольше [stuckAfterMillis] считается зависшей и не блокирует новую.
 */
internal class SyncGate(
    private val stuckAfterMillis: Long,
    private val onRunningChanged: (Boolean) -> Unit,
) {
    private val mutex = Mutex()
    private var running = false
    private var startedAt = 0L
    private var token = 0L
    private var deferredFull = false
    private var deferredMonthId: Long? = null
    private var deferredQueue = false

    /** Номер захваченной синхронизации или `null`: идёт другая, запрос отложен до её конца. */
    suspend fun tryBegin(
        request: SyncRequest,
        nowMillis: Long,
    ): Long? =
        mutex.withLock {
            if (running) {
                val elapsed = nowMillis - startedAt
                if (elapsed <= stuckAfterMillis) {
                    defer(request)
                    println("[SYNC] $request deferred: another sync is running for ${elapsed}ms")
                    return@withLock null
                }
                println("[SYNC] previous sync looks stuck (${elapsed}ms), starting a new one")
            }
            running = true
            startedAt = nowMillis
            onRunningChanged(true)
            ++token
        }

    /**
     * Освобождает синхронизацию [token] и возвращает отложенный запрос, который надо запустить.
     * Зависшая синхронизация, которую уже сменила новая, ничего не сбрасывает.
     */
    suspend fun end(token: Long): SyncRequest? =
        mutex.withLock {
            if (token != this.token) return@withLock null
            running = false
            onRunningChanged(false)
            takeDeferred()
        }

    private fun defer(request: SyncRequest) {
        when (request) {
            is SyncRequest.Full -> {
                deferredFull = true
                if (request.monthId != null) deferredMonthId = request.monthId
            }
            SyncRequest.Queue -> deferredQueue = true
        }
    }

    private fun takeDeferred(): SyncRequest? =
        when {
            deferredFull -> {
                val monthId = deferredMonthId
                deferredFull = false
                deferredMonthId = null
                // Полная синхронизация отправит и очередь
                deferredQueue = false
                SyncRequest.Full(monthId)
            }
            deferredQueue -> {
                deferredQueue = false
                SyncRequest.Queue
            }
            else -> null
        }
}

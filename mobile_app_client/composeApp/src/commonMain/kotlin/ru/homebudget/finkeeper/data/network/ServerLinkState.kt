package ru.homebudget.finkeeper.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Доступность сервера по итогам последнего HTTP-запроса: сеть на устройстве может быть,
 * а сервер — молчать. Любой ответ (даже с кодом ошибки) означает, что сервер жив;
 * сетевая неудача (таймаут, обрыв, DNS) — что нет.
 */
class ServerLinkState {
    private val _isUnreachable = MutableStateFlow(false)
    val isUnreachable: StateFlow<Boolean> = _isUnreachable.asStateFlow()

    fun reportReachable() {
        _isUnreachable.value = false
    }

    fun reportUnreachable() {
        _isUnreachable.value = true
    }
}

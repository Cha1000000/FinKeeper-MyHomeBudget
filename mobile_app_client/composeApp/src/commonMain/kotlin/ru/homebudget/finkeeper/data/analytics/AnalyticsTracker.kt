package ru.homebudget.finkeeper.data.analytics

import kotlinx.coroutines.CancellationException

/**
 * Кроссплатформенный интерфейс продуктовой аналитики и отчётов о нефатальных ошибках.
 *
 * На Android реализован через Yandex AppMetrica SDK ([AppMetricaAnalyticsTracker]),
 * на Desktop и iOS — заглушка [NoOpAnalyticsTracker].
 */
interface AnalyticsTracker {
    fun logScreen(screenName: String)

    fun logEvent(
        name: String,
        params: Map<String, Any> = emptyMap(),
    )

    fun logError(
        throwable: Throwable,
        context: String,
    )

    fun setUserId(userId: Long?)
}

/**
 * Заглушка для платформ без SDK аналитики и для unit-тестов.
 */
object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun logScreen(screenName: String) = Unit

    override fun logEvent(
        name: String,
        params: Map<String, Any>,
    ) = Unit

    override fun logError(
        throwable: Throwable,
        context: String,
    ) = Unit

    override fun setUserId(userId: Long?) = Unit
}

/**
 * Определяет ожидаемые (benign) исключения, которыми не стоит зашумлять отчёты об ошибках:
 * отмена корутин и типовые сетевые обрывы/таймауты в офлайне.
 */
fun isBenignAnalyticsThrowable(throwable: Throwable): Boolean {
    var current: Throwable? = throwable
    var depth = 0
    while (current != null && depth < 5) {
        if (current is CancellationException) return true
        val simpleName = current::class.simpleName.orEmpty()
        val qualifiedName = current::class.qualifiedName.orEmpty()
        if (
            simpleName in BENIGN_SIMPLE_NAMES ||
            qualifiedName.contains("CancellationException") ||
            qualifiedName.contains("SocketTimeoutException") ||
            qualifiedName.contains("UnknownHostException") ||
            qualifiedName.contains("ConnectException") ||
            qualifiedName.contains("NoRouteToHostException") ||
            qualifiedName.contains("HttpRequestTimeoutException") ||
            qualifiedName.contains("ConnectTimeoutException") ||
            qualifiedName.contains("SocketTimeoutException") ||
            qualifiedName.contains("UnresolvedAddressException") ||
            qualifiedName.contains("GaiException")
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

private val BENIGN_SIMPLE_NAMES =
    setOf(
        "CancellationException",
        "JobCancellationException",
        "ChildCancelledException",
        "UnknownHostException",
        "SocketTimeoutException",
        "ConnectException",
        "NoRouteToHostException",
        "HttpRequestTimeoutException",
        "ConnectTimeoutException",
        "UnresolvedAddressException",
        "GaiException",
    )

package ru.homebudget.finkeeper.data.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import ru.homebudget.finkeeper.data.remote.ApiException
import ru.homebudget.finkeeper.data.repository.Result

/**
 * Страховочный потолок фоновой фазы загрузки экрана. Зависший запрос и так ограничен HttpTimeout
 * (socket 15 с, request 30 с), а после первой сетевой ошибки фаза дальше не идёт — потолок
 * нужен лишь против очень медленной, но живой сети, и не должен обрывать её раньше времени.
 */
const val SERVER_PHASE_BUDGET_MILLIS = 60_000L

/**
 * Ответ прокси (nginx) о том, что сам сервер приложения недоступен или не ответил вовремя.
 * Для клиента это то же, что сетевой отказ: данные не получены и повтор позже имеет смысл.
 */
val GATEWAY_FAILURE_STATUS_CODES = setOf(502, 503, 504)

private const val MAX_CAUSE_DEPTH = 5

private fun Throwable.causeChain(): Sequence<Throwable> =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH)

/**
 * Сервер не ответил по сетевой причине: таймаут, обрыв, DNS, TLS, а также 502/503/504 от прокси.
 * Прочие ответы с кодом ошибки (`ApiException`) сюда не относятся — сервер при этом жив.
 */
fun Throwable.isConnectivityFailure(): Boolean =
    this !is CancellationException &&
        causeChain().any { it is IOException || (it is ApiException && it.statusCode in GATEWAY_FAILURE_STATUS_CODES) }

/**
 * Повтор такого запроса бессмыслен: сеть «зависла» или адрес не разрешается, а каждая попытка
 * стоит полный таймаут. Обрыв соединения (reset) сюда не входит — его повторить имеет смысл.
 */
fun Throwable.isTimeoutOrUnresolvable(): Boolean =
    causeChain().any {
        it is HttpRequestTimeoutException ||
            it is ConnectTimeoutException ||
            it is SocketTimeoutException ||
            it.isUnresolvableHost()
    }

/** Адрес сервера не разрешается (DNS). У каждого движка Ktor — своё исключение. */
internal expect fun Throwable.isUnresolvableHost(): Boolean

/**
 * Серия сетевых шагов с «коротким замыканием»: после первой сетевой неудачи остальные шаги
 * не выполняются (иначе каждый заново упирается в таймаут), а [isUnreachable] становится `true`.
 * Ошибка сервера (HTTP 500, битый ответ) шаг не прерывает дальнейшую фазу, а запоминается
 * в [serverError] — чтобы экран мог о ней сказать, а не выдать локальные данные за свежие.
 */
class ServerPhase {
    var isUnreachable: Boolean = false
        private set

    /** Первая не-сетевая ошибка шага (сервер ответил, но данные не получены). */
    var serverError: Throwable? = null
        private set

    /** `null` — шаг пропущен или упал (сетевая причина → [isUnreachable], иначе → [serverError]). */
    suspend fun <T> step(block: suspend () -> T): T? = runStep(recordServerError = true, block)

    /**
     * Шаг, ошибку сервера которого показывать не нужно (например, эндпоинт, которого нет
     * на старом сервере). Сетевая неудача по-прежнему замыкает фазу.
     */
    suspend fun <T> optionalStep(block: suspend () -> T): T? = runStep(recordServerError = false, block)

    /** Шаг репозитория, который сам глотает исключения и возвращает [Result]. */
    suspend fun <T> stepResult(block: suspend () -> Result<T>): Result<T>? {
        val result = step(block) ?: return null
        if (result is Result.Error) recordFailure(result.exception, recordServerError = true)
        return result
    }

    private suspend fun <T> runStep(
        recordServerError: Boolean,
        block: suspend () -> T,
    ): T? {
        if (isUnreachable) return null
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(e, recordServerError)
            null
        }
    }

    /** Сбой вне шага (например, чтение Room между шагами): учитывается так же, как сбой шага. */
    fun recordFailure(e: Throwable) = recordFailure(e, recordServerError = true)

    private fun recordFailure(
        e: Throwable,
        recordServerError: Boolean,
    ) {
        when {
            e.isConnectivityFailure() -> isUnreachable = true
            !recordServerError -> println("[SERVER-PHASE] optional step failed: ${e::class.simpleName}: ${e.message}")
            serverError == null -> {
                println("[SERVER-PHASE] server error: ${e::class.simpleName}: ${e.message}")
                serverError = e
            }
            else -> println("[SERVER-PHASE] another server error: ${e::class.simpleName}: ${e.message}")
        }
    }
}

/**
 * Итог фоновой фазы: [reachable] — сервер отвечал всю фазу и она уложилась в бюджет;
 * [serverError] — сервер ответил ошибкой хотя бы на одном шаге.
 */
data class ServerPhaseResult(
    val reachable: Boolean,
    val serverError: Throwable? = null,
)

/**
 * Выполняет [block] не дольше [budgetMillis]. Незавершённый шаг отменяется — pull-синхронизация
 * идемпотентна и безопасно дойдёт при следующем запуске. Истечение бюджета сообщается в
 * [linkState], чтобы индикатор связи не расходился с баннером «Нет связи» на экране.
 */
suspend fun runServerPhase(
    linkState: ServerLinkState? = null,
    budgetMillis: Long = SERVER_PHASE_BUDGET_MILLIS,
    block: suspend ServerPhase.() -> Unit,
): ServerPhaseResult {
    val phase = ServerPhase()
    val finished = withTimeoutOrNull(budgetMillis) { phase.block() } != null
    if (!finished) {
        println("[SERVER-PHASE] budget ${budgetMillis}ms exceeded")
        linkState?.reportUnreachable()
    }
    return ServerPhaseResult(
        reachable = finished && !phase.isUnreachable,
        serverError = phase.serverError,
    )
}

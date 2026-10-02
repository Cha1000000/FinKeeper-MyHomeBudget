package ru.homebudget.finkeeper.data.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import ru.homebudget.finkeeper.data.repository.Result

/** Сколько ждём сервер на фоновой фазе загрузки экрана, прежде чем считать его недоступным. */
const val SERVER_PHASE_BUDGET_MILLIS = 20_000L

private const val MAX_CAUSE_DEPTH = 5

private fun Throwable.causeChain(): Sequence<Throwable> =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH)

/**
 * Сервер не ответил по сетевой причине: таймаут, обрыв, DNS, TLS. Ответ с кодом ошибки
 * (`ApiException`) сюда не относится — сервер при этом жив.
 */
fun Throwable.isConnectivityFailure(): Boolean =
    this !is CancellationException && causeChain().any { it is IOException }

/**
 * Повтор такого запроса бессмыслен: сеть «зависла» или адрес не разрешается, а каждая попытка
 * стоит полный таймаут. Обрыв соединения (reset) сюда не входит — его повторить имеет смысл.
 */
fun Throwable.isTimeoutOrUnresolvable(): Boolean =
    causeChain().any {
        it is HttpRequestTimeoutException ||
            it is ConnectTimeoutException ||
            it is SocketTimeoutException ||
            it::class.simpleName == "UnknownHostException"
    }

/**
 * Серия сетевых шагов с «коротким замыканием»: после первой сетевой неудачи остальные шаги
 * не выполняются (иначе каждый заново упирается в таймаут), а [isUnreachable] становится `true`.
 */
class ServerPhase {
    var isUnreachable: Boolean = false
        private set

    fun markUnreachable() {
        isUnreachable = true
    }

    /** `null` — шаг пропущен или упал по сетевой причине. Ошибки сервера (HTTP) пробрасываются. */
    suspend fun <T> step(block: suspend () -> T): T? {
        if (isUnreachable) return null
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isConnectivityFailure()) {
                isUnreachable = true
                null
            } else {
                throw e
            }
        }
    }

    /** Шаг репозитория, который сам глотает исключения и возвращает [Result]. */
    suspend fun <T> stepResult(block: suspend () -> Result<T>): Result<T>? {
        val result = step(block) ?: return null
        if (result is Result.Error && result.exception.isConnectivityFailure()) {
            isUnreachable = true
        }
        return result
    }
}

/**
 * Выполняет [block] не дольше [budgetMillis]. Возвращает `true`, если сервер был доступен:
 * фаза уложилась в бюджет и ни один шаг не упал по сетевой причине. Незавершённый шаг
 * отменяется — pull-синхронизация идемпотентна и безопасно дойдёт при следующем запуске.
 */
suspend fun runServerPhase(
    budgetMillis: Long = SERVER_PHASE_BUDGET_MILLIS,
    block: suspend ServerPhase.() -> Unit,
): Boolean {
    val phase = ServerPhase()
    val finished = withTimeoutOrNull(budgetMillis) { phase.block() } != null
    return finished && !phase.isUnreachable
}

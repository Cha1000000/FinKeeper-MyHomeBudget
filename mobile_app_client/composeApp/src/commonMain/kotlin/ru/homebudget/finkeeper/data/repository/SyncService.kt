package ru.homebudget.finkeeper.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.homebudget.finkeeper.data.local.dao.MonthDao
import ru.homebudget.finkeeper.data.network.NetworkMonitor
import ru.homebudget.finkeeper.data.network.ServerLinkState
import ru.homebudget.finkeeper.data.remote.TokenStorage

/**
 * Сервис для автоматической синхронизации данных
 * Отслеживает состояние сети и запускает синхронизацию при появлении интернета
 */
class SyncService(
    private val networkMonitor: NetworkMonitor,
    private val syncManager: SyncManager,
    private val monthDao: MonthDao,
    private val tokenStorage: TokenStorage,
    private val serverLinkState: ServerLinkState,
) {
    // Своя Job на каждый запуск: после stop() сервис можно запустить снова (iOS останавливает
    // его при уходе в фон). Отменённый навсегда общий scope молча глушил бы все подписки
    private var runJob: Job? = null
    private var didSyncAfterLogin = false

    val dataUpdated: SharedFlow<Unit> get() = syncManager.dataUpdated

    /**
     * Запускает отслеживание сети и авто-синхронизацию
     */
    fun start() {
        if (runJob?.isActive == true) return
        val job = SupervisorJob()
        runJob = job
        val scope = CoroutineScope(job + Dispatchers.Default)

        // Запускаем мониторинг сети
        networkMonitor.startMonitoring()

        // Подписываемся на изменения состояния сети
        networkMonitor.isOnline
            .onEach { isOnline ->
                if (isOnline) {
                    // При появлении интернета запускаем синхронизацию
                    val monthId = getCurrentMonthLocalId()
                    syncManager.syncAll(monthId)
                }
            }.launchIn(scope)

        // Сеть на устройстве была всё время, а сервер не отвечал: операции в очереди уже не ждут
        // события «сеть появилась». Как только сервер снова ответил — отправляем накопленное
        var wasServerUnreachable = false
        serverLinkState.isUnreachable
            .onEach { unreachable ->
                if (unreachable) {
                    wasServerUnreachable = true
                } else if (wasServerUnreachable) {
                    wasServerUnreachable = false
                    println("[SYNC-SERVICE] Server is reachable again, syncing")
                    syncManager.syncAll(getCurrentMonthLocalId())
                }
            }.launchIn(scope)

        // Поллинг: ждём появления userId после логина и запускаем полную синхронизацию
        scope.launch {
            while (!didSyncAfterLogin) {
                delay(5_000)
                val userId = tokenStorage.userId
                if (userId > 0L && networkMonitor.isNetworkAvailable) {
                    println("[SYNC-SERVICE] Post-login sync triggered for userId=$userId")
                    didSyncAfterLogin = true
                    val monthId = getCurrentMonthLocalId()
                    syncManager.syncAll(monthId)
                }
            }
        }
    }

    /**
     * Останавливает отслеживание сети
     */
    fun stop() {
        val job = runJob ?: return
        runJob = null

        networkMonitor.stopMonitoring()
        job.cancel()
    }

    private fun getCurrentMonthLocalId(): Long? {
        val userId = tokenStorage.userId
        if (userId == 0L) return null
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return monthDao.getByUserAndYearMonth(userId, now.year.toLong(), now.monthNumber.toLong())?.id
    }
}

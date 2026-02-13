package ru.homebudget.finkeeper.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.homebudget.finkeeper.data.local.dao.MonthDao
import ru.homebudget.finkeeper.data.network.NetworkMonitor
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isStarted = false

    val dataUpdated: SharedFlow<Unit> get() = syncManager.dataUpdated

    /**
     * Запускает отслеживание сети и авто-синхронизацию
     */
    fun start() {
        if (isStarted) return
        isStarted = true

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
    }

    /**
     * Останавливает отслеживание сети
     */
    fun stop() {
        if (!isStarted) return
        isStarted = false

        networkMonitor.stopMonitoring()
        scope.cancel()
    }

    /**
     * Принудительная синхронизация
     */
    fun forceSync() {
        if (networkMonitor.isNetworkAvailable) {
            scope.launch {
                val monthId = getCurrentMonthLocalId()
                syncManager.syncAll(monthId)
            }
        }
    }

    private fun getCurrentMonthLocalId(): Long? {
        val userId = tokenStorage.userId
        if (userId == 0L) return null
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return monthDao.getByUserAndYearMonth(userId, now.year.toLong(), now.monthNumber.toLong())?.id
    }
}

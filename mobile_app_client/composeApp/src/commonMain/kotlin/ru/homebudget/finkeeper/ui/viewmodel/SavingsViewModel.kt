package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.network.ServerLinkState
import ru.homebudget.finkeeper.data.network.isConnectivityFailure
import ru.homebudget.finkeeper.data.network.runServerPhase
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsGoalRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsTransactionRepository
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.util.currentIsoDate

data class SavingsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val goals: List<SavingsGoal> = emptyList(),
    val totalSavings: Double = 0.0,
    /** Итог последней загрузки: его заменяет следующая загрузка. */
    val loadError: String? = null,
    /** Ошибка действия пользователя: живёт до «Скрыть» или следующего действия. */
    val actionError: String? = null,
    val isOffline: Boolean = false,
    /** Идёт серверная фаза загрузки (для индикатора на кнопке «Повторить»). */
    val isSyncing: Boolean = false,
) {
    /** Текст баннера ошибки экрана: ошибка действия важнее итога загрузки. */
    val error: String? get() = actionError ?: loadError
}

class SavingsViewModel(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val savingsTransactionRepository: SavingsTransactionRepository,
    private val monthRepository: MonthRepository,
    private val tokenStorage: TokenStorage,
    private val syncManager: SyncManager,
    private val serverLinkState: ServerLinkState,
) : ViewModel() {
    private val currentUserId: Long get() = tokenStorage.userId
    private val _state = MutableStateFlow(SavingsState())
    val state: StateFlow<SavingsState> = _state.asStateFlow()

    private val _savingsUpdated = MutableStateFlow(0)
    val savingsUpdated: StateFlow<Int> = _savingsUpdated.asStateFlow()

    init {
        observeSyncUpdates()
    }

    private fun observeSyncUpdates() {
        viewModelScope.launch {
            syncManager.dataUpdated.collect {
                loadData(showLoader = false, syncFromServer = false)
            }
        }
    }

    fun refreshData() {
        startServerLoad(refreshing = true)
    }

    fun clearError() {
        _state.value = _state.value.copy(loadError = null, actionError = null)
    }

    private var hasLoaded = false
    private var serverLoadJob: Job? = null
    private var localLoadJob: Job? = null

    /**
     * [syncFromServer] = `false` — только перечитать Room (после локальных правок и синка в фоне),
     * не прерывая идущую серверную загрузку.
     */
    fun loadData(
        showLoader: Boolean = true,
        syncFromServer: Boolean = true,
    ) {
        // Полноэкранный лоадер — только до первой публикации данных
        if (showLoader && !hasLoaded && !_state.value.isLoading) {
            _state.value = _state.value.copy(isLoading = true)
        }
        if (syncFromServer) startServerLoad(refreshing = false) else startLocalLoad()
    }

    // Новая серверная загрузка отменяет предыдущую: устаревшая не перезапишет свежий результат
    private fun startServerLoad(refreshing: Boolean) {
        val previous = serverLoadJob
        serverLoadJob =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                if (refreshing) _state.value = _state.value.copy(isRefreshing = true)
                try {
                    loadFromServer()
                } finally {
                    if (refreshing) _state.value = _state.value.copy(isRefreshing = false)
                }
            }
    }

    private fun startLocalLoad() {
        val previous = localLoadJob
        localLoadJob =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                try {
                    publishGoals()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportLoadFailure(e)
                }
            }
    }

    /**
     * Local-first: цели из Room показываем сразу, затем подтягиваем сервер (с бюджетом времени)
     * и перечитываем Room.
     */
    private suspend fun loadFromServer() {
        try {
            publishGoals()

            _state.value = _state.value.copy(isSyncing = true)
            val phase =
                try {
                    runServerPhase(serverLinkState) { stepResult { savingsGoalRepository.syncWithServer(currentUserId) } }
                } finally {
                    _state.value = _state.value.copy(isSyncing = false)
                }
            publishGoals()
            _state.value =
                _state.value.copy(
                    isOffline = !phase.reachable,
                    loadError = phase.serverError?.let { Strings.SERVER_REFRESH_FAILED },
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportLoadFailure(e)
        }
    }

    private fun reportLoadFailure(e: Exception) {
        println("[LOAD] savings load failed: ${e::class.simpleName}: ${e.message}")
        _state.value =
            _state.value.copy(
                isLoading = false,
                // Подробности — в лог: текст исключения Room пользователю ничего не скажет
                loadError = Strings.LOADING_ERROR,
                isOffline = e.isConnectivityFailure() || _state.value.isOffline,
            )
    }

    private suspend fun publishGoals() {
        // Сбой чтения Room — ошибка экрана, а не «копилок нет»
        val goals = savingsGoalRepository.getAllSavingsGoals(currentUserId).getOrThrow()
        val totalSavings = goals.sumOf { it.currentAmount }
        _state.value =
            _state.value.copy(
                isLoading = false,
                // Room прочитан — прежний сбой загрузки больше не актуален
                loadError = _state.value.loadError.withoutLocalLoadError(),
                goals = goals,
                totalSavings = totalSavings,
            )
        hasLoaded = true
    }

    fun createGoal(
        name: String,
        targetAmount: Double,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                // Сначала сохраняем локально через репозиторий
                savingsGoalRepository.createSavingsGoal(
                    userId = currentUserId,
                    name = name,
                    targetAmount = targetAmount,
                ).getOrThrow()

                loadData(showLoader = false, syncFromServer = false)
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    fun updateGoal(
        id: Int,
        name: String?,
        targetAmount: Double?,
        currentAmount: Double?,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                // Обновляем локально через репозиторий
                savingsGoalRepository.updateSavingsGoal(
                    id = id.toLong(),
                    name = name,
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                ).getOrThrow()

                loadData(showLoader = false, syncFromServer = false)
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    fun deleteGoal(id: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                // Удаляем локально через репозиторий
                savingsGoalRepository.deleteSavingsGoal(id.toLong()).getOrThrow()

                loadData(showLoader = false, syncFromServer = false)
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    fun addTransaction(
        goalId: Int,
        amount: Double,
    ) {
        println("[SAVINGS-VM] addTransaction: goalId=$goalId, amount=$amount, userId=$currentUserId")
        viewModelScope.launch {
            _state.value = _state.value.copy(actionError = null)
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

                // Получаем или создаём месяц локально (на сервер его зарегистрирует очередь синхронизации)
                val monthId =
                    monthRepository.getOrCreateMonth(currentUserId, now.year, now.monthNumber, registerOnServer = false)
                        .getOrThrow().localId
                println("[SAVINGS-VM] addTransaction: monthId=$monthId")

                // Текущую сумму берём из БД, а не из состояния экрана: оно могло устареть
                // (или цели в нём ещё нет), и тогда новая сумма затёрла бы настоящую
                val goal =
                    savingsGoalRepository.getAllSavingsGoals(currentUserId).getOrThrow()
                        .find { it.id == goalId }
                        ?: throw IllegalStateException("Savings goal $goalId not found")
                val oldAmount = goal.currentAmount
                val newAmount = oldAmount + amount
                println("[SAVINGS-VM] addTransaction: oldAmount=$oldAmount, newAmount=$newAmount")

                // Обновляем currentAmount только локально (без синхронизации на сервер).
                // Сервер сам обновит currentAmount при получении savings_transaction.
                savingsGoalRepository.updateCurrentAmountLocally(
                    id = goalId.toLong(),
                    currentAmount = newAmount,
                ).getOrThrow()
                println("[SAVINGS-VM] addTransaction: goal updated locally, newAmount=$newAmount")

                // Создаём транзакцию (enqueueSync запустит scheduleProcessQueue)
                try {
                    savingsTransactionRepository.createTransaction(
                        userId = currentUserId,
                        goalId = goalId.toLong(),
                        monthId = monthId,
                        amount = amount,
                        date = currentIsoDate(),
                        type = if (amount >= 0) "deposit" else "withdrawal",
                    ).getOrThrow()
                } catch (e: CancellationException) {
                    // Не откатываем: запись могла успеть сохраниться до отмены
                    throw e
                } catch (e: Exception) {
                    // Транзакции нет — откатываем сумму цели, иначе она разошлась бы с историей
                    val rollback = savingsGoalRepository.updateCurrentAmountLocally(id = goalId.toLong(), currentAmount = oldAmount)
                    if (rollback is ru.homebudget.finkeeper.data.repository.Result.Error) {
                        println("[SAVINGS-VM] addTransaction: rollback failed: ${rollback.exception.message}")
                    }
                    throw e
                }
                println("[SAVINGS-VM] addTransaction: transaction created")

                // Обновляем UI напрямую с новой суммой (без вызова loadData/syncWithServer)
                val updatedGoals = _state.value.goals.map { g ->
                    if (g.id == goalId) g.copy(currentAmount = newAmount) else g
                }
                val totalSavings = updatedGoals.sumOf { it.currentAmount }
                _state.value = _state.value.copy(goals = updatedGoals, totalSavings = totalSavings)
                println("[SAVINGS-VM] addTransaction: UI updated with newAmount=$newAmount")
                
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.toActionError())
            }
        }
    }

    private fun notifySavingsUpdated() {
        _savingsUpdated.value++
    }
}

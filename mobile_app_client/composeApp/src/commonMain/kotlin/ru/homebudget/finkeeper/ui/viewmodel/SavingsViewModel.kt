package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
import ru.homebudget.finkeeper.data.repository.onSuccess
import ru.homebudget.finkeeper.data.repository.savings.SavingsGoalRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsTransactionRepository
import ru.homebudget.finkeeper.util.currentIsoDate

data class SavingsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val goals: List<SavingsGoal> = emptyList(),
    val totalSavings: Double = 0.0,
    val error: String? = null,
    val isOffline: Boolean = false,
)

class SavingsViewModel(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val savingsTransactionRepository: SavingsTransactionRepository,
    private val monthRepository: MonthRepository,
    private val tokenStorage: TokenStorage,
    private val syncManager: SyncManager,
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
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            loadDataSuspend()
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    fun loadData(
        showLoader: Boolean = true,
        syncFromServer: Boolean = true,
    ) {
        viewModelScope.launch {
            if (showLoader) {
                _state.value = _state.value.copy(isLoading = true, error = null)
            } else {
                _state.value = _state.value.copy(error = null)
            }
            loadDataSuspend(syncFromServer)
        }
    }

    private suspend fun loadDataSuspend(syncFromServer: Boolean = true) {
        try {
            // Получаем цели через репозиторий
            var goals: List<SavingsGoal> = emptyList()
            savingsGoalRepository
                .getAllSavingsGoals(currentUserId)
                .onSuccess { goalList ->
                    goals = goalList
                }

            if (syncFromServer) {
                savingsGoalRepository.syncWithServer(currentUserId)
            }

            val totalSavings = goals.sumOf { it.currentAmount }
            _state.value = _state.value.copy(isLoading = false, goals = goals, totalSavings = totalSavings, isOffline = false)
        } catch (e: Exception) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки",
                    isOffline = true,
                )
        }
    }

    fun createGoal(
        name: String,
        targetAmount: Double,
    ) {
        viewModelScope.launch {
            try {
                // Сначала сохраняем локально через репозиторий
                savingsGoalRepository.createSavingsGoal(
                    userId = currentUserId,
                    name = name,
                    targetAmount = targetAmount,
                )

                loadData(showLoader = false, syncFromServer = false)
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
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
            try {
                // Обновляем локально через репозиторий
                savingsGoalRepository.updateSavingsGoal(
                    id = id.toLong(),
                    name = name,
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                )

                loadData(showLoader = false, syncFromServer = false)
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteGoal(id: Int) {
        viewModelScope.launch {
            try {
                // Удаляем локально через репозиторий
                savingsGoalRepository.deleteSavingsGoal(id.toLong())

                loadData(showLoader = false, syncFromServer = false)
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addTransaction(
        goalId: Int,
        amount: Double,
    ) {
        println("[SAVINGS-VM] addTransaction: goalId=$goalId, amount=$amount, userId=$currentUserId")
        viewModelScope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

                // Получаем или создаём месяц (нужен для корректной работы синхронизации)
                val monthResult = monthRepository.getOrCreateMonth(currentUserId, now.year, now.monthNumber)
                if (!monthResult.isSuccess) {
                    throw Exception("Failed to get or create month")
                }
                println("[SAVINGS-VM] addTransaction: monthId=${monthResult.getOrNull()?.localId}")

                // Получаем текущую цель для обновления currentAmount
                val currentGoals = _state.value.goals
                val goal = currentGoals.find { it.id == goalId }
                val oldAmount = goal?.currentAmount ?: 0.0
                val newAmount = oldAmount + amount
                println("[SAVINGS-VM] addTransaction: oldAmount=$oldAmount, newAmount=$newAmount")

                // Обновляем currentAmount только локально (без синхронизации на сервер).
                // Сервер сам обновит currentAmount при получении savings_transaction.
                savingsGoalRepository.updateCurrentAmountLocally(
                    id = goalId.toLong(),
                    currentAmount = newAmount,
                )
                println("[SAVINGS-VM] addTransaction: goal updated locally, newAmount=$newAmount")

                // Создаём транзакцию (enqueueSync запустит scheduleProcessQueue)
                val monthId = monthResult.getOrNull()?.localId
                savingsTransactionRepository.createTransaction(
                    userId = currentUserId,
                    goalId = goalId.toLong(),
                    monthId = monthId,
                    amount = amount,
                    date = currentIsoDate(),
                    type = if (amount >= 0) "deposit" else "withdrawal",
                )
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
                println("[SAVINGS-VM] addTransaction ERROR: ${e.message}")
                e.printStackTrace()
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private fun notifySavingsUpdated() {
        _savingsUpdated.value++
    }
}

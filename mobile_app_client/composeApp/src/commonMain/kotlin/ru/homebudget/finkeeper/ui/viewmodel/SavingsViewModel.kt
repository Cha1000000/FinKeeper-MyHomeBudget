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
                loadData()
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

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            loadDataSuspend()
        }
    }

    private suspend fun loadDataSuspend() {
        try {
            // Получаем цели через репозиторий
            var goals: List<SavingsGoal> = emptyList()
            savingsGoalRepository
                .getAllSavingsGoals(currentUserId)
                .onSuccess { goalList ->
                    goals = goalList
                }

            // Синхронизируем с сервером
            savingsGoalRepository.syncWithServer(currentUserId)

            _state.value = _state.value.copy(isLoading = false, goals = goals, isOffline = false)
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

                loadData()
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

                loadData()
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

                loadData()
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
        viewModelScope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

                // Получаем или создаём месяц
                val monthResult = monthRepository.getOrCreateMonth(currentUserId, now.year, now.monthNumber)
                val monthData =
                    if (monthResult.isSuccess) {
                        monthResult.getOrNull()!!
                    } else {
                        throw Exception("Failed to get or create month")
                    }

                // Сначала сохраняем локально через репозиторий
                savingsTransactionRepository.createTransaction(
                    userId = currentUserId,
                    goalId = goalId.toLong(),
                    amount = amount,
                    date = currentIsoDate(),
                    type = "deposit",
                )

                loadData()
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private fun notifySavingsUpdated() {
        _savingsUpdated.value++
    }
}

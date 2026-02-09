package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.util.currentIsoDate
import ru.homebudget.finkeeper.util.RetryConfig
import ru.homebudget.finkeeper.util.withRetry

data class SavingsState(
    val isLoading: Boolean = true,
    val goals: List<SavingsGoal> = emptyList(),
    val error: String? = null
)

class SavingsViewModel(
    private val apiClient: ApiClient
) : ViewModel() {

    private val _state = MutableStateFlow(SavingsState())
    val state: StateFlow<SavingsState> = _state.asStateFlow()

    private val _savingsUpdated = MutableStateFlow(0)
    val savingsUpdated: StateFlow<Int> = _savingsUpdated.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val goals = withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.getSavingsGoals()
                }
                _state.value = _state.value.copy(isLoading = false, goals = goals)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки"
                )
            }
        }
    }

    fun createGoal(name: String, targetAmount: Double) {
        viewModelScope.launch {
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.createSavingsGoal(CreateSavingsGoalRequest(name, targetAmount))
                }
                loadData()
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateGoal(id: Int, name: String?, targetAmount: Double?, currentAmount: Double?) {
        viewModelScope.launch {
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.updateSavingsGoal(id, UpdateSavingsGoalRequest(name, targetAmount, currentAmount))
                }
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
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.deleteSavingsGoal(id)
                }
                loadData()
                notifySavingsUpdated()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addTransaction(goalId: Int, amount: Double) {
        viewModelScope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val monthData = withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.ensureMonth(now.year, now.monthNumber)
                }
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.addSavingsTransaction(AddSavingsTransactionRequest(
                        goalId = goalId,
                        amount = amount,
                        date = currentIsoDate(),
                        monthId = monthData.id
                    ))
                }
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

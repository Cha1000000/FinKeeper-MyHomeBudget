package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.util.RetryConfig
import ru.homebudget.finkeeper.util.withRetry

data class CategoriesState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    val activeTab: Int = 0, // 0 = categories, 1 = income sources
    val error: String? = null
)

class CategoriesViewModel(
    private val apiClient: ApiClient
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val categories = withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.getCategories()
                }
                val incomeSources = withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.getIncomeSources()
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    categories = categories,
                    incomeSources = incomeSources
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки"
                )
            }
        }
    }

    fun setActiveTab(tab: Int) {
        _state.value = _state.value.copy(activeTab = tab)
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.createCategory(name)
                }
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateCategory(id: Int, name: String) {
        viewModelScope.launch {
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.updateCategory(id, UpdateCategoryRequest(name = name))
                }
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deactivateCategory(id: Int) {
        viewModelScope.launch {
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.updateCategory(id, UpdateCategoryRequest(isActive = 0))
                }
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addIncomeSource(name: String) {
        viewModelScope.launch {
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.createIncomeSource(name)
                }
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateIncomeSource(id: Int, name: String) {
        viewModelScope.launch {
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.updateIncomeSource(id, UpdateIncomeSourceRequest(name = name))
                }
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deactivateIncomeSource(id: Int) {
        viewModelScope.launch {
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.updateIncomeSource(id, UpdateIncomeSourceRequest(isActive = 0))
                }
                loadData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
}

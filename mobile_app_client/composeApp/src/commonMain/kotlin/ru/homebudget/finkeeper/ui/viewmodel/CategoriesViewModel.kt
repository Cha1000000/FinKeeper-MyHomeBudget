package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homebudget.finkeeper.data.model.Category
import ru.homebudget.finkeeper.data.model.IncomeSource
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeSourceRepository
import ru.homebudget.finkeeper.data.repository.onSuccess

data class CategoriesState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val categories: List<Category> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    val activeTab: Int = 0, // 0 = categories, 1 = income sources
    val isReorderMode: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
)

class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
    private val incomeSourceRepository: IncomeSourceRepository,
    private val api: ApiClient,
    private val tokenStorage: TokenStorage,
    private val syncManager: SyncManager,
) : ViewModel() {
    private val currentUserId: Long get() = tokenStorage.userId
    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    init {
        loadData()
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
            _state.update { it.copy(isRefreshing = true) }
            loadDataSuspend()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            loadDataSuspend()
        }
    }

    private suspend fun loadDataSuspend() {

        try {
            // Получаем категории через репозиторий
            var categories: List<Category> = emptyList()
            categoryRepository
                .getAllCategories(currentUserId)
                .onSuccess { categoryList ->
                    categories = categoryList
                }

            // Синхронизируем категории с сервером
            categoryRepository.syncWithServer(currentUserId)

            // Получаем источники дохода через репозиторий
            var incomeSources: List<IncomeSource> = emptyList()
            incomeSourceRepository
                .getAllIncomeSources(currentUserId)
                .onSuccess { sourceList ->
                    incomeSources = sourceList
                }

            // Синхронизируем источники дохода с сервером
            incomeSourceRepository.syncWithServer(currentUserId)

            _state.update {
                it.copy(
                    isLoading = false,
                    categories = categories,
                    incomeSources = incomeSources,
                    isOffline = false,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки данных",
                    isOffline = true,
                )
            }
        }
    }

    fun setActiveTab(tab: Int) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun toggleReorderMode() {
        _state.update { it.copy(isReorderMode = !it.isReorderMode) }
    }

    fun updateCategoriesOrder(categories: List<Category>) {
        _state.update { it.copy(categories = categories) }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                // Сначала сохраняем локально через репозиторий
                categoryRepository.createCategory(
                    userId = currentUserId,
                    name = name,
                    type = "expense",
                )

                loadData()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка создания категории") }
            }
        }
    }

    fun updateCategory(
        id: Int,
        name: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                // Обновляем локально через репозиторий
                categoryRepository.updateCategory(
                    id = id.toLong(),
                    name = name,
                )

                loadData()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка обновления категории") }
            }
        }
    }

    fun deactivateCategory(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                // Обновляем локально через репозиторий
                categoryRepository.updateCategory(
                    id = id.toLong(),
                    isActive = false,
                )

                loadData()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления категории") }
            }
        }
    }

    fun addIncomeSource(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                // Сначала сохраняем локально через репозиторий
                incomeSourceRepository.createIncomeSource(
                    userId = currentUserId,
                    name = name,
                )

                loadData()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка создания источника дохода") }
            }
        }
    }

    fun updateIncomeSource(
        id: Int,
        name: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                // Обновляем локально через репозиторий
                incomeSourceRepository.updateIncomeSource(
                    id = id.toLong(),
                    name = name,
                )

                loadData()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка обновления источника дохода") }
            }
        }
    }

    fun deactivateIncomeSource(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                // Обновляем локально через репозиторий
                incomeSourceRepository.updateIncomeSource(
                    id = id.toLong(),
                    isActive = false,
                )

                loadData()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления источника дохода") }
            }
        }
    }

    fun reorderCategories(categories: List<Category>) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                val ids = categories.map { cat -> cat.id }

                // Отправляем на сервер (reorder пока только серверный)
                try {
                    api.reorderCategories(ids)
                } catch (_: Exception) {
                    // Офлайн — порядок применится при следующей синхронизации
                }
                loadData()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка изменения порядка категорий") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

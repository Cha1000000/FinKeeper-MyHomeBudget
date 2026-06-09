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
    val fixedCategories: List<Category> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    val fixedIncomeSources: List<IncomeSource> = emptyList(),
    val activeTab: Int = 0, // 0 = categories, 1 = income sources
    val isReorderMode: Boolean = false,
    val isIncomeSourceReorderMode: Boolean = false,
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
                // Игнорируем обновления во время режима сортировки
                if (!_state.value.isReorderMode && !_state.value.isIncomeSourceReorderMode) {
                    loadData(showLoader = false, syncFromServer = false)
                }
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

    fun loadData(
        showLoader: Boolean = true,
        syncFromServer: Boolean = true,
    ) {
        viewModelScope.launch {
            if (showLoader) {
                _state.update { it.copy(isLoading = true, error = null) }
            } else {
                _state.update { it.copy(error = null) }
            }
            loadDataSuspend(syncFromServer)
        }
    }

    private suspend fun loadDataSuspend(syncFromServer: Boolean = true) {

        try {
            // Получаем категории через репозиторий
            var categories: List<Category> = emptyList()
            categoryRepository
                .getAllCategories(currentUserId)
                .onSuccess { categoryList ->
                    categories = categoryList
                }

            if (syncFromServer) {
                categoryRepository.syncWithServer(currentUserId)
            }

            // Получаем источники дохода через репозиторий
            var incomeSources: List<IncomeSource> = emptyList()
            incomeSourceRepository
                .getAllIncomeSources(currentUserId)
                .onSuccess { sourceList ->
                    incomeSources = sourceList
                }

            if (syncFromServer) {
                incomeSourceRepository.syncWithServer(currentUserId)
            }

            // Разделяем на обычные и фиксированные
            val regularCategories = categories.filter { it.isFixed == 0 && it.isActive == 1 }
            val fixedCats = categories.filter { it.isFixed == 1 && it.isActive == 1 }
            val regularSources = incomeSources.filter { it.isFixed == 0 && it.isActive == 1 }
            val fixedSrcs = incomeSources.filter { it.isFixed == 1 && it.isActive == 1 }

            _state.update {
                it.copy(
                    isLoading = false,
                    categories = regularCategories,
                    fixedCategories = fixedCats,
                    incomeSources = regularSources,
                    fixedIncomeSources = fixedSrcs,
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

    fun toggleIncomeSourceReorderMode() {
        _state.update { it.copy(isIncomeSourceReorderMode = !it.isIncomeSourceReorderMode) }
    }

    fun updateCategoriesOrder(categories: List<Category>) {
        _state.update { it.copy(categories = categories) }
    }

    fun updateIncomeSourcesOrder(incomeSources: List<IncomeSource>) {
        _state.update { it.copy(incomeSources = incomeSources) }
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

                loadData(showLoader = false, syncFromServer = false)
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

                loadData(showLoader = false, syncFromServer = false)
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

                loadData(showLoader = false, syncFromServer = false)
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

                loadData(showLoader = false, syncFromServer = false)
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

                loadData(showLoader = false, syncFromServer = false)
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

                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления источника дохода") }
            }
        }
    }

    fun reorderCategories(categories: List<Category>) {
        viewModelScope.launch {
            _state.update { it.copy(error = null, categories = categories) }
            try {
                // Конвертируем локальные ID в серверные для отправки на сервер
                val mapping = categoryRepository.getServerIdMapping(currentUserId)
                val serverIds = categories.mapNotNull { cat -> mapping[cat.id] }

                // Отправляем на сервер
                if (serverIds.size == categories.size) {
                    try {
                        api.reorderCategories(serverIds)
                    } catch (_: Exception) {
                        // Офлайн — порядок применится при следующей синхронизации
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка изменения порядка категорий") }
            }
        }
    }

    fun reorderIncomeSources(incomeSources: List<IncomeSource>) {
        viewModelScope.launch {
            _state.update { it.copy(error = null, incomeSources = incomeSources) }
            try {
                // Конвертируем локальные ID в серверные для отправки на сервер
                val mapping = incomeSourceRepository.getServerIdMapping(currentUserId)
                val serverIds = incomeSources.mapNotNull { src -> mapping[src.id] }

                if (serverIds.size == incomeSources.size) {
                    try {
                        api.reorderIncomeSources(serverIds)
                    } catch (_: Exception) {
                        // Офлайн — порядок применится при следующей синхронизации
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка изменения порядка источников дохода") }
            }
        }
    }

    // --- Фиксированные категории ---

    fun addFixedCategory(name: String, fixedAmount: Double, autoDay: Int) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                categoryRepository.createCategory(
                    userId = currentUserId,
                    name = name,
                    type = "expense",
                    isFixed = true,
                    fixedAmount = fixedAmount,
                    autoDay = autoDay,
                )
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка создания фиксированной категории") }
            }
        }
    }

    fun updateFixedCategory(id: Int, name: String? = null, fixedAmount: Double? = null, autoDay: Int? = null) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                categoryRepository.updateCategory(
                    id = id.toLong(),
                    name = name,
                    isFixed = true,
                    fixedAmount = fixedAmount,
                    autoDay = autoDay,
                )
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка обновления фиксированной категории") }
            }
        }
    }

    fun deactivateFixedCategory(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                categoryRepository.updateCategory(
                    id = id.toLong(),
                    isActive = false,
                )
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления фиксированной категории") }
            }
        }
    }

    // --- Фиксированные источники дохода ---

    fun addFixedIncomeSource(name: String, fixedAmount: Double, autoDay: Int) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                incomeSourceRepository.createIncomeSource(
                    userId = currentUserId,
                    name = name,
                    isFixed = true,
                    fixedAmount = fixedAmount,
                    autoDay = autoDay,
                )
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка создания фиксированного источника дохода") }
            }
        }
    }

    fun updateFixedIncomeSource(id: Int, name: String? = null, fixedAmount: Double? = null, autoDay: Int? = null) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                incomeSourceRepository.updateIncomeSource(
                    id = id.toLong(),
                    name = name,
                    isFixed = true,
                    fixedAmount = fixedAmount,
                    autoDay = autoDay,
                )
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка обновления фиксированного источника дохода") }
            }
        }
    }

    fun deactivateFixedIncomeSource(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                incomeSourceRepository.updateIncomeSource(
                    id = id.toLong(),
                    isActive = false,
                )
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления фиксированного источника дохода") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

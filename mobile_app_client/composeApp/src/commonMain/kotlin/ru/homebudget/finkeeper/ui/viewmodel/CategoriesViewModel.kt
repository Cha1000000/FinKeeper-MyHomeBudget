package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homebudget.finkeeper.data.model.Category
import ru.homebudget.finkeeper.data.model.IncomeSource
import ru.homebudget.finkeeper.data.network.ServerLinkState
import ru.homebudget.finkeeper.data.network.isConnectivityFailure
import ru.homebudget.finkeeper.data.network.runServerPhase
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeSourceRepository
import ru.homebudget.finkeeper.ui.Strings

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

class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
    private val incomeSourceRepository: IncomeSourceRepository,
    private val tokenStorage: TokenStorage,
    private val syncManager: SyncManager,
    private val serverLinkState: ServerLinkState,
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
        startServerLoad(refreshing = true)
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
        if (showLoader && !hasLoaded) _state.update { it.copy(isLoading = true) }
        if (syncFromServer) startServerLoad(refreshing = false) else startLocalLoad()
    }

    // Новая серверная загрузка отменяет предыдущую: устаревшая не перезапишет свежий результат
    private fun startServerLoad(refreshing: Boolean) {
        val previous = serverLoadJob
        serverLoadJob =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                if (refreshing) _state.update { it.copy(isRefreshing = true) }
                try {
                    loadFromServer()
                } finally {
                    if (refreshing) _state.update { it.copy(isRefreshing = false) }
                }
            }
    }

    private fun startLocalLoad() {
        val previous = localLoadJob
        localLoadJob =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                try {
                    publishLocalData()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportLoadFailure(e)
                }
            }
    }

    /**
     * Local-first: данные из Room показываем сразу, затем подтягиваем сервер (с бюджетом времени)
     * и перечитываем Room.
     */
    private suspend fun loadFromServer() {
        try {
            publishLocalData()

            _state.update { it.copy(isSyncing = true) }
            val phase =
                try {
                    runServerPhase(serverLinkState) {
                        stepResult { categoryRepository.syncWithServer(currentUserId) }
                        stepResult { incomeSourceRepository.syncWithServer(currentUserId) }
                    }
                } finally {
                    _state.update { it.copy(isSyncing = false) }
                }
            publishLocalData()
            _state.update {
                it.copy(
                    isOffline = !phase.reachable,
                    loadError = phase.serverError?.let { Strings.SERVER_REFRESH_FAILED },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportLoadFailure(e)
        }
    }

    private fun reportLoadFailure(e: Exception) {
        println("[LOAD] categories load failed: ${e::class.simpleName}: ${e.message}")
        _state.update {
            it.copy(
                isLoading = false,
                // Подробности — в лог: текст исключения Room пользователю ничего не скажет
                loadError = Strings.LOADING_ERROR,
                isOffline = e.isConnectivityFailure() || it.isOffline,
            )
        }
    }

    private suspend fun publishLocalData() {
        // Сбой чтения Room — ошибка экрана, а не пустые списки
        val categories = categoryRepository.getAllCategories(currentUserId).getOrThrow()
        val incomeSources = incomeSourceRepository.getAllIncomeSources(currentUserId).getOrThrow()

        // Разделяем на обычные и фиксированные
        val regularCategories = categories.filter { it.isFixed == 0 && it.isActive == 1 }
        val fixedCats = categories.filter { it.isFixed == 1 && it.isActive == 1 }
        val regularSources = incomeSources.filter { it.isFixed == 0 && it.isActive == 1 }
        val fixedSrcs = incomeSources.filter { it.isFixed == 1 && it.isActive == 1 }

        _state.update {
            it.copy(
                isLoading = false,
                // Room прочитан — прежний сбой загрузки больше не актуален
                loadError = it.loadError.withoutLocalLoadError(),
                categories = regularCategories,
                fixedCategories = fixedCats,
                incomeSources = regularSources,
                fixedIncomeSources = fixedSrcs,
            )
        }
        hasLoaded = true
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
            _state.update { it.copy(actionError = null) }
            try {
                // Сначала сохраняем локально через репозиторий
                categoryRepository.createCategory(
                    userId = currentUserId,
                    name = name,
                    type = "expense",
                ).getOrThrow()

                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_CREATING_CATEGORY)) }
            }
        }
    }

    fun updateCategory(
        id: Int,
        name: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                // Обновляем локально через репозиторий
                categoryRepository.updateCategory(
                    id = id.toLong(),
                    name = name,
                ).getOrThrow()

                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_UPDATING_CATEGORY)) }
            }
        }
    }

    fun deactivateCategory(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                // Обновляем локально через репозиторий
                categoryRepository.updateCategory(
                    id = id.toLong(),
                    isActive = false,
                ).getOrThrow()

                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_DELETING_CATEGORY)) }
            }
        }
    }

    fun addIncomeSource(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                // Сначала сохраняем локально через репозиторий
                incomeSourceRepository.createIncomeSource(
                    userId = currentUserId,
                    name = name,
                ).getOrThrow()

                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_CREATING_INCOME_SOURCE)) }
            }
        }
    }

    fun updateIncomeSource(
        id: Int,
        name: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                // Обновляем локально через репозиторий
                incomeSourceRepository.updateIncomeSource(
                    id = id.toLong(),
                    name = name,
                ).getOrThrow()

                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_UPDATING_INCOME_SOURCE)) }
            }
        }
    }

    fun deactivateIncomeSource(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                // Обновляем локально через репозиторий
                incomeSourceRepository.updateIncomeSource(
                    id = id.toLong(),
                    isActive = false,
                ).getOrThrow()

                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_DELETING_INCOME_SOURCE)) }
            }
        }
    }

    fun reorderCategories(categories: List<Category>) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null, categories = categories) }
            try {
                // Порядок сохраняется локально и уходит на сервер через очередь (работает и офлайн)
                categoryRepository.reorderCategories(currentUserId, categories.map { it.id.toLong() }).getOrThrow()
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_REORDERING_CATEGORIES)) }
            }
        }
    }

    fun reorderIncomeSources(incomeSources: List<IncomeSource>) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null, incomeSources = incomeSources) }
            try {
                incomeSourceRepository.reorderIncomeSources(currentUserId, incomeSources.map { it.id.toLong() }).getOrThrow()
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_REORDERING_INCOME_SOURCES)) }
            }
        }
    }

    // --- Фиксированные категории ---

    fun addFixedCategory(name: String, fixedAmount: Double, autoDay: Int, requireConfirm: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                categoryRepository.createCategory(
                    userId = currentUserId,
                    name = name,
                    type = "expense",
                    isFixed = true,
                    fixedAmount = fixedAmount,
                    autoDay = autoDay,
                    requireConfirm = requireConfirm,
                ).getOrThrow()
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_CREATING_FIXED_CATEGORY)) }
            }
        }
    }

    fun updateFixedCategory(id: Int, name: String? = null, fixedAmount: Double? = null, autoDay: Int? = null, requireConfirm: Boolean? = null) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                categoryRepository.updateCategory(
                    id = id.toLong(),
                    name = name,
                    isFixed = true,
                    fixedAmount = fixedAmount,
                    autoDay = autoDay,
                    requireConfirm = requireConfirm,
                ).getOrThrow()
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_UPDATING_FIXED_CATEGORY)) }
            }
        }
    }

    fun deactivateFixedCategory(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                categoryRepository.updateCategory(
                    id = id.toLong(),
                    isActive = false,
                ).getOrThrow()
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_DELETING_FIXED_CATEGORY)) }
            }
        }
    }

    // --- Фиксированные источники дохода ---

    fun addFixedIncomeSource(name: String, fixedAmount: Double, autoDay: Int, requireConfirm: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                incomeSourceRepository.createIncomeSource(
                    userId = currentUserId,
                    name = name,
                    isFixed = true,
                    fixedAmount = fixedAmount,
                    autoDay = autoDay,
                    requireConfirm = requireConfirm,
                ).getOrThrow()
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_CREATING_FIXED_INCOME_SOURCE)) }
            }
        }
    }

    fun updateFixedIncomeSource(id: Int, name: String? = null, fixedAmount: Double? = null, autoDay: Int? = null, requireConfirm: Boolean? = null) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                incomeSourceRepository.updateIncomeSource(
                    id = id.toLong(),
                    name = name,
                    isFixed = true,
                    fixedAmount = fixedAmount,
                    autoDay = autoDay,
                    requireConfirm = requireConfirm,
                ).getOrThrow()
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_UPDATING_FIXED_INCOME_SOURCE)) }
            }
        }
    }

    fun deactivateFixedIncomeSource(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                incomeSourceRepository.updateIncomeSource(
                    id = id.toLong(),
                    isActive = false,
                ).getOrThrow()
                loadData(showLoader = false, syncFromServer = false)
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.toActionError(Strings.ERROR_DELETING_FIXED_INCOME_SOURCE)) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(loadError = null, actionError = null) }
    }
}

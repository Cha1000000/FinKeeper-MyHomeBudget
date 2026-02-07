package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.ApiException

data class SettingsState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(
    private val apiClient: ApiClient
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun updateUsername(username: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null)
            try {
                apiClient.updateUsername(username)
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Имя пользователя успешно обновлено.",
                    statusIsError = false
                )
                onSuccess()
            } catch (e: ApiException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = e.message,
                    statusIsError = true
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Ошибка обновления имени.",
                    statusIsError = true
                )
            }
        }
    }

    fun updatePassword(password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null)
            try {
                apiClient.updatePassword(password)
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Пароль успешно изменен.",
                    statusIsError = false
                )
            } catch (e: ApiException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = e.message,
                    statusIsError = true
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Ошибка смены пароля.",
                    statusIsError = true
                )
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null)
            try {
                apiClient.createManualBackup()
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Резервная копия успешно создана.",
                    statusIsError = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Ошибка создания резервной копии.",
                    statusIsError = true
                )
            }
        }
    }

    fun restoreBackup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null)
            try {
                apiClient.restoreBackup()
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Данные успешно восстановлены.",
                    statusIsError = false
                )
                onSuccess()
            } catch (e: ApiException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = e.message,
                    statusIsError = true
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Ошибка восстановления данных.",
                    statusIsError = true
                )
            }
        }
    }

    fun clearStatus() {
        _state.value = _state.value.copy(statusMessage = null)
    }
}

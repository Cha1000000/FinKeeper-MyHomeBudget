package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.text.*
import ru.homebudget.finkeeper.data.model.User
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.ApiException
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.util.RetryConfig
import ru.homebudget.finkeeper.util.withRetry
import ru.homebudget.finkeeper.ui.Strings

data class AuthState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        checkAuth()
    }

    private fun checkAuth() {
        val token = tokenStorage.token
        if (token == null) {
            _state.value = AuthState(isLoading = false)
            return
        }
        viewModelScope.launch {
            try {
                val user = withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.getMe()
                }
                tokenStorage.userId = user.id.toLong()
                _state.value = AuthState(user = user, isLoading = false, isAuthenticated = true)
            } catch (_: Exception) {
                tokenStorage.clear()
                _state.value = AuthState(isLoading = false)
            }
        }
    }

    fun login(username: String, password: String) {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = Strings.ENTER_USERNAME_AND_PASSWORD
            )
            return
        }
        if (password.length < 6) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = Strings.PASSWORD_TOO_SHORT
            )
            return
        }
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val authData = withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.login(trimmedUsername, password)
                }
                tokenStorage.token = authData.token
                tokenStorage.userId = authData.user.id.toLong()
                _state.value = AuthState(
                    user = authData.user,
                    isLoading = false,
                    isAuthenticated = true
                )
            } catch (e: ApiException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = if (e.statusCode == 401) Strings.INVALID_USERNAME_OR_PASSWORD else e.message
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = Strings.CONNECTION_ERROR
                )
            }
        }
    }

    fun register(username: String, password: String) {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = Strings.ENTER_USERNAME_AND_PASSWORD
            )
            return
        }
        if (password.length < 6) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = Strings.PASSWORD_TOO_SHORT
            )
            return
        }
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val authData = withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.register(trimmedUsername, password)
                }
                tokenStorage.token = authData.token
                tokenStorage.userId = authData.user.id.toLong()
                _state.value = AuthState(
                    user = authData.user,
                    isLoading = false,
                    isAuthenticated = true
                )
            } catch (e: ApiException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = if (e.statusCode == 401) Strings.INVALID_USERNAME_OR_PASSWORD else e.message
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = Strings.CONNECTION_ERROR
                )
            }
        }
    }

    fun logout() {
        tokenStorage.clear()
        _state.value = AuthState(isLoading = false)
    }

    fun updateUser(user: User) {
        _state.value = _state.value.copy(user = user)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    val currentServerUrl: String
        get() = tokenStorage.serverUrl

    fun updateServerUrl(url: String) {
        tokenStorage.serverUrl = url
    }
}

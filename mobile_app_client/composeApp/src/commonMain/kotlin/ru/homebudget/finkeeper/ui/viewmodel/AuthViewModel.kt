package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.homebudget.finkeeper.data.model.AuthData
import ru.homebudget.finkeeper.data.model.DebugTokenPreview
import ru.homebudget.finkeeper.data.model.SocialProvider
import ru.homebudget.finkeeper.data.model.User
import ru.homebudget.finkeeper.data.remote.AuthSessionEvent
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.ApiException
import ru.homebudget.finkeeper.data.remote.SocialAuthLauncher
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.util.RetryConfig
import ru.homebudget.finkeeper.util.withRetry

data class AuthState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    val recoveryDebugToken: DebugTokenPreview? = null,
    val socialProviders: List<SocialProvider> = emptyList(),
    val socialLoginProvider: String? = null,
)

class AuthViewModel(
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
    private val socialAuthLauncher: SocialAuthLauncher,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        observeAuthEvents()
        checkAuth()
        loadSocialProviders()
    }

    private fun updateLoggedOutState(infoMessage: String? = null) {
        _state.value =
            _state.value.copy(
                user = null,
                isLoading = false,
                isAuthenticated = false,
                error = null,
                infoMessage = infoMessage,
                recoveryDebugToken = null,
                socialLoginProvider = null,
            )
    }

    private fun persistAuthData(authData: AuthData) {
        tokenStorage.accessToken = authData.accessToken
        tokenStorage.refreshToken = authData.refreshToken
        tokenStorage.userId = authData.user.id.toLong()
    }

    private fun observeAuthEvents() {
        viewModelScope.launch {
            tokenStorage.authEvents.collect { event ->
                when (event) {
                    AuthSessionEvent.LoggedOut -> updateLoggedOutState()
                    AuthSessionEvent.SessionExpired -> updateLoggedOutState(infoMessage = Strings.SESSION_EXPIRED)
                }
            }
        }
    }

    private fun checkAuth() {
        val hasAccessToken = tokenStorage.accessToken != null
        val hasRefreshToken = tokenStorage.refreshToken != null
        if (!hasAccessToken && !hasRefreshToken) {
            updateLoggedOutState(infoMessage = _state.value.infoMessage)
            return
        }
        viewModelScope.launch {
            try {
                val user =
                    withRetry(config = RetryConfig(maxAttempts = 3)) {
                        apiClient.getMe()
                    }
                tokenStorage.userId = user.id.toLong()
                _state.value = _state.value.copy(user = user, isLoading = false, isAuthenticated = true, error = null)
            } catch (_: Exception) {
                val currentInfoMessage = _state.value.infoMessage
                if (tokenStorage.accessToken != null || tokenStorage.refreshToken != null) {
                    tokenStorage.clear()
                }
                updateLoggedOutState(infoMessage = currentInfoMessage)
            }
        }
    }

    private fun loadSocialProviders() {
        viewModelScope.launch {
            try {
                val providers =
                    withRetry(config = RetryConfig(maxAttempts = 2)) {
                        apiClient.getSocialProviders()
                    }
                _state.value = _state.value.copy(socialProviders = providers.filter { it.enabled })
            } catch (_: Exception) {
                _state.value = _state.value.copy(socialProviders = emptyList())
            }
        }
    }

    private suspend fun completeSocialLogin(provider: String) {
        val startResponse =
            withRetry(config = RetryConfig(maxAttempts = 2)) {
                apiClient.startNativeSocialAuth(provider, socialAuthLauncher.clientType)
            }

        if (!socialAuthLauncher.open(startResponse.authorizeUrl)) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    socialLoginProvider = null,
                    error = Strings.SOCIAL_BROWSER_OPEN_ERROR,
                    infoMessage = null,
                )
            return
        }

        _state.value =
            _state.value.copy(
                isLoading = false,
                error = null,
                infoMessage = Strings.SOCIAL_LOGIN_IN_PROGRESS,
                socialLoginProvider = provider,
            )

        repeat(120) {
            delay(startResponse.pollIntervalMs.toLong().coerceAtLeast(750L))
            val status = apiClient.getNativeSocialAuthStatus(startResponse.attemptToken)
            when (status.status.lowercase()) {
                "completed" -> {
                    val code = status.code?.trim().orEmpty()
                    if (code.isBlank()) {
                        throw ApiException(400, Strings.SOCIAL_LOGIN_TIMEOUT)
                    }
                    val authData = apiClient.exchangeSocialAuthCode(code)
                    persistAuthData(authData)
                    _state.value =
                        _state.value.copy(
                            user = authData.user,
                            isLoading = false,
                            isAuthenticated = true,
                            error = null,
                            infoMessage = null,
                            socialLoginProvider = null,
                        )
                    return
                }
                "error" -> {
                    throw ApiException(
                        400,
                        status.errorDescription ?: status.error ?: Strings.SOCIAL_LOGIN_TIMEOUT,
                    )
                }
            }
        }

        throw ApiException(408, Strings.SOCIAL_LOGIN_TIMEOUT)
    }

    private fun validateCredentials(username: String, password: String): String? {
        if (username.isBlank() || password.isBlank()) {
            return Strings.ENTER_USERNAME_AND_PASSWORD
        }
        if (username.length > 64) {
            return Strings.USERNAME_TOO_LONG
        }
        if (password.length < 6) {
            return Strings.PASSWORD_TOO_SHORT
        }
        return null
    }

    fun login(username: String, password: String) {
        val trimmedUsername = username.trim()
        val validationError = validateCredentials(trimmedUsername, password)
        if (validationError != null) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = validationError,
                    infoMessage = null,
                    recoveryDebugToken = null,
                )
            return
        }

        _state.value =
            _state.value.copy(
                isLoading = true,
                error = null,
                infoMessage = null,
                recoveryDebugToken = null,
            )
        viewModelScope.launch {
            try {
                val authData =
                    withRetry(config = RetryConfig(maxAttempts = 3)) {
                        apiClient.login(trimmedUsername, password)
                    }
                persistAuthData(authData)
                _state.value =
                    _state.value.copy(
                        user = authData.user,
                        isLoading = false,
                        isAuthenticated = true,
                        error = null,
                        infoMessage = null,
                        recoveryDebugToken = null,
                        socialLoginProvider = null,
                    )
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = if (e.statusCode == 401) Strings.INVALID_USERNAME_OR_PASSWORD else e.message,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = Strings.CONNECTION_ERROR,
                    )
            }
        }
    }

    fun register(username: String, password: String) {
        val trimmedUsername = username.trim()
        val validationError = validateCredentials(trimmedUsername, password)
        if (validationError != null) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = validationError,
                    infoMessage = null,
                    recoveryDebugToken = null,
                )
            return
        }

        _state.value =
            _state.value.copy(
                isLoading = true,
                error = null,
                infoMessage = null,
                recoveryDebugToken = null,
            )
        viewModelScope.launch {
            try {
                val authData =
                    withRetry(config = RetryConfig(maxAttempts = 3)) {
                        apiClient.register(trimmedUsername, password)
                    }
                persistAuthData(authData)
                _state.value =
                    _state.value.copy(
                        user = authData.user,
                        isLoading = false,
                        isAuthenticated = true,
                        error = null,
                        infoMessage = null,
                        recoveryDebugToken = null,
                        socialLoginProvider = null,
                    )
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = if (e.statusCode == 401) Strings.INVALID_USERNAME_OR_PASSWORD else e.message,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = Strings.CONNECTION_ERROR,
                    )
            }
        }
    }

    fun loginWithSocial(provider: String) {
        _state.value =
            _state.value.copy(
                isLoading = true,
                error = null,
                infoMessage = null,
                recoveryDebugToken = null,
                socialLoginProvider = provider,
            )

        viewModelScope.launch {
            try {
                completeSocialLogin(provider)
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        socialLoginProvider = null,
                        error = e.message,
                        infoMessage = null,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        socialLoginProvider = null,
                        error = Strings.CONNECTION_ERROR,
                        infoMessage = null,
                    )
            }
        }
    }

    fun requestPasswordRecovery(email: String) {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = Strings.RECOVERY_EMAIL_REQUIRED,
                    infoMessage = null,
                    recoveryDebugToken = null,
                )
            return
        }

        _state.value =
            _state.value.copy(
                isLoading = true,
                error = null,
                infoMessage = null,
                recoveryDebugToken = null,
            )
        viewModelScope.launch {
            try {
                val response =
                    withRetry(config = RetryConfig(maxAttempts = 3)) {
                        apiClient.requestPasswordRecovery(normalizedEmail)
                    }
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = null,
                        infoMessage = response.message,
                        recoveryDebugToken = response.debug?.passwordReset,
                    )
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = e.message,
                        infoMessage = null,
                        recoveryDebugToken = null,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = Strings.CONNECTION_ERROR,
                        infoMessage = null,
                        recoveryDebugToken = null,
                    )
            }
        }
    }

    fun confirmPasswordRecovery(
        token: String,
        newPassword: String,
        onSuccess: () -> Unit = {},
    ) {
        val normalizedToken = token.trim()
        if (normalizedToken.isBlank()) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = Strings.RECOVERY_TOKEN_REQUIRED,
                    infoMessage = null,
                )
            return
        }
        if (newPassword.length < 6) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = Strings.PASSWORD_TOO_SHORT,
                    infoMessage = null,
                )
            return
        }

        _state.value =
            _state.value.copy(
                isLoading = true,
                error = null,
                infoMessage = null,
                recoveryDebugToken = null,
            )
        viewModelScope.launch {
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.confirmPasswordRecovery(normalizedToken, newPassword)
                }
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = null,
                        infoMessage = Strings.RECOVERY_PASSWORD_CHANGED,
                        recoveryDebugToken = null,
                    )
                onSuccess()
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = e.message,
                        infoMessage = null,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = Strings.CONNECTION_ERROR,
                        infoMessage = null,
                    )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                apiClient.logout()
            } catch (_: Exception) {
            } finally {
                tokenStorage.clear()
                updateLoggedOutState()
            }
        }
    }

    fun updateUser(user: User) {
        _state.value = _state.value.copy(user = user)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, infoMessage = null)
    }

    val currentServerUrl: String
        get() = tokenStorage.serverUrl

    fun updateServerUrl(url: String) {
        tokenStorage.serverUrl = url
    }
}

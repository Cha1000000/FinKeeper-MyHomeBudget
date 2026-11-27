package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import ru.homebudget.finkeeper.data.analytics.AnalyticsEvents
import ru.homebudget.finkeeper.data.analytics.AnalyticsTracker
import ru.homebudget.finkeeper.data.analytics.NoOpAnalyticsTracker
import ru.homebudget.finkeeper.data.model.AuthData
import ru.homebudget.finkeeper.data.model.DebugTokenPreview
import ru.homebudget.finkeeper.data.model.Entitlements
import ru.homebudget.finkeeper.data.model.SocialProvider
import ru.homebudget.finkeeper.data.model.User
import ru.homebudget.finkeeper.data.remote.AuthSessionEvent
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.network.isConnectivityFailure
import ru.homebudget.finkeeper.data.remote.ApiException
import ru.homebudget.finkeeper.data.remote.SocialAuthLauncher
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.util.RetryConfig
import ru.homebudget.finkeeper.util.withRetry

data class AuthState(
    val user: User? = null,
    val entitlements: Entitlements = Entitlements.FREE_DEFAULT,
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    val recoveryDebugToken: DebugTokenPreview? = null,
    val socialProviders: List<SocialProvider> = emptyList(),
    val socialLoginProvider: String? = null,
    val isPaywallVisible: Boolean = false,
    val paywallFeatureKey: String? = null,
    val isPaywallBusy: Boolean = false,
    val paywallError: String? = null,
    val paywallSuccessMessage: String? = null,
)

class AuthViewModel(
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
    private val socialAuthLauncher: SocialAuthLauncher,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
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
                entitlements = Entitlements.FREE_DEFAULT,
                isLoading = false,
                isAuthenticated = false,
                error = null,
                infoMessage = infoMessage,
                recoveryDebugToken = null,
                socialLoginProvider = null,
                isPaywallVisible = false,
                paywallFeatureKey = null,
                isPaywallBusy = false,
                paywallError = null,
                paywallSuccessMessage = null,
            )
    }

    private fun resolveUserEntitlements(user: User, isOffline: Boolean): Entitlements {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val serverEntitlements = user.entitlements
        if (serverEntitlements != null) {
            tokenStorage.saveCachedEntitlements(user.id.toLong(), serverEntitlements)
            return serverEntitlements.withOfflineGrace(isOffline = isOffline, nowEpochMillis = nowMillis)
        }
        val cached = tokenStorage.loadCachedEntitlements(user.id.toLong())
        return cached?.withOfflineGrace(isOffline = isOffline, nowEpochMillis = nowMillis)
            ?: Entitlements.FREE_DEFAULT
    }

    private fun persistAuthData(authData: AuthData) {
        tokenStorage.accessToken = authData.accessToken
        tokenStorage.refreshToken = authData.refreshToken
        tokenStorage.userId = authData.user.id.toLong()
        tokenStorage.username = authData.user.username
        tokenStorage.email = authData.user.email
        authData.user.entitlements?.let {
            tokenStorage.saveCachedEntitlements(authData.user.id.toLong(), it)
        }
        analyticsTracker.setUserId(authData.user.id.toLong())
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

    private fun cachedUser(): User {
        val uid = tokenStorage.userId.toInt()
        val cachedEnt = tokenStorage.loadCachedEntitlements(tokenStorage.userId)
            ?.withOfflineGrace(isOffline = true, nowEpochMillis = Clock.System.now().toEpochMilliseconds())
        return User(
            id = uid,
            username = tokenStorage.username ?: "Офлайн",
            email = tokenStorage.email,
            entitlements = cachedEnt,
        )
    }

    private fun checkAuth() {
        val hasAccessToken = tokenStorage.accessToken != null
        val hasRefreshToken = tokenStorage.refreshToken != null
        if (!hasAccessToken && !hasRefreshToken) {
            updateLoggedOutState(infoMessage = _state.value.infoMessage)
            return
        }
        // Local-first: при известном пользователе открываем приложение сразу с локальными данными,
        // а сессию проверяем в фоне. Иначе на «зависшей» сети Splash ждёт getMe по 30+ секунд.
        val hasLocalUser = tokenStorage.userId > 0L
        if (hasLocalUser) {
            analyticsTracker.setUserId(tokenStorage.userId)
            val localUser = cachedUser()
            _state.value = _state.value.copy(
                user = localUser,
                entitlements = resolveUserEntitlements(localUser, isOffline = true),
                isLoading = false,
                isAuthenticated = true,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val user =
                    withRetry(config = RetryConfig(maxAttempts = 3)) {
                        apiClient.getMe()
                    }
                tokenStorage.userId = user.id.toLong()
                tokenStorage.username = user.username
                tokenStorage.email = user.email
                val resolvedEntitlements = resolveUserEntitlements(user, isOffline = false)
                _state.value = _state.value.copy(
                    user = user,
                    entitlements = resolvedEntitlements,
                    isLoading = false,
                    isAuthenticated = true,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentInfoMessage = _state.value.infoMessage

                if (e is ApiException && (e.statusCode == 401 || e.statusCode == 403)) {
                    // getMe failed with auth error — the interceptor already tried refresh.
                    // Try one more explicit refresh as a last resort before logging out.
                    var refreshFailedOnNetwork = false
                    if (tokenStorage.refreshToken != null) {
                        try {
                            apiClient.refreshAuth()
                            val user = apiClient.getMe()
                            tokenStorage.userId = user.id.toLong()
                            tokenStorage.username = user.username
                            tokenStorage.email = user.email
                            _state.value = _state.value.copy(
                                user = user,
                                entitlements = resolveUserEntitlements(user, isOffline = false),
                                isLoading = false,
                                isAuthenticated = true,
                                error = null,
                            )
                            return@launch
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (re: Exception) {
                            // Обрыв сети — не повод разлогинивать: refresh-токен может быть валиден
                            // То же для 5xx (деплой, прокси): разлогиниваем только по 401/403 на refresh
                            refreshFailedOnNetwork = re.isConnectivityFailure() ||
                                (re is ApiException && re.statusCode >= 500)
                        }
                    }
                    if (refreshFailedOnNetwork) {
                        // Локального пользователя нет — Splash не должен висеть: пускаем в офлайн-режиме
                        if (!hasLocalUser) {
                            _state.value = _state.value.copy(
                                user = cachedUser(),
                                isLoading = false,
                                error = Strings.CONNECTION_ERROR,
                                isAuthenticated = true,
                            )
                        }
                        return@launch
                    }
                    if (tokenStorage.accessToken != null || tokenStorage.refreshToken != null) {
                        tokenStorage.clear()
                    }
                    updateLoggedOutState(infoMessage = currentInfoMessage)
                } else if (hasLocalUser) {
                    // Нет связи с сервером: приложение уже открыто на локальных данных,
                    // статус «нет связи» показывают сами экраны (isOffline)
                } else {
                    // Network error — keep session, show error, but allow offline access
                    // Restore the offline user so that the app can navigate past the login screen
                    _state.value = _state.value.copy(
                        user = cachedUser(),
                        isLoading = false,
                        error = Strings.CONNECTION_ERROR,
                        isAuthenticated = true,
                    )
                }
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
                    analyticsTracker.setUserId(authData.user.id.toLong())
                    analyticsTracker.logEvent(
                        AnalyticsEvents.AUTH_SOCIAL_SUCCESS,
                        mapOf(AnalyticsEvents.Params.PROVIDER to provider),
                    )
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
                analyticsTracker.logEvent(AnalyticsEvents.AUTH_LOGIN_SUCCESS)
                _state.value =
                    _state.value.copy(
                        user = authData.user,
                        entitlements = resolveUserEntitlements(authData.user, isOffline = false),
                        isLoading = false,
                        isAuthenticated = true,
                        error = null,
                        infoMessage = null,
                        recoveryDebugToken = null,
                        socialLoginProvider = null,
                    )
            } catch (e: ApiException) {
                analyticsTracker.logEvent(
                    AnalyticsEvents.AUTH_LOGIN_FAILED,
                    mapOf(AnalyticsEvents.Params.REASON to "http_${e.statusCode}"),
                )
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = if (e.statusCode == 401) Strings.INVALID_USERNAME_OR_PASSWORD else e.message,
                    )
            } catch (e: Exception) {
                analyticsTracker.logEvent(
                    AnalyticsEvents.AUTH_LOGIN_FAILED,
                    mapOf(AnalyticsEvents.Params.REASON to "network"),
                )
                analyticsTracker.logError(e, "auth_login")
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
                analyticsTracker.logEvent(AnalyticsEvents.AUTH_REGISTER_SUCCESS)
                _state.value =
                    _state.value.copy(
                        user = authData.user,
                        entitlements = resolveUserEntitlements(authData.user, isOffline = false),
                        isLoading = false,
                        isAuthenticated = true,
                        error = null,
                        infoMessage = null,
                        recoveryDebugToken = null,
                        socialLoginProvider = null,
                    )
            } catch (e: ApiException) {
                analyticsTracker.logEvent(
                    AnalyticsEvents.AUTH_REGISTER_FAILED,
                    mapOf(AnalyticsEvents.Params.REASON to "http_${e.statusCode}"),
                )
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = if (e.statusCode == 401) Strings.INVALID_USERNAME_OR_PASSWORD else e.message,
                    )
            } catch (e: Exception) {
                analyticsTracker.logEvent(
                    AnalyticsEvents.AUTH_REGISTER_FAILED,
                    mapOf(AnalyticsEvents.Params.REASON to "network"),
                )
                analyticsTracker.logError(e, "auth_register")
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = Strings.CONNECTION_ERROR,
                    )
            }
        }
    }

    fun loginWithSocial(provider: String) {
        analyticsTracker.logEvent(
            AnalyticsEvents.AUTH_SOCIAL_START,
            mapOf(AnalyticsEvents.Params.PROVIDER to provider),
        )
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
                analyticsTracker.logEvent(
                    AnalyticsEvents.AUTH_SOCIAL_FAILED,
                    mapOf(
                        AnalyticsEvents.Params.PROVIDER to provider,
                        AnalyticsEvents.Params.REASON to "http_${e.statusCode}",
                    ),
                )
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        socialLoginProvider = null,
                        error = e.message,
                        infoMessage = null,
                    )
            } catch (e: Exception) {
                analyticsTracker.logEvent(
                    AnalyticsEvents.AUTH_SOCIAL_FAILED,
                    mapOf(
                        AnalyticsEvents.Params.PROVIDER to provider,
                        AnalyticsEvents.Params.REASON to "network",
                    ),
                )
                analyticsTracker.logError(e, "auth_social_$provider")
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
                analyticsTracker.logEvent(AnalyticsEvents.AUTH_PASSWORD_RECOVERY_REQUESTED)
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
                analyticsTracker.logEvent(AnalyticsEvents.AUTH_PASSWORD_RECOVERY_CONFIRMED)
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
                analyticsTracker.logEvent(AnalyticsEvents.AUTH_LOGOUT)
                analyticsTracker.setUserId(null)
                tokenStorage.clear()
                updateLoggedOutState()
            }
        }
    }

    fun updateUser(user: User) {
        val nextEntitlements = user.entitlements?.also {
            tokenStorage.saveCachedEntitlements(user.id.toLong(), it)
        } ?: _state.value.entitlements
        _state.value = _state.value.copy(
            user = user,
            entitlements = nextEntitlements,
        )
    }

    fun openPaywall(featureKey: String? = null) {
        _state.value = _state.value.copy(
            isPaywallVisible = true,
            paywallFeatureKey = featureKey,
            paywallError = null,
            paywallSuccessMessage = null,
        )
    }

    fun closePaywall() {
        if (_state.value.isPaywallBusy) return
        _state.value = _state.value.copy(
            isPaywallVisible = false,
            paywallFeatureKey = null,
            paywallError = null,
            paywallSuccessMessage = null,
        )
    }

    fun clearPaywallFeedback() {
        _state.value = _state.value.copy(
            paywallError = null,
            paywallSuccessMessage = null,
        )
    }

    fun refreshEntitlements() {
        viewModelScope.launch {
            try {
                val status = apiClient.getBillingStatus()
                val uid = _state.value.user?.id?.toLong() ?: tokenStorage.userId
                if (uid > 0L) {
                    tokenStorage.saveCachedEntitlements(uid, status.entitlements)
                }
                _state.value = _state.value.copy(
                    entitlements = status.entitlements,
                    user = _state.value.user?.copy(entitlements = status.entitlements),
                )
            } catch (_: Exception) {
            }
        }
    }

    fun startFreeTrial(onActivated: (Entitlements) -> Unit = {}) {
        if (_state.value.isPaywallBusy) return
        _state.value = _state.value.copy(
            isPaywallBusy = true,
            paywallError = null,
            paywallSuccessMessage = null,
        )
        viewModelScope.launch {
            try {
                val res = apiClient.startFreeTrial()
                val uid = _state.value.user?.id?.toLong() ?: tokenStorage.userId
                if (uid > 0L) {
                    tokenStorage.saveCachedEntitlements(uid, res.entitlements)
                }
                _state.value = _state.value.copy(
                    entitlements = res.entitlements,
                    user = _state.value.user?.copy(entitlements = res.entitlements),
                    isPaywallBusy = false,
                    paywallSuccessMessage = Strings.PRO_TRIAL_ACTIVATED_SUCCESS,
                )
                onActivated(res.entitlements)
                delay(800)
                _state.value = _state.value.copy(
                    isPaywallVisible = false,
                    paywallFeatureKey = null,
                    paywallSuccessMessage = null,
                )
            } catch (e: ApiException) {
                _state.value = _state.value.copy(
                    isPaywallBusy = false,
                    paywallError = e.message,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isPaywallBusy = false,
                    paywallError = Strings.CONNECTION_ERROR,
                )
            }
        }
    }

    fun redeemPromoCode(
        code: String,
        onActivated: (Entitlements) -> Unit = {},
    ) {
        val trimmed = code.trim().uppercase()
        if (trimmed.isBlank() || _state.value.isPaywallBusy) return
        _state.value = _state.value.copy(
            isPaywallBusy = true,
            paywallError = null,
            paywallSuccessMessage = null,
        )
        viewModelScope.launch {
            try {
                val res = apiClient.redeemPromoCode(trimmed)
                val uid = _state.value.user?.id?.toLong() ?: tokenStorage.userId
                if (uid > 0L) {
                    tokenStorage.saveCachedEntitlements(uid, res.entitlements)
                }
                _state.value = _state.value.copy(
                    entitlements = res.entitlements,
                    user = _state.value.user?.copy(entitlements = res.entitlements),
                    isPaywallBusy = false,
                    paywallSuccessMessage = Strings.PRO_PROMO_ACTIVATED_SUCCESS,
                )
                onActivated(res.entitlements)
                delay(800)
                _state.value = _state.value.copy(
                    isPaywallVisible = false,
                    paywallFeatureKey = null,
                    paywallSuccessMessage = null,
                )
            } catch (e: ApiException) {
                _state.value = _state.value.copy(
                    isPaywallBusy = false,
                    paywallError = e.message,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isPaywallBusy = false,
                    paywallError = Strings.CONNECTION_ERROR,
                )
            }
        }
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

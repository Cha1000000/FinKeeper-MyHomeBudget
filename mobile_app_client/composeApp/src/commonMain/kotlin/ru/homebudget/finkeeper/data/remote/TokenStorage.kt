package ru.homebudget.finkeeper.data.remote

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.homebudget.finkeeper.BuildConfig
import ru.homebudget.finkeeper.ui.UiScale

enum class AuthSessionEvent {
    LoggedOut,
    SessionExpired,
}

class TokenStorage(
    private val settings: Settings = Settings(),
    private val secureTokenStorage: SecureTokenStorage,
) {
    private val defaultServerUrl = normalizeServerUrl(DEFAULT_SERVER_URL)
    private val _authEvents = MutableSharedFlow<AuthSessionEvent>(extraBufferCapacity = 1)
    val authEvents: SharedFlow<AuthSessionEvent> = _authEvents.asSharedFlow()


    var accessToken: String?
        get() {
            val secureToken = secureTokenStorage.accessToken
            if (secureToken != null) {
                return secureToken
            }

            val legacyToken = settings.getStringOrNull(KEY_TOKEN) ?: return null
            secureTokenStorage.accessToken = legacyToken
            settings.remove(KEY_TOKEN)
            return legacyToken
        }
        set(value) {
            if (value != null) {
                secureTokenStorage.accessToken = value
                settings.remove(KEY_TOKEN)
            } else {
                secureTokenStorage.accessToken = null
                settings.remove(KEY_TOKEN)
            }
        }

    var token: String?
        get() = accessToken
        set(value) {
            accessToken = value
        }

    var refreshToken: String?
        get() = secureTokenStorage.refreshToken
        set(value) {
            secureTokenStorage.refreshToken = value
        }

    var userId: Long
        get() = settings.getLong(KEY_USER_ID, 0L)
        set(value) {
            settings.putLong(KEY_USER_ID, value)
        }

    var username: String?
        get() = settings.getStringOrNull(KEY_USERNAME)
        set(value) {
            if (value != null) settings.putString(KEY_USERNAME, value)
            else settings.remove(KEY_USERNAME)
        }

    var email: String?
        get() = settings.getStringOrNull(KEY_EMAIL)
        set(value) {
            if (value != null) settings.putString(KEY_EMAIL, value)
            else settings.remove(KEY_EMAIL)
        }

    var serverUrl: String
        get() {
            if (!BuildConfig.SHOW_SERVER_SETTINGS) {
                settings.remove(KEY_SERVER_URL)
                return defaultServerUrl
            }

            val storedUrl = settings.getStringOrNull(KEY_SERVER_URL)
            return normalizeServerUrl(storedUrl ?: defaultServerUrl)
        }
        set(value) {
            if (!BuildConfig.SHOW_SERVER_SETTINGS) {
                settings.remove(KEY_SERVER_URL)
                return
            }

            val normalizedUrl = normalizeServerUrl(value)
            if (normalizedUrl.isBlank()) {
                settings.remove(KEY_SERVER_URL)
            } else {
                settings.putString(KEY_SERVER_URL, normalizedUrl)
            }
        }

    // Theme mode: "light", "night" (soft dark), "dark" (Cyberpunk), "dark_night", "blue_ocean", "system"
    var themeMode: String
        get() = settings.getString(KEY_THEME_MODE, "light")
        set(value) {
            settings.putString(KEY_THEME_MODE, value)
        }

    // UI scale multiplier for desktop (1.0 = 100%). null = "Авто" (следуем системному scale).
    var uiScale: Float?
        get() = settings.getFloatOrNull(KEY_UI_SCALE)
        set(value) {
            if (value == null) {
                settings.remove(KEY_UI_SCALE)
            } else {
                settings.putFloat(KEY_UI_SCALE, value.coerceIn(UiScale.MIN, UiScale.MAX))
            }
        }

    // Dashboard selected month (persisted between sessions)
    var dashboardYear: Int
        get() = settings.getInt(KEY_DASHBOARD_YEAR, 0)
        set(value) {
            settings.putInt(KEY_DASHBOARD_YEAR, value)
        }

    var dashboardMonth: Int
        get() = settings.getInt(KEY_DASHBOARD_MONTH, 0)
        set(value) {
            settings.putInt(KEY_DASHBOARD_MONTH, value)
        }

    // MonthView selected month (persisted between sessions)
    var monthViewYear: Int
        get() = settings.getInt(KEY_MONTHVIEW_YEAR, 0)
        set(value) {
            settings.putInt(KEY_MONTHVIEW_YEAR, value)
        }

    var monthViewMonth: Int
        get() = settings.getInt(KEY_MONTHVIEW_MONTH, 0)
        set(value) {
            settings.putInt(KEY_MONTHVIEW_MONTH, value)
        }

    /**
     * Последний серверный «кумулятивный баланс» и локальный баланс месяца на момент его
     * получения. Нужен, чтобы «Всего активов» не пропадало при недоступном сервере и
     * сдвигалось вместе с локальными правками (см. DashboardViewModel). Привязан к пользователю:
     * чужое значение после смены аккаунта не показываем.
     */
    fun saveCumulativeBalance(userId: Long, year: Int, month: Int, serverValue: Double, localBalance: Double) {
        settings.putString(KEY_CUMULATIVE, "$userId;$year;$month;$serverValue;$localBalance")
    }

    /** Возвращает (серверное значение, локальный баланс на тот момент) для месяца или null. */
    fun loadCumulativeBalance(userId: Long, year: Int, month: Int): Pair<Double, Double>? {
        val parts = settings.getStringOrNull(KEY_CUMULATIVE)?.split(";") ?: return null
        if (parts.size != 5) return null
        if (parts[0].toLongOrNull() != userId || parts[1].toIntOrNull() != year || parts[2].toIntOrNull() != month) return null
        val server = parts[3].toDoubleOrNull() ?: return null
        val local = parts[4].toDoubleOrNull() ?: return null
        return server to local
    }

    fun clear(authEvent: AuthSessionEvent? = null) {
        secureTokenStorage.clear()
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_USERNAME)
        settings.remove(KEY_EMAIL)
        settings.remove(KEY_CUMULATIVE)
        authEvent?.let { _authEvents.tryEmit(it) }
    }

    private fun normalizeServerUrl(url: String): String = url.trim().trimEnd('/')

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_CUMULATIVE = "cumulative_balance"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_DASHBOARD_YEAR = "dashboard_year"
        private const val KEY_DASHBOARD_MONTH = "dashboard_month"
        private const val KEY_MONTHVIEW_YEAR = "monthview_year"
        private const val KEY_MONTHVIEW_MONTH = "monthview_month"
        private const val DEFAULT_SERVER_URL = BuildConfig.DEFAULT_SERVER_URL
    }
}

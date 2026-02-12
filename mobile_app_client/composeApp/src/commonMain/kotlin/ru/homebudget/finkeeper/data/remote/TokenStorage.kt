package ru.homebudget.finkeeper.data.remote

import com.russhwolf.settings.Settings

class TokenStorage(private val settings: Settings = Settings()) {

    var token: String?
        get() = settings.getStringOrNull(KEY_TOKEN)
        set(value) {
            if (value != null) {
                settings.putString(KEY_TOKEN, value)
            } else {
                settings.remove(KEY_TOKEN)
            }
        }

    var userId: Long
        get() = settings.getLong(KEY_USER_ID, 0L)
        set(value) {
            settings.putLong(KEY_USER_ID, value)
        }

    var serverUrl: String
        get() = settings.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)
        set(value) {
            settings.putString(KEY_SERVER_URL, value)
        }

    // Theme mode: "light", "dark", "system"
    var themeMode: String
        get() = settings.getString(KEY_THEME_MODE, "light")
        set(value) {
            settings.putString(KEY_THEME_MODE, value)
        }

    fun clear() {
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_USER_ID)
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val DEFAULT_SERVER_URL = "http://217.114.8.82:3002"
    }
}

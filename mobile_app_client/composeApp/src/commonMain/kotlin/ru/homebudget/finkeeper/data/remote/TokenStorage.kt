package ru.homebudget.finkeeper.data.remote

import com.russhwolf.settings.Settings

class TokenStorage {
    private val settings = Settings()

    var token: String?
        get() = settings.getStringOrNull(KEY_TOKEN)
        set(value) {
            if (value != null) {
                settings.putString(KEY_TOKEN, value)
            } else {
                settings.remove(KEY_TOKEN)
            }
        }

    var serverUrl: String
        get() = settings.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)
        set(value) {
            settings.putString(KEY_SERVER_URL, value)
        }

    fun clear() {
        settings.remove(KEY_TOKEN)
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:3002"
    }
}

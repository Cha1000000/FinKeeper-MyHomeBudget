package ru.homebudget.finkeeper.data.remote

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings

@OptIn(ExperimentalSettingsImplementation::class)
class IosSecureTokenStorage : SecureTokenStorage {
    private val settings = KeychainSettings(SERVICE_NAME)

    override var accessToken: String?
        get() = settings.getStringOrNull(KEY_ACCESS_TOKEN)
        set(value) {
            if (value != null) {
                settings.putString(KEY_ACCESS_TOKEN, value)
            } else {
                settings.remove(KEY_ACCESS_TOKEN)
            }
        }

    override var refreshToken: String?
        get() = settings.getStringOrNull(KEY_REFRESH_TOKEN)
        set(value) {
            if (value != null) {
                settings.putString(KEY_REFRESH_TOKEN, value)
            } else {
                settings.remove(KEY_REFRESH_TOKEN)
            }
        }

    override fun clear() {
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_REFRESH_TOKEN)
    }

    companion object {
        private const val SERVICE_NAME = "ru.homebudget.finkeeper.secure"
        private const val KEY_ACCESS_TOKEN = "secure_auth_token"
        private const val KEY_REFRESH_TOKEN = "secure_refresh_token"
    }
}

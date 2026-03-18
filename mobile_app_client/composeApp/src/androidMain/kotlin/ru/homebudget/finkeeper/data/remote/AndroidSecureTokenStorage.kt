package ru.homebudget.finkeeper.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidSecureTokenStorage(context: Context) : SecureTokenStorage {
    private val appContext = context.applicationContext
    private val sharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        FILE_NAME,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override var accessToken: String?
        get() = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            if (value != null) {
                sharedPreferences.edit().putString(KEY_ACCESS_TOKEN, value).apply()
            } else {
                sharedPreferences.edit().remove(KEY_ACCESS_TOKEN).apply()
            }
        }

    override var refreshToken: String?
        get() = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
        set(value) {
            if (value != null) {
                sharedPreferences.edit().putString(KEY_REFRESH_TOKEN, value).apply()
            } else {
                sharedPreferences.edit().remove(KEY_REFRESH_TOKEN).apply()
            }
        }

    override fun clear() {
        sharedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "finkeeper_secure_storage"
        private const val KEY_ACCESS_TOKEN = "secure_auth_token"
        private const val KEY_REFRESH_TOKEN = "secure_refresh_token"
    }
}

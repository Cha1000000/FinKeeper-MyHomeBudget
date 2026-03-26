package ru.homebudget.finkeeper.data.remote

import com.microsoft.credentialstorage.SecretStore
import com.microsoft.credentialstorage.StorageProvider
import com.microsoft.credentialstorage.StorageProvider.SecureOption
import com.microsoft.credentialstorage.model.StoredToken
import com.microsoft.credentialstorage.model.StoredTokenType
import java.util.prefs.Preferences

class DesktopSecureTokenStorage : SecureTokenStorage {
    private val tokenStore: SecretStore<StoredToken>? = try {
        StorageProvider.getTokenStorage(true, SecureOption.REQUIRED)
    } catch (_: Exception) {
        null
    }

    private val fallbackPrefs: Preferences? =
        if (tokenStore == null) Preferences.userNodeForPackage(DesktopSecureTokenStorage::class.java) else null

    private var inMemoryAccessToken: String? = null
    private var inMemoryRefreshToken: String? = null

    init {
        if (tokenStore != null) {
            inMemoryAccessToken = readTokenFromStore(KEY_ACCESS_TOKEN)
            inMemoryRefreshToken = readTokenFromStore(KEY_REFRESH_TOKEN)
        } else {
            inMemoryAccessToken = fallbackPrefs?.get(KEY_ACCESS_TOKEN, null)
            inMemoryRefreshToken = fallbackPrefs?.get(KEY_REFRESH_TOKEN, null)
        }
    }

    override var accessToken: String?
        get() = inMemoryAccessToken
        set(value) {
            inMemoryAccessToken = value
            persistToken(KEY_ACCESS_TOKEN, value, StoredTokenType.ACCESS)
        }

    override var refreshToken: String?
        get() = inMemoryRefreshToken
        set(value) {
            inMemoryRefreshToken = value
            persistToken(KEY_REFRESH_TOKEN, value, StoredTokenType.REFRESH)
        }

    override fun clear() {
        inMemoryAccessToken = null
        inMemoryRefreshToken = null
        tokenStore?.delete(KEY_ACCESS_TOKEN)
        tokenStore?.delete(KEY_REFRESH_TOKEN)
        fallbackPrefs?.remove(KEY_ACCESS_TOKEN)
        fallbackPrefs?.remove(KEY_REFRESH_TOKEN)
        fallbackPrefs?.flush()
    }

    private fun readTokenFromStore(key: String): String? {
        val storedToken = tokenStore?.get(key) ?: return null
        return try {
            storedToken.value.concatToString()
        } finally {
            storedToken.clear()
        }
    }

    private fun persistToken(
        key: String,
        value: String?,
        type: StoredTokenType,
    ) {
        if (tokenStore != null) {
            if (value == null) {
                tokenStore.delete(key)
                return
            }
            val storedToken = StoredToken(value.toCharArray(), type)
            try {
                tokenStore.add(key, storedToken)
            } finally {
                storedToken.clear()
            }
        } else if (fallbackPrefs != null) {
            if (value == null) {
                fallbackPrefs.remove(key)
            } else {
                fallbackPrefs.put(key, value)
            }
            fallbackPrefs.flush()
        }
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "secure_auth_token"
        private const val KEY_REFRESH_TOKEN = "secure_refresh_token"
    }
}

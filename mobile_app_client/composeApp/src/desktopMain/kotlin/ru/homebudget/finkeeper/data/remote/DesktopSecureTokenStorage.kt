package ru.homebudget.finkeeper.data.remote

import com.microsoft.credentialstorage.SecretStore
import com.microsoft.credentialstorage.StorageProvider
import com.microsoft.credentialstorage.StorageProvider.SecureOption
import com.microsoft.credentialstorage.model.StoredToken
import com.microsoft.credentialstorage.model.StoredTokenType

class DesktopSecureTokenStorage : SecureTokenStorage {
    private val tokenStore: SecretStore<StoredToken>? =
        StorageProvider.getTokenStorage(true, SecureOption.REQUIRED)

    private var inMemoryAccessToken: String? = null
    private var inMemoryRefreshToken: String? = null

    override var accessToken: String?
        get() = readToken(KEY_ACCESS_TOKEN) ?: inMemoryAccessToken
        set(value) {
            inMemoryAccessToken = value
            persistToken(KEY_ACCESS_TOKEN, value, StoredTokenType.ACCESS)
        }

    override var refreshToken: String?
        get() = readToken(KEY_REFRESH_TOKEN) ?: inMemoryRefreshToken
        set(value) {
            inMemoryRefreshToken = value
            persistToken(KEY_REFRESH_TOKEN, value, StoredTokenType.REFRESH)
        }

    override fun clear() {
        inMemoryAccessToken = null
        inMemoryRefreshToken = null
        tokenStore?.delete(KEY_ACCESS_TOKEN)
        tokenStore?.delete(KEY_REFRESH_TOKEN)
    }

    private fun readToken(key: String): String? {
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
        val store = tokenStore ?: return
        if (value == null) {
            store.delete(key)
            return
        }

        val storedToken = StoredToken(value.toCharArray(), type)
        try {
            store.add(key, storedToken)
        } finally {
            storedToken.clear()
        }
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "secure_auth_token"
        private const val KEY_REFRESH_TOKEN = "secure_refresh_token"
    }
}

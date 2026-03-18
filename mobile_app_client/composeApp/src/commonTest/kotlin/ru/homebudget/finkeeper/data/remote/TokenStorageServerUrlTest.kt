package ru.homebudget.finkeeper.data.remote

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import ru.homebudget.finkeeper.BuildConfig

class TokenStorageServerUrlTest {
    @Test
    fun serverUrl_defaultValue_isNormalized() {
        val storage = createTokenStorage()

        assertEquals(
            BuildConfig.DEFAULT_SERVER_URL.trim().trimEnd('/'),
            storage.serverUrl,
        )
    }

    @Test
    fun serverUrl_manualOverride_isNormalizedBeforePersisting() {
        val storage = createTokenStorage()

        storage.serverUrl = " https://example.com:8443/ "

        val expectedUrl =
            if (BuildConfig.SHOW_SERVER_SETTINGS) {
                "https://example.com:8443"
            } else {
                BuildConfig.DEFAULT_SERVER_URL.trim().trimEnd('/')
            }

        assertEquals(expectedUrl, storage.serverUrl)
    }

    @Test
    fun serverUrl_blankOverride_resetsToDefault() {
        val storage = createTokenStorage()
        storage.serverUrl = "https://example.com"

        storage.serverUrl = "   "

        assertEquals(
            BuildConfig.DEFAULT_SERVER_URL.trim().trimEnd('/'),
            storage.serverUrl,
        )
    }

    private fun createTokenStorage(): TokenStorage =
        TokenStorage(
            settings = MapSettings(),
            secureTokenStorage =
                object : SecureTokenStorage {
                    override var accessToken: String? = null
                    override var refreshToken: String? = null

                    override fun clear() {
                        accessToken = null
                        refreshToken = null
                    }
                },
        )
}

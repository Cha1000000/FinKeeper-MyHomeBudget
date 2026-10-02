package ru.homebudget.finkeeper.data.remote

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStorageCumulativeTest {
    @Test
    fun cumulative_roundTrip_forSameMonth() {
        val storage = createTokenStorage()

        storage.saveCumulativeBalance(7, 2026, 10, 12345.5, -300.25)

        assertEquals(12345.5 to -300.25, storage.loadCumulativeBalance(7, 2026, 10))
    }

    @Test
    fun cumulative_otherMonth_isNull() {
        val storage = createTokenStorage()
        storage.saveCumulativeBalance(7, 2026, 10, 1.0, 2.0)

        assertNull(storage.loadCumulativeBalance(7, 2026, 9))
        assertNull(storage.loadCumulativeBalance(7, 2025, 10))
    }

    @Test
    fun cumulative_clearedOnLogout() {
        val storage = createTokenStorage()
        storage.saveCumulativeBalance(7, 2026, 10, 1.0, 2.0)

        storage.clear()

        assertNull(storage.loadCumulativeBalance(7, 2026, 10))
    }

    @Test
    fun cumulative_otherUser_isNull() {
        val storage = createTokenStorage()
        storage.saveCumulativeBalance(7, 2026, 10, 1.0, 2.0)

        assertNull(storage.loadCumulativeBalance(8, 2026, 10))
    }

    @Test
    fun cumulative_legacyFormatWithoutUser_isIgnored() {
        val settings = MapSettings()
        settings.putString("cumulative_balance", "2026;10;1.0;2.0")
        val storage = createTokenStorage(settings)

        assertNull(storage.loadCumulativeBalance(2026L, 10, 1))
    }

    private fun createTokenStorage(settings: MapSettings = MapSettings()): TokenStorage =
        TokenStorage(
            settings = settings,
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

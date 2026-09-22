package ru.homebudget.finkeeper.ui.onboarding

import com.russhwolf.settings.Settings
import kotlin.time.Clock
import ru.homebudget.finkeeper.data.model.User
import ru.homebudget.finkeeper.data.remote.TokenStorage

class SecurityOnboardingPromptState(
    private val tokenStorage: TokenStorage,
    private val settings: Settings = Settings(),
) {
    fun shouldShow(user: User, shownThisSession: Boolean): Boolean {
        if (user.recoverabilityStatus != "unprotected") {
            return false
        }

        val lastShownAt = settings.getLong(scopedKey(KEY_LAST_SHOWN_AT), 0L)
        if (lastShownAt == 0L) {
            return true
        }

        if (shownThisSession) {
            return false
        }

        return Clock.System.now().toEpochMilliseconds() - lastShownAt >= COOLDOWN_MS
    }

    fun markShown(user: User) {
        if (user.recoverabilityStatus != "unprotected") {
            return
        }

        settings.putString(scopedKey(KEY_LAST_STATUS), user.recoverabilityStatus)
    }

    fun dismiss(user: User) {
        if (user.recoverabilityStatus != "unprotected") {
            return
        }

        val dismissCount = settings.getInt(scopedKey(KEY_DISMISS_COUNT), 0)
        settings.putLong(scopedKey(KEY_LAST_SHOWN_AT), Clock.System.now().toEpochMilliseconds())
        settings.putInt(scopedKey(KEY_DISMISS_COUNT), dismissCount + 1)
        settings.putString(scopedKey(KEY_LAST_STATUS), user.recoverabilityStatus)
    }

    fun reset() {
        settings.remove(scopedKey(KEY_LAST_SHOWN_AT))
        settings.remove(scopedKey(KEY_DISMISS_COUNT))
        settings.remove(scopedKey(KEY_LAST_STATUS))
    }

    private fun scopedKey(suffix: String): String {
        val userId = tokenStorage.userId
        val serverHash = tokenStorage.serverUrl.hashCode()
        return "security_onboarding_${userId}_${serverHash}_$suffix"
    }

    private companion object {
        const val KEY_LAST_SHOWN_AT = "last_shown_at"
        const val KEY_DISMISS_COUNT = "dismiss_count"
        const val KEY_LAST_STATUS = "last_status"
        const val COOLDOWN_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

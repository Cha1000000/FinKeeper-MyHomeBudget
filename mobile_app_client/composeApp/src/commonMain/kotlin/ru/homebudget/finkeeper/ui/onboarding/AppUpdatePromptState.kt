package ru.homebudget.finkeeper.ui.onboarding

import com.russhwolf.settings.Settings
import kotlin.time.Clock
import ru.homebudget.finkeeper.data.update.AppUpdateStatus

/**
 * Логика показа баннера «доступна новая версия» — по образцу [SecurityOnboardingPromptState].
 *
 * Гейтинг per-device (без user-scope: обновление не зависит от пользователя):
 * - не показываем повторно ту же версию, которую пользователь нажал «Позже» (skippedVersion);
 * - не показываем более одного раза за сессию.
 */
class AppUpdatePromptState(
    private val settings: Settings = Settings(),
) {
    fun shouldShow(status: AppUpdateStatus.Available, shownThisSession: Boolean): Boolean {
        if (shownThisSession) {
            return false
        }
        val skipped = settings.getStringOrNull(KEY_SKIPPED_VERSION)
        return skipped != status.versionToken
    }

    fun markShown() {
        settings.putLong(KEY_LAST_SHOWN_AT, Clock.System.now().toEpochMilliseconds())
    }

    /** Пользователь нажал «Позже» — запоминаем версию, чтобы не дёргать до выхода ещё более свежей. */
    fun dismiss(versionToken: String) {
        settings.putString(KEY_SKIPPED_VERSION, versionToken)
        settings.putLong(KEY_LAST_SHOWN_AT, Clock.System.now().toEpochMilliseconds())
    }

    fun reset() {
        settings.remove(KEY_SKIPPED_VERSION)
        settings.remove(KEY_LAST_SHOWN_AT)
    }

    private companion object {
        const val KEY_SKIPPED_VERSION = "app_update_skipped_version"
        const val KEY_LAST_SHOWN_AT = "app_update_last_shown_at"
    }
}

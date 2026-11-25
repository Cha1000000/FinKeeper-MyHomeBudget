package ru.homebudget.finkeeper.data.analytics

import android.util.Log
import io.appmetrica.analytics.AppMetrica

/**
 * Android-реализация [AnalyticsTracker] поверх Yandex AppMetrica SDK.
 *
 * При [enabled] == false (например, debug-сборка без `-PAPPMETRICA_DEBUG=true` или пустой ключ)
 * все вызовы безопасно игнорируются, чтобы не засорять боевую статистику.
 */
class AppMetricaAnalyticsTracker(
    private val enabled: Boolean,
) : AnalyticsTracker {

    override fun logScreen(screenName: String) {
        if (!enabled) return
        logEvent(
            AnalyticsEvents.SCREEN_VIEW,
            mapOf(AnalyticsEvents.Params.SCREEN to screenName),
        )
    }

    override fun logEvent(
        name: String,
        params: Map<String, Any>,
    ) {
        if (!enabled) return
        runCatching {
            if (params.isEmpty()) {
                AppMetrica.reportEvent(name)
            } else {
                AppMetrica.reportEvent(name, params)
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to report event '$name': ${e.message}")
        }
    }

    override fun logError(
        throwable: Throwable,
        context: String,
    ) {
        if (!enabled) return
        if (isBenignAnalyticsThrowable(throwable)) return
        runCatching {
            AppMetrica.reportError(context, throwable)
            AppMetrica.reportEvent(
                AnalyticsEvents.ERROR_OCCURRED,
                mapOf(
                    AnalyticsEvents.Params.CONTEXT to context,
                    AnalyticsEvents.Params.ERROR_TYPE to (throwable::class.simpleName ?: "Unknown"),
                    AnalyticsEvents.Params.ERROR_MESSAGE to (throwable.message?.take(200) ?: "No message"),
                ),
            )
        }.onFailure { e ->
            Log.w(TAG, "Failed to report error '$context': ${e.message}")
        }
    }

    override fun setUserId(userId: Long?) {
        if (!enabled) return
        runCatching {
            AppMetrica.setUserProfileID(userId?.takeIf { it > 0L }?.toString())
        }.onFailure { e ->
            Log.w(TAG, "Failed to set AppMetrica userProfileID: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "AppMetricaTracker"
    }
}

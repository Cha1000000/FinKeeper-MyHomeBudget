package ru.homebudget.finkeeper

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import ru.homebudget.finkeeper.data.repository.SyncService
import ru.homebudget.finkeeper.data.repository.WebSocketService
import ru.homebudget.finkeeper.di.androidAppModule
import ru.homebudget.finkeeper.di.appModule

class FinKeeper24App : Application() {
    override fun onCreate() {
        super.onCreate()

        initAppMetrica()

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FinKeeper24App)
            modules(androidAppModule, appModule)
        }

        // Запускаем SyncService после небольшой задержки, чтобы Koin успел инициализировать все зависимости
        android.os.Handler(mainLooper).postDelayed({
            try {
                val syncService = GlobalContext.get().getOrNull<SyncService>()
                syncService?.start()
                val webSocketService = GlobalContext.get().getOrNull<WebSocketService>()
                webSocketService?.start()
            } catch (e: Exception) {
                // Игнорируем ошибки синхронизации - приложение всё равно работает
                android.util.Log.e("FinKeeper24App", "Failed to start sync service: ${e.message}")
            }
        }, 500)
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            val syncService = GlobalContext.get().getOrNull<SyncService>()
            syncService?.stop()
            val webSocketService = GlobalContext.get().getOrNull<WebSocketService>()
            webSocketService?.stop()
        } catch (e: Exception) {
            android.util.Log.e("FinKeeper24App", "Failed to stop sync service: ${e.message}")
        }
    }

    private fun initAppMetrica() {
        if (!BuildConfig.APPMETRICA_ENABLED || BuildConfig.APPMETRICA_API_KEY.isBlank()) {
            return
        }
        try {
            val builder = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY)
                .withCrashReporting(true)
                .withAnrMonitoring(true)
                .withSessionTimeout(300)
                .withAppVersion(BuildConfig.APP_VERSION.removePrefix("v"))
            if (BuildConfig.IS_DEBUG) {
                builder.withLogs()
            }
            AppMetrica.activate(this, builder.build())
            AppMetrica.enableActivityAutoTracking(this)
        } catch (e: Exception) {
            android.util.Log.e("FinKeeper24App", "Failed to initialize AppMetrica SDK: ${e.message}")
        }
    }
}

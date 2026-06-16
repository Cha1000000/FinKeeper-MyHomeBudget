package ru.homebudget.finkeeper.di

import app.cash.sqldelight.db.SqlDriver
import org.koin.dsl.module
import ru.homebudget.finkeeper.data.local.database.DatabaseProvider
import ru.homebudget.finkeeper.data.local.database.createDesktopDatabaseDriver
import ru.homebudget.finkeeper.data.remote.DesktopSocialAuthLauncher
import ru.homebudget.finkeeper.data.remote.DesktopSecureTokenStorage
import ru.homebudget.finkeeper.data.remote.SecureTokenStorage
import ru.homebudget.finkeeper.data.remote.SocialAuthLauncher
import ru.homebudget.finkeeper.data.network.NetworkMonitor
import ru.homebudget.finkeeper.data.update.AppUpdateChecker
import ru.homebudget.finkeeper.data.update.DesktopUpdateChecker

/**
 * Desktop (JVM) module for Koin DI.
 * Provides platform-specific dependencies for macOS, Windows, and Linux.
 */
val desktopAppModule = module {
    single<SecureTokenStorage> { DesktopSecureTokenStorage() }
    single<SocialAuthLauncher> { DesktopSocialAuthLauncher() }
    single<AppUpdateChecker> { DesktopUpdateChecker(get()) }

    // Database Driver for Desktop (JVM SQLite)
    single<SqlDriver> {
        createDesktopDatabaseDriver("finkeeper.db")
    }

    // Database Provider
    single { DatabaseProvider(get()) }

    // Network Monitor
    single { NetworkMonitor() }
}

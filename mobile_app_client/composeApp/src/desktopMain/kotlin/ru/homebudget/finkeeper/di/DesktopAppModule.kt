package ru.homebudget.finkeeper.di

import app.cash.sqldelight.db.SqlDriver
import org.koin.dsl.module
import ru.homebudget.finkeeper.data.local.database.DatabaseProvider
import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.createDesktopDatabaseDriver
import ru.homebudget.finkeeper.data.network.NetworkMonitor

/**
 * Desktop (JVM) module for Koin DI.
 * Provides platform-specific dependencies for macOS, Windows, and Linux.
 */
val desktopAppModule = module {
    // Database Driver for Desktop (JVM SQLite)
    single<SqlDriver> {
        createDesktopDatabaseDriver("finkeeper.db")
    }

    // Database Provider
    single { DatabaseProvider(get()) }

    // Network Monitor
    single { NetworkMonitor() }
}

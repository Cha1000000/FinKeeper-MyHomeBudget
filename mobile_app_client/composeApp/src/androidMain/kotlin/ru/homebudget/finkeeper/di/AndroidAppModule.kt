package ru.homebudget.finkeeper.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.dsl.module
import ru.homebudget.finkeeper.data.local.database.DatabaseProvider
import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.network.NetworkMonitor
import ru.homebudget.finkeeper.data.remote.AndroidSocialAuthLauncher
import ru.homebudget.finkeeper.data.remote.AndroidSecureTokenStorage
import ru.homebudget.finkeeper.data.remote.SecureTokenStorage
import ru.homebudget.finkeeper.data.remote.SocialAuthLauncher

/**
 * Android модуль Koin DI
 * Содержит зависимости специфичные для Android
 */
val androidAppModule =
    module {
        single<SecureTokenStorage> { AndroidSecureTokenStorage(get()) }
        single<SocialAuthLauncher> { AndroidSocialAuthLauncher(get()) }

        // Database Driver для Android
        single<SqlDriver> {
            AndroidSqliteDriver(
                FinKeeperDatabase.Schema,
                get<Context>(),
                "FinKeeperDatabase.db",
            )
        }

        // Database Provider
        single { DatabaseProvider(get()) }

        // Network Monitor
        single { NetworkMonitor(get()) }
    }

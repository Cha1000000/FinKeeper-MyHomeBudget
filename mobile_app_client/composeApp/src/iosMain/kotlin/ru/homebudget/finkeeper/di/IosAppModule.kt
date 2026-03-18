package ru.homebudget.finkeeper.di

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.koin.dsl.module
import ru.homebudget.finkeeper.data.local.database.DatabaseProvider
import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.network.NetworkMonitor
import ru.homebudget.finkeeper.data.remote.IosSocialAuthLauncher
import ru.homebudget.finkeeper.data.remote.IosSecureTokenStorage
import ru.homebudget.finkeeper.data.remote.SecureTokenStorage
import ru.homebudget.finkeeper.data.remote.SocialAuthLauncher

/**
 * iOS модуль Koin DI
 * Содержит зависимости специфичные для iOS
 */
val iosAppModule =
    module {
        single<SecureTokenStorage> { IosSecureTokenStorage() }
        single<SocialAuthLauncher> { IosSocialAuthLauncher() }

        // Database Driver для iOS
        single {
            NativeSqliteDriver(
                FinKeeperDatabase.Schema,
                "FinKeeperDatabase.db",
            )
        }

        // Database Provider
        single { DatabaseProvider(get()) }

        // Network Monitor
        single { NetworkMonitor() }
    }

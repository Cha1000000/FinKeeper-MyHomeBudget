package ru.homebudget.finkeeper.di

import com.squareup.sqldelight.drivers.native.NativeSqliteDriver
import org.koin.dsl.module
import ru.homebudget.finkeeper.data.local.database.DatabaseProvider
import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.network.NetworkMonitor

/**
 * iOS модуль Koin DI
 * Содержит зависимости специфичные для iOS
 */
val iosAppModule =
    module {
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

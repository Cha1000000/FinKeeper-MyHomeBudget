package ru.homebudget.finkeeper.data.local.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * Database driver factory for iOS platform.
 * Uses SQLDelight Native SQLite driver for iOS.
 */
fun createIosDatabaseDriver(
    databaseName: String = "finkeeper.db"
): SqlDriver {
    return NativeSqliteDriver(
        schema = FinKeeperDatabase.Schema,
        name = databaseName
    )
}

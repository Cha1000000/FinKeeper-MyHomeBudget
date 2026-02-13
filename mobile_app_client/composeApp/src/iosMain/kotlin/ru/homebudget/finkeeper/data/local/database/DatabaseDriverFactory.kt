package ru.homebudget.finkeeper.data.local.database

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver

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

package ru.homebudget.finkeeper.data.local.database

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import java.io.File

/**
 * Creates a SQLite database driver for desktop (JVM) platform.
 * Supports macOS, Windows, and Linux.
 * Stores database in user's home directory under .finkeeper/
 */
fun createDesktopDatabaseDriver(databaseName: String = "finkeeper.db"): SqlDriver {
    val databaseDir = File(System.getProperty("user.home"), ".finkeeper")
    if (!databaseDir.exists()) {
        databaseDir.mkdirs()
    }

    val databasePath = File(databaseDir, databaseName).absolutePath
    val databaseFile = File(databaseDir, databaseName)
    val isNewDatabase = !databaseFile.exists()

    val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")

    // Create schema only for new databases
    if (isNewDatabase) {
        FinKeeperDatabase.Schema.create(driver)
    }

    return driver
}

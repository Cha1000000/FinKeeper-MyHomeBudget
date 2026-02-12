package ru.homebudget.finkeeper.data.local.database

import com.squareup.sqldelight.db.SqlDriver

/**
 * Провайдер базы данных
 * Создаёт экземпляр FinKeeperDatabase из драйвера
 */
class DatabaseProvider(private val driver: SqlDriver) {
    val database: FinKeeperDatabase = FinKeeperDatabase(driver)
}

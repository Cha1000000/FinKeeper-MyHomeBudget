package ru.homebudget.finkeeper.data.local.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Провайдер базы данных
 * Создаёт экземпляр FinKeeperDatabase из драйвера
 */
class DatabaseProvider(private val driver: SqlDriver) {

    init {
        // Лёгкие аддитивные миграции для баз, созданных старыми версиями приложения.
        //
        // В проекте не используются .sqm-миграции SQLDelight, а версия схемы не меняется,
        // поэтому ни на одной платформе (Android / iOS / Desktop) новые колонки в уже
        // существующие таблицы при апдейте автоматически не добавляются. Делаем это вручную
        // здесь — в общей точке, где доступен driver на всех платформах.
        runAdditiveMigrations(driver)
    }

    val database: FinKeeperDatabase = FinKeeperDatabase(driver)
}

/**
 * Идемпотентно добавляет недостающие колонки в существующие таблицы.
 *
 * `ALTER TABLE ... ADD COLUMN` на уже существующей колонке (новая БД, повторный запуск)
 * бросает "duplicate column name" — такую ошибку молча игнорируем, так что вызов безопасен
 * при любом состоянии БД.
 */
private fun runAdditiveMigrations(driver: SqlDriver) {
    val statements = listOf(
        // v2.1.0 — фиксированные/повторяющиеся категории и источники дохода
        "ALTER TABLE categories ADD COLUMN is_fixed INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE categories ADD COLUMN fixed_amount INTEGER",
        "ALTER TABLE categories ADD COLUMN auto_day INTEGER",
        "ALTER TABLE income_sources ADD COLUMN is_fixed INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE income_sources ADD COLUMN fixed_amount INTEGER",
        "ALTER TABLE income_sources ADD COLUMN auto_day INTEGER",
    )
    for (sql in statements) {
        try {
            driver.execute(null, sql, 0)
        } catch (_: Exception) {
            // Колонка уже существует — это нормально, идём дальше.
        }
    }
}

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
        // v2.2.0 — план-слой: ручное подтверждение регулярного платежа
        "ALTER TABLE categories ADD COLUMN require_confirm INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE income_sources ADD COLUMN require_confirm INTEGER NOT NULL DEFAULT 0",
        // v2.2.0 — план-слой: исключения (skip/override) и зеркало материализованных платежей
        """CREATE TABLE IF NOT EXISTS planned_overrides (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            template_type TEXT NOT NULL CHECK (template_type IN ('category', 'income_source')),
            template_id INTEGER NOT NULL,
            month_id INTEGER NOT NULL,
            override_amount INTEGER,
            override_day INTEGER,
            is_skipped INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now')),
            sync_status TEXT NOT NULL DEFAULT 'synced',
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (month_id) REFERENCES months(id) ON DELETE CASCADE,
            UNIQUE(user_id, template_type, template_id, month_id)
        )""",
        """CREATE TABLE IF NOT EXISTS auto_created_records (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            template_type TEXT NOT NULL,
            template_id INTEGER NOT NULL,
            month_id INTEGER NOT NULL,
            created_record_type TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (month_id) REFERENCES months(id) ON DELETE CASCADE,
            UNIQUE(user_id, template_type, template_id, month_id)
        )""",
        "CREATE INDEX IF NOT EXISTS idx_planned_overrides_month ON planned_overrides(user_id, month_id)",
        "CREATE INDEX IF NOT EXISTS idx_auto_created_month ON auto_created_records(user_id, month_id)",
    )
    for (sql in statements) {
        try {
            driver.execute(null, sql, 0)
        } catch (_: Exception) {
            // Колонка/таблица уже существует — это нормально, идём дальше.
        }
    }
}

package ru.homebudget.finkeeper.data.local.database

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

internal fun migrateSavingsTransactionsMonthColumn(
    driver: SqlDriver,
    log: (String) -> Unit = { println("[DB] $it") },
): Boolean {
    // v2.2.5 — общая для всех платформ (раньше была только на десктопе): базы Android/iOS,
    // созданные до появления month_id (приложение ≤ 1.1.0), падали на любом чтении копилок
    // «no such column: savings_transactions.month_id».
    // Миграция: month_id должен стоять 4-й колонкой (SQLDelight читает колонки по позиции,
    // а ALTER TABLE ADD COLUMN добавляет в конец). Пересобираем таблицу, только если это не так.
    // Раньше пересборка шла на каждом запуске и теряла month_id — неотправленные пополнения
    // уходили на сервер без месяца, и скрытый расход копилки не создавался.
    try {
        val columns = tableColumns(driver, "savings_transactions")
        val needsMigration = columns.isNotEmpty() && columns.getOrNull(3) != "month_id"
        val monthIdSource = if ("month_id" in columns) "month_id" else "NULL"

        if (needsMigration) {
            log("Migrating savings_transactions table...")
            val migrationStatements = listOf(
                "DROP TABLE IF EXISTS savings_transactions_new",
                """CREATE TABLE savings_transactions_new (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    savings_goal_id INTEGER NOT NULL,
                    month_id INTEGER,
                    amount INTEGER NOT NULL CHECK (amount != 0),
                    type TEXT NOT NULL CHECK (type IN ('deposit', 'withdrawal')),
                    description TEXT,
                    date TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    server_id TEXT,
                    sync_status TEXT NOT NULL DEFAULT 'synced',
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (savings_goal_id) REFERENCES savings_goals(id) ON DELETE CASCADE,
                    FOREIGN KEY (month_id) REFERENCES months(id) ON DELETE SET NULL
                )""",
                """INSERT INTO savings_transactions_new (id, user_id, savings_goal_id, month_id, amount, type, description, date, created_at, updated_at, server_id, sync_status)
                   SELECT id, user_id, savings_goal_id, $monthIdSource, amount, type, description, date, created_at, updated_at, server_id, sync_status
                   FROM savings_transactions""",
                "DROP TABLE savings_transactions",
                "ALTER TABLE savings_transactions_new RENAME TO savings_transactions",
                "CREATE INDEX IF NOT EXISTS idx_savings_transactions_goal ON savings_transactions(savings_goal_id)",
            )
            // Одной транзакцией: при сбое на любом шаге (или падении процесса) SQLite откатит
            // и DROP, и RENAME — таблица с данными останется прежней. Именно транзакция
            // SQLDelight: файловый JdbcSqliteDriver берёт соединение на каждый execute,
            // и ручной BEGIN/COMMIT транзакцию не открывает
            try {
                object : TransacterImpl(driver) {}.transaction {
                    for (sql in migrationStatements) {
                        driver.execute(null, sql.trimIndent(), 0, null)
                    }
                }
                log("Table savings_transactions migrated successfully")
                return true
            } catch (e: Exception) {
                log("Migration rolled back: ${e.message}")
            }
        }
    } catch (e: Exception) {
        log("Migration error: ${e.message}")
    }
    return false
}

// Имена колонок таблицы в порядке их объявления (пустой список — таблицы нет)
private fun tableColumns(driver: SqlDriver, table: String): List<String> =
    driver.executeQuery(
        identifier = null,
        sql = "PRAGMA table_info($table)",
        mapper = { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) {
                cursor.getString(1)?.let(names::add)
            }
            QueryResult.Value(names)
        },
        parameters = 0,
    ).value

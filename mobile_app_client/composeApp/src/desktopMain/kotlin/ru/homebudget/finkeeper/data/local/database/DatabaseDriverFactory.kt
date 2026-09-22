package ru.homebudget.finkeeper.data.local.database

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

/**
 * Desktop sync log — пишет в ~/.finkeeper/sync_debug.log
 */
object DesktopSyncLog {
    private val logFile: File by lazy {
        val dir = File(System.getProperty("user.home"), ".finkeeper")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "sync_debug.log")
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    fun log(tag: String, message: String) {
        val ts = dateFormat.format(Date())
        val line = "$ts [$tag] $message"
        println(line)
        try {
            PrintWriter(FileWriter(logFile, true)).use { it.println(line) }
        } catch (_: Exception) { /* ignore IO errors */ }
    }
}

/**
 * Creates a SQLite database driver for desktop (JVM) platform.
 * Supports macOS, Windows, and Linux.
 * Stores database in user's home directory under .finkeeper/
 * 
 * Note: Uses a thread-safe wrapper around JdbcSqliteDriver to ensure
 * safe concurrent access from multiple coroutines.
 */
fun createDesktopDatabaseDriver(databaseName: String = "finkeeper.db"): SqlDriver {
    val databaseDir = File(System.getProperty("user.home"), ".finkeeper")
    if (!databaseDir.exists()) {
        databaseDir.mkdirs()
    }

    val databasePath = File(databaseDir, databaseName).absolutePath
    val databaseFile = File(databaseDir, databaseName)
    val isNewDatabase = !databaseFile.exists()

    // Enable WAL mode for better concurrent read/write performance (similar to Android)
    val properties = Properties().apply {
        put("journal_mode", "WAL")
    }
    val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath", properties)

    if (isNewDatabase) {
        FinKeeperDatabase.Schema.create(driver)
        DesktopSyncLog.log("DB", "Created new database at $databasePath")
    } else {
        // Ensure ALL tables exist for databases created by older app versions
        ensureSchemaUpToDate(driver)
        DesktopSyncLog.log("DB", "Opened existing database at $databasePath, ensured schema")
    }

    // JdbcSqliteDriver is already thread-safe in SQLDelight 2.x
    return driver
}

/**
 * Ensures all required tables and indexes exist in an existing database.
 * Uses CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS so it's safe
 * to run on databases that already have the tables.
 */
private fun ensureSchemaUpToDate(driver: SqlDriver) {
    val statements = listOf(
        // ── Tables ──
        """CREATE TABLE IF NOT EXISTS users (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL UNIQUE,
            email TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now'))
        )""",
        """CREATE TABLE IF NOT EXISTS categories (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            type TEXT NOT NULL CHECK (type IN ('expense', 'income')),
            icon TEXT, color TEXT,
            sort_order INTEGER NOT NULL DEFAULT 0,
            is_active INTEGER NOT NULL DEFAULT 1,
            is_fixed INTEGER NOT NULL DEFAULT 0,
            fixed_amount INTEGER,
            auto_day INTEGER,
            require_confirm INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now')),
            server_id TEXT,
            sync_status TEXT NOT NULL DEFAULT 'synced',
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS income_sources (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            sort_order INTEGER NOT NULL DEFAULT 0,
            is_active INTEGER NOT NULL DEFAULT 1,
            is_fixed INTEGER NOT NULL DEFAULT 0,
            fixed_amount INTEGER,
            auto_day INTEGER,
            require_confirm INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now')),
            server_id TEXT,
            sync_status TEXT NOT NULL DEFAULT 'synced',
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS months (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            year INTEGER NOT NULL CHECK (year >= 2000 AND year <= 2100),
            month INTEGER NOT NULL CHECK (month >= 1 AND month <= 12),
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now')),
            server_id TEXT,
            sync_status TEXT NOT NULL DEFAULT 'synced',
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            UNIQUE(user_id, year, month)
        )""",
        """CREATE TABLE IF NOT EXISTS incomes (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            month_id INTEGER NOT NULL,
            income_source_id INTEGER NOT NULL,
            amount INTEGER NOT NULL CHECK (amount >= 0),
            description TEXT,
            date TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now')),
            server_id TEXT,
            sync_status TEXT NOT NULL DEFAULT 'synced',
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (month_id) REFERENCES months(id) ON DELETE CASCADE,
            FOREIGN KEY (income_source_id) REFERENCES income_sources(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS expenses (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            month_id INTEGER NOT NULL,
            category_id INTEGER NOT NULL,
            amount INTEGER NOT NULL,
            description TEXT,
            date TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now')),
            server_id TEXT,
            sync_status TEXT NOT NULL DEFAULT 'synced',
            is_hidden INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (month_id) REFERENCES months(id) ON DELETE CASCADE,
            FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS budgets (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            month_id INTEGER NOT NULL,
            category_id INTEGER NOT NULL,
            limit_amount INTEGER NOT NULL CHECK (limit_amount >= 0),
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now')),
            server_id TEXT,
            sync_status TEXT NOT NULL DEFAULT 'synced',
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (month_id) REFERENCES months(id) ON DELETE CASCADE,
            FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,
            UNIQUE(user_id, month_id, category_id)
        )""",
        """CREATE TABLE IF NOT EXISTS savings_goals (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            target_amount INTEGER NOT NULL CHECK (target_amount >= 0),
            current_amount INTEGER NOT NULL DEFAULT 0 CHECK (current_amount >= 0),
            color TEXT, icon TEXT, target_date TEXT,
            is_achieved INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now')),
            server_id TEXT,
            sync_status TEXT NOT NULL DEFAULT 'synced',
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS savings_transactions (
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
        """CREATE TABLE IF NOT EXISTS sync_queue (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            entity_type TEXT NOT NULL,
            entity_id INTEGER NOT NULL,
            operation TEXT NOT NULL CHECK (operation IN ('insert', 'update', 'delete')),
            payload TEXT,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            retry_count INTEGER NOT NULL DEFAULT 0,
            last_attempt TEXT,
            status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'syncing', 'failed', 'completed')),
            error_message TEXT,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
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
        // ── Indexes ──
        "CREATE INDEX IF NOT EXISTS idx_categories_user ON categories(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_categories_type ON categories(type)",
        "CREATE INDEX IF NOT EXISTS idx_income_sources_user ON income_sources(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_months_user ON months(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_incomes_month ON incomes(month_id)",
        "CREATE INDEX IF NOT EXISTS idx_incomes_user ON incomes(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_expenses_month ON expenses(month_id)",
        "CREATE INDEX IF NOT EXISTS idx_expenses_category ON expenses(category_id)",
        "CREATE INDEX IF NOT EXISTS idx_expenses_user ON expenses(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_budgets_month ON budgets(month_id)",
        "CREATE INDEX IF NOT EXISTS idx_budgets_user ON budgets(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_savings_goals_user ON savings_goals(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_savings_transactions_goal ON savings_transactions(savings_goal_id)",
        "CREATE INDEX IF NOT EXISTS idx_sync_queue_user ON sync_queue(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON sync_queue(status)",
        "CREATE INDEX IF NOT EXISTS idx_planned_overrides_month ON planned_overrides(user_id, month_id)",
        "CREATE INDEX IF NOT EXISTS idx_auto_created_month ON auto_created_records(user_id, month_id)",
    )
    for (sql in statements) {
        try {
            driver.execute(null, sql.trimIndent(), 0, null)
        } catch (e: Exception) {
            DesktopSyncLog.log("DB", "ensureSchema failed for: ${sql.take(60)}... error=${e.message}")
        }
    }

    migrateSavingsTransactionsMonthColumn(driver)
}

internal fun migrateSavingsTransactionsMonthColumn(driver: SqlDriver): Boolean {
    // Миграция: month_id должен стоять 4-й колонкой (SQLDelight читает колонки по позиции,
    // а ALTER TABLE ADD COLUMN добавляет в конец). Пересобираем таблицу, только если это не так.
    // Раньше пересборка шла на каждом запуске и теряла month_id — неотправленные пополнения
    // уходили на сервер без месяца, и скрытый расход копилки не создавался.
    try {
        val columns = tableColumns(driver, "savings_transactions")
        val needsMigration = columns.isNotEmpty() && columns.getOrNull(3) != "month_id"
        val monthIdSource = if ("month_id" in columns) "month_id" else "NULL"

        if (needsMigration) {
            DesktopSyncLog.log("DB", "Migrating savings_transactions table...")
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
                """INSERT OR IGNORE INTO savings_transactions_new (id, user_id, savings_goal_id, month_id, amount, type, description, date, created_at, updated_at, server_id, sync_status)
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
                DesktopSyncLog.log("DB", "Table savings_transactions migrated successfully")
                return true
            } catch (e: Exception) {
                DesktopSyncLog.log("DB", "Migration rolled back: ${e.message}")
            }
        }
    } catch (e: Exception) {
        DesktopSyncLog.log("DB", "Migration error: ${e.message}")
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


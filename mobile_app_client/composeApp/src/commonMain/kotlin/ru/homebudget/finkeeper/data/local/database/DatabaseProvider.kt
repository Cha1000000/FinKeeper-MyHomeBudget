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

    // v2.2.1 — схлопывание локальных дублей категорий/источников по (user_id, name) c
    // перепривязкой ссылок (расходы/доходы/бюджеты/план) и UNIQUE-индексы против повторного
    // появления. Для свежих БД индексы уже есть из схемы (.sq), здесь — путь для существующих
    // установок (см. [[kmp-db-migrations]]).
    dedupeUserScopedLocal(
        driver,
        buildDedupeStatements(
            table = "categories",
            indexName = "idx_categories_user_name",
            refs = listOf(
                DedupRef("expenses", "category_id"),
                DedupRef("budgets", "category_id", unique = true),
                DedupRef("planned_overrides", "template_id", where = "template_type = 'category'", unique = true),
                DedupRef("auto_created_records", "template_id", where = "template_type = 'category'", unique = true),
            ),
        ),
    )
    dedupeUserScopedLocal(
        driver,
        buildDedupeStatements(
            table = "income_sources",
            indexName = "idx_income_sources_user_name",
            refs = listOf(
                DedupRef("incomes", "income_source_id"),
                DedupRef("planned_overrides", "template_id", where = "template_type = 'income_source'", unique = true),
                DedupRef("auto_created_records", "template_id", where = "template_type = 'income_source'", unique = true),
            ),
        ),
    )
}

private data class DedupRef(
    val table: String,
    val column: String,
    val where: String? = null,
    val unique: Boolean = false,
)

/**
 * Строит упорядоченный список SQL для схлопывания дублей в [table] по (user_id, name):
 * 1) перепривязка ссылок с дублей на «канонический» (минимальный id) ряд;
 * 2) у уникальных ссылок — удаление конфликтных остатков;
 * 3) удаление самих дублей; 4) создание UNIQUE-индекса.
 * Только корреляционные подзапросы (без UPDATE..FROM / оконных) — работает и на старом SQLite (Android minSdk 24).
 */
private fun buildDedupeStatements(table: String, indexName: String, refs: List<DedupRef>): List<String> {
    val dupIds =
        "SELECT x.id FROM $table x " +
            "WHERE x.id > (SELECT MIN(y.id) FROM $table y WHERE y.user_id = x.user_id AND y.name = x.name)"
    val stmts = mutableListOf<String>()
    for (ref in refs) {
        val canonical =
            "(SELECT MIN(y.id) FROM $table y " +
                "WHERE y.user_id = (SELECT user_id FROM $table WHERE id = ${ref.table}.${ref.column}) " +
                "AND y.name = (SELECT name FROM $table WHERE id = ${ref.table}.${ref.column}))"
        val extra = ref.where?.let { " AND $it" } ?: ""
        val verb = if (ref.unique) "UPDATE OR IGNORE" else "UPDATE"
        stmts += "$verb ${ref.table} SET ${ref.column} = $canonical WHERE ${ref.column} IN ($dupIds)$extra"
        if (ref.unique) {
            stmts += "DELETE FROM ${ref.table} WHERE ${ref.column} IN ($dupIds)$extra"
        }
    }
    stmts += "DELETE FROM $table WHERE id IN ($dupIds)"
    stmts += "CREATE UNIQUE INDEX IF NOT EXISTS $indexName ON $table(user_id, name)"
    return stmts
}

/**
 * Выполняет шаги дедупа по порядку. При ошибке (например, перепривязка не удалась) ПРЕРЫВАЕТ
 * последовательность для этой таблицы, чтобы НЕ дойти до удаления дублей и не осиротить/каскадно
 * удалить данные. Повторный запуск безопасен (на чистой БД шаги — no-op).
 */
private fun dedupeUserScopedLocal(driver: SqlDriver, statements: List<String>) {
    for (sql in statements) {
        try {
            driver.execute(null, sql, 0)
        } catch (_: Exception) {
            return
        }
    }
}

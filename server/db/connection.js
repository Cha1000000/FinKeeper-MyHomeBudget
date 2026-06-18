const Database = require('better-sqlite3');
const path = require('path');
const logger = require('../logger');

function nowIso() {
    return new Date().toISOString();
}

const dbPath = process.env.DB_PATH?.trim()
    ? path.resolve(process.env.DB_PATH.trim())
    : path.resolve(__dirname, '..', 'database.sqlite');
const db = new Database(dbPath);

function hasTable(tableName) {
    const row = db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").get(tableName);
    return !!row;
}

function hasColumn(tableName, columnName) {
    if (!hasTable(tableName)) {
        return false;
    }
    const columns = db.prepare(`PRAGMA table_info(${tableName})`).all();
    return columns.some(column => column.name === columnName);
}

function addColumnIfMissing(tableName, columnName, definition) {
    if (!hasColumn(tableName, columnName)) {
        db.exec(`ALTER TABLE ${tableName} ADD COLUMN ${columnName} ${definition}`);
        return true;
    }
    return false;
}

function backfillTimestampColumns(tableName) {
    const currentTimestamp = nowIso();
    let createdAtRows = 0;
    let updatedAtRows = 0;

    if (hasColumn(tableName, 'created_at')) {
        const result = db.prepare(`UPDATE ${tableName} SET created_at = ? WHERE created_at IS NULL OR created_at = ''`).run(currentTimestamp);
        createdAtRows = result.changes;
    }

    if (hasColumn(tableName, 'updated_at')) {
        const result = db.prepare(`UPDATE ${tableName} SET updated_at = COALESCE(NULLIF(updated_at, ''), created_at, ?) WHERE updated_at IS NULL OR updated_at = ''`).run(currentTimestamp);
        updatedAtRows = result.changes;
    }

    return {
        createdAtRows,
        updatedAtRows
    };
}

/**
 * Схлопывание дублей в user-scoped справочнике (categories / income_sources) по (user_id, name):
 * оставляем строку с минимальным id, ссылки на дубли ПЕРЕПРИВЯЗЫВАЕМ на оставляемую строку
 * (чтобы не осиротить расходы/доходы/планы), затем дубли удаляем.
 *
 * references: [{ table, column, where?, unique? }]
 *  - unique: true — у целевой таблицы есть UNIQUE по (…, column, …); перепривязка делается через
 *    UPDATE OR IGNORE, конфликтные строки (дубликаты записи оставляемой категории) затем удаляются.
 */
function dedupeUserScopedTable(tableName, references) {
    if (!hasTable(tableName)) {
        return;
    }

    const groups = db.prepare(
        `SELECT user_id, name, MIN(id) AS keepId, COUNT(*) AS cnt
         FROM ${tableName}
         GROUP BY user_id, name
         HAVING cnt > 1`
    ).all();

    if (groups.length === 0) {
        return;
    }

    const dedupe = db.transaction(() => {
        for (const group of groups) {
            const dupIds = db.prepare(
                `SELECT id FROM ${tableName} WHERE user_id = ? AND name = ? AND id <> ?`
            ).all(group.user_id, group.name, group.keepId).map(row => row.id);
            if (dupIds.length === 0) {
                continue;
            }
            const placeholders = dupIds.map(() => '?').join(',');

            for (const ref of references) {
                if (!hasColumn(ref.table, ref.column)) {
                    continue;
                }
                const extra = ref.where ? ` AND ${ref.where}` : '';
                const verb = ref.unique ? 'UPDATE OR IGNORE' : 'UPDATE';
                db.prepare(
                    `${verb} ${ref.table} SET ${ref.column} = ? WHERE ${ref.column} IN (${placeholders})${extra}`
                ).run(group.keepId, ...dupIds);
                if (ref.unique) {
                    // Строки, которые не удалось перепривязать из-за конфликта UNIQUE — это
                    // дубликаты записи оставляемой категории/источника; удаляем их.
                    db.prepare(
                        `DELETE FROM ${ref.table} WHERE ${ref.column} IN (${placeholders})${extra}`
                    ).run(...dupIds);
                }
            }

            db.prepare(`DELETE FROM ${tableName} WHERE id IN (${placeholders})`).run(...dupIds);
        }
    });
    dedupe();
    logger.info('schema_dedupe_done', { tableName, groups: groups.length });
}

function ensureSchemaUpToDate() {
    logger.info('schema_ensure_start', { dbPath });
    db.exec(`
        CREATE TABLE IF NOT EXISTS idempotency_keys (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            operation_id TEXT NOT NULL,
            request_signature TEXT NOT NULL,
            response_status INTEGER NOT NULL,
            response_body TEXT NOT NULL,
            created_at TEXT NOT NULL,
            UNIQUE(user_id, operation_id),
            FOREIGN KEY (user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS deleted_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            entity_type TEXT NOT NULL,
            entity_id INTEGER NOT NULL,
            deleted_at TEXT NOT NULL,
            UNIQUE(user_id, entity_type, entity_id),
            FOREIGN KEY (user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS auth_refresh_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            token_hash TEXT NOT NULL UNIQUE,
            expires_at TEXT NOT NULL,
            created_at TEXT NOT NULL,
            last_used_at TEXT,
            revoked_at TEXT,
            FOREIGN KEY (user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS auth_email_verification_tokens (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            email TEXT NOT NULL,
            token_hash TEXT NOT NULL UNIQUE,
            expires_at TEXT NOT NULL,
            created_at TEXT NOT NULL,
            used_at TEXT,
            FOREIGN KEY (user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS auth_password_reset_tokens (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            email TEXT NOT NULL,
            token_hash TEXT NOT NULL UNIQUE,
            expires_at TEXT NOT NULL,
            created_at TEXT NOT NULL,
            used_at TEXT,
            FOREIGN KEY (user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS auth_identities (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            provider TEXT NOT NULL,
            provider_user_id TEXT NOT NULL,
            provider_email TEXT,
            provider_email_verified INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            last_login_at TEXT,
            FOREIGN KEY (user_id) REFERENCES users(id),
            UNIQUE(provider, provider_user_id)
        );

        CREATE TABLE IF NOT EXISTS auth_login_exchange_codes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            provider TEXT NOT NULL,
            code_hash TEXT NOT NULL UNIQUE,
            client_type TEXT,
            redirect_uri TEXT,
            expires_at TEXT NOT NULL,
            created_at TEXT NOT NULL,
            used_at TEXT,
            FOREIGN KEY (user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS auth_social_login_attempts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            attempt_token_hash TEXT NOT NULL UNIQUE,
            provider TEXT NOT NULL,
            client_type TEXT NOT NULL,
            redirect_uri TEXT,
            user_id INTEGER,
            status TEXT NOT NULL,
            exchange_code TEXT,
            error_code TEXT,
            error_description TEXT,
            expires_at TEXT NOT NULL,
            created_at TEXT NOT NULL,
            completed_at TEXT,
            consumed_at TEXT,
            FOREIGN KEY (user_id) REFERENCES users(id)
        );
    `);

    addColumnIfMissing('users', 'email', 'TEXT');
    addColumnIfMissing('users', 'email_confirmed_at', 'TEXT');
    addColumnIfMissing('users', 'auth_password_enabled', 'INTEGER NOT NULL DEFAULT 1');
    addColumnIfMissing('auth_refresh_sessions', 'is_persistent', 'INTEGER NOT NULL DEFAULT 0');
    addColumnIfMissing('auth_social_login_attempts', 'exchange_code', 'TEXT');

    // Колонка используется кодом (summary, корректировки копилки), но в db_setup.js
    // её нет — без этой миграции свежая установка падает на первом же запросе к ней
    addColumnIfMissing('savings_transactions', 'is_adjustment', 'INTEGER DEFAULT 0');

    // Фиксированные (регулярные) категории и источники дохода
    addColumnIfMissing('categories', 'is_fixed', 'INTEGER DEFAULT 0');
    addColumnIfMissing('categories', 'fixed_amount', 'REAL');
    addColumnIfMissing('categories', 'auto_day', 'INTEGER');
    addColumnIfMissing('income_sources', 'is_fixed', 'INTEGER DEFAULT 0');
    addColumnIfMissing('income_sources', 'fixed_amount', 'REAL');
    addColumnIfMissing('income_sources', 'auto_day', 'INTEGER');

    // Ручное подтверждение регулярного платежа: 1 — авто-постинг в дату не выполняется,
    // плановый платёж ждёт явного подтверждения пользователем («Оплачено»)
    addColumnIfMissing('categories', 'require_confirm', 'INTEGER DEFAULT 0');
    addColumnIfMissing('income_sources', 'require_confirm', 'INTEGER DEFAULT 0');

    db.exec(`
        CREATE TABLE IF NOT EXISTS auto_created_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            template_type TEXT NOT NULL,
            template_id INTEGER NOT NULL,
            month_id INTEGER NOT NULL,
            created_record_id INTEGER NOT NULL,
            created_record_type TEXT NOT NULL,
            amount_at_creation REAL NOT NULL,
            created_at TEXT NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id),
            FOREIGN KEY (month_id) REFERENCES months(id),
            UNIQUE(user_id, template_type, template_id, month_id)
        );
    `);

    // Исключения план-слоя: хранятся ТОЛЬКО отклонения от шаблона на конкретный месяц
    // (пропуск, изменённая сумма/день). Виртуальные плановые платежи не материализуются.
    // При деактивации шаблона (is_active=0 / is_fixed=0) строки становятся инертными
    // и не удаляются — при реактивации правки месяца восстанавливаются.
    db.exec(`
        CREATE TABLE IF NOT EXISTS planned_overrides (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            template_type TEXT NOT NULL,
            template_id INTEGER NOT NULL,
            month_id INTEGER NOT NULL,
            override_amount REAL,
            override_day INTEGER,
            is_skipped INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id),
            FOREIGN KEY (month_id) REFERENCES months(id),
            UNIQUE(user_id, template_type, template_id, month_id)
        );

        CREATE INDEX IF NOT EXISTS idx_planned_overrides_month
        ON planned_overrides(user_id, month_id);
    `);

    db.exec(`
        CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_unique
        ON users(email)
        WHERE email IS NOT NULL;

        CREATE INDEX IF NOT EXISTS idx_auth_email_verification_tokens_user_id
        ON auth_email_verification_tokens(user_id);

        CREATE INDEX IF NOT EXISTS idx_auth_password_reset_tokens_user_id
        ON auth_password_reset_tokens(user_id);

        CREATE INDEX IF NOT EXISTS idx_auth_identities_user_id
        ON auth_identities(user_id);

        CREATE INDEX IF NOT EXISTS idx_auth_login_exchange_codes_user_id
        ON auth_login_exchange_codes(user_id);

        CREATE INDEX IF NOT EXISTS idx_auth_social_login_attempts_status
        ON auth_social_login_attempts(status);
    `);

    // Дедуп категорий/источников по (user_id, name) с перепривязкой ссылок и UNIQUE-индексы,
    // предотвращающие повторное появление дублей (баг гонки синка/регистрации).
    // Дедуп строго ДО создания UNIQUE-индекса — иначе индекс упадёт на существующих дублях.
    //
    // Весь блок обёрнут в try/catch: ensureSchemaUpToDate() выполняется на импорте, и любая
    // непредвиденная ошибка здесь иначе уронила бы СТАРТ сервера для всех. Дедуп атомарен
    // (внутри db.transaction — при сбое откатывается без частичной порчи), поэтому при ошибке
    // безопаснее залогировать и продолжить: данные целы, а новые дубли всё равно отсекаются
    // на уровне роутов (SELECT-before-INSERT + обработка UNIQUE). Индекс довыполнится при
    // следующем рестарте, когда причина устранена.
    try {
        dedupeUserScopedTable('categories', [
            { table: 'expenses', column: 'category_id' },
            { table: 'budgets', column: 'category_id' },
            { table: 'auto_created_records', column: 'template_id', where: "template_type = 'category'", unique: true },
            { table: 'planned_overrides', column: 'template_id', where: "template_type = 'category'", unique: true },
        ]);
        dedupeUserScopedTable('income_sources', [
            // incomes ссылается на источник по тексту (source), FK по id нет — доходы не осиротеют.
            { table: 'auto_created_records', column: 'template_id', where: "template_type = 'income_source'", unique: true },
            { table: 'planned_overrides', column: 'template_id', where: "template_type = 'income_source'", unique: true },
        ]);

        db.exec(`
            CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_user_name
            ON categories(user_id, name);

            CREATE UNIQUE INDEX IF NOT EXISTS idx_income_sources_user_name
            ON income_sources(user_id, name);
        `);
    } catch (error) {
        logger.error('schema_dedupe_failed', { error: error.message });
    }

    const syncTables = [
        'categories',
        'income_sources',
        'months',
        'incomes',
        'expenses',
        'budgets',
        'savings_goals',
        'savings_transactions'
    ];

    syncTables.forEach(tableName => {
        if (!hasTable(tableName)) {
            logger.warn('schema_table_missing', { tableName });
            return;
        }

        const addedColumns = [];
        if (addColumnIfMissing(tableName, 'created_at', 'TEXT')) {
            addedColumns.push('created_at');
        }
        if (addColumnIfMissing(tableName, 'updated_at', 'TEXT')) {
            addedColumns.push('updated_at');
        }

        const backfill = backfillTimestampColumns(tableName);
        logger.info('schema_table_checked', {
            tableName,
            addedColumns,
            backfillCreatedAtRows: backfill.createdAtRows,
            backfillUpdatedAtRows: backfill.updatedAtRows,
        });
    });

    logger.info('schema_ensure_done', { dbPath });
}

// Run schema check on import
ensureSchemaUpToDate();

module.exports = {
    db,
    dbPath,
    nowIso,
    hasTable,
    hasColumn,
    addColumnIfMissing,
    ensureSchemaUpToDate,
};

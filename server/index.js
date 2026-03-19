const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const Database = require('better-sqlite3');
const path = require('path');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const http = require('http');
const { WebSocketServer } = require('ws');
const { initialCategories, initialSavings, initialIncomeSources } = require('./default_data');
const logger = require('./logger');
const { getMailConfig, sendPasswordRecoveryEmail, sendEmailVerificationEmail } = require('./mailer');
const { createAuthValidation } = require('./validation/authValidation');

const DEFAULT_PORT = 3002;
const DEFAULT_DEV_ALLOWED_ORIGINS = [
    'http://localhost:5174',
    'http://127.0.0.1:5174',
    'http://localhost:4173',
    'http://127.0.0.1:4173',
];
const DEFAULT_JSON_BODY_LIMIT = '1mb';
const DEFAULT_RATE_LIMIT_WINDOW_MS = 15 * 60 * 1000;
const DEFAULT_AUTH_RATE_LIMIT_MAX = 5;
const DEFAULT_PASSWORD_RATE_LIMIT_MAX = 5;
const DEFAULT_RESTORE_RATE_LIMIT_MAX = 3;
const DEFAULT_EMAIL_VERIFICATION_RATE_LIMIT_MAX = 5;
const DEFAULT_PASSWORD_RECOVERY_REQUEST_RATE_LIMIT_MAX = 5;
const DEFAULT_PASSWORD_RECOVERY_CONFIRM_RATE_LIMIT_MAX = 5;
const DEFAULT_ACCESS_TOKEN_TTL = '60m';
const DEFAULT_REFRESH_TOKEN_TTL_DAYS = 30;
const DEFAULT_EMAIL_VERIFICATION_TOKEN_TTL_HOURS = 24;
const DEFAULT_PASSWORD_RESET_TOKEN_TTL_HOURS = 2;
const DEFAULT_SOCIAL_AUTH_EXCHANGE_CODE_TTL_MINUTES = 10;
const DEFAULT_NATIVE_SOCIAL_AUTH_POLL_INTERVAL_MS = 1500;
const BACKUP_RESTORE_CONFIRMATION_TEXT = 'ВОССТАНОВИТЬ';
const REFRESH_TOKEN_COOKIE_NAME = 'refresh_token';
const OAUTH_STATE_COOKIE_NAME = 'oauth_state';
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const GOOGLE_OAUTH_AUTHORIZE_URL = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_OAUTH_TOKEN_URL = 'https://oauth2.googleapis.com/token';
const GOOGLE_OAUTH_USERINFO_URL = 'https://openidconnect.googleapis.com/v1/userinfo';
const YANDEX_OAUTH_AUTHORIZE_URL = 'https://oauth.yandex.com/authorize';
const YANDEX_OAUTH_TOKEN_URL = 'https://oauth.yandex.com/token';
const YANDEX_OAUTH_USERINFO_URL = 'https://login.yandex.ru/info';
const isProduction = process.env.NODE_ENV === 'production';

function parsePositiveIntegerEnv(name, fallbackValue) {
    const rawValue = process.env[name]?.trim();
    if (!rawValue) {
        return fallbackValue;
    }

    const parsedValue = Number(rawValue);
    if (!Number.isInteger(parsedValue) || parsedValue <= 0) {
        throw new Error(`${name} environment variable must be a positive integer`);
    }

    return parsedValue;
}

function parseBooleanEnv(name, fallbackValue = false) {
    const rawValue = process.env[name]?.trim().toLowerCase();
    if (!rawValue) {
        return fallbackValue;
    }

    if (['1', 'true', 'yes', 'on'].includes(rawValue)) {
        return true;
    }

    if (['0', 'false', 'no', 'off'].includes(rawValue)) {
        return false;
    }

    throw new Error(`${name} environment variable must be a boolean-like value`);
}

function trimTrailingSlashes(value) {
    return value.replace(/\/+$/, '');
}

const jwtSecret = process.env.JWT_SECRET?.trim();
const allowedOriginsEnv = process.env.ALLOWED_ORIGINS?.trim();
const allowedOrigins = (allowedOriginsEnv
    ? allowedOriginsEnv.split(',').map(origin => origin.trim()).filter(Boolean)
    : isProduction
        ? []
        : DEFAULT_DEV_ALLOWED_ORIGINS
);
const portValue = process.env.PORT?.trim();
const parsedPort = portValue ? Number(portValue) : DEFAULT_PORT;
const jsonBodyLimit = process.env.JSON_BODY_LIMIT?.trim() || DEFAULT_JSON_BODY_LIMIT;
const rateLimitWindowMs = parsePositiveIntegerEnv('RATE_LIMIT_WINDOW_MS', DEFAULT_RATE_LIMIT_WINDOW_MS);
const authRateLimitMax = parsePositiveIntegerEnv('AUTH_RATE_LIMIT_MAX', DEFAULT_AUTH_RATE_LIMIT_MAX);
const passwordRateLimitMax = parsePositiveIntegerEnv('PASSWORD_RATE_LIMIT_MAX', DEFAULT_PASSWORD_RATE_LIMIT_MAX);
const restoreRateLimitMax = parsePositiveIntegerEnv('RESTORE_RATE_LIMIT_MAX', DEFAULT_RESTORE_RATE_LIMIT_MAX);
const emailVerificationRateLimitMax = parsePositiveIntegerEnv('EMAIL_VERIFICATION_RATE_LIMIT_MAX', DEFAULT_EMAIL_VERIFICATION_RATE_LIMIT_MAX);
const passwordRecoveryRequestRateLimitMax = parsePositiveIntegerEnv('PASSWORD_RECOVERY_REQUEST_RATE_LIMIT_MAX', DEFAULT_PASSWORD_RECOVERY_REQUEST_RATE_LIMIT_MAX);
const passwordRecoveryConfirmRateLimitMax = parsePositiveIntegerEnv('PASSWORD_RECOVERY_CONFIRM_RATE_LIMIT_MAX', DEFAULT_PASSWORD_RECOVERY_CONFIRM_RATE_LIMIT_MAX);
const accessTokenTtl = process.env.ACCESS_TOKEN_TTL?.trim() || DEFAULT_ACCESS_TOKEN_TTL;
const refreshTokenTtlDays = parsePositiveIntegerEnv('REFRESH_TOKEN_TTL_DAYS', DEFAULT_REFRESH_TOKEN_TTL_DAYS);
const refreshTokenTtlMs = refreshTokenTtlDays * 24 * 60 * 60 * 1000;
const emailVerificationTokenTtlHours = parsePositiveIntegerEnv('EMAIL_VERIFICATION_TOKEN_TTL_HOURS', DEFAULT_EMAIL_VERIFICATION_TOKEN_TTL_HOURS);
const passwordResetTokenTtlHours = parsePositiveIntegerEnv('PASSWORD_RESET_TOKEN_TTL_HOURS', DEFAULT_PASSWORD_RESET_TOKEN_TTL_HOURS);
const socialAuthExchangeCodeTtlMinutes = parsePositiveIntegerEnv('SOCIAL_AUTH_EXCHANGE_CODE_TTL_MINUTES', DEFAULT_SOCIAL_AUTH_EXCHANGE_CODE_TTL_MINUTES);
const nativeSocialAuthPollIntervalMs = parsePositiveIntegerEnv('NATIVE_SOCIAL_AUTH_POLL_INTERVAL_MS', DEFAULT_NATIVE_SOCIAL_AUTH_POLL_INTERVAL_MS);
const authDebugTokenPreviewEnabled = !isProduction && process.env.AUTH_DEBUG_TOKEN_PREVIEW !== '0';
const publicApiBaseUrl = process.env.PUBLIC_API_BASE_URL?.trim()
    ? trimTrailingSlashes(process.env.PUBLIC_API_BASE_URL.trim())
    : '';
const socialProviderConfigs = [
    {
        id: 'google',
        displayName: 'Google',
        enabled: parseBooleanEnv('GOOGLE_OAUTH_ENABLED', false),
        clientId: process.env.GOOGLE_OAUTH_CLIENT_ID?.trim() || '',
        clientSecret: process.env.GOOGLE_OAUTH_CLIENT_SECRET?.trim() || '',
    },
    {
        id: 'yandex',
        displayName: 'Яндекс',
        enabled: parseBooleanEnv('YANDEX_OAUTH_ENABLED', false),
        clientId: process.env.YANDEX_OAUTH_CLIENT_ID?.trim() || '',
        clientSecret: process.env.YANDEX_OAUTH_CLIENT_SECRET?.trim() || '',
    },
    {
        id: 'mailru',
        displayName: 'Mail.ru',
        enabled: parseBooleanEnv('MAILRU_OAUTH_ENABLED', false),
        clientId: process.env.MAILRU_OAUTH_CLIENT_ID?.trim() || '',
        clientSecret: process.env.MAILRU_OAUTH_CLIENT_SECRET?.trim() || '',
    },
];
const socialProviderConfigMap = new Map(socialProviderConfigs.map(provider => [provider.id, provider]));

if (!jwtSecret) {
    throw new Error('JWT_SECRET environment variable is required');
}

if (!Number.isInteger(parsedPort) || parsedPort <= 0 || parsedPort > 65535) {
    throw new Error('PORT environment variable must be a valid port number');
}

if (isProduction && allowedOrigins.length === 0) {
    throw new Error('ALLOWED_ORIGINS environment variable is required in production');
}

const app = express();
const PORT = parsedPort;
const JWT_SECRET = jwtSecret;
const dbPath = process.env.DB_PATH?.trim()
    ? path.resolve(process.env.DB_PATH.trim())
    : path.resolve(__dirname, 'database.sqlite');
const db = new Database(dbPath);
const requiredHealthTables = [
    'users',
    'categories',
    'months',
    'incomes',
    'expenses',
    'budgets',
    'savings_goals',
    'savings_transactions',
    'income_sources',
    'user_backups',
    'auth_refresh_sessions',
    'auth_email_verification_tokens',
    'auth_password_reset_tokens',
    'auth_identities',
    'auth_login_exchange_codes',
    'auth_social_login_attempts',
    'idempotency_keys',
    'deleted_records',
];

function nowIso() {
    return new Date().toISOString();
}

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
    addColumnIfMissing('auth_social_login_attempts', 'exchange_code', 'TEXT');
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

function getRowById(tableName, id) {
    return db.prepare(`SELECT * FROM ${tableName} WHERE id = ?`).get(id);
}

function recordDeletedRecord(userId, entityType, entityId, deletedAt = nowIso()) {
    db.prepare(`
        INSERT INTO deleted_records (user_id, entity_type, entity_id, deleted_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(user_id, entity_type, entity_id)
        DO UPDATE SET deleted_at = excluded.deleted_at
    `).run(userId, entityType, entityId, deletedAt);
}

function clearDeletedRecord(userId, entityType, entityId) {
    db.prepare('DELETE FROM deleted_records WHERE user_id = ? AND entity_type = ? AND entity_id = ?').run(userId, entityType, entityId);
}

function getOperationId(req) {
    const operationId = req.get('X-Operation-Id') || req.get('X-Idempotency-Key');
    if (typeof operationId !== 'string') {
        return null;
    }
    const normalizedOperationId = operationId.trim();
    return normalizedOperationId.length > 0 ? normalizedOperationId : null;
}

function buildIdempotencyRequestSignature(req) {
    return JSON.stringify({
        method: req.method,
        path: req.originalUrl.split('?')[0],
        body: req.body ?? null,
    });
}

function sendJsonResult(res, result) {
    return res.status(result.statusCode).json(result.body);
}

function executeIdempotent(req, res, execute) {
    const operationId = getOperationId(req);
    if (!req.user?.id || !operationId) {
        return sendJsonResult(res, execute());
    }

    const requestSignature = buildIdempotencyRequestSignature(req);
    const existing = db.prepare('SELECT request_signature, response_status, response_body FROM idempotency_keys WHERE user_id = ? AND operation_id = ?').get(req.user.id, operationId);

    if (existing) {
        if (existing.request_signature !== requestSignature) {
            return res.status(409).json({ error: 'Operation already used for different request' });
        }

        return res.status(existing.response_status).json(JSON.parse(existing.response_body));
    }

    const result = execute();

    if (result.statusCode >= 200 && result.statusCode < 300) {
        db.prepare(`
            INSERT INTO idempotency_keys (user_id, operation_id, request_signature, response_status, response_body, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        `).run(
            req.user.id,
            operationId,
            requestSignature,
            result.statusCode,
            JSON.stringify(result.body),
            nowIso(),
        );
    }

    return sendJsonResult(res, result);
}

ensureSchemaUpToDate();

app.disable('x-powered-by');
app.use(helmet({
    contentSecurityPolicy: {
        directives: {
            upgradeInsecureRequests: null,
        },
    },
    crossOriginResourcePolicy: false,
}));
app.use(cors({
    origin(origin, callback) {
        if (!origin || allowedOrigins.includes(origin)) {
            callback(null, true);
            return;
        }
        callback(new Error('Origin not allowed by CORS'));
    },
    credentials: true,
}));
app.use(express.json({ limit: jsonBodyLimit }));
app.use((req, res, next) => {
    req.requestId = crypto.randomUUID();
    const startedAt = Date.now();

    res.on('finish', () => {
        if (!req.path.startsWith('/api/')) {
            return;
        }

        logger.info('http_request', {
            requestId: req.requestId,
            method: req.method,
            path: req.originalUrl,
            statusCode: res.statusCode,
            durationMs: Date.now() - startedAt,
            ip: req.ip,
            userId: req.user?.id ?? null,
        });
    });

    next();
});

const MAX_BACKUPS = 5;

// --- Helper Functions ---
function getOrCreateMonth(userId, year, month) {
    const row = db.prepare('SELECT id FROM months WHERE user_id = ? AND year = ? AND month = ?').get(userId, year, month);
    if (row) return row.id;
    const timestamp = nowIso();
    const info = db.prepare('INSERT INTO months (user_id, year, month, created_at, updated_at) VALUES (?, ?, ?, ?, ?)').run(userId, year, month, timestamp, timestamp);
    return info.lastInsertRowid;
}

// Helper to check if month belongs to user
function checkMonthAccess(userId, monthId) {
    const month = db.prepare('SELECT id FROM months WHERE id = ? AND user_id = ?').get(monthId, userId);
    return !!month;
}

function createBackup(userId) {
    try {
        // Collect all data
        const categories = db.prepare('SELECT * FROM categories WHERE user_id = ?').all(userId);
        const income_sources = db.prepare('SELECT * FROM income_sources WHERE user_id = ?').all(userId);
        const savings_goals = db.prepare('SELECT * FROM savings_goals WHERE user_id = ?').all(userId);
        
        // Months and nested data
        const months = db.prepare('SELECT * FROM months WHERE user_id = ?').all(userId);
        const monthIds = months.map(m => m.id);
        
        let incomes = [];
        let expenses = [];
        let budgets = [];
        
        if (monthIds.length > 0) {
            const placeholders = monthIds.map(() => '?').join(',');
            incomes = db.prepare(`SELECT * FROM incomes WHERE month_id IN (${placeholders})`).all(monthIds);
            expenses = db.prepare(`SELECT * FROM expenses WHERE month_id IN (${placeholders})`).all(monthIds);
            budgets = db.prepare(`SELECT * FROM budgets WHERE month_id IN (${placeholders})`).all(monthIds);
        }

        // Savings transactions link to goal_id primarily.
        let savings_transactions = [];
        const goalIds = savings_goals.map(g => g.id);
        if (goalIds.length > 0) {
             const placeholders = goalIds.map(() => '?').join(',');
             savings_transactions = db.prepare(`SELECT * FROM savings_transactions WHERE goal_id IN (${placeholders})`).all(goalIds);
        }

        const backupData = JSON.stringify({
            categories,
            income_sources,
            savings_goals,
            months,
            incomes,
            expenses,
            budgets,
            savings_transactions
        });

        db.prepare('INSERT INTO user_backups (user_id, data) VALUES (?, ?)').run(userId, backupData);

        // Cleanup old backups
        const backups = db.prepare('SELECT id FROM user_backups WHERE user_id = ? ORDER BY created_at DESC, id DESC').all(userId);
        if (backups.length > MAX_BACKUPS) {
            const toDelete = backups.slice(MAX_BACKUPS).map(b => b.id);
            const placeholders = toDelete.map(() => '?').join(',');
            db.prepare(`DELETE FROM user_backups WHERE id IN (${placeholders})`).run(toDelete);
        }
        logger.info('backup_created', { userId });
    } catch (e) {
        logger.error('backup_failed', { userId, error: e });
    }
}

function parseBackupDataSafely(rawValue) {
    try {
        return JSON.parse(rawValue);
    } catch (_error) {
        return null;
    }
}

function buildBackupSummary(backupData) {
    const safeArrayLength = (value) => Array.isArray(value) ? value.length : 0;

    return {
        categories: safeArrayLength(backupData?.categories),
        incomeSources: safeArrayLength(backupData?.income_sources),
        savingsGoals: safeArrayLength(backupData?.savings_goals),
        months: safeArrayLength(backupData?.months),
        incomes: safeArrayLength(backupData?.incomes),
        expenses: safeArrayLength(backupData?.expenses),
        budgets: safeArrayLength(backupData?.budgets),
        savingsTransactions: safeArrayLength(backupData?.savings_transactions),
    };
}

function buildBackupEntryPayload(row) {
    const backupData = parseBackupDataSafely(row.data);

    return {
        id: row.id,
        createdAt: row.created_at,
        sizeBytes: Buffer.byteLength(row.data, 'utf8'),
        summary: buildBackupSummary(backupData),
    };
}

function listUserBackupEntries(userId) {
    const rows = db.prepare(`
        SELECT id, data, created_at
        FROM user_backups
        WHERE user_id = ?
        ORDER BY created_at DESC, id DESC
    `).all(userId);

    return rows.map(buildBackupEntryPayload);
}

function getUserBackupRecord(userId, backupId) {
    return db.prepare(`
        SELECT id, data, created_at
        FROM user_backups
        WHERE user_id = ? AND id = ?
        LIMIT 1
    `).get(userId, backupId);
}

function restoreBackupSnapshot(userId, backupData) {
    const restoreTransact = db.transaction(() => {
        db.prepare('DELETE FROM savings_transactions WHERE goal_id IN (SELECT id FROM savings_goals WHERE user_id = ?)').run(userId);
        db.prepare('DELETE FROM budgets WHERE category_id IN (SELECT id FROM categories WHERE user_id = ?)').run(userId);
        db.prepare('DELETE FROM expenses WHERE category_id IN (SELECT id FROM categories WHERE user_id = ?)').run(userId);
        db.prepare('DELETE FROM incomes WHERE month_id IN (SELECT id FROM months WHERE user_id = ?)').run(userId);
        db.prepare('DELETE FROM months WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM categories WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM income_sources WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM savings_goals WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM deleted_records WHERE user_id = ?').run(userId);

        const insertCat = db.prepare('INSERT INTO categories (id, user_id, name, sort_order, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)');
        (backupData.categories || []).forEach(row => insertCat.run(row.id, userId, row.name, row.sort_order, row.is_active, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));

        const insertSource = db.prepare('INSERT INTO income_sources (id, user_id, name, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)');
        (backupData.income_sources || []).forEach(row => insertSource.run(row.id, userId, row.name, row.is_active, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));

        const insertGoal = db.prepare('INSERT INTO savings_goals (id, user_id, name, target_amount, current_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)');
        (backupData.savings_goals || []).forEach(row => insertGoal.run(row.id, userId, row.name, row.target_amount, row.current_amount, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));

        const insertMonth = db.prepare('INSERT INTO months (id, user_id, year, month, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)');
        (backupData.months || []).forEach(row => insertMonth.run(row.id, userId, row.year, row.month, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));

        const insertIncome = db.prepare('INSERT INTO incomes (id, month_id, source, amount, date, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)');
        (backupData.incomes || []).forEach(row => insertIncome.run(row.id, row.month_id, row.source, row.amount, row.date, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));

        const insertExpense = db.prepare('INSERT INTO expenses (id, month_id, category_id, amount, date, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
        (backupData.expenses || []).forEach(row => insertExpense.run(row.id, row.month_id, row.category_id, row.amount, row.date, row.comment, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));

        const insertBudget = db.prepare('INSERT INTO budgets (id, month_id, category_id, limit_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)');
        (backupData.budgets || []).forEach(row => insertBudget.run(row.id, row.month_id, row.category_id, row.limit_amount, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));

        const insertTrans = db.prepare('INSERT INTO savings_transactions (id, goal_id, amount, date, month_id, is_adjustment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
        (backupData.savings_transactions || []).forEach(row => insertTrans.run(row.id, row.goal_id, row.amount, row.date, row.month_id, row.is_adjustment || 0, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));
    });

    restoreTransact();
}

function hashRefreshToken(token) {
    return crypto.createHash('sha256').update(token).digest('hex');
}

function createAccessToken(user) {
    return jwt.sign(
        { id: user.id, username: user.username, type: 'access' },
        JWT_SECRET,
        { expiresIn: accessTokenTtl }
    );
}

function generateRefreshToken() {
    return crypto.randomBytes(48).toString('hex');
}

function getRefreshSessionExpiryIso() {
    return new Date(Date.now() + refreshTokenTtlMs).toISOString();
}

function cleanupExpiredRefreshSessions() {
    db.prepare(`
        DELETE FROM auth_refresh_sessions
        WHERE revoked_at IS NOT NULL OR expires_at <= ?
    `).run(nowIso());
}

function issueRefreshSession(userId) {
    cleanupExpiredRefreshSessions();

    const refreshToken = generateRefreshToken();
    const tokenHash = hashRefreshToken(refreshToken);
    const timestamp = nowIso();
    const expiresAt = getRefreshSessionExpiryIso();

    db.prepare(`
        INSERT INTO auth_refresh_sessions (user_id, token_hash, expires_at, created_at, last_used_at, revoked_at)
        VALUES (?, ?, ?, ?, ?, NULL)
    `).run(userId, tokenHash, expiresAt, timestamp, timestamp);

    return {
        refreshToken,
        expiresAt,
    };
}

function revokeRefreshSessionByToken(refreshToken) {
    if (!refreshToken) {
        return;
    }

    db.prepare(`
        UPDATE auth_refresh_sessions
        SET revoked_at = COALESCE(revoked_at, ?)
        WHERE token_hash = ? AND revoked_at IS NULL
    `).run(nowIso(), hashRefreshToken(refreshToken));
}

function revokeRefreshSessionById(sessionId) {
    db.prepare(`
        UPDATE auth_refresh_sessions
        SET revoked_at = COALESCE(revoked_at, ?)
        WHERE id = ? AND revoked_at IS NULL
    `).run(nowIso(), sessionId);
}

function touchRefreshSession(sessionId) {
    db.prepare(`
        UPDATE auth_refresh_sessions
        SET last_used_at = ?
        WHERE id = ? AND revoked_at IS NULL
    `).run(nowIso(), sessionId);
}

function parseCookies(cookieHeader) {
    if (!cookieHeader || typeof cookieHeader !== 'string') {
        return {};
    }

    return cookieHeader
        .split(';')
        .map(part => part.trim())
        .filter(Boolean)
        .reduce((acc, part) => {
            const separatorIndex = part.indexOf('=');
            if (separatorIndex === -1) {
                return acc;
            }

            const key = part.slice(0, separatorIndex).trim();
            const value = part.slice(separatorIndex + 1).trim();
            if (!key) {
                return acc;
            }

            acc[key] = decodeURIComponent(value);
            return acc;
        }, {});
}

function getRefreshTokenFromRequest(req) {
    const bodyRefreshToken = typeof req.body?.refreshToken === 'string' ? req.body.refreshToken.trim() : '';
    if (bodyRefreshToken) {
        return bodyRefreshToken;
    }

    const cookies = parseCookies(req.headers.cookie);
    const cookieRefreshToken = cookies[REFRESH_TOKEN_COOKIE_NAME];
    return typeof cookieRefreshToken === 'string' && cookieRefreshToken.trim().length > 0 ? cookieRefreshToken.trim() : null;
}

function shouldReturnRefreshTokenInBody(req) {
    return req.get('X-Refresh-Transport') === 'body';
}

function setRefreshTokenCookie(res, refreshToken) {
    res.cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken, {
        httpOnly: true,
        secure: isProduction,
        sameSite: 'lax',
        path: '/api/auth',
        maxAge: refreshTokenTtlMs,
    });
}

function clearRefreshTokenCookie(res) {
    res.clearCookie(REFRESH_TOKEN_COOKIE_NAME, {
        httpOnly: true,
        secure: isProduction,
        sameSite: 'lax',
        path: '/api/auth',
    });
}

function setOAuthStateCookie(res, stateValue) {
    res.cookie(OAUTH_STATE_COOKIE_NAME, stateValue, {
        httpOnly: true,
        secure: isProduction,
        sameSite: 'lax',
        path: '/api/auth/oauth',
        maxAge: 15 * 60 * 1000,
    });
}

function clearOAuthStateCookie(res) {
    res.clearCookie(OAUTH_STATE_COOKIE_NAME, {
        httpOnly: true,
        secure: isProduction,
        sameSite: 'lax',
        path: '/api/auth/oauth',
    });
}

function getOAuthStateCookie(req) {
    const cookies = parseCookies(req.headers.cookie);
    return typeof cookies[OAUTH_STATE_COOKIE_NAME] === 'string' && cookies[OAUTH_STATE_COOKIE_NAME].trim().length > 0
        ? cookies[OAUTH_STATE_COOKIE_NAME].trim()
        : null;
}

function getAppBaseUrl() {
    return getMailConfig().appBaseUrl || '';
}

function getPublicApiBaseUrl(req) {
    if (publicApiBaseUrl) {
        return publicApiBaseUrl;
    }

    const forwardedProto = normalizeInputString(req.get('x-forwarded-proto'));
    const forwardedHost = normalizeInputString(req.get('x-forwarded-host'));
    const protocol = forwardedProto || req.protocol || 'http';
    const host = forwardedHost || req.get('host');
    return host ? `${protocol}://${host}` : '';
}

function buildWebSocialCallbackUrl(provider) {
    const appBaseUrl = getAppBaseUrl();
    if (!appBaseUrl) {
        return '';
    }
    return new URL(`/auth/${provider}/callback`, `${appBaseUrl}/`).toString();
}

function buildApiOAuthCallbackUrl(req, provider) {
    const apiBaseUrl = getPublicApiBaseUrl(req);
    if (!apiBaseUrl) {
        return '';
    }
    return new URL(`/api/auth/oauth/${provider}/callback`, `${apiBaseUrl}/`).toString();
}

function encodeOAuthStatePayload(payload) {
    return Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
}

function decodeOAuthStatePayload(value) {
    try {
        const decoded = Buffer.from(value, 'base64url').toString('utf8');
        const payload = JSON.parse(decoded);
        return payload && typeof payload === 'object' ? payload : null;
    } catch {
        return null;
    }
}

function createOAuthStatePayload({ provider, clientType, redirectUri, attemptToken = null }) {
    return {
        nonce: generateOpaqueToken(),
        provider,
        clientType,
        redirectUri,
        attemptToken,
        createdAt: nowIso(),
    };
}

function readOAuthStatePayloadFromRequest(req) {
    const stateFromQuery = normalizeInputString(req.query?.state);
    const stateFromCookie = getOAuthStateCookie(req);

    if (!stateFromQuery || !stateFromCookie || stateFromQuery !== stateFromCookie) {
        return null;
    }

    const payload = decodeOAuthStatePayload(stateFromQuery);
    if (!payload) {
        return null;
    }

    return payload;
}

function buildFrontendAuthRedirectUrl(provider, query) {
    const callbackUrl = buildWebSocialCallbackUrl(provider);
    if (!callbackUrl) {
        return '';
    }

    const url = new URL(callbackUrl);
    Object.entries(query).forEach(([key, rawValue]) => {
        if (rawValue !== undefined && rawValue !== null && rawValue !== '') {
            url.searchParams.set(key, String(rawValue));
        }
    });
    return url.toString();
}

function buildNativeSocialAuthCompletionHtml({ success, provider, errorCode = null }) {
    const providerName = provider === 'yandex' ? 'Яндекс' : provider === 'google' ? 'Google' : 'соцсеть';
    const title = success ? 'Вход завершён' : 'Вход не завершён';
    const description = success
        ? `Авторизация через ${providerName} завершена. Вернитесь в приложение FinKeeper.`
        : `Авторизация через ${providerName} не завершилась. Вернитесь в приложение FinKeeper и попробуйте ещё раз.`;
    const errorHint = errorCode ? `<p style="margin-top:12px;color:#64748b;font-size:13px;">Код ошибки: ${errorCode}</p>` : '';
    return `<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>${title}</title>
</head>
<body style="margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f8fafc;color:#0f172a;">
  <main style="min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;">
    <section style="max-width:420px;width:100%;background:#ffffff;border:1px solid #e2e8f0;border-radius:20px;padding:28px;box-shadow:0 12px 40px rgba(15,23,42,.08);text-align:center;">
      <div style="font-size:34px;line-height:1;margin-bottom:16px;">${success ? '✅' : '⚠️'}</div>
      <h1 style="margin:0 0 12px;font-size:24px;font-weight:700;">${title}</h1>
      <p style="margin:0;color:#475569;font-size:15px;line-height:1.6;">${description}</p>
      ${errorHint}
    </section>
  </main>
</body>
</html>`;
}

function sendNativeSocialAuthCompletionPage(res, { success, provider, errorCode = null, statusCode = 200 }) {
    return res
        .status(statusCode)
        .type('html')
        .send(buildNativeSocialAuthCompletionHtml({ success, provider, errorCode }));
}

function normalizeEmail(value) {
    return normalizeInputString(value).toLowerCase();
}

function isValidEmail(value) {
    return EMAIL_REGEX.test(value);
}

function getUserRecoverabilityStatus(user) {
    const recoveryInfo = getUserRecoveryInfo(user);
    return recoveryInfo.email && recoveryInfo.confirmed ? 'protected' : 'unprotected';
}

function listAuthProvidersForUser(userId) {
    return db.prepare(`
        SELECT provider, provider_email, provider_email_verified, last_login_at
        FROM auth_identities
        WHERE user_id = ?
        ORDER BY last_login_at DESC, provider ASC
    `).all(userId).map((identity) => ({
        provider: identity.provider,
        email: normalizeInputString(identity.provider_email) || null,
        emailVerified: Boolean(identity.provider_email_verified),
    }));
}

function getUserRecoveryInfo(user) {
    const accountEmail = normalizeEmail(user?.email);
    if (accountEmail && user?.email_confirmed_at) {
        return {
            email: accountEmail,
            confirmed: true,
            source: 'account',
            provider: null,
        };
    }

    const linkedProviders = user?.id ? listAuthProvidersForUser(user.id) : [];
    const verifiedProvider = linkedProviders.find((provider) => provider.email && provider.emailVerified);
    if (verifiedProvider) {
        return {
            email: verifiedProvider.email,
            confirmed: true,
            source: 'social',
            provider: verifiedProvider.provider,
        };
    }

    return {
        email: null,
        confirmed: false,
        source: null,
        provider: null,
    };
}

function buildUserPayload(user) {
    const email = typeof user?.email === 'string' && user.email.trim().length > 0 ? user.email.trim() : null;
    const emailConfirmed = Boolean(email && user?.email_confirmed_at);
    const linkedAuthProviders = listAuthProvidersForUser(user.id);
    const recoveryInfo = getUserRecoveryInfo({
        ...user,
        linkedAuthProviders,
    });
    const recoverabilityStatus = recoveryInfo.email && recoveryInfo.confirmed ? 'protected' : 'unprotected';

    return {
        id: user.id,
        username: user.username,
        email,
        emailConfirmed,
        recoverabilityStatus,
        canSelfRecover: Boolean(recoveryInfo.email && recoveryInfo.confirmed),
        recoveryEmail: recoveryInfo.email,
        recoveryEmailConfirmed: recoveryInfo.confirmed,
        recoveryEmailSource: recoveryInfo.source,
        recoveryEmailProvider: recoveryInfo.provider,
        created_at: user.created_at,
        linkedAuthProviders,
    };
}

function buildAuthResponse(req, user, accessToken, refreshToken = null) {
    const response = {
        token: accessToken,
        accessToken,
        user: buildUserPayload(user),
    };

    if (refreshToken && shouldReturnRefreshTokenInBody(req)) {
        response.refreshToken = refreshToken;
    }

    return response;
}

function seedUserDefaults(userId, timestamp) {
    const insertCat = db.prepare('INSERT INTO categories (user_id, name, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?)');
    initialCategories.forEach((cat, index) => insertCat.run(userId, cat, index + 1, timestamp, timestamp));

    const insertSource = db.prepare('INSERT INTO income_sources (user_id, name, created_at, updated_at) VALUES (?, ?, ?, ?)');
    initialIncomeSources.forEach(source => insertSource.run(userId, source, timestamp, timestamp));

    const insertGoal = db.prepare('INSERT INTO savings_goals (user_id, name, created_at, updated_at) VALUES (?, ?, ?, ?)');
    initialSavings.forEach(goal => insertGoal.run(userId, goal, timestamp, timestamp));
}

function createUserWithDefaults({
    username,
    passwordHash,
    authPasswordEnabled = true,
    email = null,
    emailConfirmedAt = null,
}) {
    const createUserTransaction = db.transaction(() => {
        const timestamp = nowIso();
        const info = db.prepare(`
            INSERT INTO users (username, email, password_hash, auth_password_enabled, email_confirmed_at)
            VALUES (?, ?, ?, ?, ?)
        `).run(username, email, passwordHash, authPasswordEnabled ? 1 : 0, emailConfirmedAt);
        const userId = info.lastInsertRowid;

        seedUserDefaults(userId, timestamp);

        return db.prepare(`
            SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled
            FROM users
            WHERE id = ?
        `).get(userId);
    });

    return createUserTransaction();
}

function issueAuthSession(req, res, user) {
    const accessToken = createAccessToken(user);
    const { refreshToken } = issueRefreshSession(user.id);
    setRefreshTokenCookie(res, refreshToken);
    return buildAuthResponse(req, user, accessToken, refreshToken);
}

function getValidRefreshSession(refreshToken) {
    if (!refreshToken) {
        return null;
    }

    cleanupExpiredRefreshSessions();

    return db.prepare(`
        SELECT id, user_id, expires_at, revoked_at
        FROM auth_refresh_sessions
        WHERE token_hash = ?
    `).get(hashRefreshToken(refreshToken));
}

function hashOpaqueToken(token) {
    return crypto.createHash('sha256').update(token).digest('hex');
}

function generateOpaqueToken() {
    return crypto.randomBytes(32).toString('hex');
}

function getFutureIsoFromHours(hours) {
    return new Date(Date.now() + (hours * 60 * 60 * 1000)).toISOString();
}

function getFutureIsoFromMinutes(minutes) {
    return new Date(Date.now() + (minutes * 60 * 1000)).toISOString();
}

function isPlaceholderSocialConfigValue(value) {
    const normalizedValue = value.trim().toLowerCase();
    if (!normalizedValue) {
        return true;
    }

    const placeholderMarkers = [
        'your-',
        'placeholder',
        'example',
        'replace-me',
        'replace_with',
        'changeme',
        'dummy',
        'sample',
    ];

    return placeholderMarkers.some(marker => normalizedValue.includes(marker));
}

function hasUsableSocialProviderCredentials(providerConfig) {
    if (!providerConfig?.clientId || !providerConfig?.clientSecret) {
        return false;
    }

    return !isPlaceholderSocialConfigValue(providerConfig.clientId) && !isPlaceholderSocialConfigValue(providerConfig.clientSecret);
}

function isValidPublicOAuthCallbackBaseUrl(req) {
    const apiBaseUrl = getPublicApiBaseUrl(req);
    if (!apiBaseUrl) {
        return false;
    }

    try {
        const parsedUrl = new URL(apiBaseUrl);
        const hostname = normalizeInputString(parsedUrl.hostname).toLowerCase();
        const isLocalhost = hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1';
        return parsedUrl.protocol === 'https:' || isLocalhost;
    } catch {
        return false;
    }
}

function getPublicSocialProviderPayload(providerConfig, req = null) {
    return {
        id: providerConfig.id,
        displayName: providerConfig.displayName,
        enabled: isSocialProviderAvailable(providerConfig, req),
    };
}

function getEnabledSocialProviders(req = null) {
    return socialProviderConfigs.map(providerConfig => getPublicSocialProviderPayload(providerConfig, req));
}

function getSocialProviderConfig(providerId) {
    return socialProviderConfigMap.get(providerId) || null;
}

function isSocialProviderAvailable(providerConfig, req = null) {
    return Boolean(
        providerConfig?.enabled
        && hasUsableSocialProviderCredentials(providerConfig)
        && isValidPublicOAuthCallbackBaseUrl(req)
    );
}

function cleanupExpiredSocialAuthExchangeCodes() {
    db.prepare(`
        DELETE FROM auth_login_exchange_codes
        WHERE used_at IS NOT NULL OR expires_at <= ?
    `).run(nowIso());
}

function cleanupExpiredNativeSocialLoginAttempts() {
    db.prepare(`
        DELETE FROM auth_social_login_attempts
        WHERE expires_at <= ? OR consumed_at IS NOT NULL
    `).run(nowIso());
}

function issueSocialAuthExchangeCode({ userId, provider, clientType = null, redirectUri = null }) {
    cleanupExpiredSocialAuthExchangeCodes();

    const code = generateOpaqueToken();
    const codeHash = hashOpaqueToken(code);
    const timestamp = nowIso();
    const expiresAt = getFutureIsoFromMinutes(socialAuthExchangeCodeTtlMinutes);

    db.prepare(`
        INSERT INTO auth_login_exchange_codes (user_id, provider, code_hash, client_type, redirect_uri, expires_at, created_at, used_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
    `).run(userId, provider, codeHash, clientType, redirectUri, expiresAt, timestamp);

    return {
        code,
        expiresAt,
    };
}

function issueNativeSocialLoginAttempt({ provider, clientType, redirectUri = null }) {
    cleanupExpiredNativeSocialLoginAttempts();

    const attemptToken = generateOpaqueToken();
    const attemptTokenHash = hashOpaqueToken(attemptToken);
    const timestamp = nowIso();
    const expiresAt = getFutureIsoFromMinutes(socialAuthExchangeCodeTtlMinutes);

    db.prepare(`
        INSERT INTO auth_social_login_attempts (
            attempt_token_hash,
            provider,
            client_type,
            redirect_uri,
            user_id,
            status,
            exchange_code,
            error_code,
            error_description,
            expires_at,
            created_at,
            completed_at,
            consumed_at
        )
        VALUES (?, ?, ?, ?, NULL, 'pending', NULL, NULL, NULL, ?, ?, NULL, NULL)
    `).run(attemptTokenHash, provider, clientType, redirectUri, expiresAt, timestamp);

    return {
        attemptToken,
        expiresAt,
    };
}

function getValidSocialAuthExchangeCode(code) {
    cleanupExpiredSocialAuthExchangeCodes();

    return db.prepare(`
        SELECT id, user_id, provider, client_type, redirect_uri, expires_at, used_at
        FROM auth_login_exchange_codes
        WHERE code_hash = ?
    `).get(hashOpaqueToken(code));
}

function getNativeSocialLoginAttempt(attemptToken) {
    cleanupExpiredNativeSocialLoginAttempts();

    return db.prepare(`
        SELECT id, provider, client_type, redirect_uri, user_id, status, exchange_code, error_code, error_description, expires_at, created_at, completed_at, consumed_at
        FROM auth_social_login_attempts
        WHERE attempt_token_hash = ?
    `).get(hashOpaqueToken(attemptToken));
}

function markSocialAuthExchangeCodeUsed(exchangeCodeId) {
    db.prepare(`
        UPDATE auth_login_exchange_codes
        SET used_at = COALESCE(used_at, ?)
        WHERE id = ?
    `).run(nowIso(), exchangeCodeId);
}

function completeNativeSocialLoginAttemptSuccess(attemptId, { userId, exchangeCode }) {
    db.prepare(`
        UPDATE auth_social_login_attempts
        SET user_id = ?, status = 'completed', exchange_code = ?, error_code = NULL, error_description = NULL, completed_at = ?, consumed_at = NULL
        WHERE id = ?
    `).run(userId, exchangeCode, nowIso(), attemptId);
}

function completeNativeSocialLoginAttemptError(attemptId, { errorCode, errorDescription = null }) {
    db.prepare(`
        UPDATE auth_social_login_attempts
        SET status = 'error', exchange_code = NULL, error_code = ?, error_description = ?, completed_at = ?, consumed_at = NULL
        WHERE id = ?
    `).run(errorCode, errorDescription, nowIso(), attemptId);
}

function markNativeSocialLoginAttemptConsumed(attemptId) {
    db.prepare(`
        UPDATE auth_social_login_attempts
        SET consumed_at = COALESCE(consumed_at, ?)
        WHERE id = ?
    `).run(nowIso(), attemptId);
}

function markNativeSocialLoginAttemptConsumedByExchangeCode(exchangeCode) {
    if (!exchangeCode) {
        return;
    }

    db.prepare(`
        UPDATE auth_social_login_attempts
        SET consumed_at = COALESCE(consumed_at, ?)
        WHERE exchange_code = ?
    `).run(nowIso(), exchangeCode);
}

function findAuthIdentity(provider, providerUserId) {
    return db.prepare(`
        SELECT id, user_id, provider, provider_user_id, provider_email, provider_email_verified
        FROM auth_identities
        WHERE provider = ? AND provider_user_id = ?
    `).get(provider, providerUserId);
}

function touchAuthIdentity(identityId, { providerEmail = null, providerEmailVerified = false } = {}) {
    db.prepare(`
        UPDATE auth_identities
        SET provider_email = ?, provider_email_verified = ?, updated_at = ?, last_login_at = ?
        WHERE id = ?
    `).run(providerEmail, providerEmailVerified ? 1 : 0, nowIso(), nowIso(), identityId);
}

function createAuthIdentity({ userId, provider, providerUserId, providerEmail = null, providerEmailVerified = false }) {
    const timestamp = nowIso();
    db.prepare(`
        INSERT INTO auth_identities (user_id, provider, provider_user_id, provider_email, provider_email_verified, created_at, updated_at, last_login_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(userId, provider, providerUserId, providerEmail, providerEmailVerified ? 1 : 0, timestamp, timestamp, timestamp);
}

function createDisabledPasswordHash() {
    return bcrypt.hashSync(crypto.randomBytes(32).toString('hex'), 8);
}

function buildUsernameCandidate(value) {
    const normalized = String(value || '')
        .toLowerCase()
        .replace(/\s+/g, '-')
        .replace(/[^\p{L}\p{N}._-]/gu, '')
        .replace(/^[._-]+|[._-]+$/g, '');

    const fallback = normalized || 'user';
    return fallback.slice(0, 48) || 'user';
}

function buildSocialDisplayUsernameCandidate(value) {
    const normalized = normalizeInputString(value)
        .replace(/\s+/g, ' ')
        .replace(/[^\p{L}\p{N} ._'’-]/gu, '')
        .trim();

    if (normalized) {
        return normalized.slice(0, 64);
    }

    const fallback = buildUsernameCandidate(value);
    return fallback.slice(0, 64) || 'user';
}

function generateUniqueUsername(baseCandidate, excludedUserId = null) {
    const base = buildUsernameCandidate(baseCandidate);
    let candidate = base;
    let counter = 0;

    while (db.prepare('SELECT id FROM users WHERE username = ? AND (? IS NULL OR id != ?)').get(candidate, excludedUserId, excludedUserId)) {
        counter += 1;
        const suffix = counter > 9
            ? `-${crypto.randomBytes(2).toString('hex')}`
            : `-${counter}`;
        candidate = `${base.slice(0, Math.max(1, 48 - suffix.length))}${suffix}`;
    }

    return candidate;
}

function generateUniqueSocialUsername(baseCandidate, excludedUserId = null) {
    const base = buildSocialDisplayUsernameCandidate(baseCandidate);
    let candidate = base;
    let counter = 1;

    while (db.prepare('SELECT id FROM users WHERE username = ? AND (? IS NULL OR id != ?)').get(candidate, excludedUserId, excludedUserId)) {
        counter += 1;
        const suffix = ` ${counter}`;
        candidate = `${base.slice(0, Math.max(1, 64 - suffix.length))}${suffix}`;
    }

    return candidate;
}

async function exchangeGoogleAuthorizationCode({ code, redirectUri, providerConfig }) {
    const response = await fetch(GOOGLE_OAUTH_TOKEN_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
            code,
            client_id: providerConfig.clientId,
            client_secret: providerConfig.clientSecret,
            redirect_uri: redirectUri,
            grant_type: 'authorization_code',
        }),
    });

    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error('Google token exchange failed');
        error.statusCode = response.status;
        error.payload = payload;
        throw error;
    }

    return payload;
}

async function fetchGoogleUserInfo(accessToken) {
    const response = await fetch(GOOGLE_OAUTH_USERINFO_URL, {
        headers: {
            Authorization: `Bearer ${accessToken}`,
        },
    });

    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error('Google userinfo fetch failed');
        error.statusCode = response.status;
        error.payload = payload;
        throw error;
    }

    return payload;
}

function isGeneratedSocialUsername(username) {
    const normalizedUsername = normalizeInputString(username).toLowerCase();
    if (!normalizedUsername) {
        return true;
    }

    return normalizedUsername === 'user' || /^user(?:-[a-z0-9]+(?:-[a-z0-9]+)*)?$/.test(normalizedUsername);
}

function shouldRefreshSocialUsername(user, profileName) {
    const currentUsername = normalizeInputString(user?.username);
    if (!currentUsername || !profileName) {
        return Boolean(profileName);
    }

    if (isGeneratedSocialUsername(currentUsername)) {
        return true;
    }

    const slugifiedProfileName = buildUsernameCandidate(profileName);
    if (currentUsername.toLowerCase() === slugifiedProfileName) {
        return true;
    }

    return false;
}

function syncExistingSocialAuthUser(userId, { profileName = '', providerEmail = '', providerEmailVerified = false } = {}) {
    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled
        FROM users
        WHERE id = ?
    `).get(userId);
    if (!user) {
        return null;
    }

    let nextUsername = user.username;
    let nextEmail = normalizeInputString(user.email) || null;
    let nextEmailConfirmedAt = user.email_confirmed_at;
    let hasChanges = false;

    if (profileName && shouldRefreshSocialUsername(user, profileName)) {
        const generatedUsername = generateUniqueSocialUsername(profileName, user.id);
        if (generatedUsername && generatedUsername !== user.username) {
            nextUsername = generatedUsername;
            hasChanges = true;
        }
    }

    if (!nextEmail && providerEmail && providerEmailVerified) {
        const conflictingUser = db.prepare('SELECT id FROM users WHERE email = ? AND id != ?').get(providerEmail, user.id);
        if (!conflictingUser) {
            nextEmail = providerEmail;
            nextEmailConfirmedAt = nowIso();
            hasChanges = true;
        }
    }

    if (hasChanges) {
        db.prepare(`
            UPDATE users
            SET username = ?, email = ?, email_confirmed_at = ?
            WHERE id = ?
        `).run(nextUsername, nextEmail, nextEmailConfirmedAt, user.id);
    }

    return db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled
        FROM users
        WHERE id = ?
    `).get(user.id);
}

function resolveGoogleEmailForNewUser(googleProfile) {
    const email = normalizeEmail(googleProfile?.email);
    if (!email || !googleProfile?.email_verified) {
        return {
            email: null,
            emailConfirmedAt: null,
        };
    }

    const conflictingUser = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
    if (conflictingUser) {
        return {
            email: null,
            emailConfirmedAt: null,
        };
    }

    return {
        email,
        emailConfirmedAt: nowIso(),
    };
}

function resolveOrCreateGoogleAuthUser(googleProfile) {
    const provider = 'google';
    const providerUserId = normalizeInputString(googleProfile?.sub);
    if (!providerUserId) {
        const error = new Error('Google profile is missing subject identifier');
        error.code = 'google_profile_invalid';
        throw error;
    }

    const providerEmail = normalizeEmail(googleProfile?.email);
    const providerEmailVerified = Boolean(googleProfile?.email_verified);
    const profileName = normalizeInputString(googleProfile?.name)
        || normalizeInputString(providerEmail?.split('@')[0])
        || provider;
    const identity = findAuthIdentity(provider, providerUserId);

    if (identity) {
        touchAuthIdentity(identity.id, {
            providerEmail,
            providerEmailVerified,
        });

        return syncExistingSocialAuthUser(identity.user_id, {
            profileName,
            providerEmail,
            providerEmailVerified,
        });
    }

    const username = generateUniqueSocialUsername(profileName);
    const emailForNewUser = resolveGoogleEmailForNewUser(googleProfile);
    const user = createUserWithDefaults({
        username,
        passwordHash: createDisabledPasswordHash(),
        authPasswordEnabled: false,
        email: emailForNewUser.email,
        emailConfirmedAt: emailForNewUser.emailConfirmedAt,
    });

    createAuthIdentity({
        userId: user.id,
        provider,
        providerUserId,
        providerEmail,
        providerEmailVerified,
    });

    return user;
}

async function exchangeYandexAuthorizationCode({ code, providerConfig }) {
    const response = await fetch(YANDEX_OAUTH_TOKEN_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
            grant_type: 'authorization_code',
            code,
            client_id: providerConfig.clientId,
            client_secret: providerConfig.clientSecret,
        }),
    });

    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error('Yandex token exchange failed');
        error.statusCode = response.status;
        error.payload = payload;
        throw error;
    }

    return payload;
}

async function fetchYandexUserInfo(accessToken) {
    const response = await fetch(`${YANDEX_OAUTH_USERINFO_URL}?format=json`, {
        headers: {
            Authorization: `OAuth ${accessToken}`,
        },
    });

    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error('Yandex userinfo fetch failed');
        error.statusCode = response.status;
        error.payload = payload;
        throw error;
    }

    return payload;
}

function resolveYandexEmailForNewUser(yandexProfile) {
    const email = normalizeEmail(yandexProfile?.default_email);
    if (!email) {
        return {
            email: null,
            emailConfirmedAt: null,
        };
    }

    const conflictingUser = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
    if (conflictingUser) {
        return {
            email: null,
            emailConfirmedAt: null,
        };
    }

    return {
        email,
        emailConfirmedAt: nowIso(),
    };
}

function resolveOrCreateYandexAuthUser(yandexProfile) {
    const provider = 'yandex';
    const providerUserId = normalizeInputString(yandexProfile?.id);
    if (!providerUserId) {
        const error = new Error('Yandex profile is missing user identifier');
        error.code = 'yandex_profile_invalid';
        throw error;
    }

    const providerEmail = normalizeEmail(yandexProfile?.default_email);
    const providerEmailVerified = Boolean(providerEmail);
    const profileName = normalizeInputString(yandexProfile?.real_name)
        || normalizeInputString(yandexProfile?.display_name)
        || normalizeInputString(yandexProfile?.login)
        || normalizeInputString(providerEmail?.split('@')[0])
        || provider;
    const identity = findAuthIdentity(provider, providerUserId);

    if (identity) {
        touchAuthIdentity(identity.id, {
            providerEmail,
            providerEmailVerified,
        });

        return syncExistingSocialAuthUser(identity.user_id, {
            profileName,
            providerEmail,
            providerEmailVerified,
        });
    }

    const username = generateUniqueSocialUsername(profileName);
    const emailForNewUser = resolveYandexEmailForNewUser(yandexProfile);
    const user = createUserWithDefaults({
        username,
        passwordHash: createDisabledPasswordHash(),
        authPasswordEnabled: false,
        email: emailForNewUser.email,
        emailConfirmedAt: emailForNewUser.emailConfirmedAt,
    });

    createAuthIdentity({
        userId: user.id,
        provider,
        providerUserId,
        providerEmail,
        providerEmailVerified,
    });

    return user;
}

function buildYandexAuthorizeUrl(req, providerConfig, stateValue) {
    const redirectUri = buildApiOAuthCallbackUrl(req, providerConfig.id);
    if (!redirectUri) {
        return '';
    }

    const url = new URL(YANDEX_OAUTH_AUTHORIZE_URL);
    url.searchParams.set('client_id', providerConfig.clientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('state', stateValue);
    return url.toString();
}

function buildGoogleAuthorizeUrl(req, providerConfig, stateValue) {
    const redirectUri = buildApiOAuthCallbackUrl(req, providerConfig.id);
    if (!redirectUri) {
        return '';
    }

    const url = new URL(GOOGLE_OAUTH_AUTHORIZE_URL);
    url.searchParams.set('client_id', providerConfig.clientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('scope', 'openid email profile');
    url.searchParams.set('state', stateValue);
    url.searchParams.set('prompt', 'select_account');
    return url.toString();
}

function redirectSocialAuthResult(res, provider, query, fallbackStatusCode = 500) {
    const redirectUrl = buildFrontendAuthRedirectUrl(provider, query);
    if (redirectUrl) {
        return res.redirect(302, redirectUrl);
    }

    const statusCode = query.error ? fallbackStatusCode : 200;
    return res.status(statusCode).json(query);
}

function cleanupExpiredEmailVerificationTokens() {
    db.prepare(`
        DELETE FROM auth_email_verification_tokens
        WHERE used_at IS NOT NULL OR expires_at <= ?
    `).run(nowIso());
}

function cleanupExpiredPasswordResetTokens() {
    db.prepare(`
        DELETE FROM auth_password_reset_tokens
        WHERE used_at IS NOT NULL OR expires_at <= ?
    `).run(nowIso());
}

function revokeAllRefreshSessionsForUser(userId) {
    db.prepare(`
        UPDATE auth_refresh_sessions
        SET revoked_at = COALESCE(revoked_at, ?)
        WHERE user_id = ? AND revoked_at IS NULL
    `).run(nowIso(), userId);
}

function issueEmailVerificationToken(userId, email) {
    cleanupExpiredEmailVerificationTokens();

    const rawToken = generateOpaqueToken();
    const tokenHash = hashOpaqueToken(rawToken);
    const createdAt = nowIso();
    const expiresAt = getFutureIsoFromHours(emailVerificationTokenTtlHours);

    db.prepare('DELETE FROM auth_email_verification_tokens WHERE user_id = ?').run(userId);
    db.prepare(`
        INSERT INTO auth_email_verification_tokens (user_id, email, token_hash, expires_at, created_at, used_at)
        VALUES (?, ?, ?, ?, ?, NULL)
    `).run(userId, email, tokenHash, expiresAt, createdAt);

    return {
        token: rawToken,
        expiresAt,
    };
}

function getValidEmailVerificationToken(token) {
    if (!token) {
        return null;
    }

    cleanupExpiredEmailVerificationTokens();

    return db.prepare(`
        SELECT id, user_id, email, expires_at, used_at
        FROM auth_email_verification_tokens
        WHERE token_hash = ?
    `).get(hashOpaqueToken(token));
}

function markEmailVerificationTokenUsed(tokenId) {
    db.prepare(`
        UPDATE auth_email_verification_tokens
        SET used_at = COALESCE(used_at, ?)
        WHERE id = ?
    `).run(nowIso(), tokenId);
}

function issuePasswordResetToken(userId, email) {
    cleanupExpiredPasswordResetTokens();

    const rawToken = generateOpaqueToken();
    const tokenHash = hashOpaqueToken(rawToken);
    const createdAt = nowIso();
    const expiresAt = getFutureIsoFromHours(passwordResetTokenTtlHours);

    db.prepare('DELETE FROM auth_password_reset_tokens WHERE user_id = ?').run(userId);
    db.prepare(`
        INSERT INTO auth_password_reset_tokens (user_id, email, token_hash, expires_at, created_at, used_at)
        VALUES (?, ?, ?, ?, ?, NULL)
    `).run(userId, email, tokenHash, expiresAt, createdAt);

    return {
        token: rawToken,
        expiresAt,
    };
}

function getValidPasswordResetToken(token) {
    if (!token) {
        return null;
    }

    cleanupExpiredPasswordResetTokens();

    return db.prepare(`
        SELECT id, user_id, email, expires_at, used_at
        FROM auth_password_reset_tokens
        WHERE token_hash = ?
    `).get(hashOpaqueToken(token));
}

function markPasswordResetTokenUsed(tokenId) {
    db.prepare(`
        UPDATE auth_password_reset_tokens
        SET used_at = COALESCE(used_at, ?)
        WHERE id = ?
    `).run(nowIso(), tokenId);
}

function buildDebugTokenPreview(token, expiresAt) {
    if (!authDebugTokenPreviewEnabled || !token) {
        return null;
    }

    return {
        token,
        expiresAt,
    };
}

function buildMailDeliveryResult(mailDelivery, fallbackReason = null) {
    return {
        delivered: Boolean(mailDelivery?.delivered),
        reason: mailDelivery?.reason ?? fallbackReason,
    };
}

function findRecoverableUsersByEmail(email) {
    const normalizedEmail = normalizeEmail(email);
    if (!normalizedEmail) {
        return [];
    }

    const usersById = new Map();
    const confirmedUserMatches = db.prepare(`
        SELECT id, username
        FROM users
        WHERE email = ? AND email_confirmed_at IS NOT NULL
    `).all(normalizedEmail);

    confirmedUserMatches.forEach((user) => {
        usersById.set(user.id, {
            id: user.id,
            username: user.username,
            email: normalizedEmail,
            source: 'account',
        });
    });

    const verifiedSocialMatches = db.prepare(`
        SELECT u.id, u.username, ai.provider
        FROM auth_identities ai
        JOIN users u ON u.id = ai.user_id
        WHERE ai.provider_email = ? AND ai.provider_email_verified = 1
        ORDER BY ai.last_login_at DESC, ai.provider ASC
    `).all(normalizedEmail);

    verifiedSocialMatches.forEach((user) => {
        if (!usersById.has(user.id)) {
            usersById.set(user.id, {
                id: user.id,
                username: user.username,
                email: normalizedEmail,
                source: user.provider,
            });
        }
    });

    return Array.from(usersById.values());
}

function createErrorBody(error, code = 'error', details = null) {
    const body = { error, code };
    if (Array.isArray(details) && details.length > 0) {
        body.details = details;
    }
    return body;
}

function sendError(res, statusCode, error, code = 'error', details = null) {
    return res.status(statusCode).json(createErrorBody(error, code, details));
}

function sendValidationError(res, details) {
    const error = details.length === 1 ? details[0].message : 'Validation failed';
    return sendError(res, 422, error, 'validation_error', details);
}

function normalizeInputString(value) {
    return typeof value === 'string' ? value.trim() : '';
}

function readRawString(value) {
    return typeof value === 'string' ? value : '';
}

const {
    validateOptionalEmail,
    validateRequiredEmail,
    validateTokenOnlyPayload,
    validateSocialAuthExchangePayload,
    validateNativeSocialAuthStartPayload,
    validateNativeSocialAuthAttemptToken,
    validatePasswordResetConfirmPayload,
    validateAuthPayload,
    validateRenamePayload,
    validatePasswordUpdatePayload,
} = createAuthValidation({
    normalizeEmail,
    normalizeInputString,
    readRawString,
    isValidEmail,
});

// --- Middleware ---
const authenticateToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1]; // Bearer TOKEN

    if (token == null) return sendError(res, 401, 'Access token required', 'access_token_required');

    jwt.verify(token, JWT_SECRET, (err, user) => {
        if (err || user?.type !== 'access') return sendError(res, 403, 'Invalid access token', 'invalid_access_token');
        req.user = user;
        next();
    });
};

const rateLimitBuckets = new Map();

function buildRateLimitKey(req, scope) {
    const normalizedUsername = typeof req.body?.username === 'string' && req.body.username.trim().length > 0
        ? req.body.username.trim().toLowerCase()
        : null;
    const normalizedEmail = typeof req.body?.email === 'string' && req.body.email.trim().length > 0
        ? req.body.email.trim().toLowerCase()
        : null;
    const identity = req.user?.id ? `user:${req.user.id}` : `ip:${req.ip || req.socket?.remoteAddress || 'unknown'}`;
    if (normalizedEmail) {
        return `${scope}:${identity}:email:${normalizedEmail}`;
    }
    if (normalizedUsername) {
        return `${scope}:${identity}:username:${normalizedUsername}`;
    }
    return `${scope}:${identity}`;
}

function createRateLimiter({ scope, maxAttempts, windowMs, skipSuccessfulRequests = false }) {
    return (req, res, next) => {
        const now = Date.now();
        const key = buildRateLimitKey(req, scope);
        const bucket = rateLimitBuckets.get(key) || [];
        const activeAttempts = bucket.filter(timestamp => now - timestamp < windowMs);

        if (activeAttempts.length >= maxAttempts) {
            const retryAfterSeconds = Math.max(1, Math.ceil((windowMs - (now - activeAttempts[0])) / 1000));
            res.set('Retry-After', String(retryAfterSeconds));
            return res.status(429).json({ error: 'Too many attempts. Please try again later.' });
        }

        rateLimitBuckets.set(key, activeAttempts);
        res.on('finish', () => {
            if (skipSuccessfulRequests && res.statusCode < 400) {
                return;
            }

            const completedAt = Date.now();
            const currentBucket = rateLimitBuckets.get(key) || [];
            const freshAttempts = currentBucket.filter(timestamp => completedAt - timestamp < windowMs);
            freshAttempts.push(completedAt);
            rateLimitBuckets.set(key, freshAttempts);
        });
        next();
    };
}

const authRateLimiter = createRateLimiter({
    scope: 'auth',
    maxAttempts: authRateLimitMax,
    windowMs: rateLimitWindowMs,
    skipSuccessfulRequests: true,
});

const passwordRateLimiter = createRateLimiter({
    scope: 'password-change',
    maxAttempts: passwordRateLimitMax,
    windowMs: rateLimitWindowMs,
});

const restoreRateLimiter = createRateLimiter({
    scope: 'backup-restore',
    maxAttempts: restoreRateLimitMax,
    windowMs: rateLimitWindowMs,
});

const emailVerificationRateLimiter = createRateLimiter({
    scope: 'email-verification',
    maxAttempts: emailVerificationRateLimitMax,
    windowMs: rateLimitWindowMs,
});

const passwordRecoveryRequestRateLimiter = createRateLimiter({
    scope: 'password-recovery-request',
    maxAttempts: passwordRecoveryRequestRateLimitMax,
    windowMs: rateLimitWindowMs,
});

const passwordRecoveryConfirmRateLimiter = createRateLimiter({
    scope: 'password-recovery-confirm',
    maxAttempts: passwordRecoveryConfirmRateLimitMax,
    windowMs: rateLimitWindowMs,
});

// Periodic cleanup of expired rate-limit buckets to prevent memory leaks
setInterval(() => {
    const now = Date.now();
    for (const [key, bucket] of rateLimitBuckets) {
        const activeBucket = bucket.filter(ts => now - ts < rateLimitWindowMs);
        if (activeBucket.length === 0) {
            rateLimitBuckets.delete(key);
        } else {
            rateLimitBuckets.set(key, activeBucket);
        }
    }
}, 60 * 1000);

app.get('/api/health', (req, res) => {
    try {
        const databaseProbe = db.prepare('SELECT 1 AS ok').get();
        const missingTables = requiredHealthTables.filter(tableName => !hasTable(tableName));
        const mailConfig = getMailConfig();
        const healthStatus = databaseProbe?.ok === 1 && missingTables.length === 0 && (!isProduction || mailConfig.configured) ? 'ok' : 'error';
        const payload = {
            status: healthStatus,
            timestamp: nowIso(),
            uptime: Number(process.uptime().toFixed(3)),
            env: process.env.NODE_ENV || 'development',
            database: databaseProbe?.ok === 1 ? 'ok' : 'error',
            schema: missingTables.length === 0 ? 'ok' : 'error',
            recoveryDelivery: mailConfig.configured ? 'ok' : (isProduction ? 'error' : 'dev_only'),
            appBaseUrl: mailConfig.appBaseUrl ? 'ok' : (isProduction ? 'error' : 'dev_default'),
            missingTables,
        };

        if (payload.status !== 'ok') {
            logger.warn('health_check_degraded', payload);
            return res.status(503).json(payload);
        }

        logger.info('health_check_ok', {
            requestId: req.requestId,
            status: payload.status,
        });
        return res.json(payload);
    } catch (error) {
        logger.error('health_check_failed', {
            requestId: req.requestId,
            error,
        });
        return res.status(503).json({
            status: 'error',
            timestamp: nowIso(),
            uptime: Number(process.uptime().toFixed(3)),
            env: process.env.NODE_ENV || 'development',
            database: 'error',
        });
    }
});

app.post('/api/auth/register', authRateLimiter, (req, res) => {
    const { username, password, details } = validateAuthPayload(req.body, { requirePasswordMinLength: true });
    if (details.length > 0) return sendValidationError(res, details);

    try {
        const hashedPassword = bcrypt.hashSync(password, 8);
        const user = createUserWithDefaults({
            username,
            passwordHash: hashedPassword,
            authPasswordEnabled: true,
        });

        // Create initial backup
        createBackup(user.id);

        const authResponse = issueAuthSession(req, res, user);
        res.json(authResponse);
    } catch (err) {
        if (err.message.includes('UNIQUE constraint failed')) {
            return sendError(res, 409, 'Username already exists', 'username_conflict');
        }
        logger.error('register_failed', {
            requestId: req.requestId,
            username,
            error: err,
        });
        return sendError(res, 500, 'Internal server error', 'internal_error');
    }
});

app.post('/api/auth/login', authRateLimiter, (req, res) => {
    const { username, password, details } = validateAuthPayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
    if (!user) return sendError(res, 401, 'Invalid credentials', 'invalid_credentials');
    if (!user.auth_password_enabled) {
        return sendError(res, 403, 'Password sign-in is not enabled for this account', 'password_auth_disabled');
    }

    const passwordIsValid = bcrypt.compareSync(password, user.password_hash);
    if (!passwordIsValid) return sendError(res, 401, 'Invalid credentials', 'invalid_credentials');

    // Create backup on login
    createBackup(user.id);

    const authResponse = issueAuthSession(req, res, user);
    res.json(authResponse);
});

app.post('/api/auth/refresh', (req, res) => {
    const refreshToken = getRefreshTokenFromRequest(req);
    if (!refreshToken) {
        clearRefreshTokenCookie(res);
        return sendError(res, 401, 'Refresh token required', 'refresh_token_required');
    }

    const session = getValidRefreshSession(refreshToken);
    if (!session || session.revoked_at || session.expires_at <= nowIso()) {
        revokeRefreshSessionByToken(refreshToken);
        clearRefreshTokenCookie(res);
        return sendError(res, 401, 'Invalid refresh token', 'invalid_refresh_token');
    }

    const user = db.prepare('SELECT id, username, email, email_confirmed_at, created_at FROM users WHERE id = ?').get(session.user_id);
    if (!user) {
        revokeRefreshSessionById(session.id);
        clearRefreshTokenCookie(res);
        return sendError(res, 401, 'Invalid refresh token', 'invalid_refresh_token');
    }

    touchRefreshSession(session.id);
    revokeRefreshSessionById(session.id);
    const authResponse = issueAuthSession(req, res, user);
    res.json(authResponse);
});

app.get('/api/auth/social/providers', (req, res) => {
    res.set('Cache-Control', 'no-store, no-cache, must-revalidate, private');
    res.set('Pragma', 'no-cache');
    return res.json({
        providers: getEnabledSocialProviders(req),
    });
});

app.post('/api/auth/oauth/:provider/native/start', authRateLimiter, (req, res) => {
    const providerId = normalizeInputString(req.params.provider).toLowerCase();
    const providerConfig = getSocialProviderConfig(providerId);
    if (!providerConfig) {
        return sendError(res, 404, 'Social provider not found', 'social_provider_not_found');
    }
    if (!isSocialProviderAvailable(providerConfig, req)) {
        return sendError(res, 503, 'Social provider is not configured', 'social_provider_not_configured');
    }
    if (providerId !== 'google' && providerId !== 'yandex') {
        return sendError(
            res,
            501,
            `${providerConfig.displayName} social login handshake is not implemented yet`,
            'social_auth_not_implemented'
        );
    }

    const { clientType, redirectUri, details } = validateNativeSocialAuthStartPayload(req.body);
    if (details.length > 0) {
        return sendValidationError(res, details);
    }

    const apiCallbackUrl = buildApiOAuthCallbackUrl(req, providerId);
    if (!apiCallbackUrl) {
        return sendError(
            res,
            500,
            `${providerConfig.displayName} OAuth callback configuration is incomplete`,
            'social_auth_configuration_invalid'
        );
    }

    const attempt = issueNativeSocialLoginAttempt({
        provider: providerId,
        clientType,
        redirectUri,
    });
    const statePayload = createOAuthStatePayload({
        provider: providerId,
        clientType,
        redirectUri,
        attemptToken: attempt.attemptToken,
    });
    const stateValue = encodeOAuthStatePayload(statePayload);
    const authorizeUrl = providerId === 'google'
        ? buildGoogleAuthorizeUrl(req, providerConfig, stateValue)
        : buildYandexAuthorizeUrl(req, providerConfig, stateValue);

    if (!authorizeUrl) {
        completeNativeSocialLoginAttemptError(
            getNativeSocialLoginAttempt(attempt.attemptToken)?.id,
            { errorCode: 'social_auth_configuration_invalid' }
        );
        return sendError(
            res,
            500,
            `${providerConfig.displayName} OAuth start configuration is incomplete`,
            'social_auth_configuration_invalid'
        );
    }

    return res.json({
        provider: providerId,
        clientType,
        attemptToken: attempt.attemptToken,
        authorizeUrl,
        expiresAt: attempt.expiresAt,
        pollIntervalMs: nativeSocialAuthPollIntervalMs,
    });
});

app.get('/api/auth/oauth/native/:attemptToken', (req, res) => {
    const { attemptToken, details } = validateNativeSocialAuthAttemptToken(req.params.attemptToken);
    if (details.length > 0) {
        return sendValidationError(res, details);
    }

    const attempt = getNativeSocialLoginAttempt(attemptToken);
    if (!attempt || attempt.expires_at <= nowIso()) {
        return sendError(res, 404, 'Native social auth attempt not found or expired', 'social_auth_attempt_not_found');
    }

    return res.json({
        provider: attempt.provider,
        clientType: attempt.client_type,
        status: attempt.status,
        code: attempt.status === 'completed' ? attempt.exchange_code : null,
        error: attempt.status === 'error' ? attempt.error_code : null,
        errorDescription: attempt.status === 'error' ? attempt.error_description : null,
        expiresAt: attempt.expires_at,
        completedAt: attempt.completed_at || null,
    });
});

app.get('/api/auth/oauth/:provider/start', (req, res) => {
    const providerId = normalizeInputString(req.params.provider).toLowerCase();
    const providerConfig = getSocialProviderConfig(providerId);
    if (!providerConfig) {
        return sendError(res, 404, 'Social provider not found', 'social_provider_not_found');
    }
    if (!isSocialProviderAvailable(providerConfig, req)) {
        return sendError(res, 503, 'Social provider is not configured', 'social_provider_not_configured');
    }
    if (providerId !== 'google' && providerId !== 'yandex') {
        return sendError(
            res,
            501,
            `${providerConfig.displayName} social login handshake is not implemented yet`,
            'social_auth_not_implemented'
        );
    }

    const frontendCallbackUrl = buildWebSocialCallbackUrl(providerId);
    const apiCallbackUrl = buildApiOAuthCallbackUrl(req, providerId);
    if (!frontendCallbackUrl || !apiCallbackUrl) {
        return sendError(
            res,
            500,
            `${providerConfig.displayName} OAuth callback configuration is incomplete`,
            'social_auth_configuration_invalid'
        );
    }

    const statePayload = createOAuthStatePayload({
        provider: providerId,
        clientType: 'web',
        redirectUri: frontendCallbackUrl,
    });
    const stateValue = encodeOAuthStatePayload(statePayload);
    const authorizeUrl = providerId === 'google'
        ? buildGoogleAuthorizeUrl(req, providerConfig, stateValue)
        : buildYandexAuthorizeUrl(req, providerConfig, stateValue);
    if (!authorizeUrl) {
        return sendError(
            res,
            500,
            `${providerConfig.displayName} OAuth start configuration is incomplete`,
            'social_auth_configuration_invalid'
        );
    }

    setOAuthStateCookie(res, stateValue);
    return res.redirect(302, authorizeUrl);
});

app.get('/api/auth/oauth/:provider/callback', async (req, res) => {
    const providerId = normalizeInputString(req.params.provider).toLowerCase();
    const providerConfig = getSocialProviderConfig(providerId);
    if (!providerConfig) {
        return sendError(res, 404, 'Social provider not found', 'social_provider_not_found');
    }
    if (!isSocialProviderAvailable(providerConfig, req)) {
        return sendError(res, 503, 'Social provider is not configured', 'social_provider_not_configured');
    }
    if (providerId !== 'google' && providerId !== 'yandex') {
        return sendError(
            res,
            501,
            `${providerConfig.displayName} social login callback is not implemented yet`,
            'social_auth_not_implemented'
        );
    }

    const oauthError = normalizeInputString(req.query?.error);
    const oauthErrorDescription = normalizeInputString(req.query?.error_description);
    const rawStateValue = normalizeInputString(req.query?.state);
    const decodedStatePayload = rawStateValue ? decodeOAuthStatePayload(rawStateValue) : null;
    const nativeAttemptToken = normalizeInputString(decodedStatePayload?.attemptToken);
    const nativeAttempt = nativeAttemptToken ? getNativeSocialLoginAttempt(nativeAttemptToken) : null;
    const isNativeClient = Boolean(
        nativeAttempt &&
        decodedStatePayload &&
        decodedStatePayload.clientType &&
        decodedStatePayload.clientType !== 'web'
    );
    const statePayload = isNativeClient ? decodedStatePayload : readOAuthStatePayloadFromRequest(req);
    clearOAuthStateCookie(res);

    if (!statePayload || statePayload.provider !== providerId) {
        if (nativeAttempt) {
            completeNativeSocialLoginAttemptError(nativeAttempt.id, { errorCode: 'invalid_oauth_state' });
            return sendNativeSocialAuthCompletionPage(res, {
                success: false,
                provider: providerId,
                errorCode: 'invalid_oauth_state',
                statusCode: 400,
            });
        }
        return redirectSocialAuthResult(res, providerId, {
            error: 'invalid_oauth_state',
            provider: providerId,
        }, 400);
    }

    if (oauthError) {
        if (nativeAttempt) {
            completeNativeSocialLoginAttemptError(nativeAttempt.id, {
                errorCode: oauthError,
                errorDescription: oauthErrorDescription || null,
            });
            return sendNativeSocialAuthCompletionPage(res, {
                success: false,
                provider: providerId,
                errorCode: oauthError,
                statusCode: 400,
            });
        }
        return redirectSocialAuthResult(res, providerId, {
            error: oauthError,
            errorDescription: oauthErrorDescription || null,
            provider: providerId,
        }, 400);
    }

    const authorizationCode = normalizeInputString(req.query?.code);
    if (!authorizationCode) {
        if (nativeAttempt) {
            completeNativeSocialLoginAttemptError(nativeAttempt.id, {
                errorCode: `${providerId}_authorization_code_missing`,
            });
            return sendNativeSocialAuthCompletionPage(res, {
                success: false,
                provider: providerId,
                errorCode: `${providerId}_authorization_code_missing`,
                statusCode: 400,
            });
        }
        return redirectSocialAuthResult(res, providerId, {
            error: `${providerId}_authorization_code_missing`,
            provider: providerId,
        }, 400);
    }

    try {
        let user = null;

        if (providerId === 'google') {
            const redirectUri = buildApiOAuthCallbackUrl(req, providerId);
            const tokenPayload = await exchangeGoogleAuthorizationCode({
                code: authorizationCode,
                redirectUri,
                providerConfig,
            });
            const googleAccessToken = normalizeInputString(tokenPayload?.access_token);
            if (!googleAccessToken) {
                throw new Error('Google token response is missing access_token');
            }

            const googleProfile = await fetchGoogleUserInfo(googleAccessToken);
            user = resolveOrCreateGoogleAuthUser(googleProfile);
        } else if (providerId === 'yandex') {
            const tokenPayload = await exchangeYandexAuthorizationCode({
                code: authorizationCode,
                providerConfig,
            });
            const yandexAccessToken = normalizeInputString(tokenPayload?.access_token);
            if (!yandexAccessToken) {
                throw new Error('Yandex token response is missing access_token');
            }

            const yandexProfile = await fetchYandexUserInfo(yandexAccessToken);
            user = resolveOrCreateYandexAuthUser(yandexProfile);
        }

        if (!user) {
            throw new Error(`${providerConfig.displayName} auth user resolution failed`);
        }

        const exchangeCode = issueSocialAuthExchangeCode({
            userId: user.id,
            provider: providerId,
            clientType: statePayload.clientType || (nativeAttempt ? nativeAttempt.client_type : 'web'),
            redirectUri: statePayload.redirectUri || buildWebSocialCallbackUrl(providerId),
        });

        logger.info('social_auth_callback_succeeded', {
            requestId: req.requestId,
            userId: user.id,
            provider: providerId,
        });

        if (nativeAttempt) {
            completeNativeSocialLoginAttemptSuccess(nativeAttempt.id, {
                userId: user.id,
                exchangeCode: exchangeCode.code,
            });
            return sendNativeSocialAuthCompletionPage(res, {
                success: true,
                provider: providerId,
            });
        }

        return redirectSocialAuthResult(res, providerId, {
            code: exchangeCode.code,
            provider: providerId,
        });
    } catch (error) {
        logger.error('social_auth_callback_failed', {
            requestId: req.requestId,
            provider: providerId,
            error,
        });

        if (nativeAttempt) {
            completeNativeSocialLoginAttemptError(nativeAttempt.id, {
                errorCode: `${providerId}_auth_failed`,
            });
            return sendNativeSocialAuthCompletionPage(res, {
                success: false,
                provider: providerId,
                errorCode: `${providerId}_auth_failed`,
                statusCode: 500,
            });
        }

        return redirectSocialAuthResult(res, providerId, {
            error: `${providerId}_auth_failed`,
            provider: providerId,
        }, 500);
    }
});

app.post('/api/auth/oauth/exchange', authRateLimiter, (req, res) => {
    const { code, details } = validateSocialAuthExchangePayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const exchangeCode = getValidSocialAuthExchangeCode(code);
    if (!exchangeCode || exchangeCode.used_at || exchangeCode.expires_at <= nowIso()) {
        return sendError(res, 400, 'Social auth exchange code is invalid or expired', 'invalid_social_auth_exchange_code');
    }

    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled
        FROM users
        WHERE id = ?
    `).get(exchangeCode.user_id);
    if (!user) {
        markSocialAuthExchangeCodeUsed(exchangeCode.id);
        return sendError(res, 404, 'User not found', 'user_not_found');
    }

    markSocialAuthExchangeCodeUsed(exchangeCode.id);
    markNativeSocialLoginAttemptConsumedByExchangeCode(code);
    createBackup(user.id);

    const authResponse = issueAuthSession(req, res, user);
    return res.json({
        ...authResponse,
        authMethod: 'social',
        provider: exchangeCode.provider,
    });
});

app.post('/api/auth/password-recovery/request', passwordRecoveryRequestRateLimiter, async (req, res) => {
    const { email, details } = validateRequiredEmail(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const genericResponse = {
        success: true,
        message: 'Если аккаунт с таким email существует и email подтверждён либо подтверждён у соцпровайдера, инструкции по восстановлению уже отправлены.',
    };

    const matchedUsers = findRecoverableUsersByEmail(email);
    const debugPreviews = [];

    for (const user of matchedUsers) {
        const { token, expiresAt } = issuePasswordResetToken(user.id, user.email);
        const debugTokenPreview = buildDebugTokenPreview(token, expiresAt);
        let mailDelivery = { delivered: false, reason: 'not_attempted' };

        try {
            mailDelivery = await sendPasswordRecoveryEmail({
                email: user.email,
                username: user.username,
                token,
                expiresAt,
            });
        } catch (error) {
            logger.error('password_recovery_email_send_failed', {
                requestId: req.requestId,
                userId: user.id,
                email: user.email,
                source: user.source,
                error,
            });
        }

        logger.info('password_recovery_requested', {
            requestId: req.requestId,
            userId: user.id,
            email: user.email,
            source: user.source,
            debugPreviewEnabled: Boolean(debugTokenPreview),
            mailDelivered: mailDelivery.delivered,
            mailReason: mailDelivery.reason ?? null,
        });

        if (debugTokenPreview) {
            debugPreviews.push({
                username: user.username,
                source: user.source,
                ...debugTokenPreview,
            });
        }
    }

    if (debugPreviews.length > 0) {
        genericResponse.debug = {
            passwordReset: debugPreviews[0],
            passwordResets: debugPreviews,
        };
    }

    return res.json(genericResponse);
});

app.post('/api/auth/password-recovery/confirm', passwordRecoveryConfirmRateLimiter, (req, res) => {
    const { token, newPassword, details } = validatePasswordResetConfirmPayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const resetToken = getValidPasswordResetToken(token);
    if (!resetToken || resetToken.used_at || resetToken.expires_at <= nowIso()) {
        return sendError(res, 400, 'Ссылка для сброса пароля недействительна или устарела', 'invalid_reset_token');
    }

    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(resetToken.user_id);
    if (!user) {
        return sendError(res, 404, 'Пользователь не найден', 'user_not_found');
    }

    const hashedPassword = bcrypt.hashSync(newPassword, 8);
    const transaction = db.transaction(() => {
        db.prepare('UPDATE users SET password_hash = ?, auth_password_enabled = 1 WHERE id = ?').run(hashedPassword, user.id);
        revokeAllRefreshSessionsForUser(user.id);
        markPasswordResetTokenUsed(resetToken.id);
    });

    transaction();

    logger.info('password_recovery_confirmed', {
        requestId: req.requestId,
        userId: user.id,
    });

    return res.json({ success: true });
});

app.post('/api/auth/email-verification/confirm', (req, res) => {
    const { token, details } = validateTokenOnlyPayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const verificationToken = getValidEmailVerificationToken(token);
    if (!verificationToken || verificationToken.used_at || verificationToken.expires_at <= nowIso()) {
        return sendError(res, 400, 'Ссылка для подтверждения email недействительна или устарела', 'invalid_email_verification_token');
    }

    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(verificationToken.user_id);
    if (!user) {
        return sendError(res, 404, 'Пользователь не найден', 'user_not_found');
    }

    const timestamp = nowIso();
    const transaction = db.transaction(() => {
        db.prepare(`
            UPDATE users
            SET email = ?, email_confirmed_at = ?
            WHERE id = ?
        `).run(verificationToken.email, timestamp, user.id);
        markEmailVerificationTokenUsed(verificationToken.id);
    });

    transaction();

    const updatedUser = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(user.id);

    logger.info('email_verified', {
        requestId: req.requestId,
        userId: user.id,
        email: verificationToken.email,
    });

    return res.json({
        success: true,
        user: buildUserPayload(updatedUser),
    });
});

app.post('/api/auth/logout', (req, res) => {
    const refreshToken = getRefreshTokenFromRequest(req);
    if (refreshToken) {
        revokeRefreshSessionByToken(refreshToken);
    }

    clearRefreshTokenCookie(res);
    return res.status(204).send();
});

app.get('/api/auth/me', authenticateToken, (req, res) => {
    const user = db.prepare('SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled FROM users WHERE id = ?').get(req.user.id);
    if (!user) return sendError(res, 404, 'User not found', 'user_not_found');

    // Check last backup time to avoid spamming backups on reload
    const lastBackup = db.prepare('SELECT created_at FROM user_backups WHERE user_id = ? ORDER BY created_at DESC LIMIT 1').get(req.user.id);
    const shouldBackup = !lastBackup || (new Date() - new Date(lastBackup.created_at + 'Z')) > 60 * 60 * 1000; // 1 hour

    if (shouldBackup) {
        createBackup(req.user.id);
    }

    res.json(buildUserPayload(user));
});

// Protect all subsequent API routes
app.use('/api', authenticateToken);

// --- User Management & Backup ---

app.put('/api/user/rename', (req, res) => {
    const { newUsername, details } = validateRenamePayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    try {
        db.prepare('UPDATE users SET username = ? WHERE id = ?').run(newUsername, req.user.id);
        res.json({ success: true, username: newUsername });
    } catch (err) {
        if (err.message.includes('UNIQUE constraint failed')) {
            return sendError(res, 409, 'Username already exists', 'username_conflict');
        }
        logger.error('rename_user_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            error: err,
        });
        return sendError(res, 500, 'Internal server error', 'internal_error');
    }
});

app.put('/api/user/password', passwordRateLimiter, (req, res) => {
    const { currentPassword, newPassword, details } = validatePasswordUpdatePayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const user = db.prepare('SELECT password_hash, auth_password_enabled FROM users WHERE id = ?').get(req.user.id);
    if (!user) return sendError(res, 404, 'User not found', 'user_not_found');
    if (!user.auth_password_enabled) {
        return sendError(res, 403, 'Password sign-in is not enabled for this account', 'password_auth_disabled');
    }

    const currentPasswordIsValid = bcrypt.compareSync(currentPassword, user.password_hash);
    if (!currentPasswordIsValid) return sendError(res, 401, 'Current password is incorrect', 'invalid_current_password');

    const hashedPassword = bcrypt.hashSync(newPassword, 8);
    db.prepare('UPDATE users SET password_hash = ?, auth_password_enabled = 1 WHERE id = ?').run(hashedPassword, req.user.id);
    res.json({ success: true });
});

app.put('/api/user/email', emailVerificationRateLimiter, async (req, res) => {
    const { email, details } = validateOptionalEmail(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(req.user.id);
    if (!user) return sendError(res, 404, 'User not found', 'user_not_found');

    const normalizedExistingEmail = normalizeEmail(user.email);
    const hasEmailChanged = email !== normalizedExistingEmail;

    if (!email) {
        db.prepare(`
            UPDATE users
            SET email = NULL, email_confirmed_at = NULL
            WHERE id = ?
        `).run(user.id);
        db.prepare('DELETE FROM auth_email_verification_tokens WHERE user_id = ?').run(user.id);
        db.prepare('DELETE FROM auth_password_reset_tokens WHERE user_id = ?').run(user.id);

        const updatedUser = db.prepare(`
            SELECT id, username, email, email_confirmed_at, created_at
            FROM users
            WHERE id = ?
        `).get(user.id);

        return res.json({
            success: true,
            verificationRequired: false,
            user: buildUserPayload(updatedUser),
        });
    }

    const conflictingUser = db.prepare(`
        SELECT id
        FROM users
        WHERE email = ? AND id != ?
    `).get(email, user.id);
    if (conflictingUser) {
        return sendError(res, 409, 'Этот email уже используется', 'email_conflict');
    }

    db.prepare(`
        UPDATE users
        SET email = ?, email_confirmed_at = CASE WHEN email = ? THEN email_confirmed_at ELSE NULL END
        WHERE id = ?
    `).run(email, email, user.id);
    if (hasEmailChanged) {
        db.prepare('DELETE FROM auth_password_reset_tokens WHERE user_id = ?').run(user.id);
    }

    let debugTokenPreview = null;
    let verificationDelivery = undefined;
    if (hasEmailChanged || !user.email_confirmed_at) {
        const tokenResult = issueEmailVerificationToken(user.id, email);
        debugTokenPreview = buildDebugTokenPreview(tokenResult.token, tokenResult.expiresAt);

        try {
            const mailDelivery = await sendEmailVerificationEmail({
                email,
                username: user.username,
                token: tokenResult.token,
                expiresAt: tokenResult.expiresAt,
            });
            verificationDelivery = buildMailDeliveryResult(mailDelivery);

            logger.info('email_verification_requested', {
                requestId: req.requestId,
                userId: user.id,
                email,
                source: hasEmailChanged ? 'email_update' : 'verification_request_implicit',
                debugPreviewEnabled: Boolean(debugTokenPreview),
                mailDelivered: mailDelivery.delivered,
                mailReason: mailDelivery.reason ?? null,
            });
        } catch (error) {
            logger.error('email_verification_send_failed', {
                requestId: req.requestId,
                userId: user.id,
                email,
                source: hasEmailChanged ? 'email_update' : 'verification_request_implicit',
                error,
            });
            verificationDelivery = buildMailDeliveryResult(null, 'send_failed');
        }
    }

    const updatedUser = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(user.id);

    return res.json({
        success: true,
        verificationRequired: !updatedUser.email_confirmed_at,
        debug: debugTokenPreview ? { emailVerification: debugTokenPreview } : undefined,
        delivery: verificationDelivery,
        user: buildUserPayload(updatedUser),
    });
});

app.post('/api/user/email/verification/request', emailVerificationRateLimiter, async (req, res) => {
    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(req.user.id);
    if (!user) return sendError(res, 404, 'Пользователь не найден', 'user_not_found');

    if (!user.email) {
        return sendError(res, 409, 'Email ещё не настроен', 'email_not_configured');
    }

    if (user.email_confirmed_at) {
        return res.json({
            success: true,
            verificationRequired: false,
            user: buildUserPayload(user),
        });
    }

    const { token, expiresAt } = issueEmailVerificationToken(user.id, user.email);
    const debugTokenPreview = buildDebugTokenPreview(token, expiresAt);
    let verificationDelivery = undefined;

    try {
        const mailDelivery = await sendEmailVerificationEmail({
            email: user.email,
            username: user.username,
            token,
            expiresAt,
        });
        verificationDelivery = buildMailDeliveryResult(mailDelivery);

        logger.info('email_verification_requested', {
            requestId: req.requestId,
            userId: user.id,
            email: user.email,
            source: 'verification_request_explicit',
            debugPreviewEnabled: Boolean(debugTokenPreview),
            mailDelivered: mailDelivery.delivered,
            mailReason: mailDelivery.reason ?? null,
        });
    } catch (error) {
        logger.error('email_verification_send_failed', {
            requestId: req.requestId,
            userId: user.id,
            email: user.email,
            source: 'verification_request_explicit',
            error,
        });
        verificationDelivery = buildMailDeliveryResult(null, 'send_failed');
    }

    return res.json({
        success: true,
        verificationRequired: true,
        debug: debugTokenPreview ? { emailVerification: debugTokenPreview } : undefined,
        delivery: verificationDelivery,
        user: buildUserPayload(user),
    });
});

app.post('/api/user/backup', (req, res) => {
    try {
        createBackup(req.user.id);
        res.json({ success: true });
    } catch (e) {
        logger.error('manual_backup_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            error: e,
        });
        res.status(500).json({ error: "Backup creation failed" });
    }
});

app.get('/api/user/backups', (req, res) => {
    try {
        const backups = listUserBackupEntries(req.user.id);
        res.json({ backups });
    } catch (e) {
        logger.error('list_backups_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            error: e,
        });
        res.status(500).json({ error: 'Failed to list backups' });
    }
});

app.post('/api/user/restore', restoreRateLimiter, (req, res) => {
    const { backupId, confirmationText } = req.body || {};

    if (!backupId || typeof backupId !== 'number') {
        return res.status(400).json({ error: 'backupId is required and must be a number' });
    }
    if (!confirmationText || typeof confirmationText !== 'string') {
        return res.status(400).json({ error: 'confirmationText is required' });
    }
    if (confirmationText.trim() !== BACKUP_RESTORE_CONFIRMATION_TEXT) {
        return res.status(400).json({ error: `Для подтверждения введите "${BACKUP_RESTORE_CONFIRMATION_TEXT}"` });
    }

    const backupRow = getUserBackupRecord(req.user.id, backupId);
    if (!backupRow) {
        return res.status(404).json({ error: 'Backup not found' });
    }

    const backupData = parseBackupDataSafely(backupRow.data);
    if (!backupData) {
        return res.status(500).json({ error: 'Backup data is corrupted' });
    }

    try {
        restoreBackupSnapshot(req.user.id, backupData);
        const restoredEntry = buildBackupEntryPayload(backupRow);
        res.json({ success: true, restoredBackup: restoredEntry });
        broadcastChange(req.user.id, 'all', 'restored');
    } catch (e) {
        logger.error('restore_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            backupId,
            error: e,
        });
        res.status(500).json({ error: e.message });
    }
});


// Categories
app.get('/api/categories', (req, res) => {
    const includeInactive = req.query.include_inactive === '1' || req.query.include_inactive === 'true';
    const categories = includeInactive
        ? db.prepare('SELECT * FROM categories WHERE user_id = ? ORDER BY sort_order ASC, name ASC').all(req.user.id)
        : db.prepare('SELECT * FROM categories WHERE user_id = ? AND is_active = 1 ORDER BY sort_order ASC, name ASC').all(req.user.id);
    res.json(categories);
});

app.get('/api/deleted_records', (req, res) => {
    const { entity_type, since } = req.query;
    const filters = ['user_id = ?'];
    const params = [req.user.id];

    if (typeof entity_type === 'string' && entity_type.trim() !== '') {
        filters.push('entity_type = ?');
        params.push(entity_type.trim());
    }

    if (typeof since === 'string' && since.trim() !== '') {
        filters.push('deleted_at > ?');
        params.push(since.trim());
    }

    const deletedRecords = db.prepare(`
        SELECT entity_type, entity_id, deleted_at
        FROM deleted_records
        WHERE ${filters.join(' AND ')}
        ORDER BY deleted_at ASC, id ASC
    `).all(...params);

    res.json(deletedRecords);
});

app.post('/api/categories', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name } = req.body;
        const result = db.prepare('SELECT MAX(sort_order) as maxOrder FROM categories WHERE user_id = ?').get(req.user.id);
        const nextOrder = (result.maxOrder || 0) + 1;
        const timestamp = nowIso();
        
        const info = db.prepare('INSERT INTO categories (user_id, name, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?)').run(req.user.id, name, nextOrder, timestamp, timestamp);
        const body = getRowById('categories', info.lastInsertRowid);
        broadcastChange(req.user.id, 'category', 'created');
        return { statusCode: 200, body };
    });
});

app.put('/api/categories/reorder', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { ids } = req.body;
        if (!Array.isArray(ids)) return { statusCode: 400, body: { error: 'ids array required' } };

        const timestamp = nowIso();
        const updateStmt = db.prepare('UPDATE categories SET sort_order = ?, updated_at = ? WHERE id = ? AND user_id = ?');
        
        const transact = db.transaction((idList) => {
            idList.forEach((id, index) => {
                updateStmt.run(index, timestamp, Number(id), req.user.id);
            });
        });

        try {
            transact(ids);
            return { statusCode: 200, body: { success: true } };
        } catch (err) {
            logger.error('categories_reorder_failed', {
                requestId: req.requestId,
                userId: req.user.id,
                error: err,
            });
            return { statusCode: 500, body: { error: err.message } };
        }
    });
});

app.put('/api/categories/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name, is_active } = req.body;
        const result = db.prepare('UPDATE categories SET name = ?, is_active = ?, updated_at = ? WHERE id = ? AND user_id = ?').run(name, is_active, nowIso(), req.params.id, req.user.id);
        if (result.changes === 0) return { statusCode: 404, body: { error: 'Category not found' } };
        if (Number(is_active) !== 0) {
            clearDeletedRecord(req.user.id, 'category', Number(req.params.id));
        }
        const body = getRowById('categories', req.params.id);
        broadcastChange(req.user.id, 'category', 'updated');
        return { statusCode: 200, body };
    });
});

app.delete('/api/categories/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const timestamp = nowIso();
        const result = db.prepare('UPDATE categories SET is_active = 0, updated_at = ? WHERE id = ? AND user_id = ?').run(timestamp, req.params.id, req.user.id);
        if (result.changes === 0) return { statusCode: 404, body: { error: 'Category not found' } };
        recordDeletedRecord(req.user.id, 'category', Number(req.params.id), timestamp);
        const body = getRowById('categories', req.params.id);
        broadcastChange(req.user.id, 'category', 'deleted');
        return { statusCode: 200, body };
    });
});

// Income Sources
app.get('/api/income_sources', (req, res) => {
    const includeInactive = req.query.include_inactive === '1' || req.query.include_inactive === 'true';
    const sources = includeInactive
        ? db.prepare('SELECT * FROM income_sources WHERE user_id = ? ORDER BY sort_order ASC, id ASC').all(req.user.id)
        : db.prepare('SELECT * FROM income_sources WHERE user_id = ? AND is_active = 1 ORDER BY sort_order ASC, id ASC').all(req.user.id);
    res.json(sources);
});

app.post('/api/income_sources', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name } = req.body;
        try {
            const result = db.prepare('SELECT MAX(sort_order) as maxOrder FROM income_sources WHERE user_id = ?').get(req.user.id);
            const nextOrder = (result.maxOrder || 0) + 1;
            const timestamp = nowIso();
            
            const stmt = db.prepare('INSERT INTO income_sources (user_id, name, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?)');
            const insertResult = stmt.run(req.user.id, name, nextOrder, timestamp, timestamp);
            const body = getRowById('income_sources', insertResult.lastInsertRowid);
            broadcastChange(req.user.id, 'income_source', 'created');
            return { statusCode: 200, body };
        } catch (err) {
            return { statusCode: 500, body: { error: err.message } };
        }
    });
});

app.put('/api/income_sources/reorder', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { ids } = req.body;
        if (!Array.isArray(ids)) return { statusCode: 400, body: { error: 'ids array required' } };

        const timestamp = nowIso();
        const updateStmt = db.prepare('UPDATE income_sources SET sort_order = ?, updated_at = ? WHERE id = ? AND user_id = ?');
        
        const transact = db.transaction((idList) => {
            idList.forEach((id, index) => {
                updateStmt.run(index, timestamp, Number(id), req.user.id);
            });
        });

        try {
            transact(ids);
            return { statusCode: 200, body: { success: true } };
        } catch (err) {
            logger.error('income_sources_reorder_failed', {
                requestId: req.requestId,
                userId: req.user.id,
                error: err,
            });
            return { statusCode: 500, body: { error: err.message } };
        }
    });
});

app.put('/api/income_sources/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name, is_active } = req.body;
        const { id } = req.params;
        try {
            const stmt = db.prepare('UPDATE income_sources SET name = ?, is_active = ?, updated_at = ? WHERE id = ? AND user_id = ?');
            const result = stmt.run(name, is_active ?? 1, nowIso(), id, req.user.id);
            if (result.changes === 0) return { statusCode: 404, body: { error: 'Income source not found' } };
            if (Number(is_active ?? 1) !== 0) {
                clearDeletedRecord(req.user.id, 'income_source', Number(id));
            }
            const body = getRowById('income_sources', id);
            broadcastChange(req.user.id, 'income_source', 'updated');
            return { statusCode: 200, body };
        } catch (err) {
            return { statusCode: 500, body: { error: err.message } };
        }
    });
});

app.delete('/api/income_sources/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { id } = req.params;
        try {
            const timestamp = nowIso();
            const stmt = db.prepare('UPDATE income_sources SET is_active = 0, updated_at = ? WHERE id = ? AND user_id = ?');
            const result = stmt.run(timestamp, id, req.user.id);
            if (result.changes === 0) return { statusCode: 404, body: { error: 'Income source not found' } };
            recordDeletedRecord(req.user.id, 'income_source', Number(id), timestamp);
            const body = getRowById('income_sources', id);
            broadcastChange(req.user.id, 'income_source', 'deleted');
            return { statusCode: 200, body };
        } catch (err) {
            return { statusCode: 500, body: { error: err.message } };
        }
    });
});

// Months
app.get('/api/months', (req, res) => {
    // Return list of months that have data
    const months = db.prepare('SELECT * FROM months WHERE user_id = ? ORDER BY year DESC, month DESC').all(req.user.id);
    res.json(months);
});

app.post('/api/months/ensure', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { year, month } = req.body;
        const id = getOrCreateMonth(req.user.id, year, month);
        return { statusCode: 200, body: getRowById('months', id) };
    });
});

// Incomes
app.get('/api/months/:monthId/incomes', (req, res) => {
    // Check access
    if (!checkMonthAccess(req.user.id, req.params.monthId)) return res.status(403).json({ error: 'Access denied' });
    
    const incomes = db.prepare('SELECT * FROM incomes WHERE month_id = ?').all(req.params.monthId);
    res.json(incomes);
});

app.post('/api/incomes', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { month_id, source, amount, date } = req.body;
        
        if (!checkMonthAccess(req.user.id, month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        const timestamp = nowIso();
        const info = db.prepare('INSERT INTO incomes (month_id, source, amount, date, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)').run(month_id, source, amount, date, timestamp, timestamp);
        const body = getRowById('incomes', info.lastInsertRowid);
        broadcastChange(req.user.id, 'income', 'created');
        return { statusCode: 200, body };
    });
});

app.put('/api/incomes/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { amount } = req.body;
        const income = db.prepare('SELECT month_id FROM incomes WHERE id = ?').get(req.params.id);
        if (!income) return { statusCode: 404, body: { error: 'Income not found' } };
        if (!checkMonthAccess(req.user.id, income.month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        db.prepare('UPDATE incomes SET amount = ?, updated_at = ? WHERE id = ?').run(amount, nowIso(), req.params.id);
        const body = getRowById('incomes', req.params.id);
        broadcastChange(req.user.id, 'income', 'updated');
        return { statusCode: 200, body };
    });
});

app.delete('/api/incomes/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const income = db.prepare('SELECT * FROM incomes WHERE id = ?').get(req.params.id);
        if (!income) return { statusCode: 404, body: { error: 'Income not found' } };
        if (!checkMonthAccess(req.user.id, income.month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        recordDeletedRecord(req.user.id, 'income', income.id);
        db.prepare('DELETE FROM incomes WHERE id = ?').run(req.params.id);
        broadcastChange(req.user.id, 'income', 'deleted');
        return { statusCode: 200, body: { success: true, id: income.id } };
    });
});

// Expenses
app.get('/api/months/:monthId/expenses', (req, res) => {
    if (!checkMonthAccess(req.user.id, req.params.monthId)) return res.status(403).json({ error: 'Access denied' });

    const expenses = db.prepare(`
    SELECT e.*, c.name as category_name 
    FROM expenses e 
    JOIN categories c ON e.category_id = c.id 
    WHERE e.month_id = ?
  `).all(req.params.monthId);
    res.json(expenses);
});

app.post('/api/expenses', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { month_id, category_id, amount, date, comment } = req.body;
        
        if (!checkMonthAccess(req.user.id, month_id)) return { statusCode: 403, body: { error: 'Access denied' } };
        
        const category = db.prepare('SELECT id FROM categories WHERE id = ? AND user_id = ?').get(category_id, req.user.id);
        if (!category) return { statusCode: 400, body: { error: 'Invalid category' } };

        const timestamp = nowIso();
        const info = db.prepare('INSERT INTO expenses (month_id, category_id, amount, date, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)').run(month_id, category_id, amount, date, comment || '', timestamp, timestamp);
        const body = getRowById('expenses', info.lastInsertRowid);
        broadcastChange(req.user.id, 'expense', 'created');
        return { statusCode: 200, body };
    });
});

app.put('/api/expenses/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { amount } = req.body;
        
        const expense = db.prepare('SELECT month_id FROM expenses WHERE id = ?').get(req.params.id);
        if (!expense) return { statusCode: 404, body: { error: 'Expense not found' } };
        if (!checkMonthAccess(req.user.id, expense.month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        db.prepare('UPDATE expenses SET amount = ?, updated_at = ? WHERE id = ?').run(amount, nowIso(), req.params.id);
        const body = getRowById('expenses', req.params.id);
        broadcastChange(req.user.id, 'expense', 'updated');
        return { statusCode: 200, body };
    });
});

app.delete('/api/expenses/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const expense = db.prepare('SELECT * FROM expenses WHERE id = ?').get(req.params.id);
        if (!expense) return { statusCode: 404, body: { error: 'Expense not found' } };
        if (!checkMonthAccess(req.user.id, expense.month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        recordDeletedRecord(req.user.id, 'expense', expense.id);
        db.prepare('DELETE FROM expenses WHERE id = ?').run(req.params.id);
        broadcastChange(req.user.id, 'expense', 'deleted');
        return { statusCode: 200, body: { success: true, id: expense.id } };
    });
});

// Budgets (Limits)
app.get('/api/months/:monthId/budgets', (req, res) => {
    if (!checkMonthAccess(req.user.id, req.params.monthId)) return res.status(403).json({ error: 'Access denied' });

    const budgets = db.prepare(`
    SELECT b.*, c.name as category_name 
    FROM budgets b 
    JOIN categories c ON b.category_id = c.id 
    WHERE b.month_id = ?
  `).all(req.params.monthId);
    res.json(budgets);
});

app.post('/api/budgets', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { month_id, category_id, limit_amount } = req.body;
        
        if (!checkMonthAccess(req.user.id, month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        const existing = db.prepare('SELECT id FROM budgets WHERE month_id = ? AND category_id = ?').get(month_id, category_id);
        let body;

        if (existing) {
            db.prepare('UPDATE budgets SET limit_amount = ?, updated_at = ? WHERE id = ?').run(limit_amount, nowIso(), existing.id);
            body = getRowById('budgets', existing.id);
        } else {
            const timestamp = nowIso();
            const info = db.prepare('INSERT INTO budgets (month_id, category_id, limit_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?)').run(month_id, category_id, limit_amount, timestamp, timestamp);
            body = getRowById('budgets', info.lastInsertRowid);
        }

        broadcastChange(req.user.id, 'budget', 'updated');
        return { statusCode: 200, body };
    });
});

// Savings
app.get('/api/savings_goals', (req, res) => {
    const goals = db.prepare('SELECT * FROM savings_goals WHERE user_id = ?').all(req.user.id);
    res.json(goals);
});

app.post('/api/savings_goals', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name, target_amount } = req.body;
        const timestamp = nowIso();
        const info = db.prepare('INSERT INTO savings_goals (user_id, name, target_amount, current_amount, created_at, updated_at) VALUES (?, ?, ?, 0, ?, ?)').run(req.user.id, name, target_amount || 0, timestamp, timestamp);
        const body = getRowById('savings_goals', info.lastInsertRowid);
        broadcastChange(req.user.id, 'savings_goal', 'created');
        return { statusCode: 200, body };
    });
});

app.put('/api/savings_goals/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name, target_amount, current_amount } = req.body;
        const updates = [];
        const values = [];
        
        if (name !== undefined) {
            updates.push('name = ?');
            values.push(name);
        }
        if (target_amount !== undefined) {
            updates.push('target_amount = ?');
            values.push(target_amount);
        }
        if (current_amount !== undefined) {
            updates.push('current_amount = ?');
            values.push(current_amount);
        }
        
        const goal = db.prepare('SELECT id FROM savings_goals WHERE id = ? AND user_id = ?').get(req.params.id, req.user.id);
        if (!goal) return { statusCode: 404, body: { error: 'Goal not found' } };

        if (updates.length === 0) {
            return { statusCode: 200, body: getRowById('savings_goals', req.params.id) };
        }

        updates.push('updated_at = ?');
        values.push(nowIso());
        const sql = `UPDATE savings_goals SET ${updates.join(', ')} WHERE id = ?`;
        db.prepare(sql).run(...values, req.params.id);
        const body = getRowById('savings_goals', req.params.id);
        broadcastChange(req.user.id, 'savings_goal', 'updated');
        return { statusCode: 200, body };
    });
});

app.delete('/api/savings_goals/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const goal = db.prepare('SELECT * FROM savings_goals WHERE id = ? AND user_id = ?').get(req.params.id, req.user.id);
        if (!goal) return { statusCode: 404, body: { error: 'Goal not found' } };

        const deletedAt = nowIso();
        const transactions = db.prepare('SELECT id FROM savings_transactions WHERE goal_id = ?').all(req.params.id);
        transactions.forEach((transaction) => {
            recordDeletedRecord(req.user.id, 'savings_transaction', transaction.id, deletedAt);
        });
        recordDeletedRecord(req.user.id, 'savings_goal', goal.id, deletedAt);
        db.prepare('DELETE FROM savings_transactions WHERE goal_id = ?').run(req.params.id);
        db.prepare('DELETE FROM savings_goals WHERE id = ?').run(req.params.id);
        broadcastChange(req.user.id, 'savings_goal', 'deleted');
        return { statusCode: 200, body: { success: true, id: goal.id } };
    });
});

app.post('/api/savings_transactions', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { goal_id, amount, date, month_id } = req.body;
        
        const goal = db.prepare('SELECT id, name FROM savings_goals WHERE id = ? AND user_id = ?').get(goal_id, req.user.id);
        if (!goal) return { statusCode: 404, body: { error: 'Goal not found' } };
        
        if (month_id && !checkMonthAccess(req.user.id, month_id)) {
            return { statusCode: 403, body: { error: 'Access denied to month' } };
        }

        const transact = db.transaction(() => {
            const timestamp = nowIso();
            const info = db.prepare('INSERT INTO savings_transactions (goal_id, amount, date, month_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)').run(goal_id, amount, date, month_id, timestamp, timestamp);
            
            db.prepare('UPDATE savings_goals SET current_amount = current_amount + ?, updated_at = ? WHERE id = ?').run(amount, timestamp, goal_id);
            
            if (amount > 0 && month_id) {
                let savingsCategory = db.prepare('SELECT id FROM categories WHERE name = ? AND user_id = ?').get('Пополнение копилки', req.user.id);
                if (!savingsCategory) {
                    const maxOrder = db.prepare('SELECT MAX(sort_order) as maxOrder FROM categories WHERE user_id = ?').get(req.user.id);
                    const nextOrder = (maxOrder.maxOrder || 0) + 1;
                    const catInfo = db.prepare('INSERT INTO categories (user_id, name, sort_order, is_active, created_at, updated_at) VALUES (?, ?, ?, 0, ?, ?)').run(req.user.id, 'Пополнение копилки', nextOrder, timestamp, timestamp);
                    savingsCategory = { id: catInfo.lastInsertRowid };
                }
                
                db.prepare('INSERT INTO expenses (month_id, category_id, amount, date, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)').run(
                    month_id,
                    savingsCategory.id,
                    amount,
                    date,
                    `Пополнение копилки "${goal.name}"`,
                    timestamp,
                    timestamp
                );
            }
            
            return info;
        });

        const info = transact();
        const body = getRowById('savings_transactions', info.lastInsertRowid);
        broadcastChange(req.user.id, 'savings_transaction', 'created');
        return { statusCode: 200, body };
    });
});

app.delete('/api/savings_transactions/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const transaction = db.prepare(`
            SELECT st.*, sg.user_id, sg.name as goal_name
            FROM savings_transactions st
            JOIN savings_goals sg ON st.goal_id = sg.id
            WHERE st.id = ? AND sg.user_id = ?
        `).get(req.params.id, req.user.id);

        if (!transaction) return { statusCode: 404, body: { error: 'Savings transaction not found' } };

        const transact = db.transaction(() => {
            const timestamp = nowIso();

            db.prepare('UPDATE savings_goals SET current_amount = current_amount - ?, updated_at = ? WHERE id = ?').run(transaction.amount, timestamp, transaction.goal_id);

            if (transaction.amount > 0 && transaction.month_id) {
                const savingsCategory = db.prepare('SELECT id FROM categories WHERE name = ? AND user_id = ?').get('Пополнение копилки', req.user.id);
                if (savingsCategory) {
                    db.prepare(`
                        DELETE FROM expenses
                        WHERE id = (
                            SELECT id
                            FROM expenses
                            WHERE month_id = ? AND category_id = ? AND amount = ? AND date = ? AND comment = ?
                            ORDER BY id DESC
                            LIMIT 1
                        )
                    `).run(
                        transaction.month_id,
                        savingsCategory.id,
                        transaction.amount,
                        transaction.date,
                        `Пополнение копилки "${transaction.goal_name}"`,
                    );
                }
            }

            recordDeletedRecord(req.user.id, 'savings_transaction', transaction.id, timestamp);
            db.prepare('DELETE FROM savings_transactions WHERE id = ?').run(req.params.id);
        });

        transact();
        broadcastChange(req.user.id, 'savings_transaction', 'deleted');
        return { statusCode: 200, body: { success: true, id: transaction.id } };
    });
});

app.put('/api/savings_transactions/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { goal_id, amount, date, month_id } = req.body;

        const existingTransaction = db.prepare(`
            SELECT st.*, sg.user_id, sg.name as goal_name
            FROM savings_transactions st
            JOIN savings_goals sg ON st.goal_id = sg.id
            WHERE st.id = ? AND sg.user_id = ?
        `).get(req.params.id, req.user.id);

        if (!existingTransaction) {
            return { statusCode: 404, body: { error: 'Savings transaction not found' } };
        }

        const nextGoalId = goal_id ?? existingTransaction.goal_id;
        const nextAmount = amount ?? existingTransaction.amount;
        const nextDate = date ?? existingTransaction.date;
        const nextMonthId = month_id !== undefined ? month_id : existingTransaction.month_id;

        const nextGoal = db.prepare('SELECT id, name FROM savings_goals WHERE id = ? AND user_id = ?').get(nextGoalId, req.user.id);
        if (!nextGoal) {
            return { statusCode: 404, body: { error: 'Goal not found' } };
        }

        if (nextMonthId && !checkMonthAccess(req.user.id, nextMonthId)) {
            return { statusCode: 403, body: { error: 'Access denied to month' } };
        }

        const transact = db.transaction(() => {
            const timestamp = nowIso();

            db.prepare('UPDATE savings_goals SET current_amount = current_amount - ?, updated_at = ? WHERE id = ?').run(
                existingTransaction.amount,
                timestamp,
                existingTransaction.goal_id
            );

            if (existingTransaction.amount > 0 && existingTransaction.month_id) {
                const savingsCategory = db.prepare('SELECT id FROM categories WHERE name = ? AND user_id = ?').get('Пополнение копилки', req.user.id);
                if (savingsCategory) {
                    db.prepare(`
                        DELETE FROM expenses
                        WHERE id = (
                            SELECT id
                            FROM expenses
                            WHERE month_id = ? AND category_id = ? AND amount = ? AND date = ? AND comment = ?
                            ORDER BY id DESC
                            LIMIT 1
                        )
                    `).run(
                        existingTransaction.month_id,
                        savingsCategory.id,
                        existingTransaction.amount,
                        existingTransaction.date,
                        `Пополнение копилки "${existingTransaction.goal_name}"`,
                    );
                }
            }

            db.prepare(`
                UPDATE savings_transactions
                SET goal_id = ?, amount = ?, date = ?, month_id = ?, updated_at = ?
                WHERE id = ?
            `).run(nextGoalId, nextAmount, nextDate, nextMonthId, timestamp, req.params.id);

            db.prepare('UPDATE savings_goals SET current_amount = current_amount + ?, updated_at = ? WHERE id = ?').run(
                nextAmount,
                timestamp,
                nextGoalId
            );

            if (nextAmount > 0 && nextMonthId) {
                let savingsCategory = db.prepare('SELECT id FROM categories WHERE name = ? AND user_id = ?').get('Пополнение копилки', req.user.id);
                if (!savingsCategory) {
                    const maxOrder = db.prepare('SELECT MAX(sort_order) as maxOrder FROM categories WHERE user_id = ?').get(req.user.id);
                    const nextOrder = (maxOrder.maxOrder || 0) + 1;
                    const catInfo = db.prepare('INSERT INTO categories (user_id, name, sort_order, is_active, created_at, updated_at) VALUES (?, ?, ?, 0, ?, ?)').run(
                        req.user.id,
                        'Пополнение копилки',
                        nextOrder,
                        timestamp,
                        timestamp
                    );
                    savingsCategory = { id: catInfo.lastInsertRowid };
                }

                db.prepare('INSERT INTO expenses (month_id, category_id, amount, date, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)').run(
                    nextMonthId,
                    savingsCategory.id,
                    nextAmount,
                    nextDate,
                    `Пополнение копилки "${nextGoal.name}"`,
                    timestamp,
                    timestamp
                );
            }
        });

        transact();
        const body = getRowById('savings_transactions', req.params.id);
        broadcastChange(req.user.id, 'savings_transaction', 'updated');
        return { statusCode: 200, body };
    });
});

app.get('/api/savings_transactions/:goalId', (req, res) => {
    const goal = db.prepare('SELECT id FROM savings_goals WHERE id = ? AND user_id = ?').get(req.params.goalId, req.user.id);
    if (!goal) return res.status(404).json({ error: 'Goal not found' });

    const transactions = db.prepare('SELECT * FROM savings_transactions WHERE goal_id = ? ORDER BY date DESC').all(req.params.goalId);
    res.json(transactions);
});

// Summary / Dashboard Data
app.get('/api/months/:monthId/summary', (req, res) => {
    const monthId = req.params.monthId;
    if (!checkMonthAccess(req.user.id, monthId)) return res.status(403).json({ error: 'Access denied' });

    const totalIncome = db.prepare('SELECT SUM(amount) as total FROM incomes WHERE month_id = ?').get(monthId).total || 0;
    const totalExpenses = db.prepare('SELECT SUM(amount) as total FROM expenses WHERE month_id = ?').get(monthId).total || 0;

    // Savings contributions (positive amounts in transactions linked to this month, EXCLUDING adjustments)
    const totalSavings = db.prepare('SELECT SUM(amount) as total FROM savings_transactions WHERE month_id = ? AND amount > 0 AND (is_adjustment = 0 OR is_adjustment IS NULL)').get(monthId).total || 0;

    res.json({
        income: totalIncome,
        expenses: totalExpenses,
        savings: totalSavings,
        balance: totalIncome - totalExpenses - totalSavings
    });
});

// Cumulative balance up to and including the specified month
app.get('/api/analytics/cumulative-balance', (req, res) => {
    const userId = req.user.id;
    const { year, month } = req.query;

    let months;
    if (year && month) {
        // Only months up to and including the specified year/month
        months = db.prepare(
            'SELECT id FROM months WHERE user_id = ? AND (year < ? OR (year = ? AND month <= ?))'
        ).all(userId, parseInt(year), parseInt(year), parseInt(month));
    } else {
        // Fallback: all months
        months = db.prepare('SELECT id FROM months WHERE user_id = ?').all(userId);
    }

    let totalIncome = 0;
    let totalExpenses = 0;
    for (const m of months) {
        totalIncome += db.prepare('SELECT SUM(amount) as total FROM incomes WHERE month_id = ?').get(m.id).total || 0;
        totalExpenses += db.prepare('SELECT SUM(amount) as total FROM expenses WHERE month_id = ?').get(m.id).total || 0;
    }

    res.json({
        cumulativeBalance: totalIncome - totalExpenses
    });
});

app.get('/api/analytics/trend', (req, res) => {
    // Get last 6 months for THIS USER
    const months = db.prepare('SELECT * FROM months WHERE user_id = ? ORDER BY year DESC, month DESC LIMIT 6').all(req.user.id).reverse();

    const data = months.map(m => {
        const income = db.prepare('SELECT SUM(amount) as total FROM incomes WHERE month_id = ?').get(m.id).total || 0;
        const expense = db.prepare('SELECT SUM(amount) as total FROM expenses WHERE month_id = ?').get(m.id).total || 0;
        // Savings contributions (positive amounts only, EXCLUDING adjustments)
        const savings = db.prepare('SELECT SUM(amount) as total FROM savings_transactions WHERE month_id = ? AND amount > 0 AND (is_adjustment = 0 OR is_adjustment IS NULL)').get(m.id).total || 0;
        
        return {
            month: `${m.month}/${m.year}`,
            income,
            expense,
            savings
        };
    });

    res.json(data);
});

// Serve static files from client/dist in production
const clientDistPath = path.join(__dirname, '..', 'client', 'dist');
app.use(express.static(clientDistPath));

// Handle SPA routing - return index.html for all non-API routes
app.get('/{*splat}', (req, res) => {
    // Skip API routes
    if (req.path.startsWith('/api/')) {
        return res.status(404).json({ error: 'API endpoint not found' });
    }
    res.sendFile(path.join(clientDistPath, 'index.html'));
});

app.use((err, req, res, next) => {
    logger.error('unhandled_request_error', {
        requestId: req.requestId,
        method: req.method,
        path: req.originalUrl,
        userId: req.user?.id ?? null,
        error: err,
    });

    if (res.headersSent) {
        return next(err);
    }

    return sendError(res, 500, 'Internal server error', 'internal_error');
});

// --- WebSocket Server ---
const server = http.createServer(app);
const wss = new WebSocketServer({ server });

// Track authenticated WebSocket connections
wss.on('connection', (ws, req) => {
    // Parse token from query string: ws://host:port?token=JWT
    const url = new URL(req.url, `http://${req.headers.host}`);
    const token = url.searchParams.get('token');

    if (!token) {
        ws.close(4001, 'Authentication required');
        return;
    }

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        if (decoded.type !== 'access') {
            ws.close(4003, 'Invalid token type');
            return;
        }
        ws.userId = decoded.id;
        ws.isAlive = true;
        logger.info('websocket_connected', { userId: decoded.id });
    } catch (err) {
        ws.close(4003, 'Invalid token');
        return;
    }

    ws.on('pong', () => { ws.isAlive = true; });
    ws.on('close', () => {
        logger.info('websocket_disconnected', { userId: ws.userId ?? null });
    });
});

// Heartbeat: ping every 30s, terminate dead connections
setInterval(() => {
    wss.clients.forEach(ws => {
        if (!ws.isAlive) return ws.terminate();
        ws.isAlive = false;
        ws.ping();
    });
}, 30000);

/**
 * Broadcast a data-change event to all authenticated clients of a given user.
 * @param {number} userId - The user whose clients should be notified
 * @param {string} entity - Entity type: 'income', 'expense', 'category', 'budget', 'savings_goal', 'savings_transaction', 'income_source'
 * @param {string} action - 'created' | 'updated' | 'deleted'
 * @param {number|null} senderId - Optional: ws that triggered the change (to skip echo). Not used for REST — all clients get notified.
 */
function broadcastChange(userId, entity, action) {
    const message = JSON.stringify({ type: 'data_changed', entity, action });
    wss.clients.forEach(ws => {
        if (ws.readyState === 1 && ws.userId === userId) {
            ws.send(message);
        }
    });
}

server.listen(PORT, () => {
    const mailConfig = getMailConfig();
    logger.info('server_started', {
        port: PORT,
        url: `http://localhost:${PORT}`,
        dbPath,
        env: process.env.NODE_ENV || 'development',
        mailConfigured: mailConfig.configured,
        mailFrom: mailConfig.from || null,
        appBaseUrl: mailConfig.appBaseUrl || null,
    });
});

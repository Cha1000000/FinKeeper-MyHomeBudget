const { db, nowIso } = require('./connection');
const logger = require('../logger');

const MAX_BACKUPS = 5;

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
            nowIso()
        );
    }

    return sendJsonResult(res, result);
}

function getOrCreateMonth(userId, year, month) {
    const row = db.prepare('SELECT id FROM months WHERE user_id = ? AND year = ? AND month = ?').get(userId, year, month);
    if (row) return row.id;
    const timestamp = nowIso();
    const info = db.prepare('INSERT INTO months (user_id, year, month, created_at, updated_at) VALUES (?, ?, ?, ?, ?)').run(userId, year, month, timestamp, timestamp);
    return info.lastInsertRowid;
}

function checkMonthAccess(userId, monthId) {
    const month = db.prepare('SELECT id FROM months WHERE id = ? AND user_id = ?').get(monthId, userId);
    return !!month;
}

function createBackup(userId) {
    try {
        const categories = db.prepare('SELECT * FROM categories WHERE user_id = ?').all(userId);
        const income_sources = db.prepare('SELECT * FROM income_sources WHERE user_id = ?').all(userId);
        const savings_goals = db.prepare('SELECT * FROM savings_goals WHERE user_id = ?').all(userId);
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

        db.prepare('INSERT INTO user_backups (user_id, data, created_at) VALUES (?, ?, ?)').run(userId, backupData, nowIso());

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

module.exports = {
    getRowById,
    recordDeletedRecord,
    clearDeletedRecord,
    executeIdempotent,
    getOrCreateMonth,
    checkMonthAccess,
    createBackup,
    parseBackupDataSafely,
    buildBackupSummary,
    buildBackupEntryPayload,
    listUserBackupEntries,
    getUserBackupRecord,
    restoreBackupSnapshot,
};

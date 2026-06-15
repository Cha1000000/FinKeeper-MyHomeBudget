// Юнит-тесты план-слоя (виртуальные запланированные платежи).
// БД — временный файл; db_setup.js создаёт базовую схему, connection.js допатчивает.
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const { test, beforeEach, after } = require('node:test');
const assert = require('node:assert/strict');

const dbPath = path.resolve(__dirname, '.planned-test.sqlite');

function cleanupDb() {
    for (const suffix of ['', '-wal', '-shm']) {
        fs.rmSync(`${dbPath}${suffix}`, { force: true });
    }
}

cleanupDb();
process.env.DB_PATH = dbPath;

const setupResult = spawnSync(process.execPath, [path.resolve(__dirname, '..', 'db_setup.js')], {
    env: { ...process.env, DB_PATH: dbPath },
    stdio: 'ignore',
});
if (setupResult.status !== 0) {
    throw new Error(`db_setup.js failed with code ${setupResult.status}`);
}

const { db, nowIso } = require('../db/connection');
const {
    autoCreateRecurringRecords,
    materializePlannedRecord,
    getPlannedRecords,
    computePlannedAggregates,
    createBackup,
    parseBackupDataSafely,
    restoreBackupSnapshot,
    getRowById,
} = require('../db/helpers');

after(() => {
    db.close();
    cleanupDb();
});

// «Сегодня» во всех тестах — 11 июня 2026
const NOW = new Date(2026, 5, 11);

let userId;

function createMonth(year, month) {
    const ts = nowIso();
    return db.prepare('INSERT INTO months (user_id, year, month, created_at, updated_at) VALUES (?, ?, ?, ?, ?)')
        .run(userId, year, month, ts, ts).lastInsertRowid;
}

function createFixedCategory({ name = 'Ипотека', amount = 45000, autoDay = 5, requireConfirm = 0, isActive = 1 } = {}) {
    const ts = nowIso();
    return db.prepare('INSERT INTO categories (user_id, name, sort_order, is_active, is_fixed, fixed_amount, auto_day, require_confirm, created_at, updated_at) VALUES (?, ?, 0, ?, 1, ?, ?, ?, ?, ?)')
        .run(userId, name, isActive, amount, autoDay, requireConfirm, ts, ts).lastInsertRowid;
}

function createFixedIncomeSource({ name = 'Зарплата', amount = 150000, autoDay = 25, requireConfirm = 0 } = {}) {
    const ts = nowIso();
    return db.prepare('INSERT INTO income_sources (user_id, name, sort_order, is_active, is_fixed, fixed_amount, auto_day, require_confirm, created_at, updated_at) VALUES (?, ?, 0, 1, 1, ?, ?, ?, ?, ?)')
        .run(userId, name, amount, autoDay, requireConfirm, ts, ts).lastInsertRowid;
}

function setOverride(templateType, templateId, monthId, { amount = null, day = null, skipped = 0 } = {}) {
    const ts = nowIso();
    db.prepare(`
        INSERT INTO planned_overrides (user_id, template_type, template_id, month_id, override_amount, override_day, is_skipped, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(user_id, template_type, template_id, month_id)
        DO UPDATE SET override_amount = excluded.override_amount, override_day = excluded.override_day, is_skipped = excluded.is_skipped, updated_at = excluded.updated_at
    `).run(userId, templateType, templateId, monthId, amount, day, skipped, ts, ts);
}

function countExpenses(monthId) {
    return db.prepare('SELECT COUNT(*) as c FROM expenses WHERE month_id = ?').get(monthId).c;
}

beforeEach(() => {
    // Чистый пользователь на каждый тест — изоляция без пересоздания БД
    const ts = nowIso();
    userId = db.prepare('INSERT INTO users (username, password_hash) VALUES (?, ?)')
        .run(`user_${Date.now()}_${Math.random().toString(36).slice(2)}`, 'x').lastInsertRowid;
});

test('виртуальный план: ненаступивший платёж текущего месяца попадает в план', () => {
    const catId = createFixedCategory({ autoDay: 20 });
    const monthId = createMonth(2026, 6);

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses.length, 1);
    assert.equal(planned.expenses[0].template_id, catId);
    assert.equal(planned.expenses[0].amount, 45000);
    assert.equal(planned.expenses[0].due_date, '2026-06-20');
    assert.equal(planned.expenses[0].is_overdue, 0);
});

test('наступивший платёж материализуется autoCreate и уходит из плана', () => {
    createFixedCategory({ autoDay: 5 });
    const monthId = createMonth(2026, 6);

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    assert.equal(countExpenses(monthId), 1);

    const expense = db.prepare('SELECT * FROM expenses WHERE month_id = ?').get(monthId);
    assert.equal(expense.date, '2026-06-05');
    assert.equal(expense.amount, 45000);

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses.length, 0);
});

test('идемпотентность: повторный autoCreate не создаёт дублей', () => {
    createFixedCategory({ autoDay: 5 });
    const monthId = createMonth(2026, 6);

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    autoCreateRecurringRecords(userId, monthId, null, NOW);
    assert.equal(countExpenses(monthId), 1);
});

test('удаление автосозданного платежа = пропуск месяца (не пересоздаётся, в план не возвращается)', () => {
    createFixedCategory({ autoDay: 5 });
    const monthId = createMonth(2026, 6);

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    db.prepare('DELETE FROM expenses WHERE month_id = ?').run(monthId);

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    assert.equal(countExpenses(monthId), 0);

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses.length, 0);
});

test('skip: пропущенный платёж не создаётся, виден в плане с is_skipped, не входит в агрегаты', () => {
    const catId = createFixedCategory({ autoDay: 5 });
    const monthId = createMonth(2026, 6);
    setOverride('category', catId, monthId, { skipped: 1 });

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    assert.equal(countExpenses(monthId), 0);

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses.length, 1);
    assert.equal(planned.expenses[0].is_skipped, 1);

    const totals = computePlannedAggregates(planned);
    assert.equal(totals.plannedExpenses, 0);
});

test('unskip: после снятия пропуска платёж создаётся', () => {
    const catId = createFixedCategory({ autoDay: 5 });
    const monthId = createMonth(2026, 6);
    setOverride('category', catId, monthId, { skipped: 1 });
    autoCreateRecurringRecords(userId, monthId, null, NOW);
    assert.equal(countExpenses(monthId), 0);

    setOverride('category', catId, monthId, { skipped: 0 });
    autoCreateRecurringRecords(userId, monthId, null, NOW);
    assert.equal(countExpenses(monthId), 1);
});

test('override: сумма и день применяются при материализации, amount_at_creation = override-сумма', () => {
    const catId = createFixedCategory({ autoDay: 20, amount: 45000 });
    const monthId = createMonth(2026, 6);
    setOverride('category', catId, monthId, { amount: 50000, day: 2 });

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    const expense = db.prepare('SELECT * FROM expenses WHERE month_id = ?').get(monthId);
    assert.equal(expense.amount, 50000);
    assert.equal(expense.date, '2026-06-02');

    const autoCreated = db.prepare('SELECT * FROM auto_created_records WHERE user_id = ? AND month_id = ?').get(userId, monthId);
    assert.equal(autoCreated.amount_at_creation, 50000);
});

test('override в плане: amount/due_day подменяются, original_amount сохраняется', () => {
    const catId = createFixedCategory({ autoDay: 20, amount: 45000 });
    const monthId = createMonth(2026, 6);
    setOverride('category', catId, monthId, { amount: 50000, day: 25 });

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses[0].amount, 50000);
    assert.equal(planned.expenses[0].original_amount, 45000);
    assert.equal(planned.expenses[0].due_day, 25);
    assert.equal(planned.expenses[0].is_overridden, 1);
});

test('require_confirm: автопостинг не происходит, платёж висит в плане как просроченный', () => {
    createFixedCategory({ autoDay: 5, requireConfirm: 1 });
    const monthId = createMonth(2026, 6);

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    assert.equal(countExpenses(monthId), 0);

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses.length, 1);
    assert.equal(planned.expenses[0].require_confirm, 1);
    assert.equal(planned.expenses[0].is_overdue, 1);
});

test('require_confirm в прошлом месяце: остаётся в плане как просроченный', () => {
    createFixedCategory({ autoDay: 5, requireConfirm: 1 });
    const monthId = createMonth(2026, 5);

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    assert.equal(countExpenses(monthId), 0);

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses.length, 1);
    assert.equal(planned.expenses[0].is_overdue, 1);
});

test('confirm: materializePlannedRecord создаёт запись, повтор бросает SQLITE_CONSTRAINT', () => {
    const catId = createFixedCategory({ autoDay: 5, requireConfirm: 1 });
    const monthId = createMonth(2026, 6);
    const month = getRowById('months', monthId);
    const template = getRowById('categories', catId);

    const { recordType, record } = materializePlannedRecord(userId, month, 'category', template, { amount: 45000, day: 11 });
    assert.equal(recordType, 'expense');
    assert.equal(record.date, '2026-06-11');
    assert.equal(countExpenses(monthId), 1);

    assert.throws(
        () => materializePlannedRecord(userId, month, 'category', template, { amount: 45000, day: 11 }),
        e => String(e.code).startsWith('SQLITE_CONSTRAINT')
    );
});

test('кламп: auto_day=31 в феврале создаётся в последний день', () => {
    createFixedCategory({ autoDay: 31 });
    const monthId = createMonth(2026, 2);

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    const expense = db.prepare('SELECT * FROM expenses WHERE month_id = ?').get(monthId);
    assert.equal(expense.date, '2026-02-28');
});

test('кламп в плане: auto_day=31 в сентябре (30 дней) — due_date 30-е', () => {
    createFixedCategory({ autoDay: 31 });
    const monthId = createMonth(2026, 9);

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses[0].due_date, '2026-09-30');
});

test('будущий месяц: autoCreate ничего не создаёт, план полный', () => {
    createFixedCategory({ autoDay: 5 });
    createFixedIncomeSource({ autoDay: 25 });
    const monthId = createMonth(2026, 7);

    autoCreateRecurringRecords(userId, monthId, null, NOW);
    assert.equal(countExpenses(monthId), 0);

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses.length, 1);
    assert.equal(planned.incomes.length, 1);
});

test('прошлый месяц: обычные платежи в план не попадают (их доберёт ensure)', () => {
    createFixedCategory({ autoDay: 5 });
    const monthId = createMonth(2026, 5);

    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses.length, 0);
});

test('агрегаты: суммируются расходы и доходы без скипнутых', () => {
    const cat1 = createFixedCategory({ name: 'Ипотека', autoDay: 20, amount: 45000 });
    const cat2 = createFixedCategory({ name: 'Интернет', autoDay: 25, amount: 900 });
    createFixedIncomeSource({ autoDay: 25, amount: 150000 });
    const monthId = createMonth(2026, 6);
    setOverride('category', cat2, monthId, { skipped: 1 });

    const totals = computePlannedAggregates(getPlannedRecords(userId, monthId, NOW));
    assert.equal(totals.plannedExpenses, 45000);
    assert.equal(totals.plannedIncomes, 150000);
});

test('деактивация категории убирает её из плана, реактивация возвращает вместе с override', () => {
    const catId = createFixedCategory({ autoDay: 20 });
    const monthId = createMonth(2026, 6);
    setOverride('category', catId, monthId, { amount: 50000 });

    db.prepare('UPDATE categories SET is_active = 0 WHERE id = ?').run(catId);
    assert.equal(getPlannedRecords(userId, monthId, NOW).expenses.length, 0);

    db.prepare('UPDATE categories SET is_active = 1 WHERE id = ?').run(catId);
    const planned = getPlannedRecords(userId, monthId, NOW);
    assert.equal(planned.expenses.length, 1);
    assert.equal(planned.expenses[0].amount, 50000);
});

test('backup/restore: planned_overrides и require_confirm восстанавливаются', () => {
    const catId = createFixedCategory({ autoDay: 20, requireConfirm: 1 });
    const monthId = createMonth(2026, 6);
    setOverride('category', catId, monthId, { amount: 50000, skipped: 1 });

    createBackup(userId);
    const backupRow = db.prepare('SELECT data FROM user_backups WHERE user_id = ? ORDER BY id DESC').get(userId);
    const backupData = parseBackupDataSafely(backupRow.data);
    assert.equal(backupData.planned_overrides.length, 1);

    db.prepare('DELETE FROM planned_overrides WHERE user_id = ?').run(userId);
    restoreBackupSnapshot(userId, backupData);

    const restored = db.prepare('SELECT * FROM planned_overrides WHERE user_id = ?').get(userId);
    assert.equal(restored.override_amount, 50000);
    assert.equal(restored.is_skipped, 1);
    assert.equal(getRowById('categories', catId).require_confirm, 1);
});

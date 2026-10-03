// Юнит-тесты итогов месяца (summary и график динамики): пополнения копилок
// дублируются скрытым расходом и не должны вычитаться из баланса дважды.
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const { test, beforeEach, after } = require('node:test');
const assert = require('node:assert/strict');

const dbPath = path.resolve(__dirname, '.month-totals-test.sqlite');

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
const { computeMonthTotals, SAVINGS_EXPENSE_CATEGORY } = require('../db/helpers');

after(() => {
    db.close();
    cleanupDb();
});

let userId;
let monthId;

beforeEach(() => {
    const ts = nowIso();
    userId = db.prepare('INSERT INTO users (username, password_hash) VALUES (?, ?)')
        .run(`user_${Date.now()}_${Math.random().toString(36).slice(2)}`, 'x').lastInsertRowid;
    monthId = db.prepare('INSERT INTO months (user_id, year, month, created_at, updated_at) VALUES (?, 2026, 10, ?, ?)')
        .run(userId, ts, ts).lastInsertRowid;
});

function createCategory(name, isActive = 1) {
    const ts = nowIso();
    return db.prepare('INSERT INTO categories (user_id, name, sort_order, is_active, created_at, updated_at) VALUES (?, ?, 0, ?, ?, ?)')
        .run(userId, name, isActive, ts, ts).lastInsertRowid;
}

function addIncome(amount) {
    const ts = nowIso();
    db.prepare('INSERT INTO incomes (month_id, source, amount, date, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)')
        .run(monthId, 'Зарплата', amount, '2026-10-01', ts, ts);
}

function addExpense(categoryId, amount) {
    const ts = nowIso();
    db.prepare('INSERT INTO expenses (month_id, category_id, amount, date, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)')
        .run(monthId, categoryId, amount, '2026-10-02', ts, ts);
}

function addSavingsTransaction(goalId, amount, isAdjustment = 0) {
    const ts = nowIso();
    db.prepare('INSERT INTO savings_transactions (goal_id, amount, date, month_id, is_adjustment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)')
        .run(goalId, amount, '2026-10-03', monthId, isAdjustment, ts, ts);
}

test('пустой месяц — все итоги нулевые', () => {
    assert.deepEqual(computeMonthTotals(monthId), {
        income: 0,
        expenses: 0,
        visibleExpenses: 0,
        savings: 0,
        balance: 0,
    });
});

test('пополнение копилки уменьшает баланс один раз и не попадает в видимые расходы', () => {
    const ts = nowIso();
    const goalId = db.prepare('INSERT INTO savings_goals (user_id, name, target_amount, current_amount, created_at, updated_at) VALUES (?, ?, 0, 0, ?, ?)')
        .run(userId, 'Отпуск', ts, ts).lastInsertRowid;
    const food = createCategory('Питание');
    const hidden = createCategory(SAVINGS_EXPENSE_CATEGORY, 0);

    addIncome(100000);
    addExpense(food, 30000);
    // Пополнение: транзакция + скрытый расход (как делает POST /savings_transactions)
    addSavingsTransaction(goalId, 10000);
    addExpense(hidden, 10000);
    // Снятие и корректировка скрытых расходов не создают и в пополнения не входят
    addSavingsTransaction(goalId, -3000);
    addSavingsTransaction(goalId, 5000, 1);

    assert.deepEqual(computeMonthTotals(monthId), {
        income: 100000,
        expenses: 40000,
        visibleExpenses: 30000,
        savings: 10000,
        balance: 60000,
    });
});

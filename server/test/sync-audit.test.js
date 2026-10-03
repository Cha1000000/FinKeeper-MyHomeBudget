// Интеграционные тесты серверных исправлений по аудиту синхронизации: поднимают сервер на
// временной БД и проверяют сценарии по HTTP (реактивация справочников, переименования,
// скрытые расходы копилок, запрет удаления непустой копилки, broadcast сортировки).
const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const Database = require('better-sqlite3');
const WebSocket = require('ws');

const serverPath = path.resolve(__dirname, '..', 'index.js');
const dbSetupPath = path.resolve(__dirname, '..', 'db_setup.js');
const port = process.env.SYNC_TEST_PORT || '3212';
const dbPath = path.resolve(__dirname, '.sync-audit-test.sqlite');
const baseUrl = `http://127.0.0.1:${port}`;
const SAVINGS_CATEGORY = 'Пополнение копилки';

function cleanupDb() {
    for (const suffix of ['', '-wal', '-shm']) {
        fs.rmSync(`${dbPath}${suffix}`, { force: true });
    }
}

let child;
let db;
let opCounter = 0;

async function waitForHealth(timeoutMs = 10000) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeoutMs) {
        try {
            const response = await fetch(`${baseUrl}/api/health`);
            if (response.ok) return;
        } catch (_) {
        }
        await new Promise(resolve => setTimeout(resolve, 200));
    }
    throw new Error('Health endpoint did not become ready in time');
}

before(async () => {
    cleanupDb();
    const setupResult = spawnSync(process.execPath, [dbSetupPath], {
        env: { ...process.env, DB_PATH: dbPath },
        stdio: 'ignore',
    });
    if (setupResult.status !== 0) {
        throw new Error(`db_setup.js failed with code ${setupResult.status}`);
    }

    db = new Database(dbPath);
    // Ключ идемпотентности старше 90 дней должен удалиться при старте сервера, свежий — остаться
    const ts = new Date().toISOString();
    const userId = db.prepare('INSERT INTO users (username, password_hash) VALUES (?, ?)').run('prune_user', 'x').lastInsertRowid;
    const insertKey = db.prepare('INSERT INTO idempotency_keys (user_id, operation_id, request_signature, response_status, response_body, created_at) VALUES (?, ?, ?, 200, ?, ?)');
    insertKey.run(userId, 'old-op', '{}', '{}', new Date(Date.now() - 91 * 24 * 60 * 60 * 1000).toISOString());
    insertKey.run(userId, 'fresh-op', '{}', '{}', ts);

    child = spawn(process.execPath, [serverPath], {
        env: {
            ...process.env,
            NODE_ENV: 'development',
            PORT: port,
            DB_PATH: dbPath,
            JWT_SECRET: 'sync-audit-test-secret',
            ALLOWED_ORIGINS: 'http://localhost:5174',
        },
        stdio: ['ignore', 'ignore', 'pipe'],
    });
    child.stderr.on('data', chunk => process.stderr.write(chunk));
    await waitForHealth();
});

after(async () => {
    if (child) {
        const exited = new Promise(resolve => child.on('exit', resolve));
        child.kill('SIGTERM');
        await exited;
    }
    db?.close();
    cleanupDb();
});

async function registerUser(prefix) {
    const response = await fetch(`${baseUrl}/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`, password: 'sync-pass-123' }),
    });
    const json = await response.json();
    assert.equal(response.status, 200, JSON.stringify(json));
    return createClient(json.token);
}

function createClient(token) {
    const call = async (method, route, body, { expect = 200, opId } = {}) => {
        const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
        if (opId !== null && method !== 'GET') headers['X-Operation-Id'] = opId ?? `sync-audit-${++opCounter}`;
        const response = await fetch(`${baseUrl}/api${route}`, {
            method,
            headers,
            body: body !== undefined ? JSON.stringify(body) : undefined,
        });
        const json = await response.json().catch(() => null);
        assert.equal(response.status, expect, `${method} ${route}: ${JSON.stringify(json)}`);
        return json;
    };
    return { token, call };
}

async function ensureMonth(client, year = 2026, month = 5) {
    return client.call('POST', '/months/ensure', { year, month });
}

async function tombstones(client, entityType) {
    return client.call('GET', `/deleted_records?entity_type=${entityType}`);
}

test('старые ключи идемпотентности удаляются при старте сервера', () => {
    const keys = db.prepare('SELECT operation_id FROM idempotency_keys ORDER BY operation_id').all().map(row => row.operation_id);
    assert.ok(!keys.includes('old-op'), 'ключ старше 90 дней удалён');
    assert.ok(keys.includes('fresh-op'), 'свежий ключ сохранён');
});

test('повторное создание удалённой категории восстанавливает её вместе с историей', async () => {
    const client = await registerUser('cat_reactivate');
    const month = await ensureMonth(client);
    const food = await client.call('POST', '/categories', { name: 'Еда тест' });
    const other = await client.call('POST', '/categories', { name: 'Прочее тест' });
    const expense = await client.call('POST', '/expenses', { month_id: month.id, category_id: food.id, amount: 500, date: '2026-05-10', comment: '' });

    await client.call('DELETE', `/categories/${food.id}`);
    assert.ok((await tombstones(client, 'category')).some(row => row.entity_id === food.id), 'tombstone категории записан');

    const restored = await client.call('POST', '/categories', { name: 'Еда тест', is_fixed: 1, fixed_amount: 3000, auto_day: 5 });
    assert.equal(restored.id, food.id, 'та же запись, а не новая');
    assert.equal(restored.is_active, 1);
    assert.equal(restored.is_fixed, 1);
    assert.equal(restored.fixed_amount, 3000);
    assert.equal(restored.auto_day, 5);
    assert.ok(restored.sort_order > other.sort_order, 'восстановленная встаёт в конец списка');
    assert.ok(restored.updated_at > food.updated_at, 'updated_at сдвинут — клиенты подтянут изменение');
    assert.ok(!(await tombstones(client, 'category')).some(row => row.entity_id === food.id), 'tombstone снят');

    const expenses = await client.call('GET', `/months/${month.id}/expenses`);
    assert.ok(expenses.some(row => row.id === expense.id && row.category_id === food.id), 'расходы категории на месте');

    // Активная категория с тем же именем по-прежнему просто возвращается
    const again = await client.call('POST', '/categories', { name: 'Еда тест' });
    assert.equal(again.id, food.id);
    assert.equal(again.is_fixed, 1, 'повторный POST активной категории её не меняет');
});

test('правка удалённой категории без is_active не воскрешает её: удаление побеждает', async () => {
    const client = await registerUser('cat_delete_wins');
    const cat = await client.call('POST', '/categories', { name: 'Удаляемая' });
    await client.call('DELETE', `/categories/${cat.id}`);

    // Офлайн-правка с другого устройства приходит после удаления и is_active не шлёт
    const edited = await client.call('PUT', `/categories/${cat.id}`, { name: 'Удаляемая+', is_fixed: 0 });
    assert.equal(edited.is_active, 0, 'категория остаётся удалённой');
    assert.ok((await tombstones(client, 'category')).some(row => row.entity_id === cat.id), 'tombstone не снят');

    // Явное восстановление (is_active = 1) снимает tombstone
    const restored = await client.call('PUT', `/categories/${cat.id}`, { is_active: 1 });
    assert.equal(restored.is_active, 1);
    assert.ok(!(await tombstones(client, 'category')).some(row => row.entity_id === cat.id), 'tombstone снят');
});

test('имя скрытой категории копилок зарезервировано, конфликт имён при переименовании — 409', async () => {
    const client = await registerUser('cat_reserved');
    const month = await ensureMonth(client);
    const goal = await client.call('POST', '/savings_goals', { name: 'Цель', target_amount: 1000 });
    await client.call('POST', '/savings_transactions', { goal_id: goal.id, amount: 100, date: '2026-05-01', month_id: month.id });

    const reserved = await client.call('POST', '/categories', { name: SAVINGS_CATEGORY }, { expect: 400 });
    assert.match(reserved.error, /зарезервировано/);
    await client.call('POST', '/categories', { name: `  ${SAVINGS_CATEGORY} ` }, { expect: 400 });
    await client.call('POST', '/categories', { name: '   ' }, { expect: 400 });
    await client.call('POST', '/categories', {}, { expect: 400 });

    const hidden = (await client.call('GET', '/categories?include_inactive=1')).find(row => row.name === SAVINGS_CATEGORY);
    assert.equal(hidden.is_active, 0, 'скрытая категория не «ожила»');

    const a = await client.call('POST', '/categories', { name: 'Кафе' });
    await client.call('POST', '/categories', { name: 'Такси' });
    await client.call('PUT', `/categories/${a.id}`, { name: SAVINGS_CATEGORY }, { expect: 400 });
    const conflict = await client.call('PUT', `/categories/${a.id}`, { name: 'Такси' }, { expect: 409 });
    assert.match(conflict.error, /уже существует/);
    await client.call('PUT', `/categories/${a.id}`, { name: '' }, { expect: 400 });
    await client.call('PUT', '/categories/999999', { name: 'Нет' }, { expect: 404 });

    // Сохранение без смены имени (например, правка фиксированных полей) проходит
    const same = await client.call('PUT', `/categories/${a.id}`, { name: 'Кафе', is_fixed: 0 });
    assert.equal(same.name, 'Кафе');
    const renamed = await client.call('PUT', `/categories/${a.id}`, { name: 'Кафе и рестораны' });
    assert.equal(renamed.name, 'Кафе и рестораны');
});

test('источник дохода: реактивация и переименование вместе с доходами', async () => {
    const client = await registerUser('src_rename');
    const stranger = await registerUser('src_stranger');
    const month = await ensureMonth(client);
    const strangerMonth = await ensureMonth(stranger);

    const source = await client.call('POST', '/income_sources', { name: 'Фриланс' });
    await stranger.call('POST', '/income_sources', { name: 'Фриланс' });
    const income = await client.call('POST', '/incomes', { month_id: month.id, source: 'Фриланс', amount: 10000, date: '2026-05-03' });
    const strangerIncome = await stranger.call('POST', '/incomes', { month_id: strangerMonth.id, source: 'Фриланс', amount: 7000, date: '2026-05-03' });

    await client.call('DELETE', `/income_sources/${source.id}`);
    const restored = await client.call('POST', '/income_sources', { name: 'Фриланс' });
    assert.equal(restored.id, source.id);
    assert.equal(restored.is_active, 1);
    assert.ok(!(await tombstones(client, 'income_source')).some(row => row.entity_id === source.id));

    await client.call('POST', '/income_sources', { name: 'Подработка' });
    await client.call('PUT', `/income_sources/${source.id}`, { name: 'Подработка' }, { expect: 409 });

    const renamed = await client.call('PUT', `/income_sources/${source.id}`, { name: 'Проекты' });
    assert.equal(renamed.name, 'Проекты');
    const incomes = await client.call('GET', `/months/${month.id}/incomes`);
    const updatedIncome = incomes.find(row => row.id === income.id);
    assert.equal(updatedIncome.source, 'Проекты', 'доход переименован вместе с источником');
    assert.ok(updatedIncome.updated_at > income.updated_at, 'updated_at дохода сдвинут — клиенты подтянут');

    const strangerIncomes = await stranger.call('GET', `/months/${strangerMonth.id}/incomes`);
    assert.equal(strangerIncomes.find(row => row.id === strangerIncome.id).source, 'Фриланс', 'чужие доходы не тронуты');
});

test('переименование копилки переписывает скрытые расходы, удаление пополнения убирает их с tombstone', async () => {
    const client = await registerUser('goal_rename');
    const month = await ensureMonth(client);
    await client.call('POST', '/incomes', { month_id: month.id, source: 'Зарплата', amount: 50000, date: '2026-05-01' });
    const goal = await client.call('POST', '/savings_goals', { name: 'Отпуск', target_amount: 100000 });
    const deposit = await client.call('POST', '/savings_transactions', { goal_id: goal.id, amount: 3000, date: '2026-05-05', month_id: month.id });

    let summary = await client.call('GET', `/months/${month.id}/summary`);
    assert.equal(summary.balance, 47000);

    await client.call('PUT', `/savings_goals/${goal.id}`, { name: 'Море' });
    let hidden = (await client.call('GET', `/months/${month.id}/expenses`)).filter(row => row.category_name === SAVINGS_CATEGORY);
    assert.equal(hidden.length, 1);
    assert.equal(hidden[0].comment, 'Пополнение копилки "Море"');

    await client.call('DELETE', `/savings_transactions/${deposit.id}`);
    hidden = (await client.call('GET', `/months/${month.id}/expenses`)).filter(row => row.category_name === SAVINGS_CATEGORY);
    assert.equal(hidden.length, 0, 'скрытый расход удалён');
    const expenseTombstones = await tombstones(client, 'expense');
    assert.equal(expenseTombstones.length, 1, 'tombstone скрытого расхода записан');

    summary = await client.call('GET', `/months/${month.id}/summary`);
    assert.equal(summary.balance, 50000, 'деньги вернулись в «Свободно»');
});

test('запасной поиск находит скрытый расход со старым именем, но не трогает чужие копилки', async () => {
    const client = await registerUser('goal_legacy');
    const month = await ensureMonth(client);
    const goalA = await client.call('POST', '/savings_goals', { name: 'Новое имя', target_amount: 0 });
    const goalB = await client.call('POST', '/savings_goals', { name: 'Машина', target_amount: 0 });
    // Одинаковые сумма и дата у двух копилок
    const depositA = await client.call('POST', '/savings_transactions', { goal_id: goalA.id, amount: 1000, date: '2026-05-07', month_id: month.id });
    await client.call('POST', '/savings_transactions', { goal_id: goalB.id, amount: 1000, date: '2026-05-07', month_id: month.id });

    // Имитируем переименование, сделанное старой версией сервера: комментарий остался прежним
    const hiddenA = db.prepare('SELECT id FROM expenses WHERE comment = ?').get('Пополнение копилки "Новое имя"');
    db.prepare('UPDATE expenses SET comment = ? WHERE id = ?').run('Пополнение копилки "Старое имя"', hiddenA.id);

    await client.call('DELETE', `/savings_transactions/${depositA.id}`);
    const hidden = (await client.call('GET', `/months/${month.id}/expenses`)).filter(row => row.category_name === SAVINGS_CATEGORY);
    assert.deepEqual(hidden.map(row => row.comment), ['Пополнение копилки "Машина"'], 'удалён расход со старым именем, расход «Машины» на месте');
    assert.ok((await tombstones(client, 'expense')).some(row => row.entity_id === hiddenA.id));
});

test('правка пополнения пересоздаёт скрытый расход и пишет tombstone старого', async () => {
    const client = await registerUser('tx_edit');
    const month = await ensureMonth(client);
    const goal = await client.call('POST', '/savings_goals', { name: 'Ремонт', target_amount: 0 });
    const deposit = await client.call('POST', '/savings_transactions', { goal_id: goal.id, amount: 2000, date: '2026-05-08', month_id: month.id });
    const before = (await client.call('GET', `/months/${month.id}/expenses`)).find(row => row.category_name === SAVINGS_CATEGORY);

    await client.call('PUT', `/savings_transactions/${deposit.id}`, { amount: 2500 });
    const hidden = (await client.call('GET', `/months/${month.id}/expenses`)).filter(row => row.category_name === SAVINGS_CATEGORY);
    assert.equal(hidden.length, 1);
    assert.equal(hidden[0].amount, 2500);
    assert.ok((await tombstones(client, 'expense')).some(row => row.entity_id === before.id), 'tombstone старого скрытого расхода');

    const goals = await client.call('GET', '/savings_goals');
    assert.equal(goals.find(row => row.id === goal.id).current_amount, 2500);

    // Клиент шлёт month_id: null явно — это «не передано», пополнение остаётся в своём месяце
    const kept = await client.call('PUT', `/savings_transactions/${deposit.id}`, { amount: 3000, month_id: null });
    assert.equal(kept.month_id, month.id);
    const hiddenAfter = (await client.call('GET', `/months/${month.id}/expenses`)).filter(row => row.category_name === SAVINGS_CATEGORY);
    assert.equal(hiddenAfter.length, 1, 'скрытый расход остался в месяце');
    assert.equal(hiddenAfter[0].amount, 3000);
});

test('копилку с ненулевым балансом удалить нельзя, пустую — можно', async () => {
    const client = await registerUser('goal_delete');
    const month = await ensureMonth(client);
    const goal = await client.call('POST', '/savings_goals', { name: 'Подушка', target_amount: 0 });
    const deposit = await client.call('POST', '/savings_transactions', { goal_id: goal.id, amount: 5000, date: '2026-05-09', month_id: month.id });

    const refused = await client.call('DELETE', `/savings_goals/${goal.id}`, undefined, { expect: 409 });
    assert.equal(refused.code, 'SAVINGS_GOAL_NOT_EMPTY');
    assert.ok((await client.call('GET', '/savings_goals')).some(row => row.id === goal.id), 'копилка на месте');
    assert.ok(!(await tombstones(client, 'savings_goal')).some(row => row.entity_id === goal.id), 'tombstone не записан');

    // Отказ не кэшируется по ключу операции: после вывода средств тот же ключ проходит
    await client.call('DELETE', `/savings_goals/${goal.id}`, undefined, { expect: 409, opId: 'goal-delete-op' });
    await client.call('POST', '/savings_transactions', { goal_id: goal.id, amount: -5000, date: '2026-05-10', month_id: month.id });
    await client.call('DELETE', `/savings_goals/${goal.id}`, undefined, { opId: 'goal-delete-op' });

    assert.ok(!(await client.call('GET', '/savings_goals')).some(row => row.id === goal.id), 'пустая копилка удалена');
    const goalTombstones = await tombstones(client, 'savings_goal');
    assert.ok(goalTombstones.some(row => row.entity_id === goal.id));
    const txTombstones = await tombstones(client, 'savings_transaction');
    assert.ok(txTombstones.some(row => row.entity_id === deposit.id), 'tombstone транзакций копилки');

    // Обнуление редактированием суммы тоже разрешает удаление
    const second = await client.call('POST', '/savings_goals', { name: 'Вторая', target_amount: 0 });
    await client.call('PUT', `/savings_goals/${second.id}`, { current_amount: 1200 });
    // null в сумме = «не менять» (клиент шлёт сумму, только когда пользователь её правил)
    const renamed = await client.call('PUT', `/savings_goals/${second.id}`, { name: 'Вторая+', target_amount: null, current_amount: null });
    assert.equal(renamed.current_amount, 1200);
    assert.equal(renamed.target_amount, 0);
    await client.call('DELETE', `/savings_goals/${second.id}`, undefined, { expect: 409 });
    await client.call('PUT', `/savings_goals/${second.id}`, { current_amount: 0 });
    await client.call('DELETE', `/savings_goals/${second.id}`);
});

test('404 на правку несуществующей записи помечен RECORD_NOT_FOUND, на связанную — нет', async () => {
    const client = await registerUser('not_found');
    const month = await ensureMonth(client);
    const [category] = await client.call('GET', '/categories');
    const goal = await client.call('POST', '/savings_goals', { name: 'Цель', target_amount: 0 });
    const deposit = await client.call('POST', '/savings_transactions', { goal_id: goal.id, amount: 100, date: '2026-05-11', month_id: month.id });
    const expense = await client.call('POST', '/expenses', { month_id: month.id, category_id: category.id, amount: 10, date: '2026-05-11' });
    await client.call('DELETE', `/expenses/${expense.id}`);

    // Нет самой записи: клиент удалит её и у себя
    for (const route of [`/expenses/${expense.id}`, '/incomes/999999', '/savings_goals/999999', '/savings_transactions/999999']) {
        const body = await client.call('PUT', route, { amount: 1, name: 'x' }, { expect: 404 });
        assert.equal(body.code, 'RECORD_NOT_FOUND', route);
    }
    // Нет целевой копилки пополнения: сама транзакция жива, удалять её нельзя
    const missingGoal = await client.call('PUT', `/savings_transactions/${deposit.id}`, { goal_id: 999999 }, { expect: 404 });
    assert.notEqual(missingGoal.code, 'RECORD_NOT_FOUND');
});

test('сортировка категорий и источников рассылает уведомление по WebSocket', async () => {
    const client = await registerUser('reorder_ws');
    const a = await client.call('POST', '/categories', { name: 'A' });
    const b = await client.call('POST', '/categories', { name: 'B' });
    const s1 = await client.call('POST', '/income_sources', { name: 'S1' });
    const s2 = await client.call('POST', '/income_sources', { name: 'S2' });

    const ws = new WebSocket(`ws://127.0.0.1:${port}/?token=${client.token}`);
    const messages = [];
    ws.on('message', data => messages.push(JSON.parse(String(data))));
    await new Promise((resolve, reject) => {
        ws.once('open', resolve);
        ws.once('error', reject);
    });

    try {
        await client.call('PUT', '/categories/reorder', { ids: [b.id, a.id] });
        await client.call('PUT', '/income_sources/reorder', { ids: [s2.id, s1.id] });
        const deadline = Date.now() + 3000;
        while (messages.length < 2 && Date.now() < deadline) {
            await new Promise(resolve => setTimeout(resolve, 50));
        }
        assert.ok(messages.some(m => m.type === 'data_changed' && m.entity === 'category'), 'broadcast категорий');
        assert.ok(messages.some(m => m.type === 'data_changed' && m.entity === 'income_source'), 'broadcast источников');
    } finally {
        ws.close();
    }

    const categories = await client.call('GET', '/categories');
    assert.deepEqual(categories.filter(row => [a.id, b.id].includes(row.id)).map(row => row.id), [b.id, a.id]);
});

test('повтор ключа операции с другим телом: 409 с исходным ответом', async () => {
    const client = await registerUser('idem_reuse');
    const month = await ensureMonth(client);
    const category = await client.call('POST', '/categories', { name: 'Продукты' });
    const body = { month_id: month.id, category_id: category.id, amount: 100, date: '2026-05-11', comment: '' };

    const created = await client.call('POST', '/expenses', body, { opId: 'expense-op-1' });
    const replay = await client.call('POST', '/expenses', body, { opId: 'expense-op-1' });
    assert.equal(replay.id, created.id, 'точный повтор — тот же ответ');

    const reused = await client.call('POST', '/expenses', { ...body, amount: 150 }, { opId: 'expense-op-1', expect: 409 });
    assert.equal(reused.code, 'IDEMPOTENCY_KEY_REUSED');
    assert.equal(reused.original_status, 200);
    assert.equal(reused.original_response.id, created.id);
    assert.equal(reused.original_response.amount, 100);

    const expenses = await client.call('GET', `/months/${month.id}/expenses`);
    assert.equal(expenses.length, 1, 'дубль не создан');
});

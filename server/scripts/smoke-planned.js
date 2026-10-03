// Интеграционный smoke план-слоя: поднимает сервер на временной БД и гоняет
// сценарии по HTTP (планы, skip/override, confirm, идемпотентность, summary).
const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');

const serverPath = path.resolve(__dirname, '..', 'index.js');
const dbSetupPath = path.resolve(__dirname, '..', 'db_setup.js');
const port = process.env.SMOKE_PORT || '3211';
const dbPath = path.resolve(__dirname, '..', '.smoke-planned.sqlite');
const baseUrl = `http://127.0.0.1:${port}`;

function cleanupDb() {
    for (const suffix of ['', '-wal', '-shm']) {
        fs.rmSync(`${dbPath}${suffix}`, { force: true });
    }
}

process.on('exit', cleanupDb);
process.on('SIGINT', () => process.exit(130));
process.on('SIGTERM', () => process.exit(143));

function assert(condition, message) {
    if (!condition) {
        throw new Error(`Assertion failed: ${message}`);
    }
}

async function waitForHealth(timeoutMs = 10000) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeoutMs) {
        try {
            const response = await fetch(`${baseUrl}/api/health`);
            if (response.ok) return;
        } catch (_) {
        }
        await new Promise(resolve => setTimeout(resolve, 250));
    }
    throw new Error('Health endpoint did not become ready in time');
}

async function main() {
    cleanupDb();

    const setupResult = spawnSync(process.execPath, [dbSetupPath], {
        env: { ...process.env, DB_PATH: dbPath },
        stdio: 'ignore',
    });
    if (setupResult.status !== 0) {
        throw new Error(`db_setup.js failed with code ${setupResult.status}`);
    }

    const child = spawn(process.execPath, [serverPath], {
        env: {
            ...process.env,
            NODE_ENV: 'development',
            PORT: port,
            DB_PATH: dbPath,
            JWT_SECRET: process.env.JWT_SECRET || 'smoke-test-secret',
            ALLOWED_ORIGINS: process.env.ALLOWED_ORIGINS || 'http://localhost:5174',
        },
        stdio: ['ignore', 'ignore', 'pipe'],
    });
    child.stderr.on('data', chunk => process.stderr.write(chunk));

    try {
        try {
            await waitForHealth();

            let token;
            let opCounter = 0;
            const api = async (method, route, body, { opId, expect = 200 } = {}) => {
                const headers = { 'Content-Type': 'application/json' };
                if (token) headers['Authorization'] = `Bearer ${token}`;
                if (opId) headers['X-Idempotency-Key'] = opId;
                const response = await fetch(`${baseUrl}/api${route}`, {
                    method,
                    headers,
                    body: body !== undefined ? JSON.stringify(body) : undefined,
                });
                const json = await response.json().catch(() => null);
                assert(response.status === expect, `${method} ${route}: expected ${expect}, got ${response.status} ${JSON.stringify(json)}`);
                return json;
            };
            // Мутации — всегда со свежим ключом идемпотентности (кроме явных повторов)
            const mutate = (method, route, body, options = {}) =>
                api(method, route, body, { opId: options.opId ?? `smoke-planned-${++opCounter}`, ...options });

            const auth = await api('POST', '/auth/register', { username: `smoke_planned_${Date.now()}`, password: 'smoke-pass-123' });
            token = auth.token;
            assert(token, 'register returns token');

            const now = new Date();
            const curYear = now.getFullYear();
            const curMonth = now.getMonth() + 1;
            const nextDate = new Date(curYear, curMonth, 1); // 1-е следующего месяца
            const nextYear = nextDate.getFullYear();
            const nextMonth = nextDate.getMonth() + 1;

            // --- Шаблоны ---
            const mortgage = await mutate('POST', '/categories', { name: 'Ипотека', is_fixed: 1, fixed_amount: 45000, auto_day: 1 });
            assert(mortgage.require_confirm === 0, 'require_confirm по умолчанию 0');
            const school = await mutate('POST', '/categories', { name: 'Школа', is_fixed: 1, fixed_amount: 8000, auto_day: 1, require_confirm: 1 });
            assert(school.require_confirm === 1, 'require_confirm сохраняется');
            const salary = await mutate('POST', '/income_sources', { name: 'Зарплата', is_fixed: 1, fixed_amount: 150000, auto_day: 1 });
            assert(salary.is_fixed === 1, 'фиксированный источник создан');

            // --- Текущий месяц: auto_day=1 наступил — обычная категория материализуется, require_confirm ждёт ---
            const curMonthRow = await mutate('POST', '/months/ensure', { year: curYear, month: curMonth });
            const curExpenses = await api('GET', `/months/${curMonthRow.id}/expenses`);
            assert(curExpenses.length === 1 && curExpenses[0].category_id === mortgage.id, 'ипотека материализована при ensure');

            let curPlanned = await api('GET', `/months/${curMonthRow.id}/planned`);
            assert(curPlanned.expenses.length === 1 && curPlanned.expenses[0].template_id === school.id, 'школа (require_confirm) висит в плане');
            assert(curPlanned.expenses[0].require_confirm === 1, 'флаг require_confirm в плане');

            // --- PUT на материализованный платёж → 409 ---
            await mutate('PUT', `/months/${curMonthRow.id}/planned/category/${mortgage.id}`, { is_skipped: 1 }, { expect: 409 });

            // --- Confirm: идемпотентность и защита от дублей ---
            const confirmKey = 'smoke-planned-confirm-1';
            const confirmed = await api('POST', `/months/${curMonthRow.id}/planned/category/${school.id}/confirm`, {}, { opId: confirmKey });
            assert(confirmed.amount === 8000 && confirmed.category_id === school.id, 'confirm создал расход');

            const confirmedRepeat = await api('POST', `/months/${curMonthRow.id}/planned/category/${school.id}/confirm`, {}, { opId: confirmKey });
            assert(confirmedRepeat.id === confirmed.id, 'повтор с тем же ключом — закэшированный ответ');

            await mutate('POST', `/months/${curMonthRow.id}/planned/category/${school.id}/confirm`, {}, { expect: 409 });

            const curExpensesAfter = await api('GET', `/months/${curMonthRow.id}/expenses`);
            assert(curExpensesAfter.length === 2, 'после confirm ровно 2 расхода');

            // --- Следующий месяц: полный виртуальный план, ничего не материализовано ---
            const nextMonthRow = await mutate('POST', '/months/ensure', { year: nextYear, month: nextMonth });
            const nextExpenses = await api('GET', `/months/${nextMonthRow.id}/expenses`);
            assert(nextExpenses.length === 0, 'будущий месяц: расходы не созданы');

            let nextPlanned = await api('GET', `/months/${nextMonthRow.id}/planned`);
            assert(nextPlanned.expenses.length === 2, 'будущий месяц: обе категории в плане');
            assert(nextPlanned.incomes.length === 1, 'будущий месяц: доход в плане');
            assert(nextPlanned.totals.plannedExpenses === 53000, 'totals.plannedExpenses = 45000 + 8000');
            assert(nextPlanned.totals.plannedIncomes === 150000, 'totals.plannedIncomes = 150000');

            // --- Skip + override ---
            const skipResult = await mutate('PUT', `/months/${nextMonthRow.id}/planned/category/${mortgage.id}`, { is_skipped: 1 });
            assert(skipResult.item.is_skipped === 1, 'skip применился');

            const overrideResult = await mutate('PUT', `/months/${nextMonthRow.id}/planned/category/${school.id}`, { override_amount: 10000, override_day: 15 });
            assert(overrideResult.item.amount === 10000 && overrideResult.item.due_day === 15, 'override применился');
            assert(overrideResult.item.original_amount === 8000, 'original_amount сохранён');

            // --- Summary с прогнозом ---
            const summary = await api('GET', `/months/${nextMonthRow.id}/summary`);
            assert(summary.plannedExpenses === 10000, 'скипнутая ипотека не в плане, школа по override');
            assert(summary.plannedIncomes === 150000, 'плановые доходы в summary');
            assert(summary.forecastExpenses === summary.expenses + summary.plannedExpenses, 'forecastExpenses');
            assert(summary.forecastBalance === summary.balance + summary.plannedIncomes - summary.plannedExpenses, 'forecastBalance');

            // --- Сброс override ---
            const resetResult = await mutate('DELETE', `/months/${nextMonthRow.id}/planned/category/${school.id}/override`);
            assert(resetResult.item.amount === 8000 && resetResult.item.is_overridden === 0, 'override сброшен');

            // --- Unskip через PUT с is_skipped=0 (строка-исключение удаляется) ---
            const unskipResult = await mutate('PUT', `/months/${nextMonthRow.id}/planned/category/${mortgage.id}`, { is_skipped: 0 });
            assert(unskipResult.item.is_skipped === 0, 'unskip применился');

            // --- planned-state: сырые данные для KMP-синка ---
            const plannedState = await api('GET', `/months/${nextMonthRow.id}/planned-state`);
            assert(Array.isArray(plannedState.auto_created) && plannedState.auto_created.length === 0, 'будущий месяц: нет материализованных');
            assert(plannedState.overrides.length === 0, 'после unskip и сброса overrides пусты');

            const curState = await api('GET', `/months/${curMonthRow.id}/planned-state`);
            assert(curState.auto_created.length === 3, 'текущий месяц: ипотека (авто) + школа (confirm) + зарплата (авто) в auto_created');
            assert(curState.auto_created.filter(r => r.template_type === 'category' && r.created_record_type === 'expense').length === 2, 'два расхода в auto_created');
            assert(curState.auto_created.filter(r => r.template_type === 'income_source' && r.created_record_type === 'income').length === 1, 'один доход в auto_created');

            // --- Копилка: пополнение уменьшает баланс один раз ---
            const goal = await mutate('POST', '/savings_goals', { name: 'Отпуск', target_amount: 100000 });
            await mutate('POST', '/savings_transactions', { goal_id: goal.id, amount: 7000, date: `${curYear}-${String(curMonth).padStart(2, '0')}-01`, month_id: curMonthRow.id });
            await mutate('POST', '/savings_transactions', { goal_id: goal.id, amount: -2000, date: `${curYear}-${String(curMonth).padStart(2, '0')}-01`, month_id: curMonthRow.id });
            const curSummary = await api('GET', `/months/${curMonthRow.id}/summary`);
            assert(curSummary.income === 150000, 'summary.income: зарплата');
            assert(curSummary.expenses === 45000 + 8000 + 7000, 'summary.expenses включает скрытое пополнение');
            assert(curSummary.savings === 7000, 'summary.savings: только пополнение, без снятия');
            assert(curSummary.balance === 150000 - 60000, 'summary.balance: пополнение вычтено один раз');
            const trend = await api('GET', '/analytics/trend');
            const curTrend = trend.find(item => item.month === `${curMonth}/${curYear}`);
            assert(curTrend, 'текущий месяц есть в графике динамики');
            assert(curTrend.expense === 45000 + 8000, 'trend.expense без скрытых пополнений');
            assert(curTrend.savings === 7000, 'trend.savings: пополнение');

            // --- Валидации ---
            await mutate('PUT', `/months/${nextMonthRow.id}/planned/category/${school.id}`, { override_amount: -5 }, { expect: 400 });
            await mutate('PUT', `/months/${nextMonthRow.id}/planned/badtype/${school.id}`, { is_skipped: 1 }, { expect: 400 });
            await mutate('PUT', `/months/${nextMonthRow.id}/planned/category/999999`, { is_skipped: 1 }, { expect: 404 });
            await mutate('POST', `/months/${nextMonthRow.id}/planned/category/${school.id}/confirm`, { date: '2000-01-01' }, { expect: 400 });

            console.log('Planned smoke test passed');
        } finally {
            child.kill('SIGTERM');
        }

        await new Promise((resolve, reject) => {
            child.on('exit', code => {
                if (code === 0 || code === null) {
                    resolve();
                    return;
                }
                reject(new Error(`Server exited with code ${code}`));
            });
        });
    } finally {
        cleanupDb();
    }
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});

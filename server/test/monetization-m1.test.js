// Интеграционные TDD-тесты Вехи 1 монетизации (Семейный PRO):
// 1. Entitlements по умолчанию (Free) и выдача в /api/auth/register, /api/auth/login, /api/auth/me
// 2. Активация 14-дневного триала (POST /api/billing/start-trial) + запрет повторной активации + WebSocket пуш
// 3. Активация промокода (POST /api/billing/redeem-promo) + защита от повторного ввода
// 4. Лимит активных устройств: в Free — мягкое вытеснение 3-м устройством самой старой сессии; в PRO — безлимит + список и удаление сессий
// 5. Расширенные бэкапы: в Free ротация до 5, в PRO до 30 + закрепление (is_pinned) защищает от ротации + экспорт/импорт .json
// 6. Глубокая аналитика (12/24 мес + /api/analytics/insights с Burn Rate и MoM) и экспорт отчётов (.xlsx и .csv) с защитой PRO_REQUIRED
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { spawn, spawnSync } = require('child_process');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const Database = require('better-sqlite3');
const WebSocket = require('ws');

const serverPath = path.resolve(__dirname, '..', 'index.js');
const dbSetupPath = path.resolve(__dirname, '..', 'db_setup.js');
const port = process.env.MONETIZATION_TEST_PORT || '3215';
const dbPath = path.resolve(__dirname, '.monetization-m1-test.sqlite');
const baseUrl = `http://127.0.0.1:${port}`;

function cleanupDb() {
    for (const suffix of ['', '-wal', '-shm']) {
        fs.rmSync(`${dbPath}${suffix}`, { force: true });
    }
}

let child;
let db;

async function waitForHealth(timeoutMs = 10000) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeoutMs) {
        try {
            const response = await fetch(`${baseUrl}/api/health`);
            if (response.ok) return;
        } catch (_) {
            // wait
        }
        await new Promise(resolve => setTimeout(resolve, 150));
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

    child = spawn(process.execPath, [serverPath], {
        env: {
            ...process.env,
            NODE_ENV: 'development',
            PORT: port,
            DB_PATH: dbPath,
            JWT_SECRET: 'monetization-m1-test-secret',
            ALLOWED_ORIGINS: 'http://localhost:5174',
        },
        stdio: ['ignore', 'ignore', 'pipe'],
    });
    child.stderr.on('data', chunk => process.stderr.write(chunk));
    await waitForHealth();
    db = new Database(dbPath);
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

async function registerUser(prefix, deviceHeaders = {}) {
    const username = `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
    const password = 'pro-test-password-123';
    const response = await fetch(`${baseUrl}/api/auth/register`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Refresh-Transport': 'body',
            'X-App-Platform': 'web',
            'X-Device-Name': 'Web - Chrome (Linux)',
            ...deviceHeaders,
        },
        body: JSON.stringify({ username, password, rememberMe: true }),
    });
    const json = await response.json();
    assert.equal(response.status, 200, JSON.stringify(json));
    return {
        username,
        password,
        token: json.token,
        refreshToken: json.refreshToken,
        user: json.user,
        client: createClient(json.token),
    };
}

async function loginUser(username, password, deviceHeaders = {}) {
    const response = await fetch(`${baseUrl}/api/auth/login`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Refresh-Transport': 'body',
            ...deviceHeaders,
        },
        body: JSON.stringify({ username, password, rememberMe: true }),
    });
    const json = await response.json();
    assert.equal(response.status, 200, JSON.stringify(json));
    return {
        token: json.token,
        refreshToken: json.refreshToken,
        user: json.user,
        client: createClient(json.token),
    };
}

function createClient(token) {
    const request = async (method, route, body, { expect = 200, headers = {}, raw = false } = {}) => {
        const reqHeaders = {
            'Authorization': `Bearer ${token}`,
            'X-App-Version': '2.3.0',
            'X-App-Platform': 'web',
            ...headers,
        };
        if (body !== undefined) {
            reqHeaders['Content-Type'] = 'application/json';
        }
        const response = await fetch(`${baseUrl}${route}`, {
            method,
            headers: reqHeaders,
            body: body !== undefined ? JSON.stringify(body) : undefined,
        });
        if (raw) {
            assert.equal(response.status, expect, `${method} ${route} -> ${response.status}`);
            const arrayBuffer = await response.arrayBuffer();
            return {
                status: response.status,
                headers: response.headers,
                buffer: Buffer.from(arrayBuffer),
            };
        }
        const text = await response.text();
        const json = text ? JSON.parse(text) : null;
        assert.equal(response.status, expect, `${method} ${route} -> ${response.status}: ${text}`);
        return json;
    };

    return {
        get: (route, opts) => request('GET', route, undefined, opts),
        post: (route, body, opts) => request('POST', route, body, opts),
        put: (route, body, opts) => request('PUT', route, body, opts),
        delete: (route, opts) => request('DELETE', route, undefined, opts),
    };
}

test('1. Новый пользователь получает план Free и флаг canStartTrial=true в /register и /me', async () => {
    const { user, client } = await registerUser('free_default');
    assert.ok(user.entitlements, 'user.entitlements must be present on register response');
    assert.equal(user.entitlements.plan, 'free');
    assert.equal(user.entitlements.status, 'active');
    assert.equal(user.entitlements.isPro, false);
    assert.equal(user.entitlements.isTrial, false);
    assert.equal(user.entitlements.canStartTrial, true);
    assert.equal(user.entitlements.features.exportReports, false);
    assert.equal(user.entitlements.features.extendedAnalytics, false);
    assert.equal(user.entitlements.features.extendedBackups, false);
    assert.equal(user.entitlements.features.unlimitedDevices, false);
    assert.equal(user.entitlements.limits.maxBackups, 5);
    assert.equal(user.entitlements.limits.maxDevices, 2);
    assert.equal(user.entitlements.limits.analyticsMonths, 6);

    const me = await client.get('/api/auth/me');
    assert.equal(me.entitlements.plan, 'free');
    assert.equal(me.entitlements.canStartTrial, true);

    const billingStatus = await client.get('/api/billing/status');
    assert.equal(billingStatus.entitlements.plan, 'free');
    assert.equal(billingStatus.entitlements.canStartTrial, true);
});

test('2. Активация 14-дневного триала включает все PRO-функции, рассылает WS-событие и запрещает повторный триал', async () => {
    const { token, client } = await registerUser('trial_user');

    // Подключаем WebSocket, чтобы проверить push-уведомление entitlement_updated
    const wsMessages = [];
    const ws = new WebSocket(`ws://127.0.0.1:${port}/?token=${encodeURIComponent(token)}`);
    await new Promise((resolve, reject) => {
        ws.once('open', resolve);
        ws.once('error', reject);
    });
    ws.on('message', (data) => {
        try {
            wsMessages.push(JSON.parse(data.toString()));
        } catch (_) {
            // ignore
        }
    });

    const trialRes = await client.post('/api/billing/start-trial', {});
    assert.equal(trialRes.success, true);
    assert.equal(trialRes.entitlements.plan, 'pro');
    assert.equal(trialRes.entitlements.status, 'trial');
    assert.equal(trialRes.entitlements.isPro, true);
    assert.equal(trialRes.entitlements.isTrial, true);
    assert.equal(trialRes.entitlements.canStartTrial, false);
    assert.equal(trialRes.entitlements.trialUsed, true);
    assert.ok(trialRes.entitlements.daysRemaining >= 13 && trialRes.entitlements.daysRemaining <= 14);
    assert.equal(trialRes.entitlements.features.exportReports, true);
    assert.equal(trialRes.entitlements.features.extendedAnalytics, true);
    assert.equal(trialRes.entitlements.features.extendedBackups, true);
    assert.equal(trialRes.entitlements.features.unlimitedDevices, true);
    assert.equal(trialRes.entitlements.limits.maxBackups, 30);
    assert.equal(trialRes.entitlements.limits.maxDevices, 99);
    assert.equal(trialRes.entitlements.limits.analyticsMonths, 36);

    // Ждём доставки WS-сообщения
    await new Promise(resolve => setTimeout(resolve, 100));
    ws.close();
    assert.ok(
        wsMessages.some(msg => msg.type === 'entitlement_updated' || (msg.type === 'data_changed' && msg.entity === 'entitlements')),
        `Expected entitlement update ws message, got: ${JSON.stringify(wsMessages)}`
    );

    // Повторная активация триала должна вернуть 409
    const secondTry = await client.post('/api/billing/start-trial', {}, { expect: 409 });
    assert.ok(['TRIAL_ALREADY_USED', 'ALREADY_PRO'].includes(secondTry.code));
});

test('3. Промокод активирует PRO на заданное число дней и не может быть активирован дважды одним пользователем', async () => {
    const { client } = await registerUser('promo_user');

    db.prepare(`
        INSERT INTO promo_codes (code, duration_days, max_uses, used_count, expires_at, is_active, created_at)
        VALUES ('EARLYBIRD30', 30, 10, 0, NULL, 1, ?)
    `).run(new Date().toISOString());

    const redeemRes = await client.post('/api/billing/redeem-promo', { code: 'earlybird30' });
    assert.equal(redeemRes.success, true);
    assert.equal(redeemRes.entitlements.isPro, true);
    assert.equal(redeemRes.entitlements.plan, 'pro');
    assert.equal(redeemRes.entitlements.status, 'active');
    assert.equal(redeemRes.entitlements.source, 'promo');
    assert.ok(redeemRes.entitlements.daysRemaining >= 29 && redeemRes.entitlements.daysRemaining <= 31);

    // Повторный ввод того же промокода этим же пользователем — 409
    const duplicateRedeem = await client.post('/api/billing/redeem-promo', { code: 'EARLYBIRD30' }, { expect: 409 });
    assert.equal(duplicateRedeem.code, 'PROMO_ALREADY_REDEEMED');
});

test('4. Лимит устройств: в Free 3-й вход мягко вытесняет самую старую сессию (остаётся 2), в PRO — все устройства остаются активными', async () => {
    // 1-е устройство (при регистрации)
    const { username, password, refreshToken: rt1 } = await registerUser('device_limit_user', {
        'X-App-Platform': 'web',
        'X-Device-Name': 'Device 1 (Web)',
    });
    await new Promise(resolve => setTimeout(resolve, 25));

    // 2-е устройство
    const dev2 = await loginUser(username, password, {
        'X-App-Platform': 'android',
        'X-Device-Name': 'Device 2 (Android)',
    });
    await new Promise(resolve => setTimeout(resolve, 25));

    // Проверяем список сессий: 2 активных устройства
    const sessionsBefore = await dev2.client.get('/api/user/sessions');
    assert.equal(sessionsBefore.sessions.length, 2);
    assert.equal(sessionsBefore.maxDevices, 2);
    assert.equal(sessionsBefore.isUnlimited, false);

    // 3-е устройство в Free -> должно автоматически отозвать самую старую сессию (Device 1)
    const dev3 = await loginUser(username, password, {
        'X-App-Platform': 'linux',
        'X-Device-Name': 'Device 3 (Linux Desktop)',
    });

    const sessionsAfter3rd = await dev3.client.get('/api/user/sessions');
    assert.equal(sessionsAfter3rd.sessions.length, 2, 'In Free plan only 2 active sessions must remain');
    const activeNames = sessionsAfter3rd.sessions.map(s => s.deviceName);
    assert.ok(activeNames.includes('Device 2 (Android)'));
    assert.ok(activeNames.includes('Device 3 (Linux Desktop)'));
    assert.ok(!activeNames.includes('Device 1 (Web)'), 'Oldest session Device 1 must be evicted');

    // Проверим, что refreshToken 1-го устройства реально отозван (401)
    const refreshDev1 = await fetch(`${baseUrl}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Refresh-Transport': 'body' },
        body: JSON.stringify({ refreshToken: rt1 }),
    });
    assert.equal(refreshDev1.status, 401);

    // Теперь включаем PRO (триал) и логинимся с 4-го и 5-го устройств — ни одно не должно вытесниться
    await dev3.client.post('/api/billing/start-trial', {});
    const dev4 = await loginUser(username, password, {
        'X-App-Platform': 'windows',
        'X-Device-Name': 'Device 4 (Windows)',
    });
    const dev5 = await loginUser(username, password, {
        'X-App-Platform': 'macos',
        'X-Device-Name': 'Device 5 (macOS)',
    });

    const proSessions = await dev5.client.get('/api/user/sessions');
    assert.equal(proSessions.sessions.length, 4);
    assert.equal(proSessions.isUnlimited, true);
    assert.equal(proSessions.sessions.filter(s => s.isCurrent).length, 1);
    assert.equal(proSessions.sessions.find(s => s.isCurrent).deviceName, 'Device 5 (macOS)');

    // Удаляем сессию Device 4 вручную через DELETE /api/user/sessions/:id
    const dev4Session = proSessions.sessions.find(s => s.deviceName === 'Device 4 (Windows)');
    assert.ok(dev4Session);
    await dev5.client.delete(`/api/user/sessions/${dev4Session.id}`);

    const afterManualRevoke = await dev5.client.get('/api/user/sessions');
    assert.equal(afterManualRevoke.sessions.length, 3);
    assert.ok(!afterManualRevoke.sessions.some(s => s.id === dev4Session.id));
});

test('5. Расширенные бэкапы: в Free лимит 5 и запрет pin/export/import; в PRO до 30, pin защищает от ротации, экспорт/импорт .json работают', async () => {
    const { user, client } = await registerUser('backups_user');

    // В Free создаём ещё 6 бэкапов -> всего должно остаться 5
    for (let i = 0; i < 6; i++) {
        await client.post('/api/user/backup', {});
    }
    const freeBackups = await client.get('/api/user/backups');
    assert.equal(freeBackups.backups.length, 5);
    assert.equal(freeBackups.maxBackups, 5);

    // В Free попытка закрепить, экспортировать или импортировать бэкап возвращает 403 PRO_REQUIRED
    const targetFreeBackupId = freeBackups.backups[0].id;
    const pinDenied = await client.put(`/api/user/backups/${targetFreeBackupId}/pin`, { isPinned: true }, { expect: 403 });
    assert.equal(pinDenied.code, 'PRO_REQUIRED');
    assert.equal(pinDenied.feature, 'extendedBackups');

    const exportDenied = await client.get(`/api/user/backups/${targetFreeBackupId}/export`, { expect: 403 });
    assert.equal(exportDenied.code, 'PRO_REQUIRED');

    // Включаем Триал PRO
    await client.post('/api/billing/start-trial', {});

    // Закрепляем самый старый из текущих 5 бэкапов
    const oldestBackupId = freeBackups.backups[freeBackups.backups.length - 1].id;
    const pinRes = await client.put(`/api/user/backups/${oldestBackupId}/pin`, { isPinned: true });
    assert.equal(pinRes.success, true);
    assert.equal(pinRes.backup.isPinned, true);

    // Экспортируем этот бэкап в JSON
    const exported = await client.get(`/api/user/backups/${oldestBackupId}/export`);
    assert.equal(exported.format, 'finkeeper24-backup');
    assert.equal(exported.backupId, oldestBackupId);
    assert.ok(Array.isArray(exported.data.categories));

    // Добавим новую категорию, экспортируем свежий снимок, затем удалим категорию и импортируем снимок с восстановлением
    const createdCat = await client.post('/api/categories', { name: 'Категория для JSON бэкапа' });
    await client.post('/api/user/backup', {});
    const latestList = await client.get('/api/user/backups');
    const latestUnpinned = latestList.backups.find(b => !b.isPinned);
    const exportedWithCat = await client.get(`/api/user/backups/${latestUnpinned.id}/export`);

    // Удаляем категорию
    await client.delete(`/api/categories/${createdCat.id}`);
    const catsAfterDelete = await client.get('/api/categories');
    assert.ok(!catsAfterDelete.some(c => c.name === 'Категория для JSON бэкапа'));

    // Импортируем JSON-бэкап с немедленным восстановлением
    const importRes = await client.post('/api/user/backups/import', {
        backup: exportedWithCat,
        restoreImmediately: true,
        confirmationText: 'ВОССТАНОВИТЬ',
    });
    assert.equal(importRes.success, true);
    assert.equal(importRes.restored, true);

    const catsAfterImport = await client.get('/api/categories');
    assert.ok(catsAfterImport.some(c => c.name === 'Категория для JSON бэкапа'));

    // Проверяем, что закреплённый бэкап переживает ротацию даже при превышении лимита
    // (создадим 32 бэкапа напрямую через API, oldestBackupId с is_pinned=1 должен остаться!)
    for (let i = 0; i < 30; i++) {
        await client.post('/api/user/backup', {});
    }
    const afterRotation = await client.get('/api/user/backups');
    assert.equal(afterRotation.backups.length, 30);
    assert.equal(afterRotation.maxBackups, 30);
    assert.ok(afterRotation.backups.some(b => b.id === oldestBackupId && b.isPinned === true), 'Pinned backup must survive 30-backup rotation');
});

test('6. Глубокая аналитика (12/24М + Burn Rate / MoM) и экспорт Excel (.xlsx) / CSV (.csv)', async () => {
    const { client } = await registerUser('analytics_export_user');

    // Создадим 8 месяцев с данными
    const categories = await client.get('/api/categories');
    const foodCat = categories[0];

    for (let m = 1; m <= 8; m++) {
        const monthRow = await client.post('/api/months/ensure', { year: 2026, month: m });
        await client.post('/api/incomes', {
            month_id: monthRow.id,
            source: 'Зарплата',
            amount: 100000 + m * 5000,
            date: `2026-${String(m).padStart(2, '0')}-10`,
        });
        await client.post('/api/expenses', {
            month_id: monthRow.id,
            category_id: foodCat.id,
            amount: 30000 + m * 2000,
            date: `2026-${String(m).padStart(2, '0')}-15`,
            comment: `Расход месяца ${m}`,
        });
    }

    // В Free запрос /api/analytics/trend без параметра (или months=6) возвращает 6 месяцев
    const trend6 = await client.get('/api/analytics/trend?months=6');
    assert.equal(trend6.length, 6);

    // В Free запрос /api/analytics/trend?months=12 возвращает 403 PRO_REQUIRED
    const trend12Denied = await client.get('/api/analytics/trend?months=12', { expect: 403 });
    assert.equal(trend12Denied.code, 'PRO_REQUIRED');
    assert.equal(trend12Denied.feature, 'extendedAnalytics');

    // В Free запрос /api/analytics/insights возвращает 403 PRO_REQUIRED
    const insightsDenied = await client.get('/api/analytics/insights?year=2026&month=8', { expect: 403 });
    assert.equal(insightsDenied.code, 'PRO_REQUIRED');

    // В Free запрос /api/export/report возвращает 403 PRO_REQUIRED
    const exportDenied = await client.get('/api/export/report?format=xlsx', { expect: 403 });
    assert.equal(exportDenied.code, 'PRO_REQUIRED');
    assert.equal(exportDenied.feature, 'exportReports');

    // Включаем PRO
    await client.post('/api/billing/start-trial', {});

    // В PRO /api/analytics/trend?months=12 возвращает все 8 созданных месяцев
    const trend12 = await client.get('/api/analytics/trend?months=12');
    assert.equal(trend12.length, 8);
    assert.equal(trend12[0].month, '1/2026');
    assert.equal(trend12[7].month, '8/2026');

    // Проверяем /api/analytics/insights за август 2026 (сравнение с июлем 2026)
    const insights = await client.get('/api/analytics/insights?year=2026&month=8');
    assert.equal(insights.year, 2026);
    assert.equal(insights.month, 8);
    assert.equal(insights.burnRate.totalExpenses, 46000); // 30000 + 8*2000
    assert.ok(insights.burnRate.dailyBurnRate > 0);
    assert.equal(insights.comparison.previousMonthExpenses, 44000); // 30000 + 7*2000
    assert.ok(Array.isArray(insights.comparison.categories));
    assert.equal(insights.comparison.categories[0].currentAmount, 46000);
    assert.equal(insights.comparison.categories[0].previousAmount, 44000);

    // Проверяем экспорт CSV (UTF-8 BOM + строки расходов/доходов)
    const csvRes = await client.get('/api/export/report?format=csv&year=2026&month=8&period=quarter', { raw: true });
    assert.ok(csvRes.headers.get('content-type').includes('text/csv'));
    const csvText = csvRes.buffer.toString('utf8');
    assert.ok(csvText.startsWith('\uFEFF'), 'CSV must start with UTF-8 BOM for Russian Excel');
    assert.ok(csvText.includes('Расход месяца 8'));
    assert.ok(csvText.includes('Зарплата'));

    // Проверяем экспорт Excel (.xlsx) — должен быть валидный ZIP (PK\x03\x04) с xl/workbook.xml внутри
    const xlsxRes = await client.get('/api/export/report?format=xlsx&year=2026&month=8&period=year', { raw: true });
    assert.ok(
        xlsxRes.headers.get('content-type').includes('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
    );
    assert.equal(xlsxRes.buffer[0], 0x50); // 'P'
    assert.equal(xlsxRes.buffer[1], 0x4b); // 'K'
    assert.equal(xlsxRes.buffer[2], 0x03);
    assert.equal(xlsxRes.buffer[3], 0x04);
    assert.ok(xlsxRes.buffer.includes(Buffer.from('xl/workbook.xml')), 'XLSX archive must contain xl/workbook.xml');
    assert.ok(xlsxRes.buffer.includes(Buffer.from('xl/worksheets/sheet1.xml')), 'XLSX archive must contain sheet1.xml');
});

test('7. Бесконечный бесплатный Семейный PRO для подтверждённого аккаунта автора (и защита от неподтверждённого email)', async () => {
    // 7.1. Пользователь с неподтверждённым email владельца НЕ получает PRO
    const unconfirmed = await registerUser('fake_owner');
    const unconfirmedNow = new Date().toISOString();
    db.prepare('UPDATE users SET email = ?, email_confirmed_at = NULL WHERE id = ?')
        .run('racerkafa@yandex.ru', unconfirmed.user.id);
    const fakeStatus = await unconfirmed.client.get('/api/billing/status');
    assert.equal(fakeStatus.entitlements.isPro, false);
    assert.equal(fakeStatus.entitlements.plan, 'free');

    // Снимаем email у фейкового пользователя перед созданием реального
    db.prepare('UPDATE users SET email = NULL WHERE id = ?').run(unconfirmed.user.id);

    // 7.2. Подтверждённый email автора автоматически получает бесконечный pro_family (expiresAt = null)
    const owner = await registerUser('vladimir_author');
    db.prepare('UPDATE users SET email = ?, email_confirmed_at = ? WHERE id = ?')
        .run('racerkafa@yandex.ru', unconfirmedNow, owner.user.id);

    const ownerMe = await owner.client.get('/api/auth/me');
    assert.equal(ownerMe.entitlements.isPro, true);
    assert.equal(ownerMe.entitlements.isTrial, false);
    assert.equal(ownerMe.entitlements.isLifetime, true);
    assert.equal(ownerMe.entitlements.plan, 'pro_family');
    assert.equal(ownerMe.entitlements.status, 'active');
    assert.equal(ownerMe.entitlements.source, 'manual_grant');
    assert.equal(ownerMe.entitlements.expiresAt, null);
    assert.equal(ownerMe.entitlements.daysRemaining, null);
    assert.equal(ownerMe.entitlements.limits.maxBackups, 30);
    assert.equal(ownerMe.entitlements.limits.maxDevices, 99);
    assert.equal(ownerMe.entitlements.limits.analyticsMonths, 36);
});

test('8. Константный промокод BOBA777 активирует бесконечный бесплатный Семейный PRO для семьи и друзей', async () => {
    // 8.1. Чистый Free аккаунт вводит промокод в нижнем регистре и с пробелами
    const friend = await registerUser('family_friend');
    const redeemRes = await friend.client.post('/api/billing/redeem-promo', { code: '  boba777  ' });

    assert.equal(redeemRes.success, true);
    assert.equal(redeemRes.entitlements.isPro, true);
    assert.equal(redeemRes.entitlements.isTrial, false);
    assert.equal(redeemRes.entitlements.isLifetime, true);
    assert.equal(redeemRes.entitlements.plan, 'pro_family');
    assert.equal(redeemRes.entitlements.status, 'active');
    assert.equal(redeemRes.entitlements.source, 'promo');
    assert.equal(redeemRes.entitlements.expiresAt, null);
    assert.equal(redeemRes.entitlements.daysRemaining, null);
    assert.equal(redeemRes.entitlements.limits.maxBackups, 30);
    assert.equal(redeemRes.entitlements.limits.maxDevices, 99);
    assert.equal(redeemRes.entitlements.limits.analyticsMonths, 36);
    assert.equal(redeemRes.entitlements.limits.maxFamilyMembers, 5);

    // 8.2. Повторная попытка ввести промокод на уже бессрочном аккаунте — 409
    const duplicateTry = await friend.client.post('/api/billing/redeem-promo', { code: 'BOBA777' }, { expect: 409 });
    assert.ok(['PROMO_ALREADY_REDEEMED', 'ALREADY_LIFETIME_PRO'].includes(duplicateTry.code));

    // 8.3. Второй друг, у которого уже запущен 14-дневный Триал, апгрейдится через BOBA777 до вечного pro_family
    const trialFriend = await registerUser('trial_to_boba');
    await trialFriend.client.post('/api/billing/start-trial', {});
    const upgradeRes = await trialFriend.client.post('/api/billing/redeem-promo', { code: 'BOBA777' });
    assert.equal(upgradeRes.entitlements.isPro, true);
    assert.equal(upgradeRes.entitlements.isTrial, false);
    assert.equal(upgradeRes.entitlements.isLifetime, true);
    assert.equal(upgradeRes.entitlements.plan, 'pro_family');
    assert.equal(upgradeRes.entitlements.expiresAt, null);
});

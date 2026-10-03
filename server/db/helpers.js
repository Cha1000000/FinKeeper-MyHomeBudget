const { db, nowIso } = require('./connection');
const logger = require('../logger');

const MAX_BACKUPS = 5;
// Скрытая категория (is_active = 0): каждое пополнение копилки дублируется в ней расходом,
// чтобы уменьшать свободные деньги месяца
const SAVINGS_EXPENSE_CATEGORY = 'Пополнение копилки';

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
            // Ключ уже применён, но тело запроса другое: клиент повторил операцию после потерянного
            // ответа, а данные за это время поменялись. Отдаём исходный ответ, чтобы клиент узнал
            // id созданной записи и дослал изменения отдельной операцией, а не создал дубль.
            return res.status(409).json({
                error: 'Operation already used for different request',
                code: 'IDEMPOTENCY_KEY_REUSED',
                original_status: existing.response_status,
                original_response: JSON.parse(existing.response_body),
            });
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

        let auto_created_records = [];
        let planned_overrides = [];
        if (monthIds.length > 0) {
            const placeholders2 = monthIds.map(() => '?').join(',');
            auto_created_records = db.prepare(`SELECT * FROM auto_created_records WHERE user_id = ? AND month_id IN (${placeholders2})`).all(userId, ...monthIds);
            planned_overrides = db.prepare(`SELECT * FROM planned_overrides WHERE user_id = ? AND month_id IN (${placeholders2})`).all(userId, ...monthIds);
        }

        const backupData = JSON.stringify({
            categories,
            income_sources,
            savings_goals,
            months,
            incomes,
            expenses,
            budgets,
            savings_transactions,
            auto_created_records,
            planned_overrides,
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
        autoCreatedRecords: safeArrayLength(backupData?.auto_created_records),
        plannedOverrides: safeArrayLength(backupData?.planned_overrides),
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

function daysInMonth(year, month) {
    return new Date(year, month, 0).getDate();
}

function formatMonthDate(year, month, day) {
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

const PLANNED_TEMPLATE_TYPES = ['category', 'income_source'];

function getFixedTemplates(userId, templateType) {
    const table = templateType === 'category' ? 'categories' : 'income_sources';
    return db.prepare(
        `SELECT * FROM ${table} WHERE user_id = ? AND is_fixed = 1 AND is_active = 1 AND fixed_amount IS NOT NULL AND auto_day IS NOT NULL`
    ).all(userId);
}

function getFixedTemplate(userId, templateType, templateId) {
    const table = templateType === 'category' ? 'categories' : 'income_sources';
    return db.prepare(
        `SELECT * FROM ${table} WHERE id = ? AND user_id = ? AND is_fixed = 1 AND is_active = 1 AND fixed_amount IS NOT NULL AND auto_day IS NOT NULL`
    ).get(templateId, userId);
}

function getAutoCreatedRecord(userId, templateType, templateId, monthId) {
    return db.prepare(
        'SELECT * FROM auto_created_records WHERE user_id = ? AND template_type = ? AND template_id = ? AND month_id = ?'
    ).get(userId, templateType, templateId, monthId);
}

function getPlannedOverridesMap(userId, monthId) {
    const rows = db.prepare('SELECT * FROM planned_overrides WHERE user_id = ? AND month_id = ?').all(userId, monthId);
    return new Map(rows.map(row => [`${row.template_type}:${row.template_id}`, row]));
}

// Создаёт реальную запись (expense/income) из шаблона + строку auto_created_records.
// amount_at_creation = фактически применённая сумма (включая override).
// При гонке UNIQUE-конфликт auto_created_records пробрасывается наружу.
function materializePlannedRecord(userId, month, templateType, template, { amount, day }) {
    const effectiveDay = Math.min(day, daysInMonth(month.year, month.month));
    const dateStr = formatMonthDate(month.year, month.month, effectiveDay);
    const timestamp = nowIso();

    const create = db.transaction(() => {
        let info;
        let recordType;
        if (templateType === 'category') {
            recordType = 'expense';
            info = db.prepare(
                'INSERT INTO expenses (month_id, category_id, amount, date, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
            ).run(month.id, template.id, amount, dateStr, 'Регулярный платёж', timestamp, timestamp);
        } else {
            recordType = 'income';
            info = db.prepare(
                'INSERT INTO incomes (month_id, source, amount, date, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)'
            ).run(month.id, template.name, amount, dateStr, timestamp, timestamp);
        }

        db.prepare(
            'INSERT INTO auto_created_records (user_id, template_type, template_id, month_id, created_record_id, created_record_type, amount_at_creation, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
        ).run(userId, templateType, template.id, month.id, info.lastInsertRowid, recordType, amount, timestamp);

        return { recordType, recordId: info.lastInsertRowid };
    });

    const { recordType, recordId } = create();
    return {
        recordType,
        record: getRowById(recordType === 'expense' ? 'expenses' : 'incomes', recordId),
    };
}

function autoCreateRecurringRecords(userId, monthId, broadcastFn, now = new Date()) {
    const month = db.prepare('SELECT * FROM months WHERE id = ? AND user_id = ?').get(monthId, userId);
    if (!month) return;

    const todayYear = now.getFullYear();
    const todayMonth = now.getMonth() + 1;
    const todayDay = now.getDate();

    // Определяем, наступил ли указанный день для этого месяца
    // Для прошлых месяцев — все дни считаются наступившими
    // Для текущего месяца — только если today >= auto_day
    // Для будущих месяцев — ничего не создаём
    function isDayReached(autoDay) {
        if (month.year < todayYear) return true;
        if (month.year === todayYear && month.month < todayMonth) return true;
        if (month.year === todayYear && month.month === todayMonth) {
            const maxDay = daysInMonth(month.year, month.month);
            const effectiveDay = Math.min(autoDay, maxDay);
            return todayDay >= effectiveDay;
        }
        return false; // будущий месяц
    }

    const overrides = getPlannedOverridesMap(userId, monthId);
    const created = [];

    for (const templateType of PLANNED_TEMPLATE_TYPES) {
        for (const template of getFixedTemplates(userId, templateType)) {
            // require_confirm=1 — платёж ждёт явного подтверждения, автопостинг запрещён
            if (template.require_confirm) continue;

            const override = overrides.get(`${templateType}:${template.id}`);
            if (override?.is_skipped) continue;

            const effectiveDay = override?.override_day ?? template.auto_day;
            const effectiveAmount = override?.override_amount ?? template.fixed_amount;
            if (!isDayReached(effectiveDay)) continue;

            const existing = getAutoCreatedRecord(userId, templateType, template.id, monthId);
            if (existing) continue;

            try {
                const { recordType } = materializePlannedRecord(userId, month, templateType, template, {
                    amount: effectiveAmount,
                    day: effectiveDay,
                });
                created.push(recordType);
            } catch (e) {
                // Гонка двух параллельных ensure: запись уже создана — пропускаем
                if (!String(e?.code).startsWith('SQLITE_CONSTRAINT')) throw e;
            }
        }
    }

    // Broadcast если что-то создано
    if (created.length > 0 && broadcastFn) {
        broadcastFn(userId, 'expense', 'created');
        broadcastFn(userId, 'income', 'created');
    }
}

// Виртуальный план-слой: ещё не материализованные фиксированные платежи месяца.
// Ничего не пишет в БД; «удалённый автоплатёж = пропуск месяца» обеспечивается тем,
// что строка auto_created_records переживает удаление самой записи.
function getPlannedRecords(userId, monthId, now = new Date()) {
    const month = db.prepare('SELECT * FROM months WHERE id = ? AND user_id = ?').get(monthId, userId);
    if (!month) return { expenses: [], incomes: [] };

    const todayYear = now.getFullYear();
    const todayMonth = now.getMonth() + 1;
    const todayDay = now.getDate();
    const isPastMonth = month.year < todayYear || (month.year === todayYear && month.month < todayMonth);
    const isCurrentMonth = month.year === todayYear && month.month === todayMonth;

    const materializedRows = db.prepare(
        'SELECT template_type, template_id FROM auto_created_records WHERE user_id = ? AND month_id = ?'
    ).all(userId, monthId);
    const materialized = new Set(materializedRows.map(row => `${row.template_type}:${row.template_id}`));
    const overrides = getPlannedOverridesMap(userId, monthId);

    const result = { expenses: [], incomes: [] };

    for (const templateType of PLANNED_TEMPLATE_TYPES) {
        for (const template of getFixedTemplates(userId, templateType)) {
            const key = `${templateType}:${template.id}`;
            if (materialized.has(key)) continue; // уже факт (или удалён пользователем = пропуск месяца)

            const override = overrides.get(key);
            const requireConfirm = template.require_confirm ? 1 : 0;
            const isSkipped = override?.is_skipped ? 1 : 0;
            const dueDay = Math.min(override?.override_day ?? template.auto_day, daysInMonth(month.year, month.month));

            // Попадание в план: будущий месяц — все шаблоны; текущий — require_confirm
            // и скипнутые всегда (для подтверждения/unskip), обычные — пока день не наступил
            // (наступившие материализует ближайший ensure); прошлый — только неподтверждённые
            // require_confirm (висят как просроченные).
            if (isPastMonth && (!requireConfirm || isSkipped)) continue;
            if (isCurrentMonth && !requireConfirm && !isSkipped && dueDay <= todayDay) continue;

            const isOverdue = (requireConfirm && !isSkipped && (isPastMonth || (isCurrentMonth && dueDay < todayDay))) ? 1 : 0;

            const item = {
                template_type: templateType,
                template_id: template.id,
                name: template.name,
                amount: override?.override_amount ?? template.fixed_amount,
                original_amount: template.fixed_amount,
                due_day: dueDay,
                due_date: formatMonthDate(month.year, month.month, dueDay),
                require_confirm: requireConfirm,
                is_skipped: isSkipped,
                is_overridden: (override && (override.override_amount !== null || override.override_day !== null)) ? 1 : 0,
                is_overdue: isOverdue,
            };

            (templateType === 'category' ? result.expenses : result.incomes).push(item);
        }
    }

    result.expenses.sort((a, b) => a.due_day - b.due_day);
    result.incomes.sort((a, b) => a.due_day - b.due_day);
    return result;
}

// Суммы план-слоя для прогнозных агрегатов (скипнутые не считаются).
// Принимает результат getPlannedRecords, чтобы не вычислять план дважды.
function computePlannedAggregates(planned) {
    const sum = items => items.reduce((acc, item) => item.is_skipped ? acc : acc + item.amount, 0);
    return {
        plannedExpenses: sum(planned.expenses),
        plannedIncomes: sum(planned.incomes),
    };
}

// Итоги месяца для summary и графика динамики.
// expenses — все расходы, включая скрытые пополнения копилок; visibleExpenses — без них
// (как в карточке «Расходы»); savings — пополнения копилок без корректировок.
// Пополнения уже входят в expenses, поэтому balance = income - expenses: повторно savings
// не вычитаем, иначе копилка уменьшает баланс дважды.
function computeMonthTotals(monthId) {
    const income = db.prepare('SELECT SUM(amount) as total FROM incomes WHERE month_id = ?').get(monthId).total || 0;
    const expenses = db.prepare('SELECT SUM(amount) as total FROM expenses WHERE month_id = ?').get(monthId).total || 0;
    const savingsExpenses = db.prepare(`
        SELECT SUM(e.amount) as total
        FROM expenses e
        JOIN categories c ON c.id = e.category_id
        WHERE e.month_id = ? AND c.name = ?
    `).get(monthId, SAVINGS_EXPENSE_CATEGORY).total || 0;
    const savings = db.prepare('SELECT SUM(amount) as total FROM savings_transactions WHERE month_id = ? AND amount > 0 AND (is_adjustment = 0 OR is_adjustment IS NULL)').get(monthId).total || 0;

    return {
        income,
        expenses,
        visibleExpenses: expenses - savingsExpenses,
        savings,
        balance: income - expenses,
    };
}

// --- Скрытые расходы копилок ---
// Пополнение копилки дублируется расходом в скрытой категории с комментарием
// `Пополнение копилки "<имя копилки>"`. Связи по id нет, поэтому расход ищется по
// месяцу, сумме, дате и комментарию.
const SAVINGS_EXPENSE_COMMENT_PREFIX = `${SAVINGS_EXPENSE_CATEGORY} "`;

function savingsExpenseComment(goalName) {
    return `${SAVINGS_EXPENSE_COMMENT_PREFIX}${goalName}"`;
}

function getSavingsExpenseCategoryId(userId) {
    const row = db.prepare('SELECT id FROM categories WHERE name = ? AND user_id = ?').get(SAVINGS_EXPENSE_CATEGORY, userId);
    return row ? row.id : null;
}

function ensureSavingsExpenseCategoryId(userId, timestamp) {
    const existingId = getSavingsExpenseCategoryId(userId);
    if (existingId) return existingId;
    const maxOrder = db.prepare('SELECT MAX(sort_order) as maxOrder FROM categories WHERE user_id = ?').get(userId);
    const nextOrder = (maxOrder.maxOrder || 0) + 1;
    return db.prepare('INSERT INTO categories (user_id, name, sort_order, is_active, created_at, updated_at) VALUES (?, ?, ?, 0, ?, ?)')
        .run(userId, SAVINGS_EXPENSE_CATEGORY, nextOrder, timestamp, timestamp).lastInsertRowid;
}

function createSavingsHiddenExpense(userId, { monthId, amount, date, goalName }, timestamp) {
    const categoryId = ensureSavingsExpenseCategoryId(userId, timestamp);
    return db.prepare('INSERT INTO expenses (month_id, category_id, amount, date, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)')
        .run(monthId, categoryId, amount, date, savingsExpenseComment(goalName), timestamp, timestamp).lastInsertRowid;
}

// Скрытый расход пополнения transaction ({ goal_id, month_id, amount, date }) копилки goalName.
// Сначала точное совпадение комментария. Если его нет — расход с тем же месяцем, суммой и
// датой, чей комментарий остался от прежнего имени копилки (переименования до исправления
// не переписывали комментарии). Расходы, принадлежащие другим копилкам пользователя, не берутся.
function findSavingsHiddenExpenseId(userId, transaction, goalName) {
    if (!(transaction.amount > 0) || !transaction.month_id) return null;
    const categoryId = getSavingsExpenseCategoryId(userId);
    if (!categoryId) return null;

    const exact = db.prepare(`
        SELECT id FROM expenses
        WHERE month_id = ? AND category_id = ? AND amount = ? AND date = ? AND comment = ?
        ORDER BY id DESC LIMIT 1
    `).get(transaction.month_id, categoryId, transaction.amount, transaction.date, savingsExpenseComment(goalName));
    if (exact) return exact.id;

    const candidates = db.prepare(`
        SELECT id, comment FROM expenses
        WHERE month_id = ? AND category_id = ? AND amount = ? AND date = ? AND substr(comment, 1, ?) = ?
        ORDER BY id DESC
    `).all(
        transaction.month_id,
        categoryId,
        transaction.amount,
        transaction.date,
        SAVINGS_EXPENSE_COMMENT_PREFIX.length,
        SAVINGS_EXPENSE_COMMENT_PREFIX,
    );
    if (candidates.length === 0) return null;

    const otherGoalComments = new Set(
        db.prepare('SELECT name FROM savings_goals WHERE user_id = ? AND id != ?')
            .all(userId, transaction.goal_id)
            .map(goal => savingsExpenseComment(goal.name))
    );
    const orphan = candidates.find(candidate => !otherGoalComments.has(candidate.comment));
    return orphan ? orphan.id : null;
}

// Удаляет скрытый расход пополнения и пишет tombstone, чтобы клиенты убрали его локально.
// Возвращает id удалённого расхода или null.
function deleteSavingsHiddenExpense(userId, transaction, goalName, timestamp) {
    const expenseId = findSavingsHiddenExpenseId(userId, transaction, goalName);
    if (!expenseId) {
        // Пополнение с месяцем всегда создаёт скрытый расход: раз его нет, «Свободно» месяца
        // расходится с копилкой — фиксируем для разбора
        if (transaction.amount > 0 && transaction.month_id) {
            logger.warn('savings_hidden_expense_not_found', { userId, transactionId: transaction.id, monthId: transaction.month_id });
        }
        return null;
    }
    recordDeletedRecord(userId, 'expense', expenseId, timestamp);
    db.prepare('DELETE FROM expenses WHERE id = ?').run(expenseId);
    return expenseId;
}

// Переименование копилки: комментарии скрытых расходов её пополнений переписываются на новое
// имя. Идём по транзакциям копилки, а не по тексту комментария: у двух копилок может быть
// одинаковое имя. Возвращает число обновлённых расходов.
function renameSavingsHiddenExpenses(userId, goalId, oldName, newName, timestamp) {
    if (oldName === newName) return 0;
    const transactions = db.prepare('SELECT goal_id, month_id, amount, date FROM savings_transactions WHERE goal_id = ? AND amount > 0 AND month_id IS NOT NULL')
        .all(goalId);
    const updateStmt = db.prepare('UPDATE expenses SET comment = ?, updated_at = ? WHERE id = ?');
    const newComment = savingsExpenseComment(newName);
    let updated = 0;
    transactions.forEach(transaction => {
        const expenseId = findSavingsHiddenExpenseId(userId, transaction, oldName);
        if (!expenseId) return;
        updateStmt.run(newComment, timestamp, expenseId);
        updated += 1;
    });
    return updated;
}

// Ключи идемпотентности нужны, пока клиент может повторить операцию (очередь офлайн-синхронизации).
// Через 90 дней повтор уже невозможен — старые ключи удаляются, чтобы таблица не росла бесконечно.
const IDEMPOTENCY_KEY_TTL_DAYS = 90;

function pruneIdempotencyKeys(now = new Date()) {
    const cutoff = new Date(now.getTime() - IDEMPOTENCY_KEY_TTL_DAYS * 24 * 60 * 60 * 1000).toISOString();
    return db.prepare('DELETE FROM idempotency_keys WHERE created_at < ?').run(cutoff).changes;
}

function restoreBackupSnapshot(userId, backupData) {
    const restoreTransact = db.transaction(() => {
        // Дочерние таблицы с FK на months удаляются ДО months,
        // иначе better-sqlite3 (foreign_keys=ON по умолчанию) падает с FK-ошибкой
        db.prepare('DELETE FROM auto_created_records WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM planned_overrides WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM savings_transactions WHERE goal_id IN (SELECT id FROM savings_goals WHERE user_id = ?)').run(userId);
        db.prepare('DELETE FROM budgets WHERE category_id IN (SELECT id FROM categories WHERE user_id = ?)').run(userId);
        db.prepare('DELETE FROM expenses WHERE category_id IN (SELECT id FROM categories WHERE user_id = ?)').run(userId);
        db.prepare('DELETE FROM incomes WHERE month_id IN (SELECT id FROM months WHERE user_id = ?)').run(userId);
        db.prepare('DELETE FROM months WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM categories WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM income_sources WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM savings_goals WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM deleted_records WHERE user_id = ?').run(userId);

        const insertCat = db.prepare('INSERT INTO categories (id, user_id, name, sort_order, is_active, is_fixed, fixed_amount, auto_day, require_confirm, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
        (backupData.categories || []).forEach(row => insertCat.run(row.id, userId, row.name, row.sort_order, row.is_active, row.is_fixed ?? 0, row.fixed_amount ?? null, row.auto_day ?? null, row.require_confirm ?? 0, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));

        const insertSource = db.prepare('INSERT INTO income_sources (id, user_id, name, is_active, sort_order, is_fixed, fixed_amount, auto_day, require_confirm, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
        (backupData.income_sources || []).forEach(row => insertSource.run(row.id, userId, row.name, row.is_active, row.sort_order ?? 0, row.is_fixed ?? 0, row.fixed_amount ?? null, row.auto_day ?? null, row.require_confirm ?? 0, row.created_at || row.updated_at || nowIso(), row.updated_at || row.created_at || nowIso()));

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

        const insertAutoCreated = db.prepare('INSERT INTO auto_created_records (id, user_id, template_type, template_id, month_id, created_record_id, created_record_type, amount_at_creation, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)');
        (backupData.auto_created_records || []).forEach(row => insertAutoCreated.run(row.id, userId, row.template_type, row.template_id, row.month_id, row.created_record_id, row.created_record_type, row.amount_at_creation, row.created_at || nowIso()));

        const insertPlannedOverride = db.prepare('INSERT INTO planned_overrides (id, user_id, template_type, template_id, month_id, override_amount, override_day, is_skipped, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
        (backupData.planned_overrides || []).forEach(row => insertPlannedOverride.run(row.id, userId, row.template_type, row.template_id, row.month_id, row.override_amount ?? null, row.override_day ?? null, row.is_skipped ?? 0, row.created_at || nowIso(), row.updated_at || row.created_at || nowIso()));
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
    autoCreateRecurringRecords,
    daysInMonth,
    getFixedTemplate,
    getAutoCreatedRecord,
    getPlannedOverridesMap,
    materializePlannedRecord,
    getPlannedRecords,
    computePlannedAggregates,
    computeMonthTotals,
    SAVINGS_EXPENSE_CATEGORY,
    savingsExpenseComment,
    createSavingsHiddenExpense,
    findSavingsHiddenExpenseId,
    deleteSavingsHiddenExpense,
    renameSavingsHiddenExpenses,
    pruneIdempotencyKeys,
    createBackup,
    parseBackupDataSafely,
    buildBackupSummary,
    buildBackupEntryPayload,
    listUserBackupEntries,
    getUserBackupRecord,
    restoreBackupSnapshot,
};

const express = require('express');
const router = express.Router();

const { db, nowIso } = require('../db/connection');
const { authenticateToken, sendError } = require('../middleware/authenticate');
const {
    getOrCreateMonth,
    checkMonthAccess,
    executeIdempotent,
    recordDeletedRecord,
    clearDeletedRecord,
    getRowById,
    autoCreateRecurringRecords,
    daysInMonth,
    getFixedTemplate,
    getAutoCreatedRecord,
    materializePlannedRecord,
    getPlannedRecords,
    computePlannedAggregates,
} = require('../db/helpers');
const logger = require('../logger');

// Retrieve broadcast logic dynamically since we mounted it in index.js
const broadcastChange = (req, userId, entity, action) => {
    const broadcast = req.app.get('broadcast');
    if (broadcast) {
        broadcast(userId, entity, action);
    }
};

// Protect data routes
router.use(authenticateToken);

router.get('/deleted_records', (req, res) => {
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

// Categories
router.get('/categories', (req, res) => {
    const includeInactive = req.query.include_inactive === '1' || req.query.include_inactive === 'true';
    const categories = includeInactive
        ? db.prepare('SELECT * FROM categories WHERE user_id = ? ORDER BY sort_order ASC, name ASC').all(req.user.id)
        : db.prepare('SELECT * FROM categories WHERE user_id = ? AND is_active = 1 ORDER BY sort_order ASC, name ASC').all(req.user.id);
    res.json(categories);
});

router.post('/categories', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name, is_fixed, fixed_amount, auto_day, require_confirm } = req.body;

        const isFixed = Number(is_fixed) === 1 ? 1 : 0;
        if (isFixed && (fixed_amount == null || fixed_amount <= 0)) return { statusCode: 400, body: { error: 'fixed_amount is required and must be > 0 for fixed categories' } };
        if (isFixed && (auto_day == null || auto_day < 1 || auto_day > 31)) return { statusCode: 400, body: { error: 'auto_day must be between 1 and 31 for fixed categories' } };
        const requireConfirm = isFixed && Number(require_confirm) === 1 ? 1 : 0;

        const result = db.prepare('SELECT MAX(sort_order) as maxOrder FROM categories WHERE user_id = ?').get(req.user.id);
        const nextOrder = (result.maxOrder || 0) + 1;
        const timestamp = nowIso();

        const existing = db.prepare('SELECT * FROM categories WHERE user_id = ? AND name = ?').get(req.user.id, name);
        if (existing) {
            return { statusCode: 200, body: existing };
        }

        try {
            const info = db.prepare('INSERT INTO categories (user_id, name, sort_order, is_fixed, fixed_amount, auto_day, require_confirm, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)').run(
                req.user.id, name, nextOrder, isFixed, isFixed ? fixed_amount : null, isFixed ? auto_day : null, requireConfirm, timestamp, timestamp
            );
            const body = getRowById('categories', info.lastInsertRowid);
            broadcastChange(req, req.user.id, 'category', 'created');
            return { statusCode: 200, body };
        } catch (err) {
            // Гонка: параллельный запрос уже создал категорию с этим именем (UNIQUE) — вернём её.
            if (String(err.code || '').startsWith('SQLITE_CONSTRAINT')) {
                const row = db.prepare('SELECT * FROM categories WHERE user_id = ? AND name = ?').get(req.user.id, name);
                if (row) return { statusCode: 200, body: row };
            }
            return { statusCode: 500, body: { error: err.message } };
        }
    });
});

router.put('/categories/reorder', (req, res) => {
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

router.put('/categories/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name, is_active, is_fixed, fixed_amount, auto_day, require_confirm } = req.body;

        const isFixed = is_fixed != null ? (Number(is_fixed) === 1 ? 1 : 0) : undefined;
        if (isFixed === 1 && (fixed_amount == null || fixed_amount <= 0)) return { statusCode: 400, body: { error: 'fixed_amount is required and must be > 0 for fixed categories' } };
        if (isFixed === 1 && (auto_day == null || auto_day < 1 || auto_day > 31)) return { statusCode: 400, body: { error: 'auto_day must be between 1 and 31 for fixed categories' } };

        const sets = ['updated_at = ?'];
        const params = [nowIso()];

        if (name != null) { sets.push('name = ?'); params.push(name); }
        if (is_active != null) { sets.push('is_active = ?'); params.push(is_active); }

        if (isFixed !== undefined) {
            sets.push('is_fixed = ?', 'fixed_amount = ?', 'auto_day = ?', 'require_confirm = ?');
            params.push(isFixed, isFixed ? fixed_amount : null, isFixed ? auto_day : null, isFixed && Number(require_confirm) === 1 ? 1 : 0);
        }

        params.push(req.params.id, req.user.id);
        const result = db.prepare(`UPDATE categories SET ${sets.join(', ')} WHERE id = ? AND user_id = ?`).run(...params);
        if (result.changes === 0) return { statusCode: 404, body: { error: 'Category not found' } };
        if (Number(is_active) !== 0) {
            clearDeletedRecord(req.user.id, 'category', Number(req.params.id));
        }
        const body = getRowById('categories', req.params.id);
        broadcastChange(req, req.user.id, 'category', 'updated');
        return { statusCode: 200, body };
    });
});

router.delete('/categories/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const timestamp = nowIso();
        const result = db.prepare('UPDATE categories SET is_active = 0, updated_at = ? WHERE id = ? AND user_id = ?').run(timestamp, req.params.id, req.user.id);
        if (result.changes === 0) return { statusCode: 404, body: { error: 'Category not found' } };
        recordDeletedRecord(req.user.id, 'category', Number(req.params.id), timestamp);
        const body = getRowById('categories', req.params.id);
        broadcastChange(req, req.user.id, 'category', 'deleted');
        return { statusCode: 200, body };
    });
});

// Income Sources
router.get('/income_sources', (req, res) => {
    const includeInactive = req.query.include_inactive === '1' || req.query.include_inactive === 'true';
    const sources = includeInactive
        ? db.prepare('SELECT * FROM income_sources WHERE user_id = ? ORDER BY sort_order ASC, id ASC').all(req.user.id)
        : db.prepare('SELECT * FROM income_sources WHERE user_id = ? AND is_active = 1 ORDER BY sort_order ASC, id ASC').all(req.user.id);
    res.json(sources);
});

router.post('/income_sources', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name, is_fixed, fixed_amount, auto_day, require_confirm } = req.body;

        const isFixed = Number(is_fixed) === 1 ? 1 : 0;
        if (isFixed && (fixed_amount == null || fixed_amount <= 0)) return { statusCode: 400, body: { error: 'fixed_amount is required and must be > 0 for fixed income sources' } };
        if (isFixed && (auto_day == null || auto_day < 1 || auto_day > 31)) return { statusCode: 400, body: { error: 'auto_day must be between 1 and 31 for fixed income sources' } };
        const requireConfirm = isFixed && Number(require_confirm) === 1 ? 1 : 0;

        try {
            const existing = db.prepare('SELECT * FROM income_sources WHERE user_id = ? AND name = ?').get(req.user.id, name);
            if (existing) {
                return { statusCode: 200, body: existing };
            }

            const result = db.prepare('SELECT MAX(sort_order) as maxOrder FROM income_sources WHERE user_id = ?').get(req.user.id);
            const nextOrder = (result.maxOrder || 0) + 1;
            const timestamp = nowIso();

            const stmt = db.prepare('INSERT INTO income_sources (user_id, name, sort_order, is_fixed, fixed_amount, auto_day, require_confirm, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)');
            const insertResult = stmt.run(req.user.id, name, nextOrder, isFixed, isFixed ? fixed_amount : null, isFixed ? auto_day : null, requireConfirm, timestamp, timestamp);
            const body = getRowById('income_sources', insertResult.lastInsertRowid);
            broadcastChange(req, req.user.id, 'income_source', 'created');
            return { statusCode: 200, body };
        } catch (err) {
            // Гонка: параллельный запрос уже создал источник с этим именем (UNIQUE) — вернём его.
            if (String(err.code || '').startsWith('SQLITE_CONSTRAINT')) {
                const row = db.prepare('SELECT * FROM income_sources WHERE user_id = ? AND name = ?').get(req.user.id, name);
                if (row) return { statusCode: 200, body: row };
            }
            return { statusCode: 500, body: { error: err.message } };
        }
    });
});

router.put('/income_sources/reorder', (req, res) => {
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

router.put('/income_sources/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name, is_active, is_fixed, fixed_amount, auto_day, require_confirm } = req.body;
        const { id } = req.params;

        const isFixed = is_fixed != null ? (Number(is_fixed) === 1 ? 1 : 0) : undefined;
        if (isFixed === 1 && (fixed_amount == null || fixed_amount <= 0)) return { statusCode: 400, body: { error: 'fixed_amount is required and must be > 0 for fixed income sources' } };
        if (isFixed === 1 && (auto_day == null || auto_day < 1 || auto_day > 31)) return { statusCode: 400, body: { error: 'auto_day must be between 1 and 31 for fixed income sources' } };

        try {
            const sets = ['updated_at = ?'];
            const params = [nowIso()];

            if (name != null) { sets.push('name = ?'); params.push(name); }
            if (is_active != null) { sets.push('is_active = ?'); params.push(is_active); }

            if (isFixed !== undefined) {
                sets.push('is_fixed = ?', 'fixed_amount = ?', 'auto_day = ?', 'require_confirm = ?');
                params.push(isFixed, isFixed ? fixed_amount : null, isFixed ? auto_day : null, isFixed && Number(require_confirm) === 1 ? 1 : 0);
            }

            params.push(id, req.user.id);
            const result = db.prepare(`UPDATE income_sources SET ${sets.join(', ')} WHERE id = ? AND user_id = ?`).run(...params);
            if (result.changes === 0) return { statusCode: 404, body: { error: 'Income source not found' } };
            if (is_active != null && Number(is_active) !== 0) {
                clearDeletedRecord(req.user.id, 'income_source', Number(id));
            }
            const body = getRowById('income_sources', id);
            broadcastChange(req, req.user.id, 'income_source', 'updated');
            return { statusCode: 200, body };
        } catch (err) {
            return { statusCode: 500, body: { error: err.message } };
        }
    });
});

router.delete('/income_sources/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { id } = req.params;
        try {
            const timestamp = nowIso();
            const stmt = db.prepare('UPDATE income_sources SET is_active = 0, updated_at = ? WHERE id = ? AND user_id = ?');
            const result = stmt.run(timestamp, id, req.user.id);
            if (result.changes === 0) return { statusCode: 404, body: { error: 'Income source not found' } };
            recordDeletedRecord(req.user.id, 'income_source', Number(id), timestamp);
            const body = getRowById('income_sources', id);
            broadcastChange(req, req.user.id, 'income_source', 'deleted');
            return { statusCode: 200, body };
        } catch (err) {
            return { statusCode: 500, body: { error: err.message } };
        }
    });
});

// Months
router.get('/months', (req, res) => {
    // Return list of months that have data
    const months = db.prepare('SELECT * FROM months WHERE user_id = ? ORDER BY year DESC, month DESC').all(req.user.id);
    res.json(months);
});

router.post('/months/ensure', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { year, month } = req.body;
        const id = getOrCreateMonth(req.user.id, year, month);
        autoCreateRecurringRecords(req.user.id, id, (userId, entity, action) => {
            broadcastChange(req, userId, entity, action);
        });
        return { statusCode: 200, body: getRowById('months', id) };
    });
});

// Incomes
router.get('/months/:monthId/incomes', (req, res) => {
    // Check access
    if (!checkMonthAccess(req.user.id, req.params.monthId)) return res.status(403).json({ error: 'Access denied' });
    
    const incomes = db.prepare('SELECT * FROM incomes WHERE month_id = ?').all(req.params.monthId);
    res.json(incomes);
});

router.post('/incomes', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { month_id, source, amount, date } = req.body;
        
        if (!checkMonthAccess(req.user.id, month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        const timestamp = nowIso();
        const info = db.prepare('INSERT INTO incomes (month_id, source, amount, date, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)').run(month_id, source, amount, date, timestamp, timestamp);
        const body = getRowById('incomes', info.lastInsertRowid);
        broadcastChange(req, req.user.id, 'income', 'created');
        return { statusCode: 200, body };
    });
});

router.put('/incomes/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { amount } = req.body;
        const income = db.prepare('SELECT month_id FROM incomes WHERE id = ?').get(req.params.id);
        if (!income) return { statusCode: 404, body: { error: 'Income not found' } };
        if (!checkMonthAccess(req.user.id, income.month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        db.prepare('UPDATE incomes SET amount = ?, updated_at = ? WHERE id = ?').run(amount, nowIso(), req.params.id);
        const body = getRowById('incomes', req.params.id);
        broadcastChange(req, req.user.id, 'income', 'updated');
        return { statusCode: 200, body };
    });
});

router.delete('/incomes/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const income = db.prepare('SELECT * FROM incomes WHERE id = ?').get(req.params.id);
        if (!income) return { statusCode: 404, body: { error: 'Income not found' } };
        if (!checkMonthAccess(req.user.id, income.month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        recordDeletedRecord(req.user.id, 'income', income.id);
        db.prepare('DELETE FROM incomes WHERE id = ?').run(req.params.id);
        broadcastChange(req, req.user.id, 'income', 'deleted');
        return { statusCode: 200, body: { success: true, id: income.id } };
    });
});

// Expenses
router.get('/months/:monthId/expenses', (req, res) => {
    if (!checkMonthAccess(req.user.id, req.params.monthId)) return res.status(403).json({ error: 'Access denied' });

    const expenses = db.prepare(`
    SELECT e.*, c.name as category_name 
    FROM expenses e 
    JOIN categories c ON e.category_id = c.id 
    WHERE e.month_id = ?
  `).all(req.params.monthId);
    res.json(expenses);
});

router.post('/expenses', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { month_id, category_id, amount, date, comment } = req.body;
        
        if (!checkMonthAccess(req.user.id, month_id)) return { statusCode: 403, body: { error: 'Access denied' } };
        
        const category = db.prepare('SELECT id FROM categories WHERE id = ? AND user_id = ?').get(category_id, req.user.id);
        if (!category) return { statusCode: 400, body: { error: 'Invalid category' } };

        const timestamp = nowIso();
        const info = db.prepare('INSERT INTO expenses (month_id, category_id, amount, date, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)').run(month_id, category_id, amount, date, comment || '', timestamp, timestamp);
        const body = getRowById('expenses', info.lastInsertRowid);
        broadcastChange(req, req.user.id, 'expense', 'created');
        return { statusCode: 200, body };
    });
});

router.put('/expenses/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { amount, comment } = req.body;

        const expense = db.prepare('SELECT month_id FROM expenses WHERE id = ?').get(req.params.id);
        if (!expense) return { statusCode: 404, body: { error: 'Expense not found' } };
        if (!checkMonthAccess(req.user.id, expense.month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        // comment — необязательное поле: если не передано, не трогаем (обратная совместимость)
        if (comment !== undefined) {
            db.prepare('UPDATE expenses SET amount = ?, comment = ?, updated_at = ? WHERE id = ?').run(amount, comment ?? null, nowIso(), req.params.id);
        } else {
            db.prepare('UPDATE expenses SET amount = ?, updated_at = ? WHERE id = ?').run(amount, nowIso(), req.params.id);
        }
        const body = getRowById('expenses', req.params.id);
        broadcastChange(req, req.user.id, 'expense', 'updated');
        return { statusCode: 200, body };
    });
});

router.delete('/expenses/:id', (req, res) => {
    return executeIdempotent(req, res, () => {
        const expense = db.prepare('SELECT * FROM expenses WHERE id = ?').get(req.params.id);
        if (!expense) return { statusCode: 404, body: { error: 'Expense not found' } };
        if (!checkMonthAccess(req.user.id, expense.month_id)) return { statusCode: 403, body: { error: 'Access denied' } };

        recordDeletedRecord(req.user.id, 'expense', expense.id);
        db.prepare('DELETE FROM expenses WHERE id = ?').run(req.params.id);
        broadcastChange(req, req.user.id, 'expense', 'deleted');
        return { statusCode: 200, body: { success: true, id: expense.id } };
    });
});

// Budgets (Limits)
router.get('/months/:monthId/budgets', (req, res) => {
    if (!checkMonthAccess(req.user.id, req.params.monthId)) return res.status(403).json({ error: 'Access denied' });

    const budgets = db.prepare(`
    SELECT b.*, c.name as category_name 
    FROM budgets b 
    JOIN categories c ON b.category_id = c.id 
    WHERE b.month_id = ?
  `).all(req.params.monthId);
    res.json(budgets);
});

router.post('/budgets', (req, res) => {
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

        broadcastChange(req, req.user.id, 'budget', 'updated');
        return { statusCode: 200, body };
    });
});

// Savings
router.get('/savings_goals', (req, res) => {
    const goals = db.prepare('SELECT * FROM savings_goals WHERE user_id = ?').all(req.user.id);
    res.json(goals);
});

router.post('/savings_goals', (req, res) => {
    return executeIdempotent(req, res, () => {
        const { name, target_amount } = req.body;
        const timestamp = nowIso();
        const info = db.prepare('INSERT INTO savings_goals (user_id, name, target_amount, current_amount, created_at, updated_at) VALUES (?, ?, ?, 0, ?, ?)').run(req.user.id, name, target_amount || 0, timestamp, timestamp);
        const body = getRowById('savings_goals', info.lastInsertRowid);
        broadcastChange(req, req.user.id, 'savings_goal', 'created');
        return { statusCode: 200, body };
    });
});

router.put('/savings_goals/:id', (req, res) => {
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
        broadcastChange(req, req.user.id, 'savings_goal', 'updated');
        return { statusCode: 200, body };
    });
});

router.delete('/savings_goals/:id', (req, res) => {
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
        broadcastChange(req, req.user.id, 'savings_goal', 'deleted');
        return { statusCode: 200, body: { success: true, id: goal.id } };
    });
});

router.post('/savings_transactions', (req, res) => {
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
        broadcastChange(req, req.user.id, 'savings_transaction', 'created');
        return { statusCode: 200, body };
    });
});

router.delete('/savings_transactions/:id', (req, res) => {
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
        broadcastChange(req, req.user.id, 'savings_transaction', 'deleted');
        return { statusCode: 200, body: { success: true, id: transaction.id } };
    });
});

router.put('/savings_transactions/:id', (req, res) => {
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
        broadcastChange(req, req.user.id, 'savings_transaction', 'updated');
        return { statusCode: 200, body };
    });
});

router.get('/savings_transactions/:goalId', (req, res) => {
    const goal = db.prepare('SELECT id FROM savings_goals WHERE id = ? AND user_id = ?').get(req.params.goalId, req.user.id);
    if (!goal) return res.status(404).json({ error: 'Goal not found' });

    const transactions = db.prepare('SELECT * FROM savings_transactions WHERE goal_id = ? ORDER BY date DESC').all(req.params.goalId);
    res.json(transactions);
});

// Planned: виртуальный план-слой регулярных платежей (ещё не материализованные
// фиксированные категории/источники месяца). Ничего не хранится, кроме исключений
// в planned_overrides (пропуск / override суммы или дня на конкретный месяц).
const PLANNED_TEMPLATE_TYPES = ['category', 'income_source'];

// Общие проверки planned-роутов; возвращает { error } ИЛИ контекст
function resolvePlannedContext(req) {
    const { monthId, templateType } = req.params;
    const templateId = Number(req.params.templateId);

    if (!PLANNED_TEMPLATE_TYPES.includes(templateType)) {
        return { error: { statusCode: 400, body: { error: 'Invalid template type' } } };
    }
    if (!Number.isInteger(templateId) || templateId <= 0) {
        return { error: { statusCode: 400, body: { error: 'Invalid template id' } } };
    }
    if (!checkMonthAccess(req.user.id, monthId)) {
        return { error: { statusCode: 403, body: { error: 'Access denied' } } };
    }
    const template = getFixedTemplate(req.user.id, templateType, templateId);
    if (!template) {
        return { error: { statusCode: 404, body: { error: 'Fixed template not found' } } };
    }
    return { monthId, templateType, templateId, template };
}

function findPlannedItem(userId, monthId, templateType, templateId) {
    const planned = getPlannedRecords(userId, monthId);
    const list = templateType === 'category' ? planned.expenses : planned.incomes;
    return list.find(item => item.template_id === templateId) ?? null;
}

router.get('/months/:monthId/planned', (req, res) => {
    const monthId = req.params.monthId;
    if (!checkMonthAccess(req.user.id, monthId)) return res.status(403).json({ error: 'Access denied' });

    const planned = getPlannedRecords(req.user.id, monthId);
    res.json({ ...planned, totals: computePlannedAggregates(planned) });
});

// Сырое состояние план-слоя месяца — для sync-pull KMP-клиентов,
// которые вычисляют план локально (оффлайн) той же формулой, что сервер
router.get('/months/:monthId/planned-state', (req, res) => {
    const monthId = req.params.monthId;
    if (!checkMonthAccess(req.user.id, monthId)) return res.status(403).json({ error: 'Access denied' });

    const auto_created = db.prepare(
        'SELECT template_type, template_id, created_record_type, created_at FROM auto_created_records WHERE user_id = ? AND month_id = ?'
    ).all(req.user.id, monthId);
    const overrides = db.prepare(
        'SELECT template_type, template_id, override_amount, override_day, is_skipped, updated_at FROM planned_overrides WHERE user_id = ? AND month_id = ?'
    ).all(req.user.id, monthId);

    res.json({ auto_created, overrides });
});

// Пропуск/override планового платежа на месяц (upsert; отсутствующее поле = не менять, null = сброс)
router.put('/months/:monthId/planned/:templateType/:templateId', (req, res) => {
    return executeIdempotent(req, res, () => {
        const ctx = resolvePlannedContext(req);
        if (ctx.error) return ctx.error;
        const { monthId, templateType, templateId } = ctx;

        if (getAutoCreatedRecord(req.user.id, templateType, templateId, monthId)) {
            return { statusCode: 409, body: { error: 'Payment already created for this month; edit the record itself' } };
        }

        const { is_skipped, override_amount, override_day } = req.body ?? {};
        if (override_amount !== undefined && override_amount !== null && !(Number(override_amount) > 0)) {
            return { statusCode: 400, body: { error: 'override_amount must be > 0' } };
        }
        if (override_day !== undefined && override_day !== null && (!Number.isInteger(override_day) || override_day < 1 || override_day > 31)) {
            return { statusCode: 400, body: { error: 'override_day must be between 1 and 31' } };
        }

        const existing = db.prepare(
            'SELECT * FROM planned_overrides WHERE user_id = ? AND template_type = ? AND template_id = ? AND month_id = ?'
        ).get(req.user.id, templateType, templateId, monthId);

        const merged = {
            is_skipped: is_skipped !== undefined ? (Number(is_skipped) === 1 ? 1 : 0) : (existing?.is_skipped ?? 0),
            override_amount: override_amount !== undefined ? override_amount : (existing?.override_amount ?? null),
            override_day: override_day !== undefined ? override_day : (existing?.override_day ?? null),
        };

        const timestamp = nowIso();
        if (!merged.is_skipped && merged.override_amount == null && merged.override_day == null) {
            // Всё дефолтное — исключение больше не нужно
            db.prepare('DELETE FROM planned_overrides WHERE user_id = ? AND template_type = ? AND template_id = ? AND month_id = ?')
                .run(req.user.id, templateType, templateId, monthId);
        } else {
            db.prepare(`
                INSERT INTO planned_overrides (user_id, template_type, template_id, month_id, override_amount, override_day, is_skipped, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id, template_type, template_id, month_id)
                DO UPDATE SET override_amount = excluded.override_amount, override_day = excluded.override_day, is_skipped = excluded.is_skipped, updated_at = excluded.updated_at
            `).run(req.user.id, templateType, templateId, monthId, merged.override_amount, merged.override_day, merged.is_skipped, timestamp, timestamp);
        }

        broadcastChange(req, req.user.id, 'planned', 'updated');
        return { statusCode: 200, body: { success: true, item: findPlannedItem(req.user.id, monthId, templateType, templateId) } };
    });
});

// Полный сброс исключения к шаблону (идемпотентен и при отсутствии строки)
router.delete('/months/:monthId/planned/:templateType/:templateId/override', (req, res) => {
    return executeIdempotent(req, res, () => {
        const ctx = resolvePlannedContext(req);
        if (ctx.error) return ctx.error;
        const { monthId, templateType, templateId } = ctx;

        db.prepare('DELETE FROM planned_overrides WHERE user_id = ? AND template_type = ? AND template_id = ? AND month_id = ?')
            .run(req.user.id, templateType, templateId, monthId);

        broadcastChange(req, req.user.id, 'planned', 'updated');
        return { statusCode: 200, body: { success: true, item: findPlannedItem(req.user.id, monthId, templateType, templateId) } };
    });
});

// «Оплачено» (require_confirm) / досрочная оплата: материализует плановый платёж в реальную запись
router.post('/months/:monthId/planned/:templateType/:templateId/confirm', (req, res) => {
    return executeIdempotent(req, res, () => {
        const ctx = resolvePlannedContext(req);
        if (ctx.error) return ctx.error;
        const { monthId, templateType, templateId, template } = ctx;

        const override = db.prepare(
            'SELECT * FROM planned_overrides WHERE user_id = ? AND template_type = ? AND template_id = ? AND month_id = ?'
        ).get(req.user.id, templateType, templateId, monthId);
        if (override?.is_skipped) {
            return { statusCode: 409, body: { error: 'Payment is skipped for this month; unskip it first' } };
        }
        if (getAutoCreatedRecord(req.user.id, templateType, templateId, monthId)) {
            return { statusCode: 409, body: { error: 'Payment already created for this month' } };
        }

        const month = getRowById('months', monthId);
        const { amount, date } = req.body ?? {};
        if (amount !== undefined && amount !== null && !(Number(amount) > 0)) {
            return { statusCode: 400, body: { error: 'amount must be > 0' } };
        }

        let day;
        if (date != null) {
            const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(date));
            if (!match) return { statusCode: 400, body: { error: 'date must be YYYY-MM-DD' } };
            if (Number(match[1]) !== month.year || Number(match[2]) !== month.month) {
                return { statusCode: 400, body: { error: 'date must belong to the month' } };
            }
            day = Number(match[3]);
            if (day < 1 || day > daysInMonth(month.year, month.month)) {
                return { statusCode: 400, body: { error: 'date is out of range for the month' } };
            }
        } else {
            const now = new Date();
            const isCurrentMonth = month.year === now.getFullYear() && month.month === now.getMonth() + 1;
            day = isCurrentMonth ? now.getDate() : (override?.override_day ?? template.auto_day);
        }

        const effectiveAmount = amount ?? override?.override_amount ?? template.fixed_amount;

        try {
            const { recordType, record } = materializePlannedRecord(req.user.id, month, templateType, template, {
                amount: effectiveAmount,
                day,
            });
            broadcastChange(req, req.user.id, recordType, 'created');
            broadcastChange(req, req.user.id, 'planned', 'updated');
            return { statusCode: 200, body: record };
        } catch (e) {
            // Гонка с автопостингом ensure: UNIQUE в auto_created_records
            if (String(e?.code).startsWith('SQLITE_CONSTRAINT')) {
                return { statusCode: 409, body: { error: 'Payment already created for this month' } };
            }
            throw e;
        }
    });
});

// Summary / Dashboard Data
router.get('/months/:monthId/summary', (req, res) => {
    const monthId = req.params.monthId;
    if (!checkMonthAccess(req.user.id, monthId)) return res.status(403).json({ error: 'Access denied' });

    const totalIncome = db.prepare('SELECT SUM(amount) as total FROM incomes WHERE month_id = ?').get(monthId).total || 0;
    const totalExpenses = db.prepare('SELECT SUM(amount) as total FROM expenses WHERE month_id = ?').get(monthId).total || 0;

    // Savings contributions (positive amounts in transactions linked to this month, EXCLUDING adjustments)
    const totalSavings = db.prepare('SELECT SUM(amount) as total FROM savings_transactions WHERE month_id = ? AND amount > 0 AND (is_adjustment = 0 OR is_adjustment IS NULL)').get(monthId).total || 0;

    const balance = totalIncome - totalExpenses - totalSavings;
    const { plannedExpenses, plannedIncomes } = computePlannedAggregates(getPlannedRecords(req.user.id, monthId));

    res.json({
        income: totalIncome,
        expenses: totalExpenses,
        savings: totalSavings,
        balance,
        plannedExpenses,
        plannedIncomes,
        forecastExpenses: totalExpenses + plannedExpenses,
        forecastBalance: balance + plannedIncomes - plannedExpenses
    });
});

// Cumulative balance up to and including the specified month
router.get('/analytics/cumulative-balance', (req, res) => {
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

router.get('/analytics/trend', (req, res) => {
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

module.exports = router;

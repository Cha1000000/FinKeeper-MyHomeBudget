const express = require('express');
const cors = require('cors');
const Database = require('better-sqlite3');
const path = require('path');

const app = express();
const PORT = 3002;
const dbPath = path.resolve(__dirname, 'database.sqlite');
const db = new Database(dbPath);

app.use(cors());
app.use(express.json());

// --- Helper Functions ---
function getOrCreateMonth(year, month) {
    const row = db.prepare('SELECT id FROM months WHERE year = ? AND month = ?').get(year, month);
    if (row) return row.id;
    const info = db.prepare('INSERT INTO months (year, month) VALUES (?, ?)').run(year, month);
    return info.lastInsertRowid;
}

// --- API Endpoints ---

// Categories
app.get('/api/categories', (req, res) => {
    const categories = db.prepare('SELECT * FROM categories WHERE is_active = 1 ORDER BY sort_order ASC, name ASC').all();
    res.json(categories);
});

app.post('/api/categories', (req, res) => {
    const { name } = req.body;
    // Get max sort_order
    const result = db.prepare('SELECT MAX(sort_order) as maxOrder FROM categories').get();
    const nextOrder = (result.maxOrder || 0) + 1;
    
    const info = db.prepare('INSERT INTO categories (name, sort_order) VALUES (?, ?)').run(name, nextOrder);
    res.json({ id: info.lastInsertRowid, name, is_active: 1, sort_order: nextOrder });
});

app.put('/api/categories/reorder', (req, res) => {
    const { ids } = req.body;
    if (!Array.isArray(ids)) return res.status(400).json({ error: 'ids array required' });

    const updateStmt = db.prepare('UPDATE categories SET sort_order = ? WHERE id = ?');
    
    const transact = db.transaction((idList) => {
        idList.forEach((id, index) => {
            updateStmt.run(index, Number(id));
        });
    });

    try {
        transact(ids);
        res.json({ success: true });
    } catch (err) {
        console.error("Reorder failed", err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/categories/:id', (req, res) => {
    const { name, is_active } = req.body;
    db.prepare('UPDATE categories SET name = ?, is_active = ? WHERE id = ?').run(name, is_active, req.params.id);
    res.json({ success: true });
});

// Income Sources
app.get('/api/income_sources', (req, res) => {
    const sources = db.prepare('SELECT * FROM income_sources WHERE is_active = 1 ORDER BY id').all();
    res.json(sources);
});

app.post('/api/income_sources', (req, res) => {
    const { name } = req.body;
    try {
        const stmt = db.prepare('INSERT INTO income_sources (name) VALUES (?)');
        const result = stmt.run(name);
        res.json({ id: result.lastInsertRowid, name, is_active: 1 });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/income_sources/:id', (req, res) => {
    const { name, is_active } = req.body;
    const { id } = req.params;
    try {
        const stmt = db.prepare('UPDATE income_sources SET name = ?, is_active = ? WHERE id = ?');
        stmt.run(name, is_active ?? 1, id);
        res.json({ id, name, is_active: is_active ?? 1 });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/income_sources/:id', (req, res) => {
    const { id } = req.params;
    try {
        const stmt = db.prepare('UPDATE income_sources SET is_active = 0 WHERE id = ?');
        stmt.run(id);
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Months
app.get('/api/months', (req, res) => {
    // Return list of months that have data
    const months = db.prepare('SELECT * FROM months ORDER BY year DESC, month DESC').all();
    res.json(months);
});

app.post('/api/months/ensure', (req, res) => {
    const { year, month } = req.body;
    const id = getOrCreateMonth(year, month);
    res.json({ id, year, month });
});

// Incomes
app.get('/api/months/:monthId/incomes', (req, res) => {
    const incomes = db.prepare('SELECT * FROM incomes WHERE month_id = ?').all(req.params.monthId);
    res.json(incomes);
});

app.post('/api/incomes', (req, res) => {
    const { month_id, source, amount, date } = req.body;
    const info = db.prepare('INSERT INTO incomes (month_id, source, amount, date) VALUES (?, ?, ?, ?)').run(month_id, source, amount, date);
    res.json({ id: info.lastInsertRowid, ...req.body });
});

app.put('/api/incomes/:id', (req, res) => {
    const { amount } = req.body;
    db.prepare('UPDATE incomes SET amount = ? WHERE id = ?').run(amount, req.params.id);
    res.json({ success: true });
});

app.delete('/api/incomes/:id', (req, res) => {
    db.prepare('DELETE FROM incomes WHERE id = ?').run(req.params.id);
    res.json({ success: true });
});

// Expenses
app.get('/api/months/:monthId/expenses', (req, res) => {
    const expenses = db.prepare(`
    SELECT e.*, c.name as category_name 
    FROM expenses e 
    JOIN categories c ON e.category_id = c.id 
    WHERE e.month_id = ?
  `).all(req.params.monthId);
    res.json(expenses);
});

app.post('/api/expenses', (req, res) => {
    const { month_id, category_id, amount, date, comment } = req.body;
    const info = db.prepare('INSERT INTO expenses (month_id, category_id, amount, date, comment) VALUES (?, ?, ?, ?, ?)').run(month_id, category_id, amount, date, comment || '');
    res.json({ id: info.lastInsertRowid, ...req.body });
});

app.put('/api/expenses/:id', (req, res) => {
    const { amount } = req.body;
    db.prepare('UPDATE expenses SET amount = ? WHERE id = ?').run(amount, req.params.id);
    res.json({ success: true });
});

app.delete('/api/expenses/:id', (req, res) => {
    db.prepare('DELETE FROM expenses WHERE id = ?').run(req.params.id);
    res.json({ success: true });
});

// Budgets (Limits)
app.get('/api/months/:monthId/budgets', (req, res) => {
    const budgets = db.prepare(`
    SELECT b.*, c.name as category_name 
    FROM budgets b 
    JOIN categories c ON b.category_id = c.id 
    WHERE b.month_id = ?
  `).all(req.params.monthId);
    res.json(budgets);
});

app.post('/api/budgets', (req, res) => {
    const { month_id, category_id, limit_amount } = req.body;

    // Check if exists
    const existing = db.prepare('SELECT id FROM budgets WHERE month_id = ? AND category_id = ?').get(month_id, category_id);

    if (existing) {
        db.prepare('UPDATE budgets SET limit_amount = ? WHERE id = ?').run(limit_amount, existing.id);
        res.json({ id: existing.id, month_id, category_id, limit_amount });
    } else {
        const info = db.prepare('INSERT INTO budgets (month_id, category_id, limit_amount) VALUES (?, ?, ?)').run(month_id, category_id, limit_amount);
        res.json({ id: info.lastInsertRowid, month_id, category_id, limit_amount });
    }
});

// Savings
app.get('/api/savings_goals', (req, res) => {
    const goals = db.prepare('SELECT * FROM savings_goals').all();
    res.json(goals);
});

app.post('/api/savings_goals', (req, res) => {
    const { name, target_amount } = req.body;
    const info = db.prepare('INSERT INTO savings_goals (name, target_amount, current_amount) VALUES (?, ?, 0)').run(name, target_amount || 0);
    res.json({ id: info.lastInsertRowid, name, target_amount, current_amount: 0 });
});

app.put('/api/savings_goals/:id', (req, res) => {
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
    
    if (updates.length === 0) {
        return res.json({ success: true });
    }
    
    const sql = `UPDATE savings_goals SET ${updates.join(', ')} WHERE id = ?`;
    db.prepare(sql).run(...values, req.params.id);
    res.json({ success: true });
});

app.delete('/api/savings_goals/:id', (req, res) => {
    // First delete all transactions for this goal
    db.prepare('DELETE FROM savings_transactions WHERE goal_id = ?').run(req.params.id);
    // Then delete the goal
    db.prepare('DELETE FROM savings_goals WHERE id = ?').run(req.params.id);
    res.json({ success: true });
});

app.post('/api/savings_transactions', (req, res) => {
    const { goal_id, amount, date, month_id } = req.body;

    const transact = db.transaction(() => {
        const info = db.prepare('INSERT INTO savings_transactions (goal_id, amount, date, month_id) VALUES (?, ?, ?, ?)').run(goal_id, amount, date, month_id);
        // Update goal balance
        db.prepare('UPDATE savings_goals SET current_amount = current_amount + ? WHERE id = ?').run(amount, goal_id);
        return info;
    });

    const info = transact();
    res.json({ id: info.lastInsertRowid, ...req.body });
});

app.get('/api/savings_transactions/:goalId', (req, res) => {
    const transactions = db.prepare('SELECT * FROM savings_transactions WHERE goal_id = ? ORDER BY date DESC').all(req.params.goalId);
    res.json(transactions);
});

// Summary / Dashboard Data
app.get('/api/months/:monthId/summary', (req, res) => {
    const monthId = req.params.monthId;

    const totalIncome = db.prepare('SELECT SUM(amount) as total FROM incomes WHERE month_id = ?').get(monthId).total || 0;
    const totalExpenses = db.prepare('SELECT SUM(amount) as total FROM expenses WHERE month_id = ?').get(monthId).total || 0;

    // Savings contributions (positive amounts in transactions linked to this month)
    // Actually savings transactions could be withdrawals too.
    // Usually "Savings" in budget means money put INTO savings.
    const totalSavings = db.prepare('SELECT SUM(amount) as total FROM savings_transactions WHERE month_id = ? AND amount > 0').get(monthId).total || 0;

    res.json({
        income: totalIncome,
        expenses: totalExpenses,
        savings: totalSavings,
        balance: totalIncome - totalExpenses - totalSavings
    });
});

app.get('/api/analytics/trend', (req, res) => {
    // Get last 6 months
    const months = db.prepare('SELECT * FROM months ORDER BY year DESC, month DESC LIMIT 6').all().reverse();

    const data = months.map(m => {
        const income = db.prepare('SELECT SUM(amount) as total FROM incomes WHERE month_id = ?').get(m.id).total || 0;
        const expense = db.prepare('SELECT SUM(amount) as total FROM expenses WHERE month_id = ?').get(m.id).total || 0;
        return {
            month: `${m.month}/${m.year}`,
            income,
            expense
        };
    });

    res.json(data);
});

// Serve static files from client/dist in production
const clientDistPath = path.join(__dirname, '..', 'client', 'dist');
app.use(express.static(clientDistPath));

// Handle SPA routing - return index.html for all non-API routes
app.get('*', (req, res) => {
    // Skip API routes
    if (req.path.startsWith('/api/')) {
        return res.status(404).json({ error: 'API endpoint not found' });
    }
    res.sendFile(path.join(clientDistPath, 'index.html'));
});

app.listen(PORT, () => {
    console.log(`Server running on http://localhost:${PORT}`);
});

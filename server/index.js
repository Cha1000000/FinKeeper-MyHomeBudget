const express = require('express');
const cors = require('cors');
const Database = require('better-sqlite3');
const path = require('path');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const http = require('http');
const { WebSocketServer } = require('ws');
const { initialCategories, initialSavings, initialIncomeSources } = require('./default_data');

const app = express();
const PORT = 3002;
const JWT_SECRET = process.env.JWT_SECRET || 'my-home-budget-secret-key-change-this';
const dbPath = path.resolve(__dirname, 'database.sqlite');
const db = new Database(dbPath);

app.use(cors());
app.use(express.json());

const MAX_BACKUPS = 5;

// --- Helper Functions ---
function getOrCreateMonth(userId, year, month) {
    const row = db.prepare('SELECT id FROM months WHERE user_id = ? AND year = ? AND month = ?').get(userId, year, month);
    if (row) return row.id;
    const info = db.prepare('INSERT INTO months (user_id, year, month) VALUES (?, ?, ?)').run(userId, year, month);
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
        const backups = db.prepare('SELECT id FROM user_backups WHERE user_id = ? ORDER BY created_at DESC').all(userId);
        if (backups.length > MAX_BACKUPS) {
            const toDelete = backups.slice(MAX_BACKUPS).map(b => b.id);
            const placeholders = toDelete.map(() => '?').join(',');
            db.prepare(`DELETE FROM user_backups WHERE id IN (${placeholders})`).run(toDelete);
        }
        console.log(`Backup created for user ${userId}`);
    } catch (e) {
        console.error("Backup failed", e);
    }
}

// --- Middleware ---
const authenticateToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1]; // Bearer TOKEN

    if (token == null) return res.sendStatus(401);

    jwt.verify(token, JWT_SECRET, (err, user) => {
        if (err) return res.sendStatus(403);
        req.user = user;
        next();
    });
};

// --- API Endpoints ---

// Auth Routes (Public)
app.post('/api/auth/register', (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) return res.status(400).json({ error: 'Username and password required' });

    try {
        const hashedPassword = bcrypt.hashSync(password, 8);

        const registerTransaction = db.transaction(() => {
            const info = db.prepare('INSERT INTO users (username, password_hash) VALUES (?, ?)').run(username, hashedPassword);
            const userId = info.lastInsertRowid;

            // Seed Categories
            const insertCat = db.prepare('INSERT INTO categories (user_id, name, sort_order) VALUES (?, ?, ?)');
            initialCategories.forEach((cat, index) => insertCat.run(userId, cat, index + 1));

            // Seed Income Sources
            const insertSource = db.prepare('INSERT INTO income_sources (user_id, name) VALUES (?, ?)');
            initialIncomeSources.forEach(source => insertSource.run(userId, source));

            // Seed Savings Goals
            const insertGoal = db.prepare('INSERT INTO savings_goals (user_id, name) VALUES (?, ?)');
            initialSavings.forEach(goal => insertGoal.run(userId, goal));

            return { id: userId, username };
        });

        const user = registerTransaction();
        
        // Create initial backup
        createBackup(user.id);

        const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '365d' }); // Valid for 1 year
        res.json({ token, user });
    } catch (err) {
        if (err.message.includes('UNIQUE constraint failed')) {
            return res.status(409).json({ error: 'Username already exists' });
        }
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/auth/login', (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) return res.status(400).json({ error: 'Username and password required' });

    const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
    if (!user) return res.status(401).json({ error: 'Invalid credentials' });

    const passwordIsValid = bcrypt.compareSync(password, user.password_hash);
    if (!passwordIsValid) return res.status(401).json({ error: 'Invalid credentials' });

    // Create backup on login
    createBackup(user.id);

    const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '365d' }); // Valid for 1 year
    res.json({ token, user: { id: user.id, username: user.username } });
});

app.get('/api/auth/me', authenticateToken, (req, res) => {
    // Just return the user info from the token/db to confirm validity
    const user = db.prepare('SELECT id, username, created_at FROM users WHERE id = ?').get(req.user.id);
    if (!user) return res.sendStatus(404);

    // Check last backup time to avoid spamming backups on reload
    const lastBackup = db.prepare('SELECT created_at FROM user_backups WHERE user_id = ? ORDER BY created_at DESC LIMIT 1').get(req.user.id);
    const shouldBackup = !lastBackup || (new Date() - new Date(lastBackup.created_at + 'Z')) > 60 * 60 * 1000; // 1 hour

    if (shouldBackup) {
        createBackup(req.user.id);
    }

    res.json(user);
});


// Protect all subsequent API routes
app.use('/api', authenticateToken); 

// --- User Management & Backup ---

app.put('/api/user/rename', (req, res) => {
    const { newUsername } = req.body;
    if (!newUsername) return res.status(400).json({ error: 'New username required' });
    
    try {
        db.prepare('UPDATE users SET username = ? WHERE id = ?').run(newUsername, req.user.id);
        res.json({ success: true, username: newUsername });
    } catch (err) {
        if (err.message.includes('UNIQUE constraint failed')) {
            return res.status(409).json({ error: 'Username already exists' });
        }
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/user/password', (req, res) => {
    const { newPassword } = req.body;
    if (!newPassword) return res.status(400).json({ error: 'New password required' });
    
    const hashedPassword = bcrypt.hashSync(newPassword, 8);
    db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(hashedPassword, req.user.id);
    res.json({ success: true });
});

app.post('/api/user/backup', (req, res) => {
    try {
        createBackup(req.user.id);
        res.json({ success: true });
    } catch (e) {
        console.error("Manual backup failed", e);
        res.status(500).json({ error: "Backup creation failed" });
    }
});

app.post('/api/user/restore', (req, res) => {
    const backup = db.prepare('SELECT data FROM user_backups WHERE user_id = ? ORDER BY created_at DESC LIMIT 1').get(req.user.id);
    
    if (!backup) return res.status(404).json({ error: 'No backup found' });
    
    const data = JSON.parse(backup.data);
    const userId = req.user.id;
    
    const restoreTransact = db.transaction(() => {
        // 1. Delete all current user data (order matters due to foreign keys)
        // Note: DELETE with WHERE foreign_key IN (SELECT ...) might be needed if cascade delete is not set (it is not set in our schema)
        
        // Helper to delete by ID list
        const deleteByIds = (table, ids) => {
             if (ids.length > 0) {
                 const ph = ids.map(() => '?').join(',');
                 db.prepare(`DELETE FROM ${table} WHERE id IN (${ph})`).run(ids);
             }
        };

        // We need to fetch current IDs to delete them? 
        // Or just delete by user_id for root tables, and by join for child tables.
        
        // Delete child tables first
        // savings_transactions
        db.prepare('DELETE FROM savings_transactions WHERE goal_id IN (SELECT id FROM savings_goals WHERE user_id = ?)').run(userId);
        
        // budgets
        db.prepare('DELETE FROM budgets WHERE category_id IN (SELECT id FROM categories WHERE user_id = ?)').run(userId);
        
        // expenses
        db.prepare('DELETE FROM expenses WHERE category_id IN (SELECT id FROM categories WHERE user_id = ?)').run(userId);
        
        // incomes
        db.prepare('DELETE FROM incomes WHERE month_id IN (SELECT id FROM months WHERE user_id = ?)').run(userId);
        
        // Delete root tables
        db.prepare('DELETE FROM months WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM categories WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM income_sources WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM savings_goals WHERE user_id = ?').run(userId);
        
        // 2. Insert backup data
        // We MUST preserve IDs to maintain relationships in the backup data.
        
        // Categories
        const insertCat = db.prepare('INSERT INTO categories (id, user_id, name, sort_order, is_active) VALUES (?, ?, ?, ?, ?)');
        data.categories.forEach(row => insertCat.run(row.id, userId, row.name, row.sort_order, row.is_active));
        
        // Income Sources
        const insertSource = db.prepare('INSERT INTO income_sources (id, user_id, name, is_active, created_at) VALUES (?, ?, ?, ?, ?)');
        data.income_sources.forEach(row => insertSource.run(row.id, userId, row.name, row.is_active, row.created_at || new Date().toISOString()));
        
        // Savings Goals
        const insertGoal = db.prepare('INSERT INTO savings_goals (id, user_id, name, target_amount, current_amount) VALUES (?, ?, ?, ?, ?)');
        data.savings_goals.forEach(row => insertGoal.run(row.id, userId, row.name, row.target_amount, row.current_amount));
        
        // Months
        const insertMonth = db.prepare('INSERT INTO months (id, user_id, year, month) VALUES (?, ?, ?, ?)');
        data.months.forEach(row => insertMonth.run(row.id, userId, row.year, row.month));
        
        // Child tables
        const insertIncome = db.prepare('INSERT INTO incomes (id, month_id, source, amount, date) VALUES (?, ?, ?, ?, ?)');
        data.incomes.forEach(row => insertIncome.run(row.id, row.month_id, row.source, row.amount, row.date));
        
        const insertExpense = db.prepare('INSERT INTO expenses (id, month_id, category_id, amount, date, comment) VALUES (?, ?, ?, ?, ?, ?)');
        data.expenses.forEach(row => insertExpense.run(row.id, row.month_id, row.category_id, row.amount, row.date, row.comment));
        
        const insertBudget = db.prepare('INSERT INTO budgets (id, month_id, category_id, limit_amount) VALUES (?, ?, ?, ?)');
        data.budgets.forEach(row => insertBudget.run(row.id, row.month_id, row.category_id, row.limit_amount));
        
        const insertTrans = db.prepare('INSERT INTO savings_transactions (id, goal_id, amount, date, month_id, is_adjustment) VALUES (?, ?, ?, ?, ?, ?)');
        data.savings_transactions.forEach(row => insertTrans.run(row.id, row.goal_id, row.amount, row.date, row.month_id, row.is_adjustment || 0));
    });

    try {
        restoreTransact();
        res.json({ success: true });
        broadcastChange(req.user.id, 'all', 'restored');
    } catch (e) {
        console.error("Restore failed", e);
        res.status(500).json({ error: e.message });
    }
});


// Categories
app.get('/api/categories', (req, res) => {
    const categories = db.prepare('SELECT * FROM categories WHERE user_id = ? AND is_active = 1 ORDER BY sort_order ASC, name ASC').all(req.user.id);
    res.json(categories);
});

app.post('/api/categories', (req, res) => {
    const { name } = req.body;
    // Get max sort_order
    const result = db.prepare('SELECT MAX(sort_order) as maxOrder FROM categories WHERE user_id = ?').get(req.user.id);
    const nextOrder = (result.maxOrder || 0) + 1;
    
    const info = db.prepare('INSERT INTO categories (user_id, name, sort_order) VALUES (?, ?, ?)').run(req.user.id, name, nextOrder);
    res.json({ id: info.lastInsertRowid, name, is_active: 1, sort_order: nextOrder });
    broadcastChange(req.user.id, 'category', 'created');
});

app.put('/api/categories/reorder', (req, res) => {
    const { ids } = req.body;
    if (!Array.isArray(ids)) return res.status(400).json({ error: 'ids array required' });

    // Ensure all categories belong to user
    // Optimization: Just update where id IN (...) AND user_id = ?
    // But sort order update is per row.
    
    const updateStmt = db.prepare('UPDATE categories SET sort_order = ? WHERE id = ? AND user_id = ?');
    
    const transact = db.transaction((idList) => {
        idList.forEach((id, index) => {
            updateStmt.run(index, Number(id), req.user.id);
        });
    });

    try {
        transact(ids);
        res.json({ success: true });
        // НЕ вызываем broadcastChange — клиент сам знает что он изменил порядок
    } catch (err) {
        console.error("Reorder failed", err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/categories/:id', (req, res) => {
    const { name, is_active } = req.body;
    const result = db.prepare('UPDATE categories SET name = ?, is_active = ? WHERE id = ? AND user_id = ?').run(name, is_active, req.params.id, req.user.id);
    if (result.changes === 0) return res.status(404).json({error: 'Category not found'});
    res.json({ success: true });
    broadcastChange(req.user.id, 'category', 'updated');
});

// Income Sources
app.get('/api/income_sources', (req, res) => {
    const sources = db.prepare('SELECT * FROM income_sources WHERE user_id = ? AND is_active = 1 ORDER BY sort_order ASC, id ASC').all(req.user.id);
    res.json(sources);
});

app.post('/api/income_sources', (req, res) => {
    const { name } = req.body;
    try {
        // Get max sort_order
        const result = db.prepare('SELECT MAX(sort_order) as maxOrder FROM income_sources WHERE user_id = ?').get(req.user.id);
        const nextOrder = (result.maxOrder || 0) + 1;
        
        const stmt = db.prepare('INSERT INTO income_sources (user_id, name, sort_order) VALUES (?, ?, ?)');
        const insertResult = stmt.run(req.user.id, name, nextOrder);
        res.json({ id: insertResult.lastInsertRowid, name, is_active: 1, sort_order: nextOrder });
        broadcastChange(req.user.id, 'income_source', 'created');
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/income_sources/reorder', (req, res) => {
    const { ids } = req.body;
    if (!Array.isArray(ids)) return res.status(400).json({ error: 'ids array required' });

    const updateStmt = db.prepare('UPDATE income_sources SET sort_order = ? WHERE id = ? AND user_id = ?');
    
    const transact = db.transaction((idList) => {
        idList.forEach((id, index) => {
            updateStmt.run(index, Number(id), req.user.id);
        });
    });

    try {
        transact(ids);
        res.json({ success: true });
        // НЕ вызываем broadcastChange — клиент сам знает что он изменил порядок
    } catch (err) {
        console.error("Reorder income sources failed", err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/income_sources/:id', (req, res) => {
    const { name, is_active } = req.body;
    const { id } = req.params;
    try {
        const stmt = db.prepare('UPDATE income_sources SET name = ?, is_active = ? WHERE id = ? AND user_id = ?');
        const result = stmt.run(name, is_active ?? 1, id, req.user.id);
        if (result.changes === 0) return res.status(404).json({error: 'Income source not found'});
        res.json({ id, name, is_active: is_active ?? 1 });
        broadcastChange(req.user.id, 'income_source', 'updated');
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/income_sources/:id', (req, res) => {
    const { id } = req.params;
    try {
        const stmt = db.prepare('UPDATE income_sources SET is_active = 0 WHERE id = ? AND user_id = ?');
        const result = stmt.run(id, req.user.id);
        if (result.changes === 0) return res.status(404).json({error: 'Income source not found'});
        res.json({ success: true });
        broadcastChange(req.user.id, 'income_source', 'deleted');
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Months
app.get('/api/months', (req, res) => {
    // Return list of months that have data
    const months = db.prepare('SELECT * FROM months WHERE user_id = ? ORDER BY year DESC, month DESC').all(req.user.id);
    res.json(months);
});

app.post('/api/months/ensure', (req, res) => {
    const { year, month } = req.body;
    const id = getOrCreateMonth(req.user.id, year, month);
    res.json({ id, year, month });
});

// Incomes
app.get('/api/months/:monthId/incomes', (req, res) => {
    // Check access
    if (!checkMonthAccess(req.user.id, req.params.monthId)) return res.status(403).json({ error: 'Access denied' });
    
    const incomes = db.prepare('SELECT * FROM incomes WHERE month_id = ?').all(req.params.monthId);
    res.json(incomes);
});

app.post('/api/incomes', (req, res) => {
    const { month_id, source, amount, date } = req.body;
    
    if (!checkMonthAccess(req.user.id, month_id)) return res.status(403).json({ error: 'Access denied' });

    const info = db.prepare('INSERT INTO incomes (month_id, source, amount, date) VALUES (?, ?, ?, ?)').run(month_id, source, amount, date);
    res.json({ id: info.lastInsertRowid, ...req.body });
    broadcastChange(req.user.id, 'income', 'created');
});

app.put('/api/incomes/:id', (req, res) => {
    const { amount } = req.body;
    // Need to verify ownership via month_id
    const income = db.prepare('SELECT month_id FROM incomes WHERE id = ?').get(req.params.id);
    if (!income) return res.status(404).json({ error: 'Income not found' });
    if (!checkMonthAccess(req.user.id, income.month_id)) return res.status(403).json({ error: 'Access denied' });

    db.prepare('UPDATE incomes SET amount = ? WHERE id = ?').run(amount, req.params.id);
    res.json({ success: true });
    broadcastChange(req.user.id, 'income', 'updated');
});

app.delete('/api/incomes/:id', (req, res) => {
    const income = db.prepare('SELECT month_id FROM incomes WHERE id = ?').get(req.params.id);
    if (!income) return res.status(404).json({ error: 'Income not found' });
    if (!checkMonthAccess(req.user.id, income.month_id)) return res.status(403).json({ error: 'Access denied' });

    db.prepare('DELETE FROM incomes WHERE id = ?').run(req.params.id);
    res.json({ success: true });
    broadcastChange(req.user.id, 'income', 'deleted');
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
    const { month_id, category_id, amount, date, comment } = req.body;
    
    if (!checkMonthAccess(req.user.id, month_id)) return res.status(403).json({ error: 'Access denied' });
    
    // Verify category ownership
    const category = db.prepare('SELECT id FROM categories WHERE id = ? AND user_id = ?').get(category_id, req.user.id);
    if (!category) return res.status(400).json({ error: 'Invalid category' });

    const info = db.prepare('INSERT INTO expenses (month_id, category_id, amount, date, comment) VALUES (?, ?, ?, ?, ?)').run(month_id, category_id, amount, date, comment || '');
    res.json({ id: info.lastInsertRowid, ...req.body });
    broadcastChange(req.user.id, 'expense', 'created');
});

app.put('/api/expenses/:id', (req, res) => {
    const { amount } = req.body;
    
    const expense = db.prepare('SELECT month_id FROM expenses WHERE id = ?').get(req.params.id);
    if (!expense) return res.status(404).json({ error: 'Expense not found' });
    if (!checkMonthAccess(req.user.id, expense.month_id)) return res.status(403).json({ error: 'Access denied' });

    db.prepare('UPDATE expenses SET amount = ? WHERE id = ?').run(amount, req.params.id);
    res.json({ success: true });
    broadcastChange(req.user.id, 'expense', 'updated');
});

app.delete('/api/expenses/:id', (req, res) => {
    const expense = db.prepare('SELECT month_id FROM expenses WHERE id = ?').get(req.params.id);
    if (!expense) return res.status(404).json({ error: 'Expense not found' });
    if (!checkMonthAccess(req.user.id, expense.month_id)) return res.status(403).json({ error: 'Access denied' });

    db.prepare('DELETE FROM expenses WHERE id = ?').run(req.params.id);
    res.json({ success: true });
    broadcastChange(req.user.id, 'expense', 'deleted');
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
    const { month_id, category_id, limit_amount } = req.body;
    
    if (!checkMonthAccess(req.user.id, month_id)) return res.status(403).json({ error: 'Access denied' });

    // Check if exists
    const existing = db.prepare('SELECT id FROM budgets WHERE month_id = ? AND category_id = ?').get(month_id, category_id);

    if (existing) {
        db.prepare('UPDATE budgets SET limit_amount = ? WHERE id = ?').run(limit_amount, existing.id);
        res.json({ id: existing.id, month_id, category_id, limit_amount });
    } else {
        const info = db.prepare('INSERT INTO budgets (month_id, category_id, limit_amount) VALUES (?, ?, ?)').run(month_id, category_id, limit_amount);
        res.json({ id: info.lastInsertRowid, month_id, category_id, limit_amount });
    }
    broadcastChange(req.user.id, 'budget', 'updated');
});

// Savings
app.get('/api/savings_goals', (req, res) => {
    const goals = db.prepare('SELECT * FROM savings_goals WHERE user_id = ?').all(req.user.id);
    res.json(goals);
});

app.post('/api/savings_goals', (req, res) => {
    const { name, target_amount } = req.body;
    const info = db.prepare('INSERT INTO savings_goals (user_id, name, target_amount, current_amount) VALUES (?, ?, ?, 0)').run(req.user.id, name, target_amount || 0);
    res.json({ id: info.lastInsertRowid, name, target_amount, current_amount: 0 });
    broadcastChange(req.user.id, 'savings_goal', 'created');
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
    
    // Check ownership
    const goal = db.prepare('SELECT id FROM savings_goals WHERE id = ? AND user_id = ?').get(req.params.id, req.user.id);
    if (!goal) return res.status(404).json({ error: 'Goal not found' });

    const sql = `UPDATE savings_goals SET ${updates.join(', ')} WHERE id = ?`;
    db.prepare(sql).run(...values, req.params.id);
    res.json({ success: true });
    broadcastChange(req.user.id, 'savings_goal', 'updated');
});

app.delete('/api/savings_goals/:id', (req, res) => {
    const goal = db.prepare('SELECT id FROM savings_goals WHERE id = ? AND user_id = ?').get(req.params.id, req.user.id);
    if (!goal) return res.status(404).json({ error: 'Goal not found' });

    // First delete all transactions for this goal
    db.prepare('DELETE FROM savings_transactions WHERE goal_id = ?').run(req.params.id);
    // Then delete the goal
    db.prepare('DELETE FROM savings_goals WHERE id = ?').run(req.params.id);
    res.json({ success: true });
    broadcastChange(req.user.id, 'savings_goal', 'deleted');
});

app.post('/api/savings_transactions', (req, res) => {
    const { goal_id, amount, date, month_id } = req.body;
    
    // Verify goal ownership
    const goal = db.prepare('SELECT id, name FROM savings_goals WHERE id = ? AND user_id = ?').get(goal_id, req.user.id);
    if (!goal) return res.status(404).json({ error: 'Goal not found' });
    
    if (month_id) {
        if (!checkMonthAccess(req.user.id, month_id)) return res.status(403).json({ error: 'Access denied to month' });
    }

    const transact = db.transaction(() => {
        const info = db.prepare('INSERT INTO savings_transactions (goal_id, amount, date, month_id) VALUES (?, ?, ?, ?)').run(goal_id, amount, date, month_id);
        
        // Update goal balance
        db.prepare('UPDATE savings_goals SET current_amount = current_amount + ? WHERE id = ?').run(amount, goal_id);
        
        // If deposit (positive amount), create a hidden expense to reduce available balance
        if (amount > 0 && month_id) {
            // Get or create "Пополнение копилки" category (hidden) for THIS USER
            let savingsCategory = db.prepare('SELECT id FROM categories WHERE name = ? AND user_id = ?').get('Пополнение копилки', req.user.id);
            if (!savingsCategory) {
                const maxOrder = db.prepare('SELECT MAX(sort_order) as maxOrder FROM categories WHERE user_id = ?').get(req.user.id);
                const nextOrder = (maxOrder.maxOrder || 0) + 1;
                const catInfo = db.prepare('INSERT INTO categories (user_id, name, sort_order, is_active) VALUES (?, ?, ?, 0)').run(req.user.id, 'Пополнение копилки', nextOrder);
                savingsCategory = { id: catInfo.lastInsertRowid };
            }
            
            // Create hidden expense for the deposit amount
            db.prepare('INSERT INTO expenses (month_id, category_id, amount, date, comment) VALUES (?, ?, ?, ?, ?)').run(
                month_id,
                savingsCategory.id,
                amount,
                date,
                `Пополнение копилки "${goal.name}"`
            );
        }
        
        return info;
    });

    const info = transact();
    res.json({ id: info.lastInsertRowid, ...req.body });
    broadcastChange(req.user.id, 'savings_transaction', 'created');
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
        ws.userId = decoded.id;
        ws.isAlive = true;
        console.log(`WebSocket connected: user ${decoded.id}`);
    } catch (err) {
        ws.close(4003, 'Invalid token');
        return;
    }

    ws.on('pong', () => { ws.isAlive = true; });
    ws.on('close', () => { console.log(`WebSocket disconnected: user ${ws.userId}`); });
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
    console.log(`Server running on http://localhost:${PORT}`);
});

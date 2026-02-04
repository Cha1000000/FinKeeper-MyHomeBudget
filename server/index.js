const express = require('express');
const cors = require('cors');
const Database = require('better-sqlite3');
const path = require('path');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { initialCategories, initialSavings, initialIncomeSources } = require('./default_data');

const app = express();
const PORT = 3002;
const JWT_SECRET = 'my-home-budget-secret-key-change-this'; // In production, use environment variable
const dbPath = path.resolve(__dirname, 'database.sqlite');
const db = new Database(dbPath);

app.use(cors());
app.use(express.json());

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

    const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '365d' }); // Valid for 1 year
    res.json({ token, user: { id: user.id, username: user.username } });
});

app.get('/api/auth/me', authenticateToken, (req, res) => {
    // Just return the user info from the token/db to confirm validity
    const user = db.prepare('SELECT id, username, created_at FROM users WHERE id = ?').get(req.user.id);
    if (!user) return res.sendStatus(404);
    res.json(user);
});


// Protect all subsequent API routes
app.use('/api', authenticateToken); 

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
});

// Income Sources
app.get('/api/income_sources', (req, res) => {
    const sources = db.prepare('SELECT * FROM income_sources WHERE user_id = ? AND is_active = 1 ORDER BY id').all(req.user.id);
    res.json(sources);
});

app.post('/api/income_sources', (req, res) => {
    const { name } = req.body;
    try {
        const stmt = db.prepare('INSERT INTO income_sources (user_id, name) VALUES (?, ?)');
        const result = stmt.run(req.user.id, name);
        res.json({ id: result.lastInsertRowid, name, is_active: 1 });
    } catch (err) {
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
});

app.put('/api/incomes/:id', (req, res) => {
    const { amount } = req.body;
    // Need to verify ownership via month_id
    const income = db.prepare('SELECT month_id FROM incomes WHERE id = ?').get(req.params.id);
    if (!income) return res.status(404).json({ error: 'Income not found' });
    if (!checkMonthAccess(req.user.id, income.month_id)) return res.status(403).json({ error: 'Access denied' });

    db.prepare('UPDATE incomes SET amount = ? WHERE id = ?').run(amount, req.params.id);
    res.json({ success: true });
});

app.delete('/api/incomes/:id', (req, res) => {
    const income = db.prepare('SELECT month_id FROM incomes WHERE id = ?').get(req.params.id);
    if (!income) return res.status(404).json({ error: 'Income not found' });
    if (!checkMonthAccess(req.user.id, income.month_id)) return res.status(403).json({ error: 'Access denied' });

    db.prepare('DELETE FROM incomes WHERE id = ?').run(req.params.id);
    res.json({ success: true });
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
});

app.put('/api/expenses/:id', (req, res) => {
    const { amount } = req.body;
    
    const expense = db.prepare('SELECT month_id FROM expenses WHERE id = ?').get(req.params.id);
    if (!expense) return res.status(404).json({ error: 'Expense not found' });
    if (!checkMonthAccess(req.user.id, expense.month_id)) return res.status(403).json({ error: 'Access denied' });

    db.prepare('UPDATE expenses SET amount = ? WHERE id = ?').run(amount, req.params.id);
    res.json({ success: true });
});

app.delete('/api/expenses/:id', (req, res) => {
    const expense = db.prepare('SELECT month_id FROM expenses WHERE id = ?').get(req.params.id);
    if (!expense) return res.status(404).json({ error: 'Expense not found' });
    if (!checkMonthAccess(req.user.id, expense.month_id)) return res.status(403).json({ error: 'Access denied' });

    db.prepare('DELETE FROM expenses WHERE id = ?').run(req.params.id);
    res.json({ success: true });
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
});

app.delete('/api/savings_goals/:id', (req, res) => {
    const goal = db.prepare('SELECT id FROM savings_goals WHERE id = ? AND user_id = ?').get(req.params.id, req.user.id);
    if (!goal) return res.status(404).json({ error: 'Goal not found' });

    // First delete all transactions for this goal
    db.prepare('DELETE FROM savings_transactions WHERE goal_id = ?').run(req.params.id);
    // Then delete the goal
    db.prepare('DELETE FROM savings_goals WHERE id = ?').run(req.params.id);
    res.json({ success: true });
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

    // Savings contributions (positive amounts in transactions linked to this month)
    const totalSavings = db.prepare('SELECT SUM(amount) as total FROM savings_transactions WHERE month_id = ? AND amount > 0').get(monthId).total || 0;

    res.json({
        income: totalIncome,
        expenses: totalExpenses,
        savings: totalSavings,
        balance: totalIncome - totalExpenses - totalSavings
    });
});

app.get('/api/analytics/trend', (req, res) => {
    // Get last 6 months for THIS USER
    const months = db.prepare('SELECT * FROM months WHERE user_id = ? ORDER BY year DESC, month DESC LIMIT 6').all(req.user.id).reverse();

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
app.get(/.*/, (req, res) => {
    // Skip API routes
    if (req.path.startsWith('/api/')) {
        return res.status(404).json({ error: 'API endpoint not found' });
    }
    res.sendFile(path.join(clientDistPath, 'index.html'));
});

app.listen(PORT, () => {
    console.log(`Server running on http://localhost:${PORT}`);
});

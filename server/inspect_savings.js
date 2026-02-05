const Database = require('better-sqlite3');
const db = new Database('database.sqlite');

console.log("=== Savings Goals ===");
const goals = db.prepare('SELECT * FROM savings_goals').all();
console.table(goals);

console.log("\n=== Savings Transactions (Last 20) ===");
const transactions = db.prepare(`
    SELECT t.id, t.amount, t.date, t.month_id, m.month, m.year, g.name as goal_name 
    FROM savings_transactions t
    LEFT JOIN months m ON t.month_id = m.id
    LEFT JOIN savings_goals g ON t.goal_id = g.id
    ORDER BY t.date DESC LIMIT 20
`).all();
console.table(transactions);

console.log("\n=== Monthly Savings Summary (February 2026) ===");
const febMonth = db.prepare("SELECT id FROM months WHERE month = 2 AND year = 2026").get();
if (febMonth) {
    const sum = db.prepare('SELECT SUM(amount) as total FROM savings_transactions WHERE month_id = ? AND amount > 0').get(febMonth.id);
    console.log(`Month ID: ${febMonth.id}, Total Savings Deposits: ${sum.total}`);
    
    console.log("\n=== Transactions for Feb 2026 ===");
    const febTrans = db.prepare(`
        SELECT t.id, t.amount, t.date, g.name 
        FROM savings_transactions t
        LEFT JOIN savings_goals g ON t.goal_id = g.id
        WHERE t.month_id = ?
    `).all(febMonth.id);
    console.table(febTrans);
} else {
    console.log("Month 2/2026 not found in DB");
}

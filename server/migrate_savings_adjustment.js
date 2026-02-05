const Database = require('better-sqlite3');
const db = new Database('database.sqlite');

try {
    // 1. Add column is_adjustment if not exists
    try {
        db.prepare('ALTER TABLE savings_transactions ADD COLUMN is_adjustment INTEGER DEFAULT 0').run();
        console.log("Added is_adjustment column.");
    } catch (e) {
        if (e.message.includes('duplicate column name')) {
            console.log("Column is_adjustment already exists.");
        } else {
            throw e;
        }
    }

    // 2. Mark initial large deposits as adjustments
    // Ids: 1, 2, 3, 5
    const initialIds = [1, 2, 3, 5];
    const updateStmt = db.prepare('UPDATE savings_transactions SET is_adjustment = 1 WHERE id = ?');
    
    let changes = 0;
    initialIds.forEach(id => {
        const res = updateStmt.run(id);
        changes += res.changes;
    });
    console.log(`Marked ${changes} initial transactions as adjustments.`);

    // 3. Mark re-deposits as adjustments (Ids 12, 13, 14)
    // These seem to be corrections/restorations after withdrawals
    const correctionIds = [12, 13, 14];
    changes = 0;
    correctionIds.forEach(id => {
        const res = updateStmt.run(id);
        changes += res.changes;
    });
    console.log(`Marked ${changes} correction transactions as adjustments.`);

    // Verify
    const adjusted = db.prepare('SELECT id, amount, date FROM savings_transactions WHERE is_adjustment = 1').all();
    console.log("Adjusted transactions:", adjusted);

} catch (err) {
    console.error("Migration failed:", err);
}

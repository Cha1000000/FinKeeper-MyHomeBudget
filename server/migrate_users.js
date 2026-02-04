const Database = require('better-sqlite3');
const path = require('path');

const dbPath = path.resolve(__dirname, 'database.sqlite');
const db = new Database(dbPath);

console.log('Starting migration to multi-user support...');

// Disable foreign keys globally for this connection to allow table swapping
db.prepare('PRAGMA foreign_keys = OFF').run();

// 1. Check if user 1 exists
const user = db.prepare('SELECT id, username FROM users WHERE id = 1').get();
if (!user) {
    console.error('ERROR: No user with ID=1 found. Please register via the app first to claim existing data.');
    process.exit(1);
}
console.log(`Assigning existing data to user: ${user.username} (ID: ${user.id})`);

const tablesToMigrate = [
    'categories',
    'months',
    'income_sources',
    'savings_goals'
];

const runMigration = db.transaction(() => {
    for (const table of tablesToMigrate) {
        // Check if column exists
        const tableInfo = db.prepare(`PRAGMA table_info(${table})`).all();
        const hasUserId = tableInfo.some(col => col.name === 'user_id');

        if (!hasUserId) {
            console.log(`Adding user_id to table: ${table}`);
            // Add column
            db.prepare(`ALTER TABLE ${table} ADD COLUMN user_id INTEGER`).run();
            
            // Assign to user 1
            console.log(`Updating existing records in ${table}...`);
            const info = db.prepare(`UPDATE ${table} SET user_id = ? WHERE user_id IS NULL`).run(user.id);
            console.log(`Updated ${info.changes} rows in ${table}.`);

            // Add index for performance
            db.prepare(`CREATE INDEX IF NOT EXISTS idx_${table}_user_id ON ${table}(user_id)`).run();
        } else {
            console.log(`Table ${table} already has user_id column. Skipping column addition.`);
        }
    }
    
    // Fix 'months' table unique constraint
    console.log("Checking 'months' table constraint...");
    
    // Check if months_new already exists (from failed previous run)
    const tableExists = db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='months_new'").get();
    if (tableExists) {
        db.prepare('DROP TABLE months_new').run();
    }

    // We need to check if the current 'months' table has the correct UNIQUE constraint including user_id.
    // We can check strictly by recreating it anyway to be safe, or inspecting DDL.
    // Let's recreate it to be sure.
    
    console.log(`Recreating 'months' table to update UNIQUE constraint...`);
    
    db.prepare('CREATE TABLE months_new (id INTEGER PRIMARY KEY AUTOINCREMENT, year INTEGER NOT NULL, month INTEGER NOT NULL, user_id INTEGER, UNIQUE(year, month, user_id))').run();
    
    // Copy data
    db.prepare('INSERT INTO months_new (id, year, month, user_id) SELECT id, year, month, user_id FROM months').run();
    
    // Drop old table
    db.prepare('DROP TABLE months').run();
    
    // Rename new
    db.prepare('ALTER TABLE months_new RENAME TO months').run();
    
    console.log("'months' table recreated.");
});

try {
    runMigration();
    // Re-enable foreign keys
    db.prepare('PRAGMA foreign_keys = ON').run();
    
    // Check integrity just in case
    const integrity = db.prepare('PRAGMA foreign_key_check').all();
    if (integrity.length > 0) {
        console.error('Foreign key integrity violations found:', integrity);
    } else {
        console.log('Migration completed successfully.');
    }
} catch (err) {
    console.error('Migration failed:', err);
}

db.close();
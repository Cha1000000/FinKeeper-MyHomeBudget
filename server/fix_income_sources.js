const Database = require('better-sqlite3');
const path = require('path');

const dbPath = path.resolve(__dirname, 'database.sqlite');
const db = new Database(dbPath);

console.log('Fixing income_sources table schema...');

try {
    const tableInfo = db.prepare("PRAGMA table_info('income_sources')").all();
    const hasCreatedAt = tableInfo.some(col => col.name === 'created_at');

    if (!hasCreatedAt) {
        console.log('Recreating income_sources table to add created_at...');
        
        db.prepare('PRAGMA foreign_keys = OFF').run();
        
        const migrate = db.transaction(() => {
            // Check if user_id exists in current table
            const hasUserId = tableInfo.some(col => col.name === 'user_id');
            
            // Rename old
            db.prepare('ALTER TABLE income_sources RENAME TO income_sources_old').run();
            
            // Create new
            db.prepare(`
                CREATE TABLE income_sources (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id INTEGER NOT NULL,
                  name TEXT NOT NULL,
                  is_active INTEGER DEFAULT 1,
                  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                  FOREIGN KEY (user_id) REFERENCES users(id)
                )
            `).run();
            
            // Copy data
            if (hasUserId) {
                db.prepare('INSERT INTO income_sources (id, user_id, name, is_active) SELECT id, user_id, name, is_active FROM income_sources_old').run();
            } else {
                // Should not happen if previous migration ran, but fallback to user 1
                console.log('Warning: user_id missing in old table, assigning to user 1');
                db.prepare('INSERT INTO income_sources (id, user_id, name, is_active) SELECT id, 1, name, is_active FROM income_sources_old').run();
            }
            
            // Drop old
            db.prepare('DROP TABLE income_sources_old').run();
        });
        
        migrate();
        
        db.prepare('PRAGMA foreign_keys = ON').run();
        console.log('Table recreated successfully.');
    } else {
        console.log('Column created_at already exists.');
    }
} catch (err) {
    console.error('Error updating schema:', err);
}

db.close();
const Database = require('better-sqlite3');
const path = require('path');
const logger = require('./logger');

const dbPath = process.env.DB_PATH?.trim()
    ? path.resolve(process.env.DB_PATH.trim())
    : path.resolve(__dirname, 'database.sqlite');
const db = new Database(dbPath);

logger.info('db_setup_started', { dbPath });

const schema = `
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT UNIQUE NOT NULL,
  email TEXT UNIQUE,
  password_hash TEXT NOT NULL,
  auth_password_enabled INTEGER NOT NULL DEFAULT 1,
  email_confirmed_at TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  sort_order INTEGER DEFAULT 0,
  is_active INTEGER DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS months (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  year INTEGER NOT NULL,
  month INTEGER NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(year, month, user_id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS incomes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  month_id INTEGER NOT NULL,
  source TEXT NOT NULL,
  amount REAL NOT NULL,
  date TEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (month_id) REFERENCES months(id)
);

CREATE TABLE IF NOT EXISTS expenses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  month_id INTEGER NOT NULL,
  category_id INTEGER NOT NULL,
  amount REAL NOT NULL,
  date TEXT NOT NULL,
  comment TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (month_id) REFERENCES months(id),
  FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS budgets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  month_id INTEGER NOT NULL,
  category_id INTEGER NOT NULL,
  limit_amount REAL NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (month_id) REFERENCES months(id),
  FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS savings_goals (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  target_amount REAL DEFAULT 0,
  current_amount REAL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS savings_transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  goal_id INTEGER NOT NULL,
  amount REAL NOT NULL,
  date TEXT NOT NULL,
  month_id INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (goal_id) REFERENCES savings_goals(id),
  FOREIGN KEY (month_id) REFERENCES months(id)
);

CREATE TABLE IF NOT EXISTS income_sources (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  is_active INTEGER DEFAULT 1,
  sort_order INTEGER DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS user_backups (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  data TEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS auth_refresh_sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  last_used_at TEXT,
  revoked_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS auth_email_verification_tokens (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  email TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  used_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS auth_password_reset_tokens (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  email TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  used_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS auth_identities (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  provider TEXT NOT NULL,
  provider_user_id TEXT NOT NULL,
  provider_email TEXT,
  provider_email_verified INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  last_login_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id),
  UNIQUE(provider, provider_user_id)
);

CREATE TABLE IF NOT EXISTS auth_login_exchange_codes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  provider TEXT NOT NULL,
  code_hash TEXT NOT NULL UNIQUE,
  client_type TEXT,
  redirect_uri TEXT,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  used_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS auth_social_login_attempts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  attempt_token_hash TEXT NOT NULL UNIQUE,
  provider TEXT NOT NULL,
  client_type TEXT NOT NULL,
  redirect_uri TEXT,
  user_id INTEGER,
  status TEXT NOT NULL,
  exchange_code TEXT,
  error_code TEXT,
  error_description TEXT,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  completed_at TEXT,
  consumed_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  operation_id TEXT NOT NULL,
  request_signature TEXT NOT NULL,
  response_status INTEGER NOT NULL,
  response_body TEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(user_id, operation_id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS deleted_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id INTEGER NOT NULL,
  deleted_at DATETIME NOT NULL,
  UNIQUE(user_id, entity_type, entity_id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);
`;

db.exec(schema);

db.exec(`
CREATE INDEX IF NOT EXISTS idx_auth_email_verification_tokens_user_id
ON auth_email_verification_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_auth_password_reset_tokens_user_id
ON auth_password_reset_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_auth_identities_user_id
ON auth_identities(user_id);

CREATE INDEX IF NOT EXISTS idx_auth_login_exchange_codes_user_id
ON auth_login_exchange_codes(user_id);

CREATE INDEX IF NOT EXISTS idx_auth_social_login_attempts_status
ON auth_social_login_attempts(status);
`);

logger.info('db_setup_completed', { dbPath });
db.close();
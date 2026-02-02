const Database = require('better-sqlite3');
const path = require('path');
const fs = require('fs');

const dbPath = path.resolve(__dirname, 'database.sqlite');
const db = new Database(dbPath);

console.log('Setting up database at', dbPath);

const schema = `
CREATE TABLE IF NOT EXISTS categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  is_active INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS months (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  year INTEGER NOT NULL,
  month INTEGER NOT NULL,
  UNIQUE(year, month)
);

CREATE TABLE IF NOT EXISTS incomes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  month_id INTEGER NOT NULL,
  source TEXT NOT NULL,
  amount REAL NOT NULL,
  date TEXT NOT NULL,
  FOREIGN KEY (month_id) REFERENCES months(id)
);

CREATE TABLE IF NOT EXISTS expenses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  month_id INTEGER NOT NULL,
  category_id INTEGER NOT NULL,
  amount REAL NOT NULL,
  date TEXT NOT NULL,
  comment TEXT,
  FOREIGN KEY (month_id) REFERENCES months(id),
  FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS budgets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  month_id INTEGER NOT NULL,
  category_id INTEGER NOT NULL,
  limit_amount REAL NOT NULL,
  FOREIGN KEY (month_id) REFERENCES months(id),
  FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS savings_goals (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  target_amount REAL DEFAULT 0,
  current_amount REAL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS savings_transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  goal_id INTEGER NOT NULL,
  amount REAL NOT NULL,
  date TEXT NOT NULL,
  month_id INTEGER,
  FOREIGN KEY (goal_id) REFERENCES savings_goals(id),
  FOREIGN KEY (month_id) REFERENCES months(id)
);
`;

db.exec(schema);

// Seed Categories
const initialCategories = [
  'Комуналка/связь/подписки',
  '🎓 Садик Кирюши',
  '🏠💰 Ипотека',
  'Необходимое в квартиру',
  '🚙🅿️ Аренда парковки',
  '👩🏼‍🤝‍👩🏻 Помощь родителям',
  '🍔 Питание',
  '🛒 Бытовые расходы',
  '🎁 Подарки/Ништяки',
  '🏆🤸🏼‍♀️ Здоровье/спорт/красота',
  '🚕 Проезд по городу (+ бензин)',
  '🚘 Автомобиль (обслуживание)',
  '🎈🥂🍸 Отдых и развлечения',
  'Алине',
  'Олегу',
  'Кирюше',
  '🧺 Другое/непредвиденное'
];

const checkCategories = db.prepare('SELECT count(*) as count FROM categories').get();
if (checkCategories.count === 0) {
  console.log('Seeding categories...');
  const insert = db.prepare('INSERT INTO categories (name) VALUES (?)');
  const insertMany = db.transaction((cats) => {
    for (const cat of cats) insert.run(cat);
  });
  insertMany(initialCategories);
}

// Seed Savings Goals (Копилки)
const initialSavings = [
  'Подушка безопасности',
  'Отпуск/поездки',
  'Фонд Кирюши',
  'Ремонт'
];

const checkSavings = db.prepare('SELECT count(*) as count FROM savings_goals').get();
if (checkSavings.count === 0) {
  console.log('Seeding savings goals...');
  const insert = db.prepare('INSERT INTO savings_goals (name) VALUES (?)');
  const insertMany = db.transaction((goals) => {
    for (const goal of goals) insert.run(goal);
  });
  insertMany(initialSavings);
}

console.log('Database setup complete.');
db.close();

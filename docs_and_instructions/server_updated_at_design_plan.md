# Server-side `updated_at` Design + Migration Plan

## Цель

Добавить на сервере единый и предсказуемый `updated_at` contract для всех синхронизируемых сущностей так, чтобы:

- KMP-клиент мог сравнивать локальную и серверную версии записи по общему серверному признаку
- существующий web client не был сломан
- обновление работало не только на новой БД, но и на уже существующих SQLite базах пользователей
- это стало основой для следующего этапа с `operationId/idempotency key` и tombstones

## Почему это нужно делать отдельно

Сейчас серверный слой не даёт единого ответа на вопрос «какая версия записи новее»:

- у большинства таблиц вообще нет `updated_at`
- write-endpoints возвращают непоследовательный shape
- backup/restore логика знает только про текущие колонки
- отдельного migration framework на сервере нет

Поэтому `updated_at` нужно внедрять как самостоятельный server-side этап, а не как побочную правку в роуты.

## Scope Phase S1

### Включаем в scope

- schema changes для основных sync-сущностей
- migration path для существующей `server/database.sqlite`
- обновление write-queries, чтобы `updated_at` реально поддерживался
- возврат `updated_at` в read/write responses
- обновление backup/restore под новые колонки
- сохранение backward compatibility для web client и KMP

### Не включаем в scope

- `operationId / idempotency key`
- tombstones / soft-delete унификацию
- delta-sync endpoints
- изменение WebSocket протокола

## Затронутые сущности

### Обязательно

- `categories`
- `income_sources`
- `months`
- `incomes`
- `expenses`
- `budgets`
- `savings_goals`
- `savings_transactions`

### Необязательно в первом проходе

- `users`
- `user_backups`

Для `user_backups` текущего `created_at` достаточно, так как это не sync entity.

## Предлагаемый schema contract

### Общий принцип

Для каждой sync-сущности сервер хранит:

- `created_at TEXT NOT NULL`
- `updated_at TEXT NOT NULL`

Формат значений:

- ISO-8601 UTC строка
- пример: `2026-03-10T10:12:45.123Z`

### Почему строка, а не integer millis

Текущий сервер уже использует ISO-строки в части полей и backup JSON. Для SQLite и JavaScript это проще и меньше ломает существующий код.

### Таблицы и поля

#### `categories`

Добавить:

- `created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`

#### `income_sources`

Сейчас есть только `created_at`. Добавить:

- `updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`

`created_at` сохранить как есть.

#### `months`

Добавить:

- `created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`

#### `incomes`

Добавить:

- `created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`

#### `expenses`

Добавить:

- `created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`

#### `budgets`

Добавить:

- `created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`

#### `savings_goals`

Добавить:

- `created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`

#### `savings_transactions`

Добавить:

- `created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP`

## Семантика `updated_at`

### Когда обновлять `updated_at`

`updated_at` должен изменяться при любом логическом изменении записи:

- `INSERT`
- `UPDATE`
- soft-delete в будущем
- reorder для сущностей, где `sort_order` является частью состояния

### Когда не обновлять `updated_at`

- при простом чтении
- при broadcast через WebSocket
- при операциях над другими сущностями, которые не меняют конкретную запись

### Важное правило

На сервере должен быть единый helper, например `nowIso()`, и все write-endpoints должны использовать его, а не разрозненные `new Date().toISOString()` в разных местах.

## Migration strategy для существующей SQLite базы

## Основная проблема

`db_setup.js` создаёт таблицы только для fresh install. Для уже существующей `database.sqlite` этого недостаточно.

Нужен runtime migration path, который выполняется при старте сервера.

## Рекомендуемый подход

Добавить в `server/index.js` или выделенный server helper функцию вида:

- `ensureSchemaUpToDate()`

Она должна:

- проверять наличие колонок через `PRAGMA table_info(table_name)`
- выполнять `ALTER TABLE ... ADD COLUMN ...` только если колонка отсутствует
- после добавления новых колонок нормализовать уже существующие строки

## Порядок миграции

### Шаг 1 — добавить недостающие колонки

Для каждой таблицы:

- если нет `created_at`, добавить её
- если нет `updated_at`, добавить её

### Шаг 2 — backfill существующих строк

После добавления колонок выполнить update для строк, где значения пустые или `NULL`:

- `created_at = COALESCE(created_at, CURRENT_TIMESTAMP)`
- `updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)`

### Шаг 3 — сохранить идемпотентность миграции

Миграция должна быть безопасна при повторном запуске:

- повторный старт сервера не должен ломать схему
- не должно быть duplicate-column ошибок

### Шаг 4 — логирование

На старте сервера нужно логировать:

- какие таблицы были проверены
- какие колонки были добавлены
- сколько строк прошло backfill

Это критично, потому что migration framework отсутствует.

## Почему не стоит сразу пересобирать таблицы

SQLite позволяет безопасно добавить колонки через `ALTER TABLE ... ADD COLUMN`, и для first phase этого достаточно.

Полная пересборка таблиц здесь избыточна и повышает риск.

## Изменения в server code

## 1. Добавить schema helper

Нужен server-side helper с ответственностями:

- `hasColumn(tableName, columnName)`
- `addColumnIfMissing(tableName, sqlDefinition)`
- `backfillTimestampColumns(tableName)`
- `ensureSchemaUpToDate()`
- `nowIso()`

## 2. Обновить `db_setup.js`

`db_setup.js` должен уметь создавать fresh schema уже с новыми колонками, чтобы новая база сразу создавалась правильно.

## 3. Вызывать runtime schema check при старте сервера

Сразу после открытия `new Database(dbPath)` и до обслуживания запросов.

## Затронутые endpoints

### Categories

#### Текущие endpoints `categories`

- `GET /api/categories`
- `POST /api/categories`
- `PUT /api/categories/reorder`
- `PUT /api/categories/:id`

#### Что нужно для `categories`

- при `POST` возвращать объект с `created_at` и `updated_at`
- при `PUT` обновлять `updated_at`
- при reorder обновлять `updated_at` у изменённых категорий
- `GET` уже должен включать новые поля через `SELECT *`

### Income Sources

#### Текущие endpoints `income_sources`

- `GET /api/income_sources`
- `POST /api/income_sources`
- `PUT /api/income_sources/reorder`
- `PUT /api/income_sources/:id`
- `DELETE /api/income_sources/:id`

#### Что нужно для `income_sources`

- на `POST` выставлять `updated_at`
- на `PUT` и `DELETE` обновлять `updated_at`
- на reorder обновлять `updated_at` у изменённых записей

### Months

#### Текущие endpoints `months`

- `GET /api/months`
- `POST /api/months/ensure`

#### Что нужно для `months`

- `getOrCreateMonth()` должен заполнять timestamp-поля при создании
- `months/ensure` должен возвращать canonical month row, включая `created_at` и `updated_at`, а не только `{ id, year, month }`

### Incomes

#### Что нужно для `incomes`

- на `POST /api/incomes` выставлять timestamp-поля
- на `PUT /api/incomes/:id` обновлять `updated_at`
- `GET` и `POST` responses должны включать `updated_at`

### Expenses

#### Что нужно для `expenses`

- на `POST /api/expenses` выставлять timestamp-поля
- на `PUT /api/expenses/:id` обновлять `updated_at`
- `GET` и `POST` responses должны включать `updated_at`

### Budgets

#### Что нужно для `budgets`

- на `POST /api/budgets` при insert выставлять `created_at/updated_at`
- при update существующего бюджета обновлять `updated_at`
- response должен возвращать canonical row с `updated_at`

### Savings Goals

#### Что нужно для `savings_goals`

- на `POST /api/savings_goals` выставлять timestamp-поля
- на `PUT /api/savings_goals/:id` обновлять `updated_at`
- `GET` и `POST` responses должны включать `updated_at`

### Savings Transactions

#### Что нужно для `savings_transactions`

- на `POST /api/savings_transactions` выставлять timestamp-поля
- response должен включать `updated_at`
- если в будущем появятся update/delete endpoints, они тоже обязаны поддерживать `updated_at`

## Canonical write response policy

Чтобы server-side `updated_at` был полезен, write-endpoints должны по возможности возвращать **canonical server row**, а не `{ success: true }`.

### Предпочтительный подход

После `INSERT/UPDATE`:

- выполнить запись
- перечитать строку из БД
- вернуть её клиенту

### Почему это важно

- сервер сам определяет итоговое `updated_at`
- не надо вручную собирать response object из `req.body`
- все клиенты получают одинаковую форму данных

## Влияние на backup / restore

## Backup

`createBackup()` уже делает `SELECT *`, поэтому после добавления колонок новые поля автоматически начнут попадать в backup JSON.

## Restore

`/api/user/restore` нужно обновить обязательно.

Сейчас restore inserts перечисляют колонки вручную и не знают про новые timestamp-поля.

### Нужно сделать

Для всех затронутых `INSERT` в restore:

- добавить `created_at`
- добавить `updated_at`
- если в backup старые данные без этих полей, использовать fallback:
  - `row.created_at || row.updated_at || nowIso()`
  - `row.updated_at || row.created_at || nowIso()`

Иначе после restore новые колонки будут либо теряться, либо ломать insert-ы.

## Влияние на web client (`@client`)

## Что, скорее всего, менять не нужно

Если изменения будут additive:

- добавятся новые поля в responses
- старые поля сохранятся
- обычные list endpoints не начнут возвращать tombstones

то web client менять не обязательно.

Причина:

- web pages в основном делают reload после mutations
- web не использует сложный offline merge
- дополнительные поля в JSON не ломают текущий runtime

## Что можно улучшить позже, но не обязательно сразу

В `client/src/api/index.ts` можно потом опционально расширить интерфейсы:

- `created_at?: string`
- `updated_at?: string`

Но для первого этапа это не обязательно.

## Влияние на KMP client

KMP получит от этого этапа реальную серверную основу для:

- сравнения local vs server версии записи
- более точного merge
- следующего этапа с `operationId`

То есть именно KMP выигрывает от этого этапа больше всего.

## Риски

### 1. Existing DB migration risk

Если забыть runtime migration и ограничиться `db_setup.js`, обновление не сработает на уже существующих пользовательских БД.

### 2. Restore regression risk

Если не обновить `/api/user/restore`, backup restore станет терять timestamp-метаданные.

### 3. Inconsistent response risk

Если часть endpoints продолжит возвращать `{ success: true }`, клиенты не смогут использовать `updated_at` предсказуемо.

### 4. Reorder semantics

Нужно заранее зафиксировать, что изменение `sort_order` действительно считается изменением сущности и обновляет `updated_at`.

## Порядок реализации

### Step 1

Добавить `nowIso()` и runtime `ensureSchemaUpToDate()`.

### Step 2

Обновить `db_setup.js` под fresh schema.

### Step 3

Обновить write-path для:

- `months`
- `categories`
- `income_sources`
- `incomes`
- `expenses`
- `budgets`
- `savings_goals`
- `savings_transactions`

### Step 4

Перевести write responses на canonical row c `updated_at`.

### Step 5

Обновить backup/restore.

### Step 6

Проверить web compatibility и KMP compatibility.

## Acceptance criteria

- существующая серверная SQLite база обновляется без ручного пересоздания
- новая база создаётся уже с `created_at/updated_at`
- все sync-сущности возвращают `updated_at` в read responses
- все основные write-endpoints возвращают данные, содержащие `updated_at`
- restore не теряет timestamp-поля
- web client продолжает работать без обязательных правок
- KMP может начать использовать server `updated_at` как часть merge policy

## Рекомендуемый следующий шаг после этого design-дока

Сразу после утверждения этого плана перейти к implementation Phase S1 в таком порядке:

1. runtime schema migration helper
2. fresh schema update в `db_setup.js`
3. canonical helper functions для чтения строк после insert/update
4. endpoint-by-endpoint rollout
5. smoke-check серверных read/write flows

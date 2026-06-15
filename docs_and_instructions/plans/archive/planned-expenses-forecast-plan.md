# План: «Запланированные расходы на месяц» — этап 1 (сервер + API-слой веба)

## Контекст

Сейчас фиксированный платёж (категория/источник с `is_fixed`, `fixed_amount`, `auto_day`) появляется в месяце только при наступлении `auto_day` — до этого пользователь не видит предстоящие списания и не понимает, сколько денег реально останется. Фича добавляет **виртуальный план-слой**: ещё не материализованные фиксированные платежи месяца + 4 прогнозных агрегата (факт, план, прогноз расходов, прогноз остатка).

**Принятые с Володей решения:**
- Вариант А: planned-инстансы НЕ хранятся в БД, вычисляются на лету. `expenses`/`incomes` не расширяем (защита старых KMP-клиентов при sync).
- Исключения (пропуск/override на месяц) — отдельная таблица `planned_overrides`.
- В scope: пропуск, override суммы/дня, ручное подтверждение «Оплачено» (`require_confirm`), «удаление автосозданного платежа = пропуск месяца» (уже работает де-факто — `DELETE /expenses/:id` не трогает `auto_created_records` — закрепить тестом).
- Плановые доходы учитываются в прогнозе симметрично расходам, по бакету месяца. «Расчётный месяц со своим днём начала» — отдельная будущая фича, вне scope.
- Кламп `auto_day` 29–31 к концу месяца уже реализован в `isDayReached()` — бага нет, переиспользуем логику.
- UI веб-клиента — отдельное обсуждение; здесь только API-слой. KMP — следующий этап.
- Ленивый автопостинг (только при `POST /months/ensure`, без cron) — оставляем как есть, вернёмся при необходимости.

## Шаг 1. Миграции — `server/db/connection.js` (`ensureSchemaUpToDate`)

Рядом с блоком фиксированных категорий (строки ~166–189), по существующим паттернам:

```js
addColumnIfMissing('categories', 'require_confirm', 'INTEGER DEFAULT 0');
addColumnIfMissing('income_sources', 'require_confirm', 'INTEGER DEFAULT 0');
```

```sql
CREATE TABLE IF NOT EXISTS planned_overrides (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    template_type TEXT NOT NULL,        -- 'category' | 'income_source'
    template_id INTEGER NOT NULL,
    month_id INTEGER NOT NULL,
    override_amount REAL,               -- NULL = сумма из шаблона
    override_day INTEGER,               -- NULL = auto_day шаблона
    is_skipped INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (month_id) REFERENCES months(id),
    UNIQUE(user_id, template_type, template_id, month_id)
);
CREATE INDEX IF NOT EXISTS idx_planned_overrides_month ON planned_overrides(user_id, month_id);
```

UNIQUE-четвёрка идентична `auto_created_records`. `db_setup.js` не трогаем (как в 2.1.1).

## Шаг 2. Ядро — `server/db/helpers.js`

1. **`materializePlannedRecord(userId, month, templateType, template, {amount, day})`** — выделить из `autoCreateRecurringRecords` (строки 205–294) общий код создания записи: кламп дня, INSERT в `expenses` (`comment='Регулярный платёж'`) или `incomes`, INSERT в `auto_created_records` с `amount_at_creation` = фактически применённая сумма (вкл. override). В `db.transaction`; UNIQUE-конфликт пробрасывается (вызывающий мапит в 409).
2. **`getPlannedOverridesMap(userId, monthId)`** → `Map<'type:id', row>`.
3. **`getPlannedRecords(userId, monthId, now = new Date())`** → `{ expenses: PlannedItem[], incomes: PlannedItem[] }`. PlannedItem: `template_type, template_id, name, amount (override ?? fixed), original_amount, due_day (кламп), due_date, require_confirm, is_skipped (скипнутые включаются — для unskip в UI), is_overridden, is_overdue`. Алгоритм: активные fixed-шаблоны (тот же фильтр, что в autoCreate) минус те, у кого есть `auto_created_records` за месяц (это же покрывает «удаление = пропуск»), плюс overrides. Временная логика: будущий месяц — всё; текущий — `require_confirm=1` всегда + ненаступившие обычные; прошлый — только неподтверждённые `require_confirm=1` (`is_overdue=1`).
4. **Правки `autoCreateRecurringRecords(…, now = new Date())`**: параметр `now` для тестов; в циклах — `is_skipped → continue`, `require_confirm → continue`, effective day/amount из override, тело через `materializePlannedRecord` с try/catch UNIQUE (гонка двух ensure).
5. **`computePlannedAggregates(userId, monthId)`** — суммы plannedExpenses/plannedIncomes без `is_skipped`.
6. **Бэкап**: `createBackup` (по образцу выборки `auto_created_records`, строки 117–121), `buildBackupSummary` (+`plannedOverrides`), `restoreBackupSnapshot` (DELETE + INSERT-цикл по образцу строк 333–334; старые бэкапы — `|| []`).
7. **Деактивация шаблона** (`is_active=0` или снятие `is_fixed`): overrides не удаляем — они инертны (всё фильтруется по активным fixed), при реактивации восстанавливаются.

## Шаг 3. Роуты — `server/routes/data.js`

1. **CRUD шаблонов**: `POST/PUT /categories`, `POST/PUT /income_sources` — принять/нормализовать `require_confirm`; при `is_fixed=0` сбрасывать в 0.
2. **`GET /months/:monthId/planned`** → `{ expenses, incomes, totals: { plannedExpenses, plannedIncomes } }`. `checkMonthAccess`.
3. **`GET /months/:monthId/summary`** (строки 712–728): старые 4 поля не менять, добавить `plannedExpenses, plannedIncomes, forecastExpenses = expenses + plannedExpenses, forecastBalance = balance + plannedIncomes − plannedExpenses` (наследует семантику копилки через существующий `balance`).
4. **`PUT /months/:monthId/planned/:templateType/:templateId`** — upsert skip/override. Body `{ is_skipped?, override_amount?, override_day? }` (null = сброс). `executeIdempotent`; валидации (тип, доступ, шаблон is_fixed, amount>0, day 1..31); **409** если платёж уже материализован; «всё дефолтное» → удалить строку. Broadcast `('planned','updated')`.
5. **`DELETE /months/:monthId/planned/:templateType/:templateId/override`** — полный сброс, идемпотентный 200.
6. **`POST /months/:monthId/planned/:templateType/:templateId/confirm`** — «Оплачено»/досрочная оплата. Body `{ amount?, date? }`; дата по умолчанию: текущий месяц — сегодня, иначе due_date; явная дата валидируется на бакет. `is_skipped=1` → 409; уже материализован → 409 (+ catch UNIQUE). Через `materializePlannedRecord`. Broadcast `('expense'|'income','created')` + `('planned','updated')`.

## Шаг 4. Веб-клиент, только API-слой — `client/src/api/index.ts`

Типы `PlannedItem`, `PlannedResponse`, `MonthSummary` (расширенный); функции `getPlannedRecords`, `setPlannedOverride`, `resetPlannedOverride`, `confirmPlanned`; `require_confirm` в `Category`/`IncomeSource`; `plannedOverrides` в `BackupEntrySummary`. Точки будущей UI-интеграции (не делать сейчас): `MonthView.tsx` (`loadData` ~186–220, `useDataChanged` ~226 — добавить `'planned'`), `Dashboard.tsx`, `Layout.tsx`, `Categories.tsx` (тумблер require_confirm).

## Шаг 5. Тесты (инфраструктуры нет — Node 18+, встроенный `node:test`)

- `server/test/planned.test.js` — unit на helpers с временной `DB_PATH` (паттерн smoke-health, cleanup wal/shm) и инъекцией `now`.
- `server/scripts/smoke-planned.js` — интеграционный по образцу `smoke-health.js` (spawn сервера, HTTP-сценарии, идемпотентность, 409).
- package.json: `"test:planned": "node --test ./test/"`, включить в `npm test`.

Сценарии-минимум: виртуальный план (будущий/текущий/прошлый месяц); идемпотентность autoCreate; **удаление = пропуск** (DELETE expense → не пересоздаётся, в план не возвращается); skip/unskip; override (+409 после материализации, `amount_at_creation` = override); confirm (require_confirm не автопостится, confirm создаёт, повтор → 409, повтор с тем же X-Idempotency-Key → кэш 200); кламп auto_day=31 в коротком месяце; summary-формулы (+копилка, старые поля не изменились); backup/restore overrides; деактивация/реактивация категории.

## Шаг 6. Порядок реализации и верификация

1. Миграции → `npm run smoke:health`.
2. Рефакторинг `materializePlannedRecord` (без изменения поведения) → smoke.
3. Overrides в autoCreate + `getPlannedRecords` + агрегаты.
4. Юнит-тесты helpers (основная масса сценариев).
5. Роуты + расширение summary и CRUD.
6. Бэкап/restore.
7. smoke-planned, обновить `npm test`.
8. API-слой клиента → `cd client && npm run build` (tsc поймает потребителей summary).
9. Ручная верификация: `npm start` + curl-чек-лист (ensure → planned → skip → summary → confirm → 409), WS-сообщения `'planned'`.
10. `server/package.json` version → 2.2.0.

## Риски / на потом

- KMP-клиенты получат новые JSON-поля (`require_confirm`, расширенный summary) — аддитивно; перед релизом проверить, что парсер KMP игнорирует неизвестные ключи.
- Для KMP-этапа: новые колонки → не забыть `runAdditiveMigrations()` (память [[kmp-db-migrations]]).
- Cron для автопостинга/пушей — отложено; undo-удаления (10 сек) — клиентская часть, отложено до UI-этапа.

---

# Этап 2: UI веб-клиента (согласовано с Володей 2026-06-12)

**Статус этапа 1 (сервер + API-слой): реализован в ветке `feature/planned-expenses-forecast`, не закоммичен.**

Принципы (решения Володи): Дашборд — только информация, без кнопок действий; все действия — на экране «Месяц». Вариант плашек — «объединённый» (вторые строки в существующих карточках + одна новая «Прогноз на месяц»).

## 2.1. Дашборд — `client/src/pages/Dashboard.tsx`

Сетка 6 плашек, 2 ряда по 3 («месяц» / «итоги»):
- **Ряд 1:** «Доходы» (+ вторая приглушённая строка `🕐 ожидается: X ₽` = plannedIncomes, только если > 0) | «Расходы» (+ `🕐 по плану: X ₽` = plannedExpenses, только если > 0) | «Лимит на расходы» (без изменений, название НЕ сокращать).
- **Ряд 2:** **новая** «Прогноз на месяц» (главная цифра — «Свободно: X ₽» = forecastBalance; вторая строка — «расходы: Y ₽» = forecastExpenses) | «Накопления» (+ вторая строка «в копилку: X% от дохода» — поглощает бывшую плашку «% в копилку») | «В наличии без накоплений» (без изменений).
- Данные: расширенный `MonthSummary` из `getMonthSummary` (уже типизирован).
- **Read-only попап**: клик по «Расходы»/«Доходы» → модалка со списком плановых платежей месяца (`getPlannedRecords`): название, сумма, «спишется/ожидается N-го», маркировка просроченных (`is_overdue`) и пропущенных (`is_skipped`). Без кнопок действий; внизу ссылка «Управлять — на экране Месяц» (роут /month).
- Вторые строки и кликабельность прячутся, если плана нет (фича не используется — дашборд выглядит как раньше).
- Мини-сводка в сайдбаре (`Layout.tsx`) — НЕ трогаем в этой итерации.

## 2.2. Экран «Месяц» — `client/src/pages/MonthView.tsx`

- `loadData`: добавить `getPlannedRecords(monthId)` в существующий `Promise.all`; в `useDataChanged([...])` добавить `'planned'`.
- **Вкладка «Расходы»**: сворачиваемая секция «Запланированные платежи» НАД группами фактических расходов (сумма в заголовке секции). Строка: иконка 🕐, название, сумма, бейдж «спишется N-го»; просроченный require_confirm — warning-стиль «ждёт подтверждения»; пропущенный — приглушённый «пропущен в этом месяце» + кнопка [Вернуть] (unskip).
- **Действия в строке**: [Оплачено] (primary) → `confirmPlanned` (диалог подтверждения с суммой; для обычных категорий это «оплатить досрочно»); меню [⋯]: «Изменить на этот месяц» (диалог: сумма + день → `setPlannedOverride`), «Пропустить в этом месяце» (`setPlannedOverride {is_skipped:1}`), «Сбросить изменения» (`resetPlannedOverride`, видно только при `is_overridden`).
- **Вкладка «Доходы»**: зеркальная секция «Ожидаемые поступления» с теми же действиями.
- Карточки итогов сверху: вторые строки плана как на Дашборде (без отдельной карточки прогноза — она на Дашборде).
- Секции скрываются, когда план пуст.

## 2.3. Экран «Категории» — `client/src/pages/Categories.tsx`

- В форме фиксированной категории/источника — переключатель «Требует подтверждения оплаты» (`require_confirm` в POST/PUT payload) с подсказкой «платёж не спишется автоматически, а будет ждать кнопки „Оплачено" на экране Месяц».
- Бейдж «ручное подтверждение» на фиксированных элементах списка с `require_confirm=1`.

## 2.4. Верификация этапа 2

- `cd client && npm run build` (tsc) — типы.
- Запуск сервера + клиента, ручной прогон: создать фиксированную категорию с днём в будущем → увидеть на Дашборде вторую строку и «Прогноз», попап; на «Месяце» — секцию, skip/override/confirm; require_confirm-сценарий с кнопкой «Оплачено»; пропавший после confirm план и появившийся факт.
- Скриншоты через браузер для сверки с Володей.

Отложено на потом (фиксация): undo-удаления автосозданного платежа (10 сек), строка прогноза в мини-сводке сайдбара, график баланса с проекцией.

---

# Этап 3: KMP-клиент (Android / iOS / Desktop)

**Статус этапов 1–2: реализованы в ветке `feature/planned-expenses-forecast` (сервер 2.2.0 + веб-UI), не закоммичены.**

## Архитектура: локальное вычисление плана (оффлайн-парность)

План вычисляется **локально** той же формулой, что на сервере: активные fixed-шаблоны − материализованные за месяц + исключения. Для этого клиенту нужны два недостающих знания, которые добавляем в синхронизацию:

1. **Новый серверный эндпоинт `GET /months/:monthId/planned-state`** → `{ auto_created: [...], overrides: [...] }` — сырые строки `auto_created_records` и `planned_overrides` месяца (для sync-pull; вычисленный `/planned` для KMP не подходит — он не отдаёт сырых данных).
2. **Две новые локальные таблицы** (зеркала): `auto_created_records` (user_id, template_type, template_id, month_id, UNIQUE по четвёрке — минимальное зеркало) и `planned_overrides` (+ override_amount/override_day/is_skipped/updated_at/sync_status).

**Единицы**: локально суммы в копейках (INTEGER, как fixed_amount), конвертация ₽↔коп. на границе ApiClient (по образцу существующей `fixedAmount / 100.0`).

## 3.1. БД и миграции

- `FinKeeperDatabase.sq`: колонки `require_confirm` в categories/income_sources (в CREATE TABLE — для новых установок), таблицы `auto_created_records`, `planned_overrides` + queries (upsert/delete/getByMonth; replaceForMonth для pull).
- `DatabaseProvider.runAdditiveMigrations()`: ALTER `require_confirm` × 2 + `CREATE TABLE IF NOT EXISTS` × 2 (правило [[kmp-db-migrations]] — иначе у старых юзеров «пропадут» данные).
- desktopMain `DatabaseDriverFactory.ensureSchemaUpToDate()`: зеркальные CREATE TABLE/колонки.

## 3.2. Ядро — `PlannedCalculator` (commonMain, чистая функция)

`data/planned/PlannedCalculator.kt`: вход — fixed-шаблоны, set материализованных ключей, map overrides, year/month месяца, `today` (kotlinx.datetime, инъекция для тестов); выход — `List<PlannedItem>` (template_type/id, name, amountCents, originalAmountCents, dueDay (кламп к числу дней месяца), dueDate, requireConfirm, isSkipped, isOverridden, isOverdue) + totals. Правила попадания в план — точно как в server/db/helpers.js getPlannedRecords (будущий месяц — всё; текущий — require_confirm и скипнутые всегда, обычные до наступления дня; прошлый — только просроченные require_confirm).

Юнит-тесты в commonTest на сценарии: будущий/текущий/прошлый месяц, кламп 31-го, skip/override, overdue (зеркало серверных planned.test.js).

## 3.3. Данные и синхронизация

- `Models.kt`: DTO `PlannedStateResponse`, `AutoCreatedDto`, `PlannedOverrideDto`; `require_confirm` в Category/IncomeSource DTO.
- `ApiClient`: `getPlannedState(monthId)`, `putPlannedOverride(...)`, `deletePlannedOverride(...)`, `confirmPlanned(...)`; `require_confirm` в create/update категорий/источников.
- DAO: `PlannedOverrideDao`, `AutoCreatedDao`.
- `PlannedRepository` (commonMain): локальное чтение плана (через PlannedCalculator), действия skip/unskip/override/reset — **offline-first**: upsert локальной строки + `sync_queue` (entity_type `planned_override`, op upsert/delete → PUT/DELETE эндпоинты, идемпотентные на сервере).
- `SyncManager.syncFromServer(monthId)`: + pull `planned-state` → replace локальных строк месяца (конфликт — по `updated_at`, как у остальных сущностей); push — обработка `planned_override` из очереди.
- `WebSocketService`: убедиться, что неизвестный entity `planned` не ломает обработку (ожидаемо триггерит общий syncAll), при необходимости добавить в список.
- **«Оплачено/Получено» (confirm) в v1 — только при сети**: вызов `POST .../confirm` + полный рефреш месяца. Оффлайн кнопка неактивна с подсказкой «нужно подключение». (Оффлайн-confirm с optimistic-маркером — отдельная доработка, зафиксирована в «на потом».)
- Категории: `require_confirm` в CategoryRepository/IncomeSourceRepository (create/update/syncWithServer) и в SyncManager push.

## 3.4. ViewModel

- `MonthViewModel`: в state — `plannedExpenses/plannedIncomes: List<PlannedItem>`, totals; загрузка из PlannedRepository в `loadData` (локально, мгновенно; после syncFromServer — пересчёт). Экшены: confirm/skip/unskip/override/reset.
- `DashboardViewModel`: totals плана + прогноз («свободно» = доход + плановые доходы − все расходы − плановые расходы — как на вебе, из видимых цифр).
- `CategoriesViewModel`: requireConfirm в колбэках add/update fixed.

## 3.5. UI (desktop ↔ mobile различия)

- **MonthViewScreen**: сворачиваемая секция «Запланированные платежи» над группами расходов и «Ожидаемые поступления» во вкладке доходов (GlassyCard, приглушённый стиль, иконка 🕐, warning для просроченных, зачёркнутые скипнутые с [Вернуть]).
  - **Desktop**: inline-кнопка [Оплачено]/[Получено] + DropdownMenu по [⋯] (как в вебе), hover-эффекты.
  - **Mobile**: компактные строки, тап по строке открывает действия (DropdownMenu/диалог), touch-target ≥ 48dp, кнопка подтверждения иконкой + текст в диалоге; секции по умолчанию свёрнуты на маленьких экранах, если записей > 3.
  - Диалоги: подтверждение оплаты/получения (с редактируемой суммой), override (сумма + день, пометка «только этот месяц»), на базе AppTextField/AppButton/ConfirmDialog.
- **Карточки итогов** (Month + Dashboard): вторые приглушённые строки «🕐 по плану / ожидается», на Dashboard — карточка «Прогноз на месяц» (SummaryCard: «Свободно» + прогноз расходов).
- **CategoriesScreen**: чекбокс в диалоге фиксированного элемента — тексты раздельные: расходы «Требует подтверждения оплаты», источники «Требует подтверждения получения» (урок [[double-check-shared-ui-texts]] — проверить обе вкладки!); бейдж «✋ вручную» в FixedItemCard.
- **Strings.kt**: все новые строки парами для расходов/доходов.

## 3.6. Порядок и верификация

1. Сервер: эндпоинт `planned-state` (+ в smoke-planned тест).
2. БД/миграции (.sq + runAdditiveMigrations + desktop mirror) → сборка.
3. PlannedCalculator + commonTest.
4. DTO/ApiClient/DAO/PlannedRepository/SyncManager.
5. ViewModels + UI Month → Categories → Dashboard.
6. Верификация: `./gradlew :composeApp:desktopTest` (или общий test), запуск desktop-версии против локального сервера с тестовой БД (SKIKO_RENDER_API=SOFTWARE — память [[nvidia-wayland-black-window]]), скриншоты; Android — сборка `assembleDebug` + по возможности прогон на эмуляторе/устройстве; проверить оффлайн-режим (выключить сервер → план виден, skip/override работают и доезжают после включения).
7. Проверка обратной совместимости: старый KMP-клиент с новым сервером уже совместим (ignoreUnknownKeys=true — подтверждено).

## На потом (этап 3)

- Оффлайн-confirm (optimistic-маркер в auto_created_records + op в очереди).
- iOS-прогон вручную (нет окружения) — проверить при ближайшей сборке.

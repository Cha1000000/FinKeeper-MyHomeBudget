# План монетизации FinKeeper24 (`Семейный PRO`)

**Статус:** Одобрен к поэтапной реализации (2026-11-26)  
**Целевой тариф на лендинге:** `Семейный PRO` — `199 ₽ / мес` (дополнительно: годовой `1 490 ₽ / год`, опционально `3 490 ₽ Навсегда` на старте)

---

## 1. Продуктовые принципы

1. **Не забирать существующий бесплатный функционал (`Free`):**
   - Учёт доходов и расходов, категории, источники дохода, лимиты по категориям, умные копилки, виртуальный план-слой (фиксированные платежи), офлайн-режим, синхронизация и базовые облачные авто-бэкапы (до 5 снимков) остаются **бесплатными навсегда**.
2. **Честное выполнение обещаний с лендинга (`finkeeper24.ru`):**
   - Первым приоритетом реализуются ровно те 5 пунктов, которые заявлены в карточке `Семейный PRO` на лендинге:
     1. Глубокая аналитика и экспорт отчётов (`.xlsx` / `.csv`, горизонты 12+ месяцев, сравнение периодов).
     2. Расширенный архив резервных копий (до 30 снимков, закрепление `pin`, скачивание/загрузка `.json` на диск).
     3. Неограниченно устройств + управление активными сессиями в Настройках (в `Free` — мягкий лимит 2 активных устройства).
     4. Совместный семейный доступ с ролями (до 5 участников по одной подписке владельца).
     5. Приоритетная поддержка (формат канала связи и ИИ-ассистента проектируется отдельным шагом — см. Раздел 6).
3. **Мягкий онбординг в PRO:**
   - **14 дней бесплатного Триала PRO** в один клик без привязки карты (`canUseTrial`).
   - **Жест лояльности для ранних пользователей (~154 аккаунта):** возможность начислить 30–60 дней бонусного PRO всем активным пользователям, зарегистрированным до запуска монетизации.
   - **Закладка фундамента под промокоды/ручную выдачу:** в схеме БД сразу предусматривается источник `source = 'promo' | 'manual_grant'` и таблица `promo_codes` (без публичного UI на старте, чтобы включить в любой момент).

---

## 2. Архитектура разделения Free / PRO (`Entitlements`)

### 2.1. Бэкенд (`server/`) — единый источник правды

Все миграции добавляются в `server/db/connection.js` → `ensureSchemaUpToDate()`:

1. **Таблица `subscriptions`:**
   - `id INTEGER PRIMARY KEY AUTOINCREMENT`
   - `user_id INTEGER NOT NULL UNIQUE REFERENCES users(id)`
   - `plan TEXT NOT NULL DEFAULT 'free'` (`'free' | 'pro' | 'pro_family'`)
   - `status TEXT NOT NULL DEFAULT 'active'` (`'active' | 'trial' | 'grace_period' | 'canceled' | 'expired'`)
   - `source TEXT NOT NULL DEFAULT 'system'` (`'system' | 'trial' | 'yookassa' | 'robokassa' | 'rustore' | 'promo' | 'family_member' | 'manual_grant'`)
   - `trial_used INTEGER NOT NULL DEFAULT 0`
   - `started_at TEXT`
   - `expires_at TEXT` (ISO-8601 UTC; `NULL` для `free` или `lifetime`)
   - `family_group_id INTEGER` (ссылка на семейную группу, если пользователь — владелец или участник семьи)
   - `created_at TEXT NOT NULL`, `updated_at TEXT NOT NULL`

2. **Таблица `billing_transactions` (журнал платежей и активаций):**
   - `id INTEGER PRIMARY KEY AUTOINCREMENT`
   - `user_id INTEGER NOT NULL REFERENCES users(id)`
   - `provider TEXT NOT NULL` (`'yookassa' | 'robokassa' | 'rustore' | 'trial' | 'promo'`)
   - `external_payment_id TEXT UNIQUE` (ID платежа в кассе для идемпотентности вебхуков)
   - `plan_period TEXT NOT NULL` (`'month' | 'year' | 'lifetime' | 'trial_14d'`)
   - `amount_rub REAL NOT NULL DEFAULT 0`
   - `status TEXT NOT NULL` (`'pending' | 'succeeded' | 'canceled' | 'refunded'`)
   - `raw_payload TEXT`
   - `created_at TEXT NOT NULL`, `updated_at TEXT NOT NULL`

3. **Таблица `promo_codes` (фундамент на будущее):**
   - `code TEXT PRIMARY KEY`, `duration_days INTEGER NOT NULL`, `max_uses INTEGER`, `used_count INTEGER DEFAULT 0`, `expires_at TEXT`, `is_active INTEGER DEFAULT 1`.

4. **Сервис `server/services/entitlementService.js`:**
   - Вычисляет для `userId` (с учётом его личной подписки или членства в активной семейной группе владельца PRO) единый объект `entitlements` и включает его в ответ `/api/auth/me`, `/api/auth/login`, `/api/auth/refresh`:
     ```json
     {
       "plan": "pro",
       "status": "trial",
       "isPro": true,
       "isTrial": true,
       "canStartTrial": false,
       "expiresAt": "2026-12-10T12:00:00.000Z",
       "source": "trial",
       "features": {
         "exportReports": true,
         "extendedAnalytics": true,
         "extendedBackups": true,
         "unlimitedDevices": true,
         "familyAccess": true,
         "prioritySupport": true
       },
       "limits": {
         "maxBackups": 30,
         "maxDevices": 99,
         "analyticsMonths": 36,
         "maxFamilyMembers": 5
       }
     }
     ```
   - Middleware `requireEntitlement(featureKey)` для защиты PRO-роутов на сервере (`HTTP 403` с `{ code: 'PRO_REQUIRED', feature: featureKey }`).

### 2.2. KMP-клиент (`mobile_app_client/`) с поддержкой Offline-First

1. **Кэширование и Offline Grace Period:**
   - `Entitlements` сохраняются локально при каждом обновлении профиля/синке.
   - Если устройство офлайн, а сохранённый `isPro == true`, действует **льготный офлайн-период 7 дней (`OFFLINE_GRACE_DAYS = 7`)** после `expiresAt`, чтобы у платящего пользователя без интернета не отключались PRO-функции.
2. **Единый `ProFeatureGate` и `ProPaywallDialog`:**
   - Компонент/хелпер в `commonMain`, который при клике на PRO-функцию проверяет `entitlements.features.<key>`.
   - Если `false` — открывает модалку `ProPaywallDialog` в стиле Liquid Glass с описанием конкретной фичи, списком преимуществ `Семейный PRO` и кнопкой **«Попробовать 14 дней бесплатно»** (или переходом к оплате).

### 2.3. Веб-клиент (`client/`)

1. Поле `user.entitlements` в `AuthContext` + хук `useEntitlements()`.
2. Единый компонент `<ProPaywallModal />` с запуском 14-дневного триала (`POST /api/billing/start-trial`) и выбором тарифа.

---

## 3. План реализации по вехам

### Веха 1 — Фундамент `Entitlements`, 14-дневный Триал и быстрые PRO-фичи
1. **Сервер (`Entitlements` + Триал):**
   - Миграция таблиц `subscriptions`, `billing_transactions`, `promo_codes` в `ensureSchemaUpToDate()`.
   - Расширение `auth_refresh_sessions` метаданными устройства (`platform`, `device_name`, `user_agent`, `ip`).
   - Эндпоинты `GET /api/billing/status`, `POST /api/billing/start-trial` (активация 14 дней PRO один раз на аккаунт), `POST /api/billing/redeem-promo` (базовый обработчик).
   - Выдача `entitlements` в `/api/auth/me` и broadcast `entitlement_updated` по WebSocket.
2. **Глубокая аналитика и экспорт отчётов (`exportReports`, `extendedAnalytics`):**
   - Серверный и/или клиентский экспорт в **Excel (`.xlsx` / `.csv`)** за месяц, квартал или год (сводка, расходы, доходы, копилки).
   - Расширение `/api/analytics/trend?months=12|24|36` (в `Free` — 6 месяцев, в `PRO` — переключатель `6 мес / 12 мес / По годам`, карточка сравнения с прошлым месяцем и `Burn Rate`).
3. **Расширенный архив резервных копий (`extendedBackups`):**
   - В `Free` — ротация до 5 авто-бэкапов; в `PRO` — до 30 бэкапов + флаг `is_pinned` (защита снимка от автоудаления).
   - Скачивание бэкапа в `.json` файл на устройство и загрузка/восстановление из локального `.json` файла.
4. **Контроль устройств и сессий (`unlimitedDevices`):**
   - Список активных сессий/устройств в Настройках (Web + KMP) с кнопкой «Завершить сеанс».
   - Мягкий лимит в `Free` (2 активных устройства: при входе на 3-м либо вытесняется самая старая сессия, либо предлагается PRO).
5. **UI Paywall & Trial (Web + KMP):**
   - Карточка статуса подписки в Настройках («Базовый / Триал PRO (осталось X дн.) / Семейный PRO»).
   - Модалка `ProPaywallModal` / `ProPaywallDialog` с активацией 14-дневного триала в один клик.

---

### Веха 2 — Платёжный шлюз (ЮКасса / Робокасса + СБП + опционально RuStore Pay)
1. **Интеграция в режиме песочницы (Sandbox без ожидания оформления самозанятости):**
   - И в ЮКассе, и в Робокассе **тестовый магазин (test mode)** доступен сразу после регистрации в личном кабинете без статуса самозанятого.
   - Перед стартом Вехи 2 сравним комиссии и удобство API ЮКассы vs Робокассы для самозанятых (НПД, авточеки в «Мой налог», СБП QR, карты `Мир`).
2. **Серверный платёжный модуль (`server/routes/billing.js`):**
   - `POST /api/billing/create-payment` (создание платежа на месяц / год / навсегда, возврат `confirmationUrl` / СБП-ссылки).
   - `POST /api/billing/webhook` (проверка подписи/IP провайдера, идемпотентная обработка `payment.succeeded`, продление `subscriptions.expires_at`, WebSocket пуш `entitlement_updated`).
3. **Боевое включение:**
   - После оформления самозанятости Володей — простановка боевых ключей в `.env` на сервере и разблокировка кнопки покупки на лендинге `finkeeper24.ru`.

---

### Веха 3 — Совместный семейный доступ с ролями (`familyAccess`)
1. **Модель данных семейного бюджета:**
   - Таблицы `family_groups` (`id`, `owner_user_id`, `name`, `created_at`), `family_members` (`group_id`, `user_id`, `role`: `'owner' | 'partner' | 'member'`, `joined_at`), `family_invites` (`token`, `group_id`, `role`, `expires_at`).
   - Поле `author_user_id` в `expenses`, `incomes`, `savings_transactions` (чтобы видеть, кто из членов семьи добавил запись).
2. **Механика работы:**
   - Когда пользователь состоит в семейной группе, запросы к месяцам/категориям/копилкам работают в контексте `effectiveBudgetOwnerId` (владельца семьи), а права проверяются по роли:
     - `owner` — полный доступ + управление участниками, бэкапами и подпиской.
     - `partner` — добавление, редактирование и удаление операций, категорий, лимитов и копилок.
     - `member` (ребёнок / наблюдатель) — просмотр и добавление своих расходов без удаления чужих записей и без доступа к бэкапам/настройкам семьи.
   - Все участники семьи (до 5 человек) автоматически получают PRO-привилегии от подписки `owner`.
   - В списках расходов/доходов отображается аккуратный бейдж автора операции + фильтр по участникам семьи.

---

## 4. Отдельное проектирование: «Приоритетная поддержка» (после фиксации базовых пунктов)

Перед реализацией блока поддержки отдельно обсудить и выбрать архитектуру:
- **Канал связи в Настройках приложения:**
  - Вариант А: встроенная форма тикета / кнопка перехода в **Telegram-бота поддержки FinKeeper24** (с автоматической передачей привязанного `userId` и статуса `[PRO]`).
  - Вариант Б: отправка обращения на выделенный email с метаданными аккаунта и приоритетным флагом.
- **ИИ-помощник первой линии в Telegram-боте:**
  - Подключение бесплатного LLM API (например, **Google Gemini API Free Tier** — `gemini-2.5-flash`, либо бесплатные модели через **Groq / OpenRouter**) с системным промптом по документации и FAQ FinKeeper24.
  - Сценарий: бот мгновенно отвечает на 80% типовых вопросов («как работают копилки», «как настроить фиксированный платёж», «как восстановить бэкап»), а если вопрос требует участия разработчика или пользователь нажимает «Позвать Володю» — пересылает диалог Володе в Telegram с пометкой `⚡ [PRO Support]`.

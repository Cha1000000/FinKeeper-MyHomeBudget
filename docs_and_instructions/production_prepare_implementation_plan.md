# Production Prepare Implementation Plan

## Назначение документа

Этот документ фиксирует текущее состояние FinKeeper с точки зрения подготовки к достойному production-качеству для публичного или полупубличного использования сторонними пользователями.

Документ основан на текущей реализации проекта:

- `server/` — Express + SQLite + JWT + WebSocket
- `client/` — React + Vite + TypeScript
- `mobile_app_client/` — Kotlin Multiplatform + Compose + offline-first sync

Документ содержит:

- фактически выявленные текущие недочёты и риски
- рекомендации по улучшению
- инструкции по внедрению улучшений
- roadmap по приоритетам `P0 / P1 / P2`

Порядок изложения и приоритизации:

1. Серверная часть
2. Web-клиент
3. KMP-клиент

---

## Краткий итог по текущей готовности

На текущем этапе FinKeeper уже является сильным функциональным продуктом для:

- личного использования
- ограниченного круга пользователей
- закрытого beta-режима
- soft launch без агрессивной нагрузки

Однако для уверенного публичного production launch проекту пока не хватает нескольких обязательных слоёв зрелости:

- security hardening
- более зрелой auth/session модели
- более безопасных destructive flows
- лучшей production-конфигурации среды
- улучшенного UX для ошибок, сессий и recovery-сценариев
- более формализованных эксплуатационных практик

Ключевой вывод:

**Функционально продукт уже близок к production, но по безопасности и эксплуатационной зрелости он пока не полностью production-grade для открытого публичного использования.**

---

## Сильные стороны текущей реализации

### Сервер

- JWT-аутентификация уже работает
- пароли хешируются через `bcryptjs`
- большая часть API защищена через `authenticateToken`
- данные разделены по `user_id`
- реализованы backup/restore
- есть WebSocket-обновления
- есть поддержка idempotency для sync-операций
- есть `deleted_records` и `updated_at/created_at` для синхронизации
- схема БД частично self-healing через `ensureSchemaUpToDate()`

### Web

- есть защищённые маршруты
- auth flow реализован просто и понятно
- Dashboard, Month View, Categories, Savings, Settings образуют цельный продуктовый контур
- есть адаптивность и приятный UI
- есть backup/restore и смена имени/пароля

### KMP

- есть offline-first архитектура
- локальное хранилище и sync queue уже встроены
- есть login flow
- есть settings, backup/restore, logout
- есть WebSocket + sync manager
- есть поддержка тем и единая архитектура для нескольких платформ

---

## Roadmap по приоритетам

### P0 — обязательно до публичного релиза

#### P0-01. Сервер: убрать небезопасный fallback для JWT secret

- **Текущая проблема:** в `server/index.js` используется `process.env.JWT_SECRET || 'my-home-budget-secret-key-change-this'`
- **Риск:** production может запуститься с предсказуемым секретом; появляется риск подделки токенов
- **Рекомендация:** убрать fallback и требовать `JWT_SECRET` строго через environment
- **Инструкция по исправлению:**
  1. Изменить инициализацию `JWT_SECRET` в `server/index.js`
  2. Добавить раннюю проверку конфигурации при старте сервера
  3. Если `JWT_SECRET` отсутствует, завершать процесс с понятной ошибкой
  4. Обновить `README.md` и `docs_and_instructions/DEPLOYMENT.md`

#### P0-02. Сервер: сократить срок жизни access token и внедрить зрелую session strategy

- **Текущая проблема:** в `server/index.js` токены выдаются с `expiresIn: '365d'`
- **Риск:** слишком длинное окно компрометации украденного токена и отсутствие управляемой модели сессий
- **Рекомендация:** сократить TTL access token; лучшее решение — `short-lived access token + refresh token`
- **Инструкция по исправлению:**
  1. Пересмотреть контракт `/api/auth/login` и `/api/auth/register`
  2. При необходимости добавить endpoints refresh/revoke
  3. Определить стратегию хранения refresh token
  4. Обновить web и KMP auth flow
  5. Продумать forced logout на всех клиентах

#### P0-03. Сервер: ограничить CORS

- **Текущая проблема:** в `server/index.js` используется `app.use(cors())` без allowlist
- **Риск:** API принимает запросы с любых origins, что не соответствует production hardening
- **Рекомендация:** перевести CORS на whitelist через env
- **Инструкция по исправлению:**
  1. Добавить `APP_ENV` и `CORS_ALLOWED_ORIGINS`
  2. Вычислять список разрешённых origin из env
  3. Разделить dev и production конфигурации
  4. Разрешать в production только официальные домены клиентов

#### P0-04. Сервер: добавить rate limiting для auth и чувствительных endpoints

- **Текущая проблема:** отсутствует rate limiting для auth и destructive endpoints
- **Риск:** brute force, credential stuffing, abuse
- **Рекомендация:** внедрить rate limiting минимум для `/api/auth/login`, `/api/auth/register`, `/api/user/password`, `/api/user/restore`
- **Инструкция по исправлению:**
  1. Подключить middleware наподобие `express-rate-limit`
  2. Сделать отдельные профили лимитов
  3. Вернуть понятные ответы при превышении лимита
  4. Описать настройки в deployment-документации

#### P0-05. Сервер: усилить password-change flow

- **Текущая проблема:** `/api/user/password` принимает только `newPassword`
- **Риск:** украденная активная сессия даёт злоумышленнику возможность сразу сменить пароль
- **Рекомендация:** требовать `currentPassword` и `newPassword`
- **Инструкция по исправлению:**
  1. Изменить контракт endpoint в `server/index.js`
  2. Проверять текущий пароль через `bcrypt.compareSync` или async-эквивалент
  3. Обновить web settings screen и KMP settings screen
  4. После смены пароля инвалидировать другие сессии или перевыпускать токены

#### P0-06. Сервер: усилить backup restore flow

- **Текущая проблема:** `/api/user/restore` восстанавливает только последнюю резервную копию целиком
- **Риски:** destructive action без re-auth, нет выбора backup, нет preview
- **Рекомендация:** добавить список backup entries и restore по выбранному `backupId`
- **Инструкция по исправлению:**
  1. Добавить endpoint получения backup-метаданных
  2. Переделать restore на восстановление по `backupId`
  3. Добавить дополнительное подтверждение или re-auth
  4. Обновить web и KMP интерфейсы под выбор backup по дате

#### P0-07. Сервер: добавить базовый security hardening middleware

- **Текущая проблема:** не видно `helmet`, ограничений размера body и централизованного security-oriented error handling
- **Риск:** слабая базовая защищённость HTTP-слоя
- **Рекомендация:** внедрить `helmet`, ограничить `express.json()`, централизовать обработку ошибок
- **Инструкция по исправлению:**
  1. Добавить `helmet`
  2. Настроить `express.json({ limit: ... })`
  3. Проверить совместимость заголовков со SPA и WebSocket
  4. Вынести глобальный обработчик ошибок в единый слой

#### P0-08. Web: пересмотреть стратегию хранения токена

- **Текущая проблема:** токен хранится в `localStorage`
- **Где:** `client/src/context/AuthContext.tsx`, `client/src/api/index.ts`, `client/src/hooks/useWebSocket.ts`
- **Риск:** токен доступен JS-коду; при XSS его легче похитить
- **Рекомендация:** пересмотреть auth model для web, по возможности перейти на безопасную схему с refresh token
- **Инструкция по исправлению:**
  1. Выбрать целевую auth-модель
  2. Обновить `AuthContext` и API interceptor
  3. Обновить WebSocket auth flow
  4. Добавить обработку истёкшей сессии

#### P0-09. KMP: перевести хранение токена на secure storage

- **Текущая проблема:** в `mobile_app_client/.../TokenStorage.kt` токен хранится в обычном `Settings`
- **Риск:** storage не соответствует platform-grade secure storage
- **Рекомендация:** использовать platform-specific secure storage
- **Инструкция по исправлению:**
  1. Спроектировать абстракцию secure token storage
  2. Вынести хранение auth token в platform-specific реализацию
  3. Сохранить совместимость с текущим `AuthViewModel`
  4. Выполнить миграцию со старого хранилища

#### P0-10. KMP: скрыть или ограничить настройку server URL в production-сборках

- **Текущая проблема:** в `LoginScreen.kt` есть ручная настройка server URL, а в `TokenStorage.kt` зашит дефолтный `http://217.114.8.82:3002`
- **Риск:** пользователю доступна лишняя техническая настройка; `http://` в production — плохой сигнал
- **Рекомендация:** скрыть настройку в production и перевести production URL на `https://`
- **Инструкция по исправлению:**
  1. Вынести server URL в build-time/environment strategy
  2. Показывать server settings только в debug/internal builds
  3. Заменить production URL на HTTPS-вариант (это пока отложим, так как текущий внешний сервер не поддерживает HTTPS)
  4. Проверить derivation для WebSocket (`wss://`)

### P1 — очень желательно до широкого релиза

#### P1-01. Сервер: валидация payload по схемам

- **Текущая проблема:** проверка входных данных в основном сводится к наличию полей
- **Риск:** malformed payloads, неединые ошибки, сложно поддерживать правила
- **Рекомендация:** внедрить schema validation для auth/user/settings и основных write endpoints
- **Инструкция по исправлению:**
  1. Выбрать validation layer
  2. Формализовать правила для username/password и пользовательских строк
  3. Вернуть единый формат ошибок валидации

#### P1-02. Сервер: централизованный logging и audit trail

- **Текущая проблема:** много `console.log`/`console.error`, но нет оформленного audit слоя
- **Риск:** сложно расследовать security-sensitive инциденты
- **Рекомендация:** ввести структурированное логирование и audit trail
- **Инструкция по исправлению:**
  1. Ввести logger wrapper
  2. Разделить operational logs и security logs
  3. Не допускать утечки токенов и секретов в логах

#### P1-03. Сервер: усилить production-конфигурацию среды

- **Текущая проблема:** env-конфигурация используется ограниченно
- **Рекомендация:** вынести в конфигурацию `PORT`, `JWT_SECRET`, token TTL, CORS, database path, body size limit, rate limit thresholds
- **Инструкция по исправлению:**
  1. Вынести конфигурацию в единый config module
  2. Валидировать env на старте
  3. Документировать переменные в deployment docs

#### P1-04. Сервер: усилить WebSocket auth strategy

- **Текущая проблема:** WebSocket auth идёт через query string token
- **Риск:** query params легче попадают в логи и инфраструктурные следы
- **Рекомендация:** пересмотреть auth handshake и обеспечить только `wss://` в production
- **Инструкция по исправлению:**
  1. Оценить допустимую альтернативу auth handshake
  2. Если query token остаётся, исключить его из логов
  3. Гарантировать TLS на production

#### P1-05. Web: улучшить session UX и 401/403 recovery

- **Текущая проблема:** нет полноценного UX вокруг истечения токена и forced logout
- **Рекомендация:** добавить глобальный обработчик auth errors и корректный relogin flow
- **Инструкция по исправлению:**
  1. Добавить response interceptor в `client/src/api/index.ts`
  2. Централизовать logout-on-401
  3. Показывать пользователю понятное сообщение
  4. Продумать поведение websocket reconnect после logout

#### P1-06. Web: улучшить auth UX

- **Текущая проблема:** `client/src/pages/Login.tsx` функционален, но без достаточного production polish
- **Рекомендация:** добавить password rules, helper texts, более понятные ошибки
- **Инструкция по исправлению:**
  1. Добавить UI-подсказки по username/password policy
  2. Ввести клиентскую предварительную валидацию
  3. Подготовить точку входа для future recovery flow

#### P1-07. Web: улучшить UX destructive operations в Settings

- **Текущая проблема:** restore backup идёт к последнему backup и без выбора версии
- **Рекомендация:** добавить список backup entries, явное destructive confirmation и прозрачный post-restore UX
- **Инструкция по исправлению:**
  1. Добавить backup list UI
  2. Показывать дату и краткое описание backup
  3. Усилить предупреждение перед откатом
  4. Сделать post-restore поведение более прозрачным

#### P1-08. Web: улучшить loading, error и empty states

- **Текущая проблема:** основной интерфейс сильный, но не хватает цельной обработки edge cases
- **Рекомендация:** унифицировать skeleton, empty, retry и error patterns
- **Инструкция по исправлению:**
  1. Выделить ключевые страницы
  2. Для каждой описать loading/empty/error states
  3. Привести UX к единому шаблону

#### P1-09. KMP: улучшить sync UX

- **Текущая проблема:** архитектурно sync силён, но пользовательский UX ещё можно усилить
- **Рекомендация:** добавить sync status, время последней успешной синхронизации, retry action и понятные offline/conflict messages
- **Инструкция по исправлению:**
  1. Определить единый sync status model для UI
  2. Вывести его на ключевые экраны
  3. Добавить manual sync trigger
  4. Улучшить recovery texts

#### P1-10. KMP: усилить UX авторизации

- **Текущая проблема:** `AuthViewModel.kt` и `LoginScreen.kt` уже хороши, но их можно сделать более продуктово понятными
- **Рекомендация:** усилить объяснение ошибок сети и авторизации, добавить helper texts
- **Инструкция по исправлению:**
  1. Уточнить тексты в `Strings`
  2. Добавить helper text на логине/регистрации
  3. При необходимости улучшить loading/error states

#### P1-11. KMP: улучшить backup/restore UX

- **Текущая проблема:** backup и restore уже есть, но UX остаётся довольно базовым
- **Рекомендация:** показывать список резервных копий и лучше объяснять последствия restore
- **Инструкция по исправлению:**
  1. Добавить экран или секцию списка backup entries
  2. Обновить `SettingsViewModel` под backup metadata
  3. Улучшить post-restore UX

### P2 — улучшения следующей волны

#### P2-01. Сервер: продумать account recovery / password reset

- **Текущая проблема:** нет восстановления доступа при забытом пароле
- **Рекомендация:** спроектировать account recovery strategy
- **Принятое направление:** `email-based self-service recovery` только для аккаунтов с подтверждённым email, без recovery по одному `username`
- **Артефакт решения:** `docs_and_instructions/adr/0001-email-based-account-recovery.md`
- **Текущий статус:** strategy принята и основной flow уже внедрён на server + web + KMP; пункт можно считать функционально закрытым
- **Инструкция по исправлению:**
  1. Определить идентификатор восстановления
  2. Добавить recovery flow и anti-abuse защиту
  3. Интегрировать flow в web и KMP

#### P2-02. Сервер: наблюдаемость и мониторинг

- **Рекомендация:** добавить structured logs, metrics, health checks, alerts и crash visibility
- **Инструкция по исправлению:**
  1. Ввести health endpoint
  2. Вынести логи в удобный формат
  3. Настроить минимальный monitoring stack

#### P2-03. Сервер: переоценить долгосрочную пригодность SQLite

- **Текущая ситуация:** SQLite пока остаётся допустимым компромиссом для небольшого числа пользователей
- **Рекомендация:** заранее определить критерии миграции на PostgreSQL
- **Инструкция по исправлению:**
  1. Определить триггеры миграции
  2. Подготовить migration plan заранее

#### P2-04. Web и KMP: product polish и onboarding

- **Рекомендация:** усилить onboarding первого запуска, подсказки, нулевые состояния и объяснения по backup/sync
- **Дополнительный future item:** предусмотреть эволюцию auth UX с social login (`Google`, `Яндекс`, при необходимости `Mail.ru`) для web/KMP клиентов с соответствующей серверной поддержкой
- **Артефакт решения по стратегии:** `docs_and_instructions/adr/0003-social-login-strategy.md`
- **Текущий статус foundation:** на сервере уже подготовлены schema/base contracts для social auth (`auth_identities`, `auth_login_exchange_codes`, provider registry, `oauth/*` contract stubs, `exchange` endpoint); provider-specific handshake и client UX остаются следующей фазой
- **Текущий статус Google Web wave:** реализован backend `start/callback/exchange` flow и web callback completion; для реального запуска необходимы `APP_BASE_URL`, `PUBLIC_API_BASE_URL`, `GOOGLE_OAUTH_ENABLED=1`, `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`

#### P2-05. Тестирование и quality gates

- **Текущая проблема:** в корневом `package.json` нет реального test-based quality gate
- **Рекомендация:** усилить smoke tests, integration tests и regression checklist
- **Инструкция по исправлению:**
  1. Зафиксировать минимальный pre-release набор проверок
  2. Добавить автоматизируемые smoke tests
  3. Вынести критичные regression scenarios в отдельный checklist

---

## Реестр выявленных проблем по компонентам

### Серверная часть

#### SRV-01. JWT secret с небезопасным fallback

- **Где обнаружено:** `server/index.js`
- **Что сейчас:** используется fallback secret при отсутствии env
- **Что нужно сделать:** убрать fallback и валидировать env на старте
- **Приоритет:** `P0`

#### SRV-02. Слишком долгоживущий JWT

- **Где обнаружено:** `server/index.js`
- **Что сейчас:** `expiresIn: '365d'`
- **Что нужно сделать:** сократить access token TTL и внедрить refresh/re-auth strategy
- **Приоритет:** `P0`

#### SRV-03. Нет rate limiting

- **Где обнаружено:** auth и user endpoints в `server/index.js`
- **Что нужно сделать:** добавить rate limiting middleware
- **Приоритет:** `P0`

#### SRV-04. Слишком открытый CORS

- **Где обнаружено:** `server/index.js`
- **Что нужно сделать:** whitelist по origin
- **Приоритет:** `P0`

#### SRV-05. Смена пароля без current password

- **Где обнаружено:** `server/index.js`, web/KMP settings flows
- **Что нужно сделать:** изменить API contract и обновить оба клиента
- **Приоритет:** `P0`

#### SRV-06. Restore backup без выбора backup и без re-auth

- **Где обнаружено:** `server/index.js`, `client/src/pages/Settings.tsx`, `mobile_app_client/.../SettingsScreen.kt`
- **Что нужно сделать:** backup list, restore by id, stronger confirmation
- **Приоритет:** `P0 / P1`

#### SRV-07. Недостаточный security hardening HTTP-слоя

- **Где обнаружено:** `server/index.js`
- **Что нужно сделать:** `helmet`, body limit, better error handling
- **Приоритет:** `P0`

#### SRV-08. Недостаточная формализация env-конфигурации

- **Где обнаружено:** `server/index.js`, deployment docs
- **Что нужно сделать:** единый config layer и validate env on boot
- **Приоритет:** `P1`

#### SRV-09. WebSocket auth через query string token

- **Где обнаружено:** `server/index.js`, `client/src/hooks/useWebSocket.ts`, `mobile_app_client/.../WebSocketService.kt`
- **Что нужно сделать:** пересмотреть модель auth для WS, минимизировать утечку query params в логах, обеспечить `wss://`
- **Приоритет:** `P1`

### Web-клиент

#### WEB-01. Токен хранится в `localStorage`

- **Где обнаружено:** `client/src/context/AuthContext.tsx`, `client/src/api/index.ts`, `client/src/hooks/useWebSocket.ts`
- **Что нужно сделать:** пересмотреть auth persistence strategy
- **Приоритет:** `P0`

#### WEB-02. Нет зрелого session-expired UX

- **Где обнаружено:** `client/src/context/AuthContext.tsx`, `client/src/api/index.ts`
- **Что нужно сделать:** response interceptor и global logout/relogin flow
- **Приоритет:** `P1`

#### WEB-03. Auth UI ещё можно усилить

- **Где обнаружено:** `client/src/pages/Login.tsx`
- **Что нужно сделать:** password rules, helper texts, better error texts, future recovery entry point
- **Приоритет:** `P1`

#### WEB-04. Restore UX в Settings слишком простой

- **Где обнаружено:** `client/src/pages/Settings.tsx`
- **Что нужно сделать:** backup version selection, stronger destructive confirmation, clearer restore aftermath UX
- **Приоритет:** `P1`

#### WEB-05. Недостаточно полные loading/error/empty states

- **Где обнаружено:** ключевые web pages
- **Что нужно сделать:** унифицировать состояния, добавить retry/empty/skeleton patterns
- **Приоритет:** `P1`

### KMP-клиент

#### KMP-01. Токен хранится не в secure storage

- **Где обнаружено:** `mobile_app_client/.../TokenStorage.kt`
- **Что нужно сделать:** platform-secure token storage
- **Приоритет:** `P0`

#### KMP-02. В production UI не должна торчать ручная настройка server URL

- **Где обнаружено:** `mobile_app_client/.../LoginScreen.kt`, `mobile_app_client/.../TokenStorage.kt`, `mobile_app_client/.../AuthViewModel.kt`
- **Что нужно сделать:** скрыть настройку для production и перевести default server URL на HTTPS
- **Приоритет:** `P0`

#### KMP-03. Sync UX можно сделать понятнее для обычного пользователя

- **Где обнаружено:** KMP sync/ui flows
- **Что нужно сделать:** last sync time, sync status, retry action, clearer offline/conflict messaging
- **Приоритет:** `P1`

#### KMP-04. Auth UX и backup UX требуют дальнейшего product polish

- **Где обнаружено:** `LoginScreen.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`
- **Что нужно сделать:** stronger helper texts, better restore flow, more transparent post-action states
- **Приоритет:** `P1`

---

## Практический порядок внедрения

### Этап 1 — закрыть критичные риски на сервере

1. JWT secret only via env
2. CORS allowlist
3. rate limiting
4. password change contract hardening
5. restore flow hardening
6. `helmet` и body limits
7. новая session/token strategy

### Этап 2 — привести web auth/session UX в production-ready состояние

1. обновить auth storage strategy
2. глобально обработать `401/403`
3. улучшить Login UX
4. переделать backup restore UX

### Этап 3 — усилить KMP security и production UX

1. secure token storage
2. production-safe server URL strategy
3. sync status UX
4. backup restore UX

### Этап 4 — усилить эксплуатационную зрелость

1. config layer
2. structured logging
3. smoke/integration checks
4. health checks
5. критерии масштабирования beyond SQLite

---

## Implementation batches по файлам

Ниже приведена практическая разбивка внедрения по пакетам работ. Каждый batch должен быть по возможности самостоятельным, проверяемым и небольшим по объёму изменений.

### Batch 1 — серверная конфигурация и базовый hardening

- **Цель:** закрыть самые опасные production-конфигурационные риски без изменения auth-модели целиком
- **Связанные приоритеты:** `P0-01`, `P0-03`, `P0-07`, частично `P1-03`
- **Основные файлы:**
  - `server/index.js`
  - `README.md`
  - `docs_and_instructions/DEPLOYMENT.md`
- **Что внедрять:**
  - mandatory `JWT_SECRET` без fallback
  - env-based `PORT`
  - env-based CORS allowlist
  - `helmet`
  - `express.json({ limit: ... })`
  - базовую валидацию обязательной env-конфигурации при старте
- **Почему это отдельный batch:**
  - даёт немедленный прирост безопасности
  - минимально затрагивает клиентские контракты
  - хорошо проверяется отдельно
- **Зависимости:** нет
- **Критерий готовности:**
  - сервер не стартует без обязательных env
  - CORS работает только для разрешённых origins
  - HTTP hardening включён

### Batch 2 — rate limiting и защита чувствительных endpoints

- **Цель:** ограничить abuse до изменений в auth/session архитектуре
- **Связанные приоритеты:** `P0-04`
- **Основные файлы:**
  - `server/index.js`
  - при необходимости новый utility/module внутри `server/`
- **Что внедрять:**
  - rate limiting для login/register
  - rate limiting для password change
  - rate limiting для backup restore
  - понятные ответы при превышении лимитов
- **Почему это отдельный batch:**
  - изменение локальное и хорошо изолируется
  - не требует немедленной переделки клиентов
- **Зависимости:** желательно после Batch 1
- **Критерий готовности:**
  - чувствительные endpoints защищены лимитами
  - лимиты документированы

### Batch 3 — безопасная смена пароля end-to-end

- **Цель:** убрать небезопасный контракт смены пароля
- **Связанные приоритеты:** `P0-05`
- **Основные файлы:**
  - `server/index.js`
  - `client/src/api/index.ts`
  - `client/src/pages/Settings.tsx`
  - `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/ui/screens/SettingsScreen.kt`
  - `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/ui/viewmodel/SettingsViewModel.kt`
  - KMP API client file для password endpoint
- **Что внедрять:**
  - `currentPassword + newPassword` на сервере
  - обновлённый контракт в web API
  - обновлённые формы web/KMP
  - более ясные пользовательские сообщения об ошибках
- **Почему это отдельный batch:**
  - меняет API-контракт
  - затрагивает сразу server + оба клиента
  - удобно тестировать как отдельный пользовательский сценарий
- **Зависимости:** желательно после Batch 1
- **Критерий готовности:**
  - пароль нельзя сменить без текущего пароля
  - web и KMP корректно работают с новым контрактом

### Batch 4 — безопасный backup restore flow

- **Цель:** убрать рискованный restore последнего backup без выбора версии
- **Связанные приоритеты:** `P0-06`, `P1-07`, `P1-11`
- **Основные файлы:**
  - `server/index.js`
  - `client/src/api/index.ts`
  - `client/src/pages/Settings.tsx`
  - `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/ui/screens/SettingsScreen.kt`
  - `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/ui/viewmodel/SettingsViewModel.kt`
  - KMP API client file для backup endpoints
- **Что внедрять:**
  - endpoint списка backup metadata
  - restore по `backupId`
  - более сильное подтверждение destructive action
  - выбор backup в web/KMP UI
- **Почему это отдельный batch:**
  - это отдельный критичный доменный сценарий
  - изменения затрагивают сервер и оба клиента, но изолированы от auth-модели
- **Зависимости:** желательно после Batch 1
- **Критерий готовности:**
  - пользователь может выбрать backup по дате
  - restore не завязан на безусловный «последний backup»

### Batch 5 — выбор новой auth/session strategy для web и KMP

- **Цель:** принять архитектурное решение по дальнейшей модели токенов и сессий
- **Связанные приоритеты:** `P0-02`, `P0-08`, `P0-09`, частично `P1-05`
- **Основные файлы:**
  - `server/index.js`
  - `client/src/context/AuthContext.tsx`
  - `client/src/api/index.ts`
  - `client/src/hooks/useWebSocket.ts`
  - `mobile_app_client/.../TokenStorage.kt`
  - `mobile_app_client/.../AuthViewModel.kt`
  - `mobile_app_client/.../WebSocketService.kt`
  - KMP API client/auth-related files
- **Что решить перед кодом:**
  - останется ли JWT-only модель, но с коротким TTL
  - будет ли refresh token
  - как хранится refresh token в web
  - как хранится токен в KMP
  - как ведут себя клиенты при истечении сессии
- **Почему это отдельный batch:**
  - это архитектурный узел, влияющий почти на все последующие auth-изменения
  - без этого решения не стоит хаотично править `localStorage` и KMP storage
- **Зависимости:** желательно после Batch 1 и Batch 2
- **Критерий готовности:**
  - выбран и зафиксирован единый auth/session contract
  - понятны требования к server, web и KMP

### Batch 6 — серверная реализация новой session/token модели

- **Цель:** реализовать выбранную auth/session strategy на backend
- **Связанные приоритеты:** `P0-02`
- **Основные файлы:**
  - `server/index.js`
  - при необходимости новые auth/session utilities внутри `server/`
- **Что внедрять:**
  - короткий TTL access token
  - refresh/revoke endpoints при выбранной стратегии
  - invalidation logic
  - унифицированные auth-ответы сервера
- **Почему это отдельный batch:**
  - удобно сначала стабилизировать server contract
  - клиенты затем адаптируются поверх уже готового API
- **Зависимости:** Batch 5
- **Критерий готовности:**
  - auth endpoints работают по новой стратегии
  - контракт стабилен для клиентов

### Batch 7 — web auth storage и session recovery

- **Цель:** довести web auth до production-ready UX и storage-модели
- **Фактический статус:** выполнено
- **Связанные приоритеты:** `P0-08`, `P1-05`
- **Основные файлы:**
  - `client/src/context/AuthContext.tsx`
  - `client/src/api/index.ts`
  - `client/src/hooks/useWebSocket.ts`
  - `client/src/pages/Login.tsx`
- **Что внедрять:**
  - новую стратегию auth persistence
  - глобальный `401/403` handling
  - relogin/session-expired UX
  - более аккуратный logout flow
- **Почему это отдельный batch:**
  - batch затрагивает только web-клиент после стабилизации backend auth
  - хорошо тестируется отдельно
- **Зависимости:** Batch 6
- **Критерий готовности:**
  - web корректно переживает истечение сессии
  - storage соответствует новой модели
- **Фактически внедрено:**
  - web auth persistence через `sessionStorage` и optional persistent storage для `remember me`
  - глобальный `401` handling и refresh для remembered-сессий
  - обработка серверного `403 invalid_access_token` как session-expired case
  - forced logout с понятным `session-expired` UX на `Login`
  - best-effort logout с вызовом серверного `/api/auth/logout`
  - ручная browser-проверка сценариев login, refresh recovery, forced logout и обычного logout
  - follow-up fix для remembered-session: `AuthContext` теперь пытается refresh перед очисткой auth state, если `remember me` включён и access token уже истёк
  - follow-up fix для remembered-session: WebSocket auth-close (`4003`) теперь сначала пытается восстановить remembered-session через refresh, а не делает немедленный logout

### Batch 8 — KMP secure storage и session handling

- **Цель:** привести KMP auth-хранение и сессионное поведение к production-уровню
- **Фактический статус:** выполнено
- **Связанные приоритеты:** `P0-09`, частично `P0-02`
- **Основные файлы:**
  - `mobile_app_client/.../TokenStorage.kt`
  - `mobile_app_client/.../AuthViewModel.kt`
  - `mobile_app_client/.../WebSocketService.kt`
  - KMP API client/auth-related files
  - platform-specific secure storage implementations
- **Что внедрять:**
  - secure token storage
  - migration со старого storage
  - обработку expired session
  - совместимость с новым серверным auth-контрактом
- **Фактически внедрено:**
  - единая KMP auth/session модель с глобальным `401/403` recovery через refresh в `ApiClient`
  - single-flight refresh через `Mutex`, чтобы параллельные запросы не запускали несколько refresh подряд
  - автоматический forced logout при неуспешном refresh с `session-expired` уведомлением на экране входа
  - `TokenStorage.authEvents` для синхронизации session state между `ApiClient` и `AuthViewModel`
  - secure storage на Android через `EncryptedSharedPreferences`, на iOS через `KeychainSettings`
  - secure desktop storage через OS credential store (`com.microsoft:credential-secure-storage`) вместо обычного `Settings()` на диске
  - безопасный desktop fallback: если OS secure store недоступен, токены живут только в памяти процесса
  - сохранена migration access token из legacy `Settings()` в secure storage через `TokenStorage`
  - обновлены KMP unit tests под текущий `AuthViewModel` init flow и `Dispatchers.Main` harness
- **Почему это отдельный batch:**
  - требует платформенно-специфичной реализации
  - логически отделяется от web-части
- **Зависимости:** Batch 6
- **Критерий готовности:**
  - токены больше не лежат в обычном insecure storage
  - KMP корректно переживает refresh/relogin сценарии

### Batch 9 — KMP production-safe server URL strategy

- **Цель:** убрать dev-oriented server URL поведение из production UX
- **Связанные приоритеты:** `P0-10`
- **Основные файлы:**
  - `mobile_app_client/.../TokenStorage.kt`
  - `mobile_app_client/.../LoginScreen.kt`
  - `mobile_app_client/.../AuthViewModel.kt`
  - build config / platform config files KMP
- **Что внедрять:**
  - production URL через build-time config
  - hidden/manual override только для debug/internal builds
  - обязательный `https://`/`wss://`
- **Почему это отдельный batch:**
  - изменение больше про release discipline и product UX, чем про core auth
- **Зависимости:** можно делать параллельно с Batch 8, если не мешает auth refactor
- **Критерий готовности:**
  - production build не показывает лишние server settings обычному пользователю

### Batch 10 — schema validation и унификация ошибок сервера

- **Цель:** формализовать входные данные и улучшить устойчивость API
- **Фактический статус:** выполнено
- **Связанные приоритеты:** `P1-01`
- **Основные файлы:**
  - `server/index.js`
  - при необходимости новые validation utilities/modules внутри `server/`
- **Что внедрять:**
  - schema validation для auth/user endpoints
  - единый формат validation errors
  - нормализацию пользовательских входных данных
- **Почему это отдельный batch:**
  - повышает качество API без обязательной переделки UI
- **Зависимости:** лучше после Batch 1–4
- **Критерий готовности:**
  - сервер стабильно возвращает предсказуемые ошибки валидации
- **Фактически внедрено:**
  - единый формат validation-ошибок через `422 validation_error` с массивом `details: [{ field, message }]`
  - нормализация пользовательских входных данных через общие helper-функции сервера
  - централизованные payload-валидаторы для auth, password recovery, email verification, social auth и user endpoints
  - вынос части auth/user валидаторов из `server/index.js` в отдельный модуль `server/validation/authValidation.js`
  - сохранение совместимого серверного API-контракта при более чистой структуре validation-слоя

### Batch 11 — web UX polish для auth/settings/error states

- **Цель:** довести web UX до более зрелого production-вида
- **Фактический статус:** выполнено
- **Связанные приоритеты:** `P1-06`, `P1-07`, `P1-08`
- **Основные файлы:**
  - `client/src/pages/Login.tsx`
  - `client/src/pages/Settings.tsx`
  - ключевые страницы `Dashboard`, `MonthView`, `Savings`
  - при необходимости общие UI components
- **Что внедрять:**
  - password rules и helper texts
  - улучшенные destructive confirmations
  - единые loading/error/empty states
  - retry patterns
- **Почему это отдельный batch:**
  - улучшает product polish после стабилизации критичных server/auth изменений
- **Зависимости:** желательно после Batch 3, 4 и 7
- **Критерий готовности:**
  - web UX по auth/settings/error scenarios стал более цельным и предсказуемым
- **Фактически внедрено:**
  - общий `StatusBanner` для success/error/info/warning feedback на auth/settings и ключевых страницах
  - расширенный `PageState` для единых loading/error/empty states, включая compact-вариант
  - унификация состояний на `Login`, `PasswordRecovery`, `EmailVerification`, `SocialAuthCallback`, `Settings`, `Dashboard`, `MonthView`, `Savings`, `Categories`
  - stylistic polish destructive confirmation модалок в `MonthView`, `Categories`, `Savings`
  - mutation-level error feedback в `MonthView` и `Categories`
  - мягкий fallback UX для боковой сводки в `Layout` с сохранением последних данных и retry action

### Batch 12 — KMP UX polish для auth/settings/sync

- **Цель:** улучшить KMP UX поверх уже стабилизированной security/session базы
- **Фактический статус:** выполнено
- **Связанные приоритеты:** `P1-09`, `P1-10`, `P1-11`
- **Основные файлы:**
  - `mobile_app_client/.../LoginScreen.kt`
  - `mobile_app_client/.../SettingsScreen.kt`
  - `mobile_app_client/.../SettingsViewModel.kt`
  - экраны/компоненты, где показывается sync state
- **Что внедрять:**
  - helper texts на auth экранах
  - лучший UX backup/restore
  - sync status, last sync, retry action
  - более понятные offline/error messages
- **Почему это отдельный batch:**
  - это product polish слой после основной security и auth стабилизации
- **Зависимости:** желательно после Batch 4, 8 и 9
- **Критерий готовности:**
  - KMP ощущается как более зрелый пользовательский продукт, а не только как технически сильный клиент
- **Фактически внедрено:**
  - helper texts и валидационные подсказки на auth flow (`login/register/recovery/social`)
  - улучшенный UX backup/restore в `SettingsScreen`: loading/empty список backup, выбор backup, confirm dialog, явное подтверждение восстановление
  - sync UX в `SettingsScreen` и `NetworkStatusIndicator`: online/offline, pending count, retry action, clear error action
  - отображение `lastSuccessfulSyncAt` + сохранение последней успешной синхронизации через `SyncManager`/`SyncStateStorage`
  - более понятные offline/error сообщения в `SettingsViewModel` и `AuthViewModel` (включая `OFFLINE_RETRY_LATER`, `SESSION_EXPIRED`, `CONNECTION_ERROR`)
  - smoke-check KMP: `./gradlew composeApp:testDebugUnitTest` (успешно)

### Batch 13 — observability, health checks и quality gates

- **Цель:** довести проект до эксплуатационно более зрелого состояния
- **Связанные приоритеты:** `P1-02`, `P2-02`, `P2-05`
- **Основные файлы:**
  - `server/index.js`
  - возможные новые logging/health modules
  - `package.json`
  - CI/CD или workflow files при необходимости
  - тестовые файлы для server/web/KMP
- **Что внедрять:**
  - structured logging
  - health endpoint
  - базовые smoke/integration checks
  - pre-release checklist automation там, где это оправдано
- **Почему это отдельный batch:**
  - это эксплуатационный слой, который лучше делать после стабилизации ключевой бизнес-логики и auth
- **Зависимости:** после Batch 1–12 по мере готовности
- **Критерий готовности:**
  - проект лучше наблюдаем и лучше проверяется перед релизом

### Batch 14 — долгосрочные продуктовые и архитектурные улучшения

- **Цель:** подготовить следующую волну развития после закрытия основных production gaps
- **Связанные приоритеты:** `P2-01`, `P2-03`, `P2-04`
- **Основные файлы:**
  - `docs_and_instructions/adr/0001-email-based-account-recovery.md`
  - `docs_and_instructions/product_onboarding_web_kmp_plan.md`
  - `docs_and_instructions/production_prepare_implementation_plan.md`
  - остальные файлы будут зависеть от выбранных следующих решений
- **Что внедрять:**
  - account recovery
  - product onboarding
  - social login strategy (`Google`, `Яндекс`, при возможности `Mail.ru`)
  - критерии миграции с SQLite
  - долгосрочные release/scale решения
- **Уже зафиксировано в рамках Batch 14:**
  - `ADR-0001` с направлением на `email-based account recovery`
  - отдельный implementation-план onboarding: `docs_and_instructions/product_onboarding_web_kmp_plan.md`
  - явное правило: self-service recovery доступен только для аккаунтов с подтверждённым email
  - аккаунты без подтверждённого email требуют product-level mitigation через onboarding/settings
- **Почему это отдельный batch:**
  - эти задачи не должны блокировать ближайший production hardening
- **Зависимости:** после закрытия основной P0/P1 зоны
- **Критерий готовности:**
  - у проекта есть понятный план роста после базового production hardening

### Рекомендуемый порядок выполнения batches

1. Batch 1
2. Batch 2
3. Batch 3
4. Batch 4
5. Batch 5
6. Batch 6
7. Batch 7
8. Batch 8
9. Batch 9
10. Batch 10
11. Batch 11
12. Batch 12
13. Batch 13
14. Batch 14

### Какие batches особенно удобно делать следующими сразу

Если переходить к реализации уже сейчас, я бы рекомендовал стартовать так:

1. **Batch 1**
2. **Batch 2**
3. **Batch 3**

Это даст быстрый и реальный прирост production readiness ещё до большого auth refactor.

---

## Минимальный чеклист перед публичным релизом

### Обязательно

- `JWT_SECRET` без fallback
- короткоживущий access token
- rate limiting на auth
- CORS allowlist
- secure password change flow
- hardened backup restore flow
- `helmet` и body limits
- безопасная стратегия хранения токена на web/KMP
- `https://` и `wss://` в production

### Очень желательно

- schema validation
- session-expired UX
- backup list and restore by id
- improved sync UX
- structured logging
- smoke tests

---

## Финальный вывод

На данный момент FinKeeper уже достоин статуса сильного рабочего продукта, но не полностью дотягивает до уровня публичного production-grade сервиса.

Основной разрыв находится не в core-функциональности, а в следующих слоях:

- безопасность
- зрелость сессий и авторизации
- безопасность destructive operations
- эксплуатационная конфигурация
- UX для ошибок, восстановления и edge cases

После выполнения задач уровня `P0` проект можно будет рассматривать как кандидата на более уверенный публичный запуск.

После выполнения `P1` он будет заметно ближе к реально зрелому production-quality уровню.

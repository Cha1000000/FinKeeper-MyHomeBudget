# План реализации приложения "Домашняя Бухгалтерия"

## Назначение документа

Этот документ описывает **актуальное состояние реализации FinKeeper** с учётом текущего кода:

- web-клиента
- серверной части
- Kotlin Multiplatform приложения

Это уже **не стартовый план создания проекта с нуля**, а актуализированная карта:

- что уже реализовано
- как сейчас устроена система
- какие направления развития ещё остаются

## Текущий статус проекта

На данный момент FinKeeper — это **готовое мультиплатформенное приложение** для ведения личных финансов с общей серверной частью и несколькими клиентами:

- web-клиент
- Android-приложение
- iOS-приложение
- desktop-приложение для macOS / Windows / Linux

Поддерживаются:

- учёт доходов
- учёт расходов
- категории расходов
- источники дохода
- лимиты бюджета по категориям на месяц
- накопления ("копилки")
- аналитика по месяцу и трендам
- авторизация пользователей
- резервные копии и восстановление
- WebSocket-обновления
- offline-first sync для KMP-клиента

## Актуальный стек технологий

### Web-клиент

- React 19
- TypeScript
- Vite 7
- Tailwind CSS 4
- React Router 7
- Axios
- Recharts 3
- `@dnd-kit` для drag-and-drop
- `date-fns`
- `lucide-react`

### Сервер

- Node.js
- Express 5
- SQLite
- `better-sqlite3`
- `jsonwebtoken`
- `bcryptjs`
- `ws`
- `cors`

### KMP-клиент

- Kotlin 2.2.10
- Compose Multiplatform 1.6.10
- Ktor Client 3.0.1
- Koin 3.5.6
- SQLDelight 2.0.2
- kotlinx-serialization 1.7.1
- kotlinx-datetime 0.6.0
- Multiplatform Settings 1.1.1

## Структура проекта

```text
FinKeeper-MyHomeBudget/
├── client/                  # Web-клиент
├── server/                  # Backend API + SQLite + WebSocket
├── mobile_app_client/       # Kotlin Multiplatform клиент
├── docs_and_instructions/   # Техническая документация
├── start.sh                 # Dev startup (remote IP)
├── start_local.sh           # Dev startup (localhost)
└── start_prod.sh            # Production startup
```

## Актуальная архитектура системы

### 1. Сервер как общий источник данных

Сервер отвечает за:

- хранение пользовательских данных
- JWT-аутентификацию
- CRUD-операции по финансовым сущностям
- резервные копии и восстановление
- WebSocket-уведомления
- sync contract для KMP-клиента

Все пользовательские данные привязаны к `user_id`.

### 2. Web-клиент

Web-версия работает как основной online-клиент и использует REST API напрямую.

Ключевые особенности:

- авторизация через `AuthContext`
- защищённые маршруты
- помесячный режим работы через `ensureMonth`
- аналитический dashboard
- управление категориями и источниками дохода
- работа с копилками
- настройка профиля, тем и резервных копий

### 3. KMP-клиент

KMP-приложение реализовано по модели **offline-first**:

- локальная БД используется как источник UI-состояния
- изменения ставятся в очередь синхронизации
- `SyncManager` выполняет download/upload sync
- `SyncService` запускает sync автоматически при наличии сети и после логина

Это делает мобильный и desktop-клиент независимыми от постоянной доступности сервера.

## Реализованные бизнес-сущности

Система уже работает со следующими сущностями:

- `users`
- `categories`
- `income_sources`
- `months`
- `incomes`
- `expenses`
- `budgets`
- `savings_goals`
- `savings_transactions`
- `user_backups`
- `deleted_records`
- `idempotency_keys`

## Особенности модели данных

### Категории

- категории расходов можно создавать и переупорядочивать
- для удаления используется soft-delete через `is_active`
- порядок категорий хранится через `sort_order`

### Источники дохода

- поддерживают soft-delete через `is_active`
- доступны отдельно от расходов

### Месяцы

- данные привязаны к месяцу
- месяц идентифицируется комбинацией `year + month + user_id`
- KMP-клиент использует `ensureMonth` для получения server month

### Бюджеты

- лимит задаётся для пары `месяц + категория`
- в интерфейсах отображается превышение лимита

### Копилки

- пополнение копилки создаёт скрытый расход в категории `"Пополнение копилки"`
- это уменьшает доступный баланс месяца
- снятие из копилки создаёт отрицательную savings transaction без скрытого расхода

## Что уже реализовано по платформам

### Web

#### Уже реализовано

- логин и регистрация
- dashboard со сводкой месяца
- тренд по 6 месяцам
- круговая аналитика расходов
- month view для доходов и расходов
- inline-редактирование сумм
- drag-and-drop сортировка категорий
- настройка месячных лимитов
- CRUD категорий
- CRUD источников дохода
- экран копилок
- экран настроек
- резервные копии
- адаптивный layout

#### Текущие особенности UX

- desktop sidebar
- mobile bottom navigation
- обновление части данных по событиям и WebSocket
- сохранение выбранного месяца в локальном хранилище

### Серверная часть

#### Сервер: уже реализовано

- JWT auth
- middleware `authenticateToken`
- multi-user изоляция данных
- SQLite schema и runtime migration-подобные проверки
- CRUD API для основных сущностей
- backup/restore
- WebSocket-уведомления
- canonical write responses для sync
- поддержка `created_at` / `updated_at`
- tombstones через `deleted_records`
- idempotency через `operationId`

### KMP

#### KMP: уже реализовано

- Android / iOS / Desktop targets
- общий UI на Compose Multiplatform
- MVVM + `StateFlow`
- Koin DI
- локальная БД
- очередь sync-операций
- download/upload sync
- merge policy по `updated_at`
- tombstone apply logic
- post-login sync
- network-aware sync
- темы оформления

## Актуальный sync-статус

Текущая реализация синхронизации фактически завершена и работает по модели:

- локальная запись → очередь → upload
- download server snapshots
- merge через server timestamps
- удаление через tombstones
- защита от повторной обработки через `operationId`

Синхронизация покрывает:

- category
- income_source
- month
- income
- expense
- budget
- savings_goal
- savings_transaction

Подробности вынесены в отдельный документ:

- `docs_and_instructions/current_sync_implementation.md`

## Актуальные команды разработки

### Web / server dev

```bash
npm start
```

### Production startup

```bash
npm run start:prod
```

### KMP Android

```bash
cd mobile_app_client
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:assembleRelease
```

### KMP Desktop

```bash
cd mobile_app_client
./gradlew :composeApp:run
./gradlew :composeApp:packageDistributionForCurrentOS
```

### KMP iOS compile check

```bash
cd mobile_app_client
./gradlew composeApp:compileKotlinIosSimulatorArm64
```

## Что больше неактуально в старом плане

Следующие пункты из исходной версии документа больше не соответствуют состоянию проекта:

- проект уже не ограничивается только SPA web-приложением
- модель данных не требует предварительного подтверждения — она уже реализована
- backend, frontend и KMP-клиент уже созданы
- аналитика, лимиты, копилки и адаптивный UI уже реализованы
- вопрос стоит не о создании базовой архитектуры, а о развитии и поддержке существующей системы

## Актуальные направления дальнейшего развития

Ниже перечислены не "обязательные недостающие этапы запуска", а реальные возможные направления развития проекта.

### 1. Повышение качества web-клиента

- расширять accessibility
- убирать оставшиеся UI edge cases
- добавлять более формализованные тесты
- улучшать пустые состояния и микроинтеракции

### 2. Развитие KMP-клиента

- укреплять UX вокруг offline-first сценариев
- улучшать диагностику sync-ошибок
- развивать desktop packaging и platform-specific polish
- расширять тестовое покрытие sync-логики

### 3. Развитие серверной части

- укреплять безопасность production-конфигурации
- улучшать observability и диагностику
- развивать миграционную стратегию БД
- расширять автоматизированные проверки API

### 4. Документация и эксплуатация

- поддерживать README/BUILD/docs в актуальном состоянии
- фиксировать архитектурные решения отдельными ADR при крупных изменениях
- документировать эксплуатационные процедуры и восстановление данных

## Актуальный приоритет работ

Если ориентироваться на текущее состояние системы, практический приоритет сейчас выглядит так:

1. Поддержка и полировка существующего функционала
2. Тестирование и стабилизация sync / multi-platform сценариев
3. UX-улучшения web и KMP клиентов
4. Улучшение эксплуатационной документации и production readiness

## План проверки

### Ручная проверка

- регистрация и логин нового пользователя
- создание и редактирование доходов / расходов
- установка и проверка лимитов
- работа с копилками
- проверка backup / restore
- проверка dashboard и month view
- проверка mobile layout в web-клиенте
- проверка KMP sync между устройствами

### Автоматизированные проверки

Текущее состояние:

- у web-части нет полноценного развитого тестового набора
- у KMP доступны unit-тесты через Gradle
- часть проверок сейчас фактически опирается на ручную валидацию поведения

Рекомендуемые направления:

- расширение unit/integration тестов для KMP
- формализация API smoke/integration checks
- постепенное усиление UI/regression-проверок для web

## Связанные документы

- `README.md`
- `client/README.md`
- `mobile_app_client/README.md`
- `mobile_app_client/BUILD.md`
- `docs_and_instructions/current_sync_implementation.md`

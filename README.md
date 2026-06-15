# FinKeeper — Домашняя бухгалтерия

Мультиплатформенное приложение для учёта личных финансов: доходы, расходы, лимиты бюджета по категориям, накопления ("копилки"), аналитика за месяц и тренды за 6 месяцев. Валюта — RUB.

## Платформы

| Платформа | Технологии | Статус |
| --------- | ---------- | ------ |
| **Web** | React 19 + Vite 7 + TypeScript + Tailwind CSS 4 | ✅ Готово |
| **Android** | Kotlin Multiplatform + Compose Multiplatform | ✅ Готово |
| **iOS** | Kotlin Multiplatform + Compose Multiplatform | ✅ Готово |
| **macOS** | Compose Desktop (JVM) | ✅ Готово |
| **Windows** | Compose Desktop (JVM) | ✅ Готово |
| **Linux** | Compose Desktop (JVM) | ✅ Готово |

## Структура репозитория

```text
FinKeeper-MyHomeBudget/
├── client/                  # Web-клиент (React + Vite + TypeScript)
├── server/                  # Backend (Express + SQLite)
├── mobile_app_client/       # Мобильное и десктопное приложение (KMP)
│   └── composeApp/
│       └── src/
│           ├── commonMain/  # Общий код для всех платформ
│           ├── androidMain/ # Android-специфичный код
│           ├── iosMain/     # iOS-специфичный код
│           └── desktopMain/ # Desktop-специфичный код (macOS/Windows/Linux)
├── .github/workflows/       # CI/CD (GitHub Actions)
├── docs_and_instructions/   # Документация и планы
├── start.sh                 # Запуск dev-сервера (remote IP)
├── start_local.sh           # Запуск dev-сервера (localhost)
└── start_prod.sh            # Запуск production
```

---

## Backend (сервер)

- **Стек**: Express 5, SQLite (better-sqlite3), JWT (jsonwebtoken), bcryptjs, cors, WebSocket (ws)
- **Порт**: `PORT` из env, по умолчанию `3002`
- **Авторизация**: регистрация/логин с хешированием паролей и JWT (365 дней). Middleware `authenticateToken` защищает все `/api/*` маршруты. `JWT_SECRET` обязателен через env
- **Резервные копии**: JSON-снапшоты данных пользователя (макс 5), создаются при логине, проверке токена (раз в час), вручную. Транзакционное восстановление
- **WebSocket**: real-time обновления между клиентами
- **Sync contract**: сервер поддерживает `created_at` / `updated_at`, tombstones через `deleted_records` и idempotent write requests через `operationId`
- **План-слой (регулярные платежи)**: фиксированные категории/источники авто-создают записи в дату списания (`autoCreateRecurringRecords`); виртуальный «план» ещё не наступивших платежей и прогноз месяца отдаются через `GET /months/:id/planned` и расширенный `/summary`; исключения (пропуск/override/ручное подтверждение) — `planned_overrides` + endpoints `PUT/DELETE .../planned/...` и `POST .../confirm`; сырое состояние для оффлайн-клиентов — `GET /months/:id/planned-state`
- **Production hardening**: `helmet`, CORS allowlist через `ALLOWED_ORIGINS`, лимит JSON body через `JSON_BODY_LIMIT`
- **Rate limiting**: отдельные лимиты на `register/login`, `password change` и `backup restore` с `429` и `Retry-After`

### Схема БД

| Таблицы | Назначение |
| ------- | ---------- |
| `users` | Пользователи |
| `categories` | Категории расходов (sort_order, is_active; фиксированные платежи: `is_fixed`, `fixed_amount`, `auto_day`, `require_confirm`) |
| `income_sources` | Источники дохода (is_active; те же поля фиксированных платежей, что и у категорий) |
| `months` | Месяцы (year + month + user_id, unique) |
| `incomes`, `expenses` | Доходы и расходы (expenses с comment) |
| `budgets` | Лимиты на категорию/месяц |
| `auto_created_records` | Трекинг автосозданных регулярных платежей (идемпотентность; UNIQUE на user+тип+шаблон+месяц) |
| `planned_overrides` | Исключения план-слоя на месяц: пропуск / override суммы или дня регулярного платежа |
| `savings_goals` | Цели накоплений (копилки) |
| `savings_transactions` | Транзакции копилок (с month_id) |
| `deleted_records` | Tombstones удалённых записей для sync |
| `idempotency_keys` | Хранилище обработанных `operationId` |
| `user_backups` | Резервные копии данных |
| `auth_refresh_sessions` | Refresh-сессии (хэш токена, срок, отзыв) для обновления access-токена |
| `auth_email_verification_tokens` | Токены подтверждения email (хэш, срок, `used_at`) |
| `auth_password_reset_tokens` | Токены восстановления пароля (хэш, срок, `used_at`) |
| `auth_identities` | Привязки аккаунта к OAuth-провайдерам (`provider` + `provider_user_id`, unique) |
| `auth_login_exchange_codes` | Одноразовые коды обмена при OAuth-логине (`code_hash`, `client_type`, `redirect_uri`) |
| `auth_social_login_attempts` | Состояние попыток social-логина (статус, `exchange_code`, коды ошибок) |

**Логика копилок**: при пополнении создаётся скрытый расход в категории "Пополнение копилки" (`is_active=0`), чтобы вклад в копилки снижал доступный баланс месяца. Снятие — отрицательная транзакция без скрытого расхода.

---

## Web-клиент (`client/`)

- **Стек**: React 19, TypeScript, Vite 7, Tailwind CSS 4, Recharts 3, React Router 7, @dnd-kit, date-fns, Lucide icons, Axios
- **Роутинг**: защищённые маршруты под `RequireAuth`, публичный `/login`
- **Состояние**: `AuthContext` (user, login/logout, UI-настройки)
- **API слой**: Axios instance с `Authorization: Bearer <token>`, типизированные интерфейсы

### Страницы

| Страница | Описание |
| -------- | -------- |
| **Dashboard** | Сводка месяца (доходы/расходы с план-подписками, прогноз месяца, накопления, лимит, активы), тренд 6 мес., структура расходов (PieChart); попап со списком плановых платежей |
| **MonthView** | Ядро учёта: доходы/расходы, inline-редактирование, drag-and-drop сортировка, лимиты бюджета; секции «Запланированные платежи»/«Ожидаемые поступления» с действиями Оплачено/пропуск/override |
| **Categories** | CRUD категорий расходов и источников дохода; фиксированные платежи (сумма, день, флаг «требует подтверждения») |
| **Savings** | Копилки: цели, пополнение/снятие, прогресс-бар |
| **Settings** | Профиль, безопасность, бэкапы, тема, UI-настройки |

### Layout

- **Desktop**: левый сайдбар (glassmorphism, emerald gradient), сворачиваемый
- **Mobile**: нижняя навигация (fixed bottom bar)
- Финансовая сводка обновляется каждые 30 сек + по событию `savingsUpdated`

---

## Мобильное и десктопное приложение (`mobile_app_client/`)

### Стек

| Библиотека | Версия | Назначение |
| ---------- | ------ | ---------- |
| Kotlin | 2.2.10 | Язык |
| Compose Multiplatform | 1.6.10 | UI-фреймворк |
| Ktor Client | 3.0.3 | HTTP-клиент |
| Koin | 3.5.6 | Dependency Injection |
| kotlinx-serialization | 1.7.1 | JSON сериализация |
| kotlinx-datetime | 0.6.1 | Дата/время |
| SQLDelight | 2.0.2 | Локальная БД |
| Multiplatform Settings | 1.2.0 | Хранение настроек |

### Архитектура

- **MVVM**: ViewModels (`androidx.lifecycle.ViewModel`) + `StateFlow`
- **Offline-first**: локальная SQLite БД + очередь синхронизации (`SyncManager`, `SyncService`)
- **DI**: Koin — общий `appModule` + платформенные модули (`androidAppModule`, `desktopAppModule`)
- **Сеть**: `NetworkMonitor` (expect/actual) — мониторинг подключения, авто-синхронизация при появлении интернета
- **WebSocket**: real-time обновления от сервера
- **Sync**: timestamp-aware merge, canonical server snapshots, tombstones и `operationId`

### Экраны

| Экран | Описание |
| ----- | -------- |
| **SplashScreen** | Экран загрузки (2 сек) |
| **LoginScreen** | Авторизация/регистрация, настройка URL сервера |
| **DashboardScreen** | Сводка: доходы/расходы с план-подписками, прогноз месяца, накопления, графики |
| **MonthViewScreen** | Учёт за месяц: расходы по категориям, доходы, бюджеты; секции плановых платежей/поступлений (Оплачено/пропуск/override); редактирование суммы доходов и суммы+комментария расходов |
| **CategoriesScreen** | Управление категориями и источниками дохода; фиксированные платежи с флагом «требует подтверждения» |
| **SavingsScreen** | Копилки с целями и транзакциями |
| **SettingsScreen** | Профиль, тема, бэкапы |

### Темы оформления

Поддерживается 4 темы: **Light**, **Cyberpunk** (dark), **Dark** (ночная), **Dark Night** (глубокая ночная), а также автоматический режим (системная тема).

### Платформенные особенности

**Android:**

- Min SDK 24 (Android 7.0), Target SDK 35
- HTTP-клиент: Ktor OkHttp
- SQLite: Android SQLite Driver
- Подписанный APK для release

**iOS:**

- Поддержка ARM64 и Simulator ARM64
- HTTP-клиент: Ktor Darwin
- SQLite: Native Driver

**Desktop (macOS / Windows / Linux):**

- JVM Target 17
- HTTP-клиент: Ktor CIO
- SQLite: JDBC SQLite Driver
- Корутины: `kotlinx-coroutines-swing` (Dispatchers.Main)
- JDK модули: `java.instrument`, `java.management`, `java.prefs`, `java.sql`, `jdk.unsupported`
- Packaging: DMG/PKG (macOS), EXE/MSI (Windows), DEB (Linux)

---

## Запуск

### Web (dev)

```bash
npm start
# Запускает backend (:3002) и frontend dev-server (:5174)
# Vite проксирует /api на backend
```

### Web (production)

```bash
npm run start:prod
# Собирает client, Express раздаёт client/dist + API на :3002
```

### Android

```bash
cd mobile_app_client
./gradlew :composeApp:assembleDebug     # Debug APK
./gradlew :composeApp:assembleRelease   # Release APK (подписанный)
```

### Desktop (macOS)

```bash
cd mobile_app_client
./gradlew :composeApp:run                              # Запуск без упаковки
./gradlew :composeApp:packageDistributionForCurrentOS   # Сборка .app
```

Результат: `composeApp/build/compose/binaries/main/app/FinKeeper.app`

### Desktop (Windows)

```bash
cd mobile_app_client
./gradlew :composeApp:packageExe   # EXE установщик
./gradlew :composeApp:packageMsi   # MSI установщик
```

### iOS

Открыть `mobile_app_client/iosApp/iosApp.xcodeproj` в Xcode и собрать.

---

## CI/CD (GitHub Actions)

Все workflow'ы запускаются вручную (`workflow_dispatch`): **Actions** → выбрать workflow → **Run workflow**. Артефакты сборки хранятся 3 дня.

### Сборка desktop-пакетов

| Workflow | Платформы | Артефакты |
| --- | --- | --- |
| **Build Desktop Apps** (`build-desktop.yml`) | Linux + Windows + macOS | `.deb`, `.rpm`, `.msi`, `.app` |
| **Build Linux Desktop App** (`build-linux.yml`) | Linux | `.deb`, `.rpm` |
| **Build Windows Desktop App** (`build-windows.yml`) | Windows | `.msi` |
| **Build macOS Desktop App** (`build-mac.yml`) | macOS | `.app` |

### Публикация AUR-пакета

**Publish AUR package** (`publish-aur.yml`) обновляет AUR-пакет `finkeeper24-bin`: берёт версию из `app_version`, считает `sha256` с `.deb` на `finkeeper24.ru` и публикует рецепт в AUR. Запускать **после** ручной заливки нового `.deb` на сайт.

- порядок релиза: `mobile_app_client/README.md` → раздел «Релиз и публикация»
- план и обоснование: `docs_and_instructions/aur-ci-publish-plan.md`

---

## Потоки данных

- **Клиент → API**: все операции через `/api/*` с JWT-токеном
- **Месяцы**: `ensureMonth` гарантирует наличие записи месяца, затем через `month_id` загружаются доходы, расходы, лимиты
- **Аналитика**: тренд по 6 последним месяцам и месячная сводка (доходы/расходы/накопления/баланс)
- **Offline-режим (мобильное/десктоп)**: операции сохраняются в локальную БД и очередь синхронизации. При появлении интернета `SyncService` автоматически синхронизирует данные с сервером
- **Merge policy (KMP)**: серверные `updated_at` используются для сравнения локальной и удалённой версии записи
- **Удаления (KMP)**: tombstones загружаются с incremental cursor `since`
- **Real-time**: WebSocket-соединение для мгновенных обновлений между устройствами

---

## Особенности

- Мультипользовательская система (`user_id` во всех таблицах, проверка доступа)
- Soft-delete для категорий и источников дохода (`is_active=0`)
- Event-driven обновления между компонентами (`savingsUpdated`)
- Glassmorphism UI с emerald gradient темой
- Адаптивный дизайн: desktop sidebar + mobile bottom navigation
- Денежный формат: `1 000 000 ₽` (RUB, без дробной части)
- Формат дат: `DD.MM.YYYY`
- Язык интерфейса: русский

---

## Актуальная документация

- Текущая реализация синхронизации клиента и сервера: `docs_and_instructions/current_sync_implementation.md`
- KMP build/run команды: `mobile_app_client/BUILD.md`

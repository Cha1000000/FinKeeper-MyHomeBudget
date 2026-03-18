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
- **Production hardening**: `helmet`, CORS allowlist через `ALLOWED_ORIGINS`, лимит JSON body через `JSON_BODY_LIMIT`
- **Rate limiting**: отдельные лимиты на `register/login`, `password change` и `backup restore` с `429` и `Retry-After`

### Схема БД

| Таблицы | Назначение |
| ------- | ---------- |
| `users` | Пользователи |
| `categories` | Категории расходов (с sort_order, is_active) |
| `income_sources` | Источники дохода (с is_active) |
| `months` | Месяцы (year + month + user_id, unique) |
| `incomes`, `expenses` | Доходы и расходы (expenses с comment) |
| `budgets` | Лимиты на категорию/месяц |
| `savings_goals` | Цели накоплений (копилки) |
| `savings_transactions` | Транзакции копилок (с month_id) |
| `deleted_records` | Tombstones удалённых записей для sync |
| `idempotency_keys` | Хранилище обработанных `operationId` |
| `user_backups` | Резервные копии данных |

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
| **Dashboard** | Сводка месяца, 6 карточек (доходы/расходы/накопления/доступно/активы), тренд 6 мес., структура расходов (PieChart) |
| **MonthView** | Ядро учёта: доходы/расходы, inline-редактирование, drag-and-drop сортировка, лимиты бюджета |
| **Categories** | CRUD категорий расходов и источников дохода |
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
| Ktor Client | 3.0.1 | HTTP-клиент |
| Koin | 3.5.6 | Dependency Injection |
| kotlinx-serialization | 1.7.1 | JSON сериализация |
| kotlinx-datetime | 0.6.0 | Дата/время |
| SQLDelight | 2.0.2 | Локальная БД |
| Multiplatform Settings | 1.1.1 | Хранение настроек |

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
| **DashboardScreen** | Сводка: доходы/расходы/накопления, графики |
| **MonthViewScreen** | Учёт за месяц: расходы по категориям, доходы, бюджеты |
| **CategoriesScreen** | Управление категориями и источниками дохода |
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

### Build Desktop Apps

Workflow: `.github/workflows/build-desktop.yml`

Запускается вручную (`workflow_dispatch`):

1. Перейти в **Actions** → **Build Desktop Apps**
2. Нажать **Run workflow** → выбрать ветку → **Run workflow**

Артефакты:

- **FinKeeper-Windows-EXE** — `.exe` установщик
- **FinKeeper-Windows-MSI** — `.msi` установщик
- **FinKeeper-macOS** — `.app` приложение

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

# FinKeeper — Правила для Kilo Code

## Общие правила проекта

- **Язык ответов**: русский (если пользователь явно не попросит иной).
- **Валюта**: ₽ (российский рубль), формат: `1 000 000 ₽` (неразрывные пробелы, без копеек).
- **Формат даты**: `DD.MM.YYYY`.
- **Запрещено самостоятельно делать `git commit` и `git push`** — только после явного одобрения пользователя.
- **Источник истины**: текущая реализация кода и build-конфигурация, а так же markdown-документация.

## Роли и скиллы

При определении подходящей роли и скиллов для задачи — используй каталог ролей и маршрутизацию из:

- **Роли**: `.agents/roles/Roles.md`
- **Скиллы**: `.agents/skills/`

Доступные роли: Architect, Orchestrator, Coder, Analyst, Designer, Layout Engineer, Reviewer, QA.

Проектные кастомные скиллы:
- `stitch-design` — генерация/редактирование экранов через Stitch
- `enhance-prompt` — усиление слабых UI-промптов
- `react-components` — конвертация Stitch-вывода в React/Vite компоненты
- `shadcn-ui` — реализация UI с помощью shadcn/ui

Приоритет:
1. Явно указанная пользователем роль/скилл.
2. Автоматическая маршрутизация из `Roles.md`.
3. Проектные конвенции из этого файла.

---

## Проекты и их стеки

### `client/` — Web-фронтенд

**Стек**: React 19, TypeScript, Vite 7, Tailwind CSS 4, React Router 7, Recharts 3, Axios, @dnd-kit, Lucide React, date-fns

**Правила**:
- Стилизация только через Tailwind CSS 4 (утилиты + `@theme` API), без отдельных CSS-файлов кроме `index.css`.
- Компоненты — функциональные React, с хуками.
- Состояние: `AuthContext` для авторизации, локальный state / `useState` для экранов.
- API-слой: `src/api/` (Axios-инстанс с Bearer token).
- Роутинг: `/login` — публичный, `/` (Dashboard), `/month`, `/categories`, `/savings`, `/settings` — защищённые (`RequireAuth`).
- Авторизация: JWT access token в `sessionStorage` (обычные сессии) или `localStorage` (remember_me). Axios auto-refresh только для запомненных сессий.
- Сборка: `npm run build` → `tsc -b && vite build`.
- Линт: `npm run lint`.
- Тесты: `npm run test` (Vitest).

### `server/` — Бэкенд

**Стек**: Express 5, better-sqlite3, JWT (jsonwebtoken), bcryptjs, ws (WebSocket), helmet, cors, nodemailer

**Правила**:
- Все API-роуты под `/api/*` защищены middleware `authenticateToken`.
- База: SQLite с foreign keys (см. `db_setup.js` для схемы).
- Soft delete: категории и источники дохода используют `is_active=0` вместо DELETE.
- Автобэкапы: максимум 5 JSON-снапшотов на пользователя, создаются при логине и ежечасных проверках токена.
- WebSocket: real-time обновления между подключёнными клиентами.
- CORS включён: `app.use(cors())`.
- Social OAuth: Google/Яндекс через backend-mediated flow (`auth_identities`, `auth_login_exchange_codes`).
- Запуск: `node index.js` (порт 3002).
- Инициализация БД: `node db_setup.js`.
- Тип модулей: CommonJS (`require`/`module.exports`).

### `mobile_app_client/` — KMP-клиент (Android / iOS / Desktop)

**Стек**: Kotlin 2.4.20, Compose Multiplatform 1.12.1, Ktor Client 3.6.0, Koin 4.2.2, SQLDelight 2.4.0, kotlinx-serialization 1.11.0, kotlinx-coroutines 1.11.0, kotlinx-datetime 0.8.0, Multiplatform Settings 1.3.0

**Правила**:
- Архитектура: MVVM (`ViewModel` + `StateFlow`).
- DI: Koin — общий `appModule` + платформенные модули (`androidAppModule`, `iosAppModule`, `desktopAppModule`).
- Навигация: state-driven (`AppNavigation.kt`), не navigation graph.
- Сеть: Ktor Client с авто-обновлением access token при 401/403 (Mutex-защищённый single-flight refresh).
- Auth: JWT + refresh token, `TokenStorage` с `authEvents: SharedFlow`, платформенный secure storage.
- Social auth: native browser launch + polling/exchange flow (`SocialAuthLauncher` expect/actual).
- Sync: offline-first, `SyncManager` + `SyncService`, sync queue, timestamp-aware merge, tombstones, `operationId`.
- WebSocket: real-time обновления от сервера.
- Локальная БД: SQLDelight, платформенные драйверы (Android SQLite Driver, iOS Native Driver, Desktop JDBC).
- Desktop: база и логи в `~/.finkeeper/`, schema migration через `ensureSchemaUpToDate()`.
- UI: Material 3, Compose, все строки на русском.
- Сериализация: `@Serializable` + `@SerialName` для snake_case API-полей.
- ViewModel: `androidx.lifecycle.ViewModel`, `viewModelScope`, состояние через `StateFlow<T>`.
- Сборка Android: `./gradlew :composeApp:assembleDebug` / `assembleRelease`.
- Сборка iOS: `./gradlew composeApp:compileKotlinIosSimulatorArm64`.
- Сборка Desktop: `./gradlew :composeApp:run` / `packageDistributionForCurrentOS`.
- Тесты: `./gradlew composeApp:testDebugUnitTest`.
- JVM Target: Android — 11, Desktop — 17.
- Min SDK Android: 24, Target/Compile SDK: 35.
- iOS: ARM64 + Simulator ARM64, static framework.
- BuildConfig: генерируется из `build.gradle`, содержит `APP_VERSION`, `DEFAULT_SERVER_URL`, `SHOW_SERVER_SETTINGS`.

### `lending/` — Лендинг

**Стек**: React 19, TypeScript, Vite 7, Tailwind CSS 4, Framer Motion, Lucide React, clsx, tailwind-merge

**Правила**:
- Стилизация: Tailwind CSS 4 (`@theme` API внутри `index.css`), без отдельного `tailwind.config.ts`.
- Анимации: Framer Motion (scroll, hover, stagger).
- Шрифты: Unbounded (заголовки), Inter (UI), JetBrains Mono (суммы) — подключаются через `@fontsource`.
- Иконки: Lucide React.
- Glassmorphism-стиль, премиальная финансовая эстетика.
- Сборка: `npm run build` → `tsc -b && vite build`.
- Dev: `npm run dev` (порт 5173).
- Линт: `npm run lint`.
- Лендинг ссылается на `https://app.finkeeper24.ru`.

---

## Ключевые конвенции

### Код-стайл

- **Kotlin**: 4 пробела, длина строки 120, `PascalCase` для классов, `camelCase` для функций/переменных, `SCREAMING_SNAKE_CASE` для констант.
- **TypeScript/React**: следовать ESLint-конфигурации проекта, функциональные компоненты, хуки.
- **Комментарии**: не удалять и не добавлять комментарии без явной просьбы пользователя.

### Безопасность

- Не хардкодить API-ключи, секреты, пароли. Использовать env-переменные.
- JWT-секрет на сервере — через env, не захардкоженный (текущий захардкоженный — технический долг).
- Токены на клиенте — через secure storage, не в открытом виде в обычных preferences.

### Тестирование

- Web: Vitest (`npm run test` в `client/`).
- KMP: `./gradlew composeApp:testDebugUnitTest`.
- Перед коммитом (когда одобрен пользователем): запускать тесты соответствующего проекта.

### Типичные команды

```bash
# Web dev (backend :3002 + frontend :5174)
npm start

# KMP Android debug
cd mobile_app_client && ./gradlew :composeApp:assembleDebug

# KMP Desktop run
cd mobile_app_client && ./gradlew :composeApp:run

# Lending dev
cd lending && npm run dev

# Server
cd server && node index.js
```

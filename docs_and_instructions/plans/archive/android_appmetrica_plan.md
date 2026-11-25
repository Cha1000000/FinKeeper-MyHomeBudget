# План: подключение Yandex AppMetrica (Android) и слой аналитики KMP

## Цель

Подключить Yandex AppMetrica к Android-части KMP-клиента (`mobile_app_client/`) для отслеживания:
- установок, активных пользователей (DAU/MAU) и сессий;
- крашей и ANR (с корректной деобфускацией R8 в release-сборках);
- продуктовой аналитики использования приложения и нефатальных ошибок (потеря сети/сервера, сбои синхронизации и локальной БД).

## Архитектурное решение (KMP + Android)

- Официального AppMetrica SDK для JVM Desktop у Яндекса нет, поэтому сам SDK (`io.appmetrica.analytics:analytics:8.6.0`) подключается **только в `androidMain`**.
- При этом **интерфейс аналитики и все вызовы событий засеваются в `commonMain`** — единый код для Android, iOS и Desktop:
  - `commonMain`: интерфейс `AnalyticsTracker`, константы событий `AnalyticsEvents`, реализация-заглушка `NoOpAnalyticsTracker` (для Desktop/iOS и для debug при отключённой отправке).
  - `androidMain`: `AppMetricaAnalyticsTracker` поверх `io.appmetrica.analytics.AppMetrica` + фильтрация benign-исключений (`CancellationException`, сетевые таймауты/обрывы).
  - Если в будущем понадобится аналитика на Desktop/iOS — достаточно подменить реализацию `AnalyticsTracker` в соответствующем DI-модуле без правки экранов и ViewModel.

## Этапы реализации

### Этап 1. Консоль AppMetrica (в Edge)
1. Настроить постоянный доступ к открытому Edge через `edge://inspect/#remote-debugging` + `--cdp-endpoint=msedge` в `~/.gemini/config/mcp_config.json`.
2. В консоли AppMetrica создать приложение **FinKeeper24** (категория «Финансы», часовой пояс), убедиться, что оно появилось в списке рядом с HabitTracker.
3. Получить **API key (для SDK)** и **Post API key** (для загрузки R8 mapping-файлов).
4. Сохранить ключи локально в `mobile_app_client/local.properties` (gitignored) и описать переменные в `mobile_app_client/local.properties.example` / `BUILD.md` (без коммита реальных ключей в git).

### Этап 2. Зависимости, BuildConfig и R8-деобфускация
1. `mobile_app_client/gradle/libs.versions.toml`:
   - `appmetrica = "8.6.0"` → `appmetrica-analytics = { group = "io.appmetrica.analytics", name = "analytics", version.ref = "appmetrica" }`.
   - `appmetrica-gradle = "2.1.0"` → плагин `io.appmetrica.analytics` (поддерживает AGP 9.4+ и Gradle 9.6+).
2. `mobile_app_client/build.gradle.kts` и `composeApp/build.gradle`:
   - Добавить `implementation libs.appmetrica.analytics` в `androidMain`.
   - Чтение `APPMETRICA_API_KEY` (и `APPMETRICA_POST_API_KEY`) из `local.properties` с fallback на переменные окружения.
   - Прокинуть `APPMETRICA_API_KEY` и `APPMETRICA_ENABLED` в генерируемый `BuildConfig.kt` (или Android `buildConfigField`):
     - На время проверки на эмуляторе: `APPMETRICA_ENABLED = true` и в debug;
     - После подтверждения доставки событий в консоль AppMetrica: по умолчанию `false` для debug (с возможностью включить флагом `-PAPPMETRICA_DEBUG=true`), `true` для release.
   - Настроить `appmetrica { ... }` для автозагрузки R8 `mapping.txt` в release-сборках (при наличии `APPMETRICA_POST_API_KEY`; в debug отключено).
3. `mobile_app_client/composeApp/proguard-rules.pro`:
   - Добавить `-keepattributes SourceFile,LineNumberTable` (сейчас отсутствуют — нужны для читаемых стектрейсов и номеров строк при деобфускации).

### Этап 3. Инициализация SDK и слой `AnalyticsTracker`
1. `commonMain/.../data/analytics/`:
   - `AnalyticsEvents.kt` — единый словарь имён событий и ключей параметров.
   - `AnalyticsTracker.kt` — интерфейс:
     - `logScreen(screenName: String)`
     - `logEvent(name: String, params: Map<String, Any> = emptyMap())`
     - `logError(throwable: Throwable, context: String)`
     - `setUserId(userId: Long?)`
   - `NoOpAnalyticsTracker.kt` — пустая реализация для iOS/Desktop (и unit-тестов).
2. `androidMain/.../data/analytics/AppMetricaAnalyticsTracker.kt`:
   - Безопасная обёртка (`runCatching`) над `AppMetrica.reportEvent`, `AppMetrica.reportError`, `AppMetrica.setUserProfileID`.
   - Фильтрация benign-ошибок (`CancellationException`, `UnknownHostException`, `SocketTimeoutException`, `ConnectException`, `IOException`) — чтобы в раздел Errors не сыпался шум от обычного офлайна.
3. `androidMain/.../FinKeeperApp.kt`:
   - Инициализация `AppMetrica.activate(this, config)` в `onCreate()` (до старта сервисов) при `APPMETRICA_ENABLED && apiKey.isNotBlank()`:
     - `.withCrashReporting(true)`
     - `.withAnrMonitoring(true)`
     - `.withSessionTimeout(300)`
     - `.withAppVersion(BuildConfig.APP_VERSION)`
     - `.withLogs()` только в debug.
4. DI (`AndroidAppModule.kt`, `DesktopAppModule.kt`, `IosAppModule.kt`, `AppModule.kt`):
   - Зарегистрировать `AnalyticsTracker` в платформенных модулях (`AppMetricaAnalyticsTracker` на Android, `NoOpAnalyticsTracker` на Desktop/iOS).

### Этап 4. События, которые засеваем в `commonMain`

**Приватность:** суммы, комментарии, названия категорий/копилок, email и пароли в аналитику **никогда не отправляются** — только факты действий и безопасные метаданные.

| Группа | События | Где вызывается |
| --- | --- | --- |
| **Навигация** | `screen_view` (`screen`: `login`, `dashboard`, `month_view`, `categories`, `savings`, `settings`) | `App.kt`, `AppNavigation.kt` |
| **Авторизация** | `auth_login_success` / `auth_login_failed`, `auth_register_success` / `auth_register_failed`, `auth_social_start` / `auth_social_success` / `auth_social_failed` (`provider`), `auth_logout`, `auth_password_recovery_requested` / `confirmed`, `setUserId(user.id)` | `AuthViewModel.kt` |
| **Сеть и синк** | `network_offline`, `network_online`, `server_unreachable`, `server_reachable`, `sync_failed` (без шума: только переходы состояния и не-сетевые сбои синка) | `SyncService.kt`, `SyncManager.kt` |
| **Операции за месяц** | `expense_added`, `expense_updated`, `expense_deleted`, `income_added`, `income_updated`, `income_deleted`, `budget_set`, `planned_confirmed`, `planned_skipped`, `planned_overridden`, `planned_reset` | `MonthViewModel.kt` |
| **Категории** | `category_added` (`is_fixed`), `category_deleted`, `income_source_added` (`is_fixed`), `income_source_deleted` | `CategoriesViewModel.kt` |
| **Копилки** | `savings_goal_created`, `savings_goal_deleted`, `savings_tx_added` (`type`: `deposit`/`withdraw`) | `SavingsViewModel.kt` |
| **Настройки** | `theme_changed` (`theme`), `backup_created`, `backup_restored`, `sync_manual_retry`, `email_verification_requested` | `SettingsViewModel.kt`, `AppNavigation.kt` |
| **Нефатальные ошибки** | `logError(e, context)` при неожиданных сбоях синка/БД/парсинга | `SyncManager.kt`, `DatabaseProvider.kt`, ViewModels |

### Этап 5. Тестирование и верификация
1. Unit-тесты в `commonTest` (фильтрация benign-ошибок, вызовы `AnalyticsTracker` через fake-реализацию).
2. Прогон всех существующих тестов (`./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest`).
3. Проверка сборки Android debug + desktop (`compileKotlinDesktop`) + iOS (`compileKotlinIosSimulatorArm64` при доступности, иначе commonMain + android + desktop).
4. **Проверка Debug на эмуляторе (`Pixel_10` / `Pixel_9_Pro`):**
   - Запуск против локального сервера (`DEFAULT_SERVER_URL=http://10.0.2.2:3002`, временная БД).
   - Прогон базовых действий (вход, переключение экранов, добавление расхода, перевод в офлайн/онлайн).
   - Проверка Logcat (`AppMetrica`) и проверка в консоли AppMetrica в Edge, что события и сессия реально дошли.
5. **Отключение отправки в debug по умолчанию** (чтобы не засорять боевую статистику).
6. **Проверка Release на физическом смартфоне Володи:**
   - Сборка подписанного `assembleRelease` (с R8 и загрузкой `mapping.txt`).
   - Установка на смартфон, прогон реальной сессии и проверка в консоли AppMetrica.

## Ветка и Git
- Разработка ведётся в отдельной ветке `feature/android-appmetrica`.
- Коммит и пуш — только после твоей проверки и явного «ок».

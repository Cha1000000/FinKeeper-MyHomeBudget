# FinKeeper24 — Mobile / Desktop Client

Kotlin Multiplatform клиент FinKeeper24 для Android, iOS и desktop-платформ.

## Назначение

Клиент предоставляет тот же основной функционал, что и web-версия:

- учёт доходов и расходов по месяцам
- категории и источники дохода
- бюджеты по категориям
- копилки и транзакции накоплений
- dashboard и аналитика
- профиль, пароль, backup/restore

## Актуальный стек

### Базовые версии

Источник истины — `gradle/libs.versions.toml` (версия приложения — `app_version` в `composeApp/build.gradle`).

| Компонент | Версия |
| --- | --- |
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.12.1 |
| Ktor Client | 3.6.0 |
| Kotlinx Serialization | 1.11.0 |
| Kotlinx Coroutines | 1.11.0 |
| Kotlinx Datetime | 0.8.0 |
| Multiplatform Settings | 1.3.0 |
| Koin | 4.2.2 |
| SQLDelight | 2.4.0 |
| AGP / Gradle | 9.4.1 / 9.6.0 |
| App version | 2.2.4 |

### Платформы

- Android
- iOS
- macOS
- Windows
- Linux

## Архитектура

Клиент построен на:

- MVVM
- Koin для DI
- Ktor для HTTP и WebSocket
- SQLDelight для локальной БД
- offline-first sync с локальной очередью изменений
- платформенном secure storage для токенов
- авто-обновлении access token через refresh flow

Основные слои:

- `data/model` — API и shared models
- `data/remote` — `ApiClient`, `TokenStorage`, social auth launchers
- `data/local` — база, DAO, sync queue
- `data/repository` — бизнес-логика и sync
- `ui/viewmodel` — экранные состояния и действия
- `ui/navigation` — state-driven навигационная оболочка приложения
- `di/AppModule.kt` — wiring зависимостей

## Синхронизация

Ключевые компоненты sync:

- `SyncManager`
- `SyncService`
- `SyncQueueDao`
- `SyncStateStorage`

Что умеет текущая sync-реализация:

- локальная запись до отправки на сервер
- очередь `INSERT` / `UPDATE` / `DELETE`
- автоматический upload после локальных изменений
- полный sync в порядке `download -> upload`
- timestamp-aware merge через server `created_at` / `updated_at`
- tombstones через `deleted_records`
- idempotent write requests через `operationId`
- post-login sync trigger и online-triggered sync
- диагностика sync-ошибок и состояния последней успешной синхронизации

Подробное описание вынесено в:

- `../docs_and_instructions/current_sync_implementation.md`

## Сетевое поведение

- JWT добавляется в `Authorization: Bearer ...`
- URL сервера хранится в `TokenStorage`
- `ApiClient` автоматически пытается обновить access token по `refreshToken` при `401/403` на защищённых endpoint'ах
- social auth для KMP идёт через native browser launch + polling/exchange flow
- для Android-эмулятора по умолчанию используется `http://10.0.2.2:3002`
- для iOS/реальных устройств нужен реальный адрес сервера

## Платформенные заметки

### Android

- `minSdk = 24`
- `compileSdk = 37`
- `targetSdk = 35`
- JVM target для Android — `11`

### Desktop

- desktop target использует JVM `17`
- HTTP engine: Ktor CIO
- SQLDelight driver: sqlite-driver
- secure token storage через OS credential storage (с fallback)
- база и диагностические логи хранятся в `~/.finkeeper/`

### iOS

- поддерживаются `iosArm64` и `iosSimulatorArm64`
- framework собирается как static framework

## Основные команды

### Команды Android

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:assembleRelease
```

### Команды iOS

```bash
./gradlew composeApp:compileKotlinIosSimulatorArm64
```

Дальше сборка и запуск через Xcode (`iosApp/iosApp.xcodeproj`).

### Команды Desktop

```bash
./gradlew :composeApp:run
./gradlew :composeApp:packageDistributionForCurrentOS
```

### Тесты

```bash
./gradlew composeApp:testDebugUnitTest
```

## Релиз и публикация

Сборка устанавливаемых пакетов и публикация в AUR автоматизированы через GitHub Actions
(workflow'ы — в `../.github/workflows/`). Версия пакетов берётся из `app_version`
в `composeApp/build.gradle`.

### Сборка desktop-пакетов

| Платформа | Workflow | Артефакты |
| --- | --- | --- |
| Linux | **Build Linux Desktop App** (`build-linux.yml`) | `.deb`, `.rpm` |
| Windows | **Build Windows Desktop App** (`build-windows.yml`) | `.msi` |
| macOS | **Build macOS Desktop App** (`build-mac.yml`) | `.app` |

Запуск вручную: Actions → выбрать workflow → **Run workflow**. Артефакты хранятся 3 дня.
Локально пакет для текущей ОС: `./gradlew :composeApp:packageDistributionForCurrentOS`
(для Linux отдельно — `packageDeb` / `packageRpm`).

### Публикация AUR-пакета `finkeeper24-bin`

AUR хранит только рецепт (`PKGBUILD`) — он скачивает `.deb` с `finkeeper24.ru` и проверяет
его по `sha256`. Поэтому публиковать в AUR нужно **после** того, как новый `.deb` залит на сайт
(контрольная сумма берётся с живого файла, иначе сборка из AUR у пользователей упадёт).

- канонический рецепт: `../packaging/aur/PKGBUILD`
- workflow публикации: **Publish AUR package** (`../.github/workflows/publish-aur.yml`)
- требуется секрет репозитория `AUR_SSH_PRIVATE_KEY` (приватный SSH-ключ AUR-аккаунта)

**Порядок релиза:**

1. Поднять `app_version` в `composeApp/build.gradle`.
2. Собрать `.deb` — workflow **Build Linux Desktop App**.
3. **Вручную** залить `finkeeper24_<версия>_amd64.deb` на `finkeeper24.ru/downloads/linux/`.
4. Запустить публикацию в AUR:

   ```bash
   gh workflow run publish-aur.yml
   ```

   Версия подхватится из `build.gradle` (можно переопределить полем `version`).
   Workflow проверит наличие `.deb` на сайте, посчитает `sha256`, обновит `PKGBUILD`,
   опубликует в AUR и закоммитит `PKGBUILD` обратно в `main`.

## Связанные документы

- команды сборки: `BUILD.md`
- корневой обзор репозитория: `../README.md`
- актуальная документация по sync: `../docs_and_instructions/current_sync_implementation.md`
- план и обоснование AUR-публикации: `../docs_and_instructions/aur-ci-publish-plan.md`
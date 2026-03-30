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

| Компонент | Версия |
| --- | --- |
| Kotlin | 2.2.10 |
| Compose Multiplatform | 1.6.10 |
| Ktor Client | 3.0.1 |
| Kotlinx Serialization | 1.7.1 |
| Kotlinx Coroutines | 1.8.1 |
| Kotlinx Datetime | 0.6.0 |
| Multiplatform Settings | 1.1.1 |
| Koin | 3.5.6 |
| SQLDelight | 2.0.2 |
| App version | 1.5.0 |

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

Основные слои:

- `data/model` — API и shared models
- `data/remote` — `ApiClient`, `TokenStorage`
- `data/local` — база и DAO
- `data/repository` — бизнес-логика и sync
- `ui/viewmodel` — экранные состояния и действия
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

Подробное описание вынесено в:

- `../docs_and_instructions/current_sync_implementation.md`

## Сетевое поведение

- JWT добавляется в `Authorization: Bearer ...`
- URL сервера хранится в `TokenStorage`
- для Android-эмулятора по умолчанию используется `http://10.0.2.2:3002`
- для iOS/реальных устройств нужен реальный адрес сервера

## Платформенные заметки

### Android

- `minSdk = 24`
- `compileSdk = 35`
- `targetSdk = 35`
- JVM target для Android — `11`

### Desktop

- desktop target использует JVM `17`
- HTTP engine: Ktor CIO
- SQLDelight driver: sqlite-driver

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

## Связанные документы

- команды сборки: `BUILD.md`
- корневой обзор репозитория: `../README.md`
- актуальная документация по sync: `../docs_and_instructions/current_sync_implementation.md`
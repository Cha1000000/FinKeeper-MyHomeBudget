# FinKeeper24 Mobile & Desktop Client (KMP)

This directory contains the **FinKeeper24** multiplatform client, built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform** for **Android, iOS, and Desktop (macOS, Windows, Linux)**. It connects to the FinKeeper24 backend (Express + SQLite).

## Role Routing Rules

When handling tasks, apply role routing and skill mapping from:
`.agents/roles/Roles.md`

If the user explicitly asks for a role or names specific skills, that explicit instruction overrides automatic routing.

## Project Overview

FinKeeper24 is a personal finance management application that allows users to track incomes, expenses, budgets, planned recurring payments, and savings goals. The KMP client replicates the full functionality of the web client in an offline-first native experience across mobile and desktop.

### Tech Stack
- **Language:** Kotlin 2.4.20 (AGP 9.4.1 / Gradle 9.6.0)
- **UI Framework:** Compose Multiplatform 1.12.1 (Material 3)
- **Networking:** Ktor Client 3.6.0 (OkHttp on Android, Darwin on iOS, CIO on Desktop)
- **Dependency Injection:** Koin 4.2.2
- **Serialization:** kotlinx.serialization 1.11.0
- **Concurrency:** kotlinx.coroutines 1.11.0
- **Persistence:** Multiplatform Settings 1.3.0 + secure token storage per platform
- **Date/Time:** kotlinx-datetime 0.8.0
- **Database:** SQLDelight 2.4.0 (additive migrations in `DatabaseProvider`)
- **Analytics & Crashes (Android):** Yandex AppMetrica SDK 8.6.0 + AppMetrica Gradle Plugin 2.1.0 (automatic R8 `mapping.txt` upload in release)
- **In-App Updates:** RuStore AppUpdate SDK 10.5.1 (Android), `latest.json` checker (Desktop)

### Architecture
The project follows the **MVVM (Model-View-ViewModel)** pattern with **local-first** startup:
- **`data/model/`**: Shared `@Serializable` models.
- **`data/remote/`**: `ApiClient`, `TokenStorage`, platform `SecureTokenStorage` & `SocialAuthLauncher`.
- **`data/local/`**: SQLDelight database (`DatabaseProvider`), DAOs, sync queue (`SyncQueueDao`).
- **`data/repository/`**: Entity repositories, `SyncManager`, `SyncGate`, `SyncService`, `WebSocketService`.
- **`data/planned/`**: `PlannedCalculator` — offline calculation of recurring payments and monthly overrides.
- **`data/analytics/`**: Cross-platform `AnalyticsTracker` & `AnalyticsEvents` (`AppMetricaAnalyticsTracker` on Android, `NoOpAnalyticsTracker` on Desktop/iOS).
- **`data/network/`**: `NetworkMonitor` (expect/actual) and `ServerLinkState`.
- **`di/`**: Common `appModule` + platform modules (`androidAppModule`, `desktopAppModule`, `iosAppModule`).
- **`ui/`**: Compose screens, reusable components, themes, state-driven navigation (`AppNavigation`), and ViewModels.
- **`util/`**: Formatters, `AmountExpression`, retry helper, platform utilities.

## Building and Running

### Prerequisites
- JDK 17+ (required by Gradle 9.6 / AGP 9.4 and Desktop JVM target 17)
- Android Studio (Ladybug or newer)
- Xcode 15+ (for iOS)

### Build Commands
- **Android:**
  ```bash
  ./gradlew :composeApp:assembleDebug
  ./gradlew :composeApp:assembleRelease   # Signed APK + R8 + AppMetrica mapping upload
  ```
- **Desktop (macOS / Windows / Linux):**
  ```bash
  ./gradlew :composeApp:run                              # Run app (use FINKEEPER_USER_HOME=<tmp> for dev isolation)
  ./gradlew :composeApp:compileKotlinDesktop             # Compilation check
  ./gradlew :composeApp:packageDistributionForCurrentOS  # Native installer (.deb/.rpm/.dmg/.msi/.exe)
  ```
- **iOS (Simulator):**
  - Open `iosApp/iosApp.xcodeproj` in Xcode and run.
  - Or use Gradle for compilation check:
    ```bash
    ./gradlew :composeApp:compileKotlinIosSimulatorArm64
    ```

### Running Tests
Always target a local server (`DEFAULT_SERVER_URL=http://localhost:3002`), never production:
```bash
DEFAULT_SERVER_URL=http://localhost:3002 ./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest
```

## Development Conventions

### Coding Style
- **Kotlin 2.4.20:** Follows standard Kotlin Multiplatform conventions used in this repository.
- **Compose Multiplatform:** UI code is shared in `commonMain`. Platform-specific entry points are in `androidMain`, `desktopMain`, and `iosMain`.
- **Naming:** Standard Kotlin camelCase conventions. Data models use `@SerialName` for snake_case API mapping.

### API Integration & BuildConfig
- **Server URL:** Default `BuildConfig.DEFAULT_SERVER_URL` is `https://app.finkeeper24.ru` (override via `-PDEFAULT_SERVER_URL` or env, e.g., `http://10.0.2.2:3002` for Android emulator). Server URL switching on the Login screen is hidden in release builds (`SHOW_SERVER_SETTINGS = false`).
- **AppMetrica Keys:** Configured in gitignored `local.properties` (`APPMETRICA_API_KEY`, `APPMETRICA_POST_API_KEY`; see `local.properties.example`) or env. Analytics sending is disabled in debug by default (`APPMETRICA_ENABLED = false`); enable for debug testing via `-PAPPMETRICA_DEBUG=true`.
- **Auth:** JWT Bearer tokens, refresh tokens, auto-refresh interception, and platform secure token storage.
- **Social Auth:** Native-safe social login via platform browser launch + polling/exchange flow.
- **Error Handling:** `ApiClient` throws `ApiException` for non-success HTTP status codes.

### UI & UX
- **Themes:** Material 3 with 5 palettes (`Light`, `Cyberpunk`, `Dark`, `DarkNight`, `BlueOcean`) plus `system` auto mode.
- **Navigation:** 5-section navigation (`Dashboard`, `MonthView`, `Categories`, `Savings`, `Settings`) — bottom bar on mobile, adaptive layout on desktop.
- **Localization:** UI is in Russian (`1 000 000 ₽`, `DD.MM.YYYY`).

## Project Structure
- `composeApp/src/commonMain`: Shared logic, SQLDelight schema, and Compose UI.
- `composeApp/src/androidMain`: Android setup (`FinKeeperApp`, `MainActivity`, `AppMetricaAnalyticsTracker`, `RuStoreUpdateChecker`, `AndroidSecureTokenStorage`).
- `composeApp/src/desktopMain`: Desktop JVM setup (`Main.kt`, `DesktopUpdateChecker`, `DesktopSecureTokenStorage`).
- `composeApp/src/iosMain`: iOS setup (`MainViewController`, `IosSecureTokenStorage`).
- `composeApp/src/commonTest` & `composeApp/src/jvmSharedTest`: Unit tests and shared JVM SQLite DAO/schema tests.
- `iosApp/`: Native iOS wrapper project (SwiftUI).
- `gradle/libs.versions.toml`: Centralized dependency management.
- `local.properties.example`: Template for local SDK and AppMetrica keys.

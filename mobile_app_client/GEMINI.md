# FinKeeper24 Mobile Client (KMP)

This directory contains the **FinKeeper24** mobile application, built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It serves as a mobile client for the FinKeeper24 backend (Express + SQLite).

## Role Routing Rules

When handling tasks, apply role routing and skill mapping from:
`.agents/roles/Roles.md`

If the user explicitly asks for a role or names specific skills, that explicit instruction overrides automatic routing.

## Project Overview

FinKeeper24 is a personal finance management application that allows users to track incomes, expenses, budgets, and savings goals. The mobile client aims to replicate the full functionality of the web client in a native mobile environment.

### Tech Stack
- **Language:** Kotlin 2.4.20
- **UI Framework:** Compose Multiplatform 1.12.1 (Material 3)
- **Networking:** Ktor Client 3.6.0 (OkHttp on Android, Darwin on iOS, CIO on Desktop)
- **Dependency Injection:** Koin 4.2.2
- **Serialization:** kotlinx.serialization 1.11.0
- **Concurrency:** kotlinx.coroutines 1.11.0
- **Persistence:** Multiplatform Settings 1.3.0 + secure token storage per platform
- **Date/Time:** kotlinx-datetime 0.8.0
- **Database:** SQLDelight 2.4.0

### Architecture
The project follows the **MVVM (Model-View-ViewModel)** pattern:
- **`data/`**: Models, API client, token storage, SQLDelight database, sync queue, repositories.
- **`di/`**: Common and platform-specific Koin modules.
- **`ui/`**: Compose screens, components, themes, navigation shell, and ViewModels.
- **`util/`**: Formatters and utility functions.
- **Sync**: Offline-first synchronization via `SyncManager` and `SyncService`.
- **Realtime**: WebSocket updates from server.

## Building and Running

### Prerequisites
- JDK 11+
- Android Studio (Ladybug or newer)
- Xcode 15+ (for iOS)

### Build Commands
- **Android:**
  ```bash
  ./gradlew :composeApp:assembleDebug
  ```
- **iOS (Simulator):**
  - Open `iosApp/iosApp.xcodeproj` in Xcode and run.
  - Or use Gradle for compilation check:
    ```bash
    ./gradlew composeApp:compileKotlinIosSimulatorArm64
    ```

### Running Tests
```bash
./gradlew composeApp:testDebugUnitTest
```

## Development Conventions

### Coding Style
- **Kotlin 2.4.20:** Follows standard Kotlin Multiplatform conventions used in this repository.
- **Compose Multiplatform:** UI code is shared in `commonMain`. Platform-specific entry points are in `androidMain` and `iosMain`.
- **Naming:** Follows standard Kotlin camelCase conventions. Data models use `@SerialName` for snake_case API mapping.

### API Integration
- **Server URL:** Configurable via the Login screen. Default for Android emulator is `http://10.0.2.2:3002`.
- **Auth:** Uses JWT Bearer tokens, refresh tokens, auto-refresh interception, and secure token storage per platform.
- **Social Auth:** Native-safe social login is supported via platform browser launch + polling/exchange flow.
- **Error Handling:** `ApiClient` throws `ApiException` for non-success HTTP status codes.

### UI & UX
- **Theme:** Material 3 with a custom "Emerald" palette (Light/Dark support).
- **Navigation:** 5-tab bottom navigation (Dashboard, Month View, Categories, Savings, Settings).
- **Localization:** UI is currently in Russian.

## Project Structure
- `composeApp/src/commonMain`: Shared logic and UI.
- `composeApp/src/androidMain`: Android-specific setup (Manifest, MainActivity).
- `composeApp/src/iosMain`: iOS-specific setup (MainViewController).
- `iosApp/`: Native iOS wrapper project (SwiftUI).
- `gradle/libs.versions.toml`: Centralized dependency management.

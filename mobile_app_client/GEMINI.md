# FinKeeper Mobile Client (KMP)

This directory contains the **FinKeeper** mobile application, built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It serves as a mobile client for the FinKeeper backend (Express + SQLite).

## Project Overview

FinKeeper is a personal finance management application that allows users to track incomes, expenses, budgets, and savings goals. The mobile client aims to replicate the full functionality of the web client in a native mobile environment.

### Tech Stack
- **Language:** Kotlin 2.3.0
- **UI Framework:** Compose Multiplatform 1.10.0 (Material 3)
- **Networking:** Ktor Client 3.1.3 (OkHttp on Android, Darwin on iOS)
- **Dependency Injection:** Koin 4.1.0-Beta5
- **Serialization:** kotlinx.serialization 1.8.1
- **Concurrency:** kotlinx.coroutines 1.10.2
- **Persistence:** Multiplatform Settings 1.3.0 (for JWT token and server URL)
- **Date/Time:** kotlinx-datetime 0.7.1
- **Navigation:** Navigation Compose 2.9.0

### Architecture
The project follows the **MVVM (Model-View-ViewModel)** pattern:
- **`data/`**: Models, API client, and token storage.
- **`di/`**: Koin modules for dependency injection.
- **`ui/`**: Compose screens, components, themes, and ViewModels.
- **`util/`**: Formatters and utility functions.

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
- **Kotlin 2.3.0+:** Adheres to modern Kotlin features. Note that `Instant` and `Clock` are now part of the standard library (`kotlin.time`).
- **Compose Multiplatform:** UI code is shared in `commonMain`. Platform-specific entry points are in `androidMain` and `iosMain`.
- **Naming:** Follows standard Kotlin camelCase conventions. Data models use `@SerialName` for snake_case API mapping.

### API Integration
- **Server URL:** Configurable via the Login screen. Default for Android emulator is `http://10.0.2.2:3002`.
- **Auth:** Uses JWT Bearer tokens stored in `TokenStorage`.
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

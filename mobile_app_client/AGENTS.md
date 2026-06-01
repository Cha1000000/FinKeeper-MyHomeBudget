# AGENTS.md - FinKeeper24 Mobile Client (KMP)

Instructions for AI agents working in the FinKeeper24 Kotlin Multiplatform codebase.

## Role Routing

For automatic role selection (Architect/Orchestrator/Coder/Analyst/Designer/Layout/Reviewer/QA) and matching skill selection, use:
`.agents/roles/Roles.md`

Priority order:
1. Explicit user role/skills in prompt.
2. Role auto-routing rules from `Roles.md`.
3. Mobile/KMP conventions from this `AGENTS.md`.

## Build/Test/Lint Commands

### Build
```bash
# Android Debug APK
./gradlew :composeApp:assembleDebug

# Android Release APK
./gradlew :composeApp:assembleRelease

# iOS compilation check (Simulator ARM64)
./gradlew composeApp:compileKotlinIosSimulatorArm64

# Clean build
./gradlew clean
```

### Testing
```bash
# Run all common tests
./gradlew composeApp:testDebugUnitTest

# Run specific test class
./gradlew composeApp:testDebugUnitTest --tests "ru.homebudget.finkeeper.util.FormattersTest"

# Run single test method
./gradlew composeApp:testDebugUnitTest --tests "ru.homebudget.finkeeper.util.FormattersTest.formatCurrency_positiveInteger"

# Run tests with verbose output
./gradlew composeApp:testDebugUnitTest --info
```

### Lint/Format
```bash
# Kotlin code style check (if ktlint configured)
./gradlew ktlintCheck

# Auto-format Kotlin code
./gradlew ktlintFormat
```

## Code Style Guidelines

### Kotlin Style
- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters max
- **Imports**: Use wildcard imports for same-package classes; organize by category
- **Naming**:
  - Classes/Objects: `PascalCase`
  - Functions/Variables: `camelCase`
  - Constants: `SCREAMING_SNAKE_CASE`
  - Compose functions: `PascalCase` starting with uppercase

### Imports
```kotlin
// Standard order:
// 1. Kotlin stdlib
// 2. AndroidX / JetBrains libraries
// 3. Third-party libraries (Ktor, Koin, etc.)
// 4. Project internal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.ViewModel
import io.ktor.client.*
import ru.homebudget.finkeeper.data.model.*
```

### Serialization & API Models
- Use `@Serializable` for all data classes exposed to API
- Use `@SerialName` for snake_case API field mapping:
```kotlin
@Serializable
data class Category(
    val id: Int,
    @SerialName("user_id") val userId: Int? = null,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0
)
```

### Compose UI
- Use Material3 components (`MaterialTheme`, `Scaffold`, etc.)
- Prefix stateless composables describing what they display
- Keep business logic in ViewModels, not composables
- Use `rememberSaveable` for UI state that survives config changes

### ViewModels
- Extend `androidx.lifecycle.ViewModel`
- Use `viewModelScope` for coroutines
- Expose state via `StateFlow<T>` (never expose `MutableStateFlow`)
- Handle errors by setting `error` field in state, not throwing in UI layer

### Error Handling
```kotlin
try {
    val result = apiClient.fetchData()
    _state.value = _state.value.copy(data = result, error = null)
} catch (e: ApiException) {
    _state.value = _state.value.copy(error = e.message)
} catch (e: Exception) {
    _state.value = _state.value.copy(error = "Неизвестная ошибка")
}
```

### Coroutines & Concurrency
- Use `suspend` functions for async operations
- Use `withRetry()` utility for network calls
- Always specify dispatcher when doing blocking work (though usually not needed for Ktor)

### Testing
- Use `kotlin.test` framework
- Test function names: descriptive snake_case
- Group related tests with section comments:
```kotlin
// ── formatCurrency ──
@Test
fun formatCurrency_positiveInteger() { ... }
```

## Project Structure

```
composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/
├── data/
│   ├── model/           # @Serializable data classes
│   ├── remote/          # ApiClient, TokenStorage
│   ├── local/           # SQLDelight database, DAOs, sync queue
│   └── repository/      # Repository pattern implementations
├── di/
│   └── AppModule.kt     # Koin module definitions
├── ui/
│   ├── components/      # Reusable Compose components
│   ├── screens/         # Screen-level composables
│   ├── theme/           # Colors, Theme, Typography
│   ├── navigation/      # State-driven app navigation
│   └── viewmodel/       # ViewModels (one per screen)
└── util/
    └── Formatters.kt    # Currency, date formatting
```

## Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.2.10 | Language |
| Compose Multiplatform | 1.6.10 | UI Framework |
| Ktor Client | 3.0.3 | HTTP Client |
| Koin | 3.5.6 | Dependency Injection |
| kotlinx-serialization | 1.7.1 | JSON Serialization |
| kotlinx-datetime | 0.6.1 | Date/Time handling |
| Multiplatform Settings | 1.2.0 | Settings and server URL storage |
| SQLDelight | 2.0.2 | Local database and sync queue |

## Platform Notes

### Android
- Min SDK: 24 (Android 7.0)
- Target/Compile SDK: 35
- JVM Target: 11
- Default server URL for emulator: `http://10.0.2.2:3002`

### iOS
- Requires Xcode 15+
- Supports iOS ARM64 and Simulator ARM64
- Real device requires server IP (not localhost)

## Before Committing

1. Run tests: `./gradlew composeApp:testDebugUnitTest`
2. Verify compilation for both platforms
3. Ensure no hardcoded URLs in production code (use TokenStorage)
4. Check for memory leaks in ViewModels (cancel coroutines properly)
5. Add tests for new utility functions

## Localization

UI strings are currently in **Russian**. All user-facing text should be in Russian.
Currency format: `1 000 000 ₽` (non-breaking spaces, no decimals)
Date format: `DD.MM.YYYY`

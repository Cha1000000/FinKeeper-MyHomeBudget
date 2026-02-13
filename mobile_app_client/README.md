# FinKeeper — Мобильный клиент

Мобильное приложение для личной бухгалтерии **FinKeeper**, реализованное на **Kotlin Multiplatform** (KMP) с общим UI на **Compose Multiplatform**. Является мобильным клиентом к существующему бэкенду (Express + SQLite), полностью повторяющим функциональность веб-клиента (React).

## Назначение

Приложение позволяет:

- Вести учёт **доходов** и **расходов** по месяцам
- Управлять **категориями расходов** и **источниками дохода**
- Устанавливать **лимиты бюджета** по категориям
- Вести **накопительные цели** (копилки) с пополнением и снятием
- Просматривать **аналитику**: сводка за месяц, тренды за 6 месяцев, структура расходов
- Управлять **настройками** профиля (смена пароля, бэкапы)

Валюта — **RUB** (₽).

---

## Технологический стек

### Язык и платформа

| Компонент | Версия |
| --- | --- |
| **Kotlin** | 2.3.0 |
| **Compose Multiplatform** | 1.10.0 |
| **Android Gradle Plugin** | 8.11.2 |
| **Android compileSdk / targetSdk** | 36 |
| **Android minSdk** | 24 (Android 7.0) |
| **JVM Target** | 11 |

### Основные библиотеки

| Библиотека | Версия | Назначение |
| --- | --- | --- |
| **Ktor Client** | 3.1.3 | HTTP-клиент для API-запросов |
| **Kotlinx Serialization** | 1.8.1 | JSON сериализация/десериализация |
| **Kotlinx Coroutines** | 1.10.2 | Асинхронность, корутины |
| **Kotlinx Datetime** | 0.7.1 | Работа с датами и временем (KMP) |
| **Koin** | 4.1.0-Beta5 | Dependency Injection |
| **Navigation Compose** | 2.9.0 | Навигация (JetBrains Multiplatform) |
| **Multiplatform Settings** | 1.3.0 | Хранение настроек (SharedPreferences / NSUserDefaults) |
| **AndroidX Lifecycle** | 2.9.6 | ViewModel + Compose интеграция |

### Платформенные HTTP-движки

- **Android**: `ktor-client-okhttp` (OkHttp)
- **iOS**: `ktor-client-darwin` (URLSession)

### Тестирование

- `kotlin-test` — мультиплатформенный тестовый фреймворк
- `kotlinx-coroutines-test` — тестирование корутин
- `kotlinx-serialization-json` — тесты сериализации моделей

---

## Архитектура

### Общая структура

Проект следует архитектуре **MVVM** (Model-View-ViewModel) с разделением на слои:

```text
commonMain/kotlin/ru/homebudget/finkeeper/
├── App.kt                          # Точка входа Compose UI
├── data/
│   ├── model/Models.kt             # Data-классы (Serializable)
│   └── remote/
│       ├── ApiClient.kt            # HTTP-клиент (Ktor)
│       └── TokenStorage.kt         # Хранение JWT-токена
├── di/
│   └── AppModule.kt                # Koin DI модуль
├── ui/
│   ├── components/
│   │   └── CommonComponents.kt     # Переиспользуемые UI-компоненты
│   ├── navigation/
│   │   └── AppNavigation.kt        # Навигация с нижним меню
│   ├── screens/
│   │   ├── LoginScreen.kt          # Экран авторизации
│   │   ├── DashboardScreen.kt      # Главный экран (обзор)
│   │   ├── MonthViewScreen.kt      # Просмотр месяца
│   │   ├── CategoriesScreen.kt     # Управление категориями
│   │   ├── SavingsScreen.kt        # Копилки
│   │   └── SettingsScreen.kt       # Настройки
│   ├── theme/
│   │   ├── Color.kt                # Палитра цветов (light/dark)
│   │   └── Theme.kt                # Material3 тема
│   └── viewmodel/
│       ├── AuthViewModel.kt        # Авторизация
│       ├── DashboardViewModel.kt   # Данные обзора
│       ├── MonthViewModel.kt       # Данные месяца
│       ├── CategoriesViewModel.kt  # Категории и источники
│       ├── SavingsViewModel.kt     # Копилки
│       └── SettingsViewModel.kt    # Настройки профиля
└── util/
    └── Formatters.kt               # Форматирование валюты, дат, месяцев
```

### Платформенный код

```text
androidMain/
├── AndroidManifest.xml
└── kotlin/ru/homebudget/finkeeper/
    ├── FinKeeperApp.kt             # Application class (инициализация Koin)
    └── MainActivity.kt             # Activity с ComposeView

iosMain/
└── kotlin/ru/homebudget/finkeeper/
    └── MainViewController.kt       # initKoin() + ComposeUIViewController

iosApp/iosApp/
├── iOSApp.swift                    # SwiftUI entry point (вызов initKoin())
└── ContentView.swift               # SwiftUI обёртка для ComposeUIViewController
```

### Тесты

```text
commonTest/kotlin/ru/homebudget/finkeeper/
├── ComposeAppCommonTest.kt                     # Базовые проверки
├── data/
│   ├── model/ModelsSerializationTest.kt        # Сериализация всех моделей (34 теста)
│   └── remote/ApiExceptionTest.kt              # ApiException (5 тестов)
├── ui/viewmodel/ViewModelStateTest.kt          # State data-классы (24 теста)
└── util/FormattersTest.kt                      # Форматирование (17 тестов)
```

**Всего: 80 unit-тестов.**

---

## Слой данных

### Модели (`Models.kt`)

Все модели аннотированы `@Serializable` с маппингом `@SerialName` для snake_case полей API:

- **AuthData** / **User** — авторизация
- **Category** — категория расходов (`user_id`, `sort_order`, `is_active`)
- **IncomeSource** — источник дохода
- **Month** — месяц (year + month + user_id)
- **Income** / **Expense** — записи доходов/расходов
- **Budget** — лимит бюджета на категорию/месяц
- **SavingsGoal** / **SavingsTransaction** — копилки и транзакции
- **MonthSummary** / **TrendItem** — аналитика
- **Request-классы** — тела запросов (LoginRequest, AddIncomeRequest и т.д.)
- **ErrorResponse** — ответ об ошибке

### API-клиент (`ApiClient.kt`)

HTTP-клиент на базе **Ktor** с:

- **ContentNegotiation** — автоматическая JSON сериализация/десериализация
- **HttpTimeout** — таймауты запросов
- **Bearer-авторизация** — JWT-токен из `TokenStorage` добавляется в заголовки
- **Обработка ошибок** — `ApiException` с HTTP status code и сообщением

Поддерживаемые эндпоинты:

- `POST /api/auth/login`, `/register`, `GET /api/auth/me`
- CRUD для categories, income_sources, incomes, expenses, budgets
- `POST /api/months/ensure` — создание/получение месяца
- `GET /api/months/:id/summary`, `/api/trend`
- CRUD для savings_goals, savings_transactions
- `PUT /api/categories/reorder`
- `POST /api/settings/change-password`
- `POST /api/backups`, `POST /api/backups/restore`

### Хранение токена (`TokenStorage.kt`)

Использует **Multiplatform Settings** (обёртка над `SharedPreferences` на Android и `NSUserDefaults` на iOS):

- `token` — JWT-токен авторизации
- `serverUrl` — URL бэкенда (по умолчанию `http://10.0.2.2:3002` — localhost для Android-эмулятора)
- `clear()` — очистка токена при выходе

---

## Dependency Injection

**Koin** инициализируется платформенно:

- **Android**: в `FinKeeperApp.onCreate()` через `startKoin { androidContext(...); modules(appModule) }`
- **iOS**: в `iOSApp.init()` через вызов `MainViewControllerKt.doInitKoin()`

Модуль `appModule` регистрирует:

- **Singletons**: `TokenStorage`, `ApiClient`
- **ViewModels**: `AuthViewModel`, `DashboardViewModel`, `MonthViewModel`, `CategoriesViewModel`, `SavingsViewModel`, `SettingsViewModel`

---

## UI и темизация

### Material3 тема

Приложение использует **Material3** с кастомной палитрой, повторяющей цвета веб-клиента:

- **Основной цвет (light)**: `#1B905B` (изумрудный)
- **Основной цвет (dark)**: `#4ADE80` (яркий зелёный)
- Семантические цвета: доходы (зелёный), расходы (красный), накопления (синий), предупреждения (жёлтый)
- Карточки с цветным фоном для каждого типа данных
- Автоматическое переключение light/dark по системным настройкам

### Навигация

Нижнее меню с 5 экранами:

1. **Обзор** (Dashboard) — сводка, тренды, структура расходов
2. **Месяц** (MonthView) — детальный просмотр доходов/расходов за месяц
3. **Категории** — управление категориями расходов и источниками дохода
4. **Копилки** (Savings) — накопительные цели
5. **Настройки** — профиль, безопасность, бэкапы

### Экран авторизации

- Формы логина и регистрации с переключением
- Настраиваемый URL сервера (для подключения к разным бэкендам)
- Автоматическая проверка сохранённого токена при запуске

---

## Нюансы и особенности

### Совместимость Kotlin 2.3.0 + kotlinx-datetime 0.7.x

В Kotlin 2.3.0 классы `Instant` и `Clock` были перенесены из `kotlinx.datetime` в стандартную библиотеку (`kotlin.time`). Проект использует `kotlin.time.Clock.System.now()` вместо устаревшего `kotlinx.datetime.Clock.System.now()`. При этом `TimeZone`, `toLocalDateTime()`, `LocalDate` остаются в `kotlinx.datetime`.

### Логика копилок

При пополнении копилки на бэкенде автоматически создаётся скрытый расход в категории «Пополнение копилки» (`is_active=0`), чтобы вклад в копилки уменьшал доступный баланс месяца. Мобильный клиент фильтрует эту категорию из отображения расходов.

### URL сервера

- **Android-эмулятор**: `http://10.0.2.2:3002` (маппинг localhost хоста)
- **iOS-симулятор / реальное устройство**: необходимо указать реальный IP-адрес сервера через экран авторизации
- URL сохраняется в `TokenStorage` и переиспользуется между сессиями

### Формат данных

- Валюта форматируется без дробной части с неразрывными пробелами в качестве разделителя тысяч: `1 000 000 ₽`
- Даты отображаются в формате `DD.MM.YYYY`
- Названия месяцев — на русском языке

---

## Сборка и запуск

### Android

Из терминала:

```shell
./gradlew :composeApp:assembleDebug
```

Или через Run-конфигурацию в Android Studio / IntelliJ IDEA.

### iOS

1. Открыть директорию [/iosApp](./iosApp) в Xcode
2. Запустить на симуляторе или устройстве

Или через Run-конфигурацию в Android Studio с плагином KMM.

### Запуск тестов

```shell
./gradlew composeApp:testDebugUnitTest
```

### Проверка компиляции

```shell
# Android
./gradlew composeApp:compileDebugKotlinAndroid

# iOS (Simulator ARM64)
./gradlew composeApp:compileKotlinIosSimulatorArm64
```

---

## Требования

- **JDK**: 11+
- **Android Studio**: Ladybug или новее (с поддержкой KMP)
- **Xcode**: 15+ (для iOS-сборки)
- **Бэкенд**: запущенный сервер FinKeeper (Express + SQLite) на порту 3002

---

## Связь с другими компонентами проекта

Данный мобильный клиент является частью монорепозитория **My Home Budget**:

```text
My Home Budget/
├── client/              # Веб-клиент (React 19 + Vite + TypeScript + Tailwind CSS)
├── server/              # Бэкенд (Express 5 + SQLite)
└── mobile_app_client/   # Мобильный клиент (данный проект)
```

Мобильный клиент использует тот же REST API, что и веб-клиент, и полностью совместим с существующим бэкендом.
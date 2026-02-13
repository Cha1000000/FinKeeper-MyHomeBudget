# План архитектуры офлайн-режима для FinKeeper Mobile

## Обзор задачи

Добавить возможность работы офлайн в мобильном приложении FinKeeper (KMP + Compose Multiplatform):
- Локальное хранение данных на устройстве пользователя
- Механизм синхронизации актуальных данных между БД сервера и локальной БД устройства
- Механизм отслеживания изменений данных и их синхронизации при появлении интернета
- Использование SQLite3 или подходящего кроссплатформенного решения для Android и iOS

---

## 1. Выбор технологии локальной БД

### Варианты:

#### Вариант A: SQLDelight (Рекомендуемый)
**Преимущества:**
- Нативная поддержка KMP
- Автоматическая генерация SQL на основе Kotlin data classes
- Поддержка Android (SQLite) и iOS (SQLite) из коробки
- Легкая миграция схем
- Асинхронные операции через корутины

**Недостатки:**
- Требует написания SQL-запросов вручную для сложных запросов

#### Вариант B: Room (Android) + Core Data (iOS)
**Преимущества:**
- Room: мощный ORM для Android с compile-time проверкой SQL
- Core Data: нативное решение для iOS

**Недостатки:**
- Разные API для разных платформ
- Больше платформенно-специфичного кода
- Сложнее поддерживать единый код

#### Вариант C: Realm
**Преимущества:**
- Полностью кроссплатформенный
- Автоматическая синхронизация
- Реактивные запросы

**Недостатки:**
- Требует использования Realm SDK
- Меньше контроля над SQL
- Размер библиотеки

### Рекомендация: **SQLDelight**

**Причины выбора:**
1. Нативная поддержка KMP без платформенно-специфичного кода
2. Простота интеграции с существующей архитектурой
3. Хорошая документация и примеры для KMP
4. Легкая миграция схем
5. Асинхронные операции через корутины (уже используются в проекте)

---

## 2. Архитектура офлайн-режима

### 2.1. Общая схема

```
┌─────────────────────────────────────────────────────────────────┐
│                     UI Layer (Compose)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ Dashboard    │  │ MonthView    │  │ Categories   │ │
│  │ Screen       │  │ Screen       │  │ Screen       │ │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘ │
└─────────┼──────────────────┼──────────────────┼──────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                  ViewModels (MVVM)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ Dashboard    │  │ Month        │  │ Categories   │ │
│  │ ViewModel     │  │ ViewModel     │  │ ViewModel     │ │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘ │
└─────────┼──────────────────┼──────────────────┼──────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Repository Layer                           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  LocalRepository (SQLite)                      │   │
│  │  - CRUD операции с локальной БД              │   │
│  │  - Отслеживание изменений                     │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  SyncRepository                                │   │
│  │  - Очередь офлайн-операций               │   │
│  │  - Синхронизация с сервером                   │   │
│  │  - Разрешение конфликтов                      │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────┼──────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Data Layer                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  LocalDatabase (SQLDelight)                    │   │
│  │  - Таблицы: users, months, incomes,          │   │
│  │    expenses, categories, budgets,               │   │
│  │    income_sources, savings_goals,               │   │
│  │    savings_transactions, sync_queue             │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  ApiClient (существующий)                    │   │
│  │  - HTTP запросы к серверу                     │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Network Monitor                          │
│  - Отслеживание состояния сети                      │
│  - Запуск синхронизации при появлении интернета   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2. Потоки данных

#### Поток чтения данных (Read Flow)
```
UI → ViewModel → Repository
  ├─ Проверить локальную БД
  ├─ Если данные актуальные → вернуть из локальной БД
  └─ Если данные устарели или отсутствуют → запросить с сервера
      └─ Сохранить в локальную БД
      └─ Вернуть в UI
```

#### Поток записи данных (Write Flow)
```
UI → ViewModel → Repository
  ├─ Проверить состояние сети
  ├─ Если онлайн:
  │   ├─ Отправить на сервер
  │   ├─ При успехе → сохранить в локальную БД
  │   └─ При ошибке → добавить в очередь синхронизации
  └─ Если офлайн:
      ├─ Сохранить в локальную БД
      └─ Добавить в очередь синхронизации
```

#### Поток синхронизации (Sync Flow)
```
Network Monitor → SyncRepository
  ├─ При появлении интернета
  ├─ Обработать очередь синхронизации
  │   ├─ Для каждой операции:
  │   │   ├─ Отправить на сервер
  │   │   ├─ При успехе → удалить из очереди
  │   │   └─ При ошибке → оставить в очереди (retry later)
  └─ Обновить локальную БД с сервера
```

---

## 3. Схема локальной БД

### 3.1. Основные таблицы

#### Таблица: `users`
```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL,
    email TEXT NOT NULL,
    server_id INTEGER NOT NULL,
    last_sync_at INTEGER
);
```

#### Таблица: `months`
```sql
CREATE TABLE months (
    id INTEGER PRIMARY KEY,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    server_id INTEGER NOT NULL,
    last_sync_at INTEGER,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

#### Таблица: `categories`
```sql
CREATE TABLE categories (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    user_id INTEGER NOT NULL,
    server_id INTEGER NOT NULL,
    last_sync_at INTEGER,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

#### Таблица: `income_sources`
```sql
CREATE TABLE income_sources (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    server_id INTEGER NOT NULL,
    last_sync_at INTEGER,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

#### Таблица: `incomes`
```sql
CREATE TABLE incomes (
    id INTEGER PRIMARY KEY,
    month_id INTEGER NOT NULL,
    source TEXT NOT NULL,
    amount REAL NOT NULL,
    date TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    server_id INTEGER NOT NULL,
    last_sync_at INTEGER,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (month_id) REFERENCES months(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

#### Таблица: `expenses`
```sql
CREATE TABLE expenses (
    id INTEGER PRIMARY KEY,
    month_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    amount REAL NOT NULL,
    date TEXT NOT NULL,
    comment TEXT,
    category_name TEXT,
    user_id INTEGER NOT NULL,
    server_id INTEGER NOT NULL,
    last_sync_at INTEGER,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (month_id) REFERENCES months(id),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

#### Таблица: `budgets`
```sql
CREATE TABLE budgets (
    id INTEGER PRIMARY KEY,
    month_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    limit_amount REAL NOT NULL,
    user_id INTEGER NOT NULL,
    server_id INTEGER NOT NULL,
    last_sync_at INTEGER,
    FOREIGN KEY (month_id) REFERENCES months(id),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

#### Таблица: `savings_goals`
```sql
CREATE TABLE savings_goals (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    target_amount REAL NOT NULL,
    current_amount REAL NOT NULL DEFAULT 0,
    user_id INTEGER NOT NULL,
    server_id INTEGER NOT NULL,
    last_sync_at INTEGER,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

#### Таблица: `savings_transactions`
```sql
CREATE TABLE savings_transactions (
    id INTEGER PRIMARY KEY,
    goal_id INTEGER NOT NULL,
    amount REAL NOT NULL,
    date TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    server_id INTEGER NOT NULL,
    last_sync_at INTEGER,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (goal_id) REFERENCES savings_goals(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

### 3.2. Таблица очереди синхронизации

#### Таблица: `sync_queue`
```sql
CREATE TABLE sync_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type TEXT NOT NULL,        -- 'income', 'expense', 'category', etc.
    operation TEXT NOT NULL,          -- 'create', 'update', 'delete'
    entity_id INTEGER NOT NULL,        -- локальный ID сущности
    server_id INTEGER,                 -- ID на сервере (для update/delete)
    payload TEXT NOT NULL,              -- JSON с данными операции
    status TEXT NOT NULL DEFAULT 'pending', -- 'pending', 'syncing', 'failed', 'success'
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    error_message TEXT
);
```

### 3.3. Индексы для оптимизации

```sql
CREATE INDEX idx_months_user ON months(user_id);
CREATE INDEX idx_months_year_month ON months(year, month);
CREATE INDEX idx_incomes_month ON incomes(month_id);
CREATE INDEX idx_expenses_month ON expenses(month_id);
CREATE INDEX idx_expenses_category ON expenses(category_id);
CREATE INDEX idx_budgets_month ON budgets(month_id);
CREATE INDEX idx_savings_goals_user ON savings_goals(user_id);
CREATE INDEX idx_sync_queue_status ON sync_queue(status, created_at);
CREATE INDEX idx_sync_queue_entity ON sync_queue(entity_type, entity_id);
```

---

## 4. Механизм синхронизации

### 4.1. Стратегия синхронизации

**Выбранная стратегия: Pull + Push с очередью**

1. **Pull (загрузка с сервера):**
   - При первом запуске приложения
   - При появлении интернета
   - При ручном обновлении (pull-to-refresh)
   - Загружаются только изменения с момента последней синхронизации

2. **Push (отправка на сервер):**
   - Офлайн-операции добавляются в очередь
   - При появлении интернета очередь обрабатывается
   - Операции выполняются в порядке FIFO

3. **Конфликтное разрешение:**
   - **Last-Write-Wins** для простых сущностей
   - **Server-Wins** для критических данных (балансы, бюджеты)
   - **Manual Resolution** для конфликтов редактирования (будет в будущем)

### 4.2. Отслеживание изменений

#### Версионирование данных

Каждая сущность имеет:
- `server_id` - ID на сервере (для связи с серверными данными)
- `last_sync_at` - время последней синхронизации
- `is_deleted` - мягкое удаление (для синхронизации)

#### Операции синхронизации

| Операция | Офлайн | Онлайн | Очередь |
|-----------|---------|--------|----------|
| Create | Сохранить в БД + добавить в очередь | Отправить на сервер + сохранить в БД | Да |
| Update | Сохранить в БД + добавить в очередь | Отправить на сервер + сохранить в БД | Да |
| Delete | Пометить is_deleted=1 + добавить в очередь | Отправить DELETE на сервер + удалить из БД | Да |

### 4.3. Алгоритм обработки очереди

```kotlin
suspend fun processSyncQueue() {
    val pendingOperations = syncQueueDao.getPendingOperations()
    
    for (operation in pendingOperations) {
        try {
            syncQueueDao.updateStatus(operation.id, "syncing")
            
            when (operation.operation) {
                "create" -> apiClient.create(operation.payload)
                "update" -> apiClient.update(operation.serverId, operation.payload)
                "delete" -> apiClient.delete(operation.serverId)
            }
            
            // Успех - обновить локальные данные
            syncQueueDao.markAsSuccess(operation.id)
            
        } catch (e: Exception) {
            // Ошибка - увеличить счетчик повторов
            val retryCount = operation.retryCount + 1
            if (retryCount >= MAX_RETRIES) {
                syncQueueDao.markAsFailed(operation.id, e.message)
            } else {
                syncQueueDao.updateRetryCount(operation.id, retryCount)
            }
        }
    }
}
```

---

## 5. Мониторинг сети

### 5.1. Реализация

Для KMP используем expect/actual:

```kotlin
// commonMain
expect class NetworkMonitor {
    val isOnline: StateFlow<Boolean>
    fun startMonitoring()
    fun stopMonitoring()
}

// androidMain
actual class NetworkMonitor(
    private val context: Context
) {
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }
        
        override fun onLost(network: Network) {
            _isOnline.value = false
        }
    }
    
    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }
    
    fun stopMonitoring() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}

// iosMain
actual class NetworkMonitor {
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val reachability = NWPathMonitor()
    
    fun startMonitoring() {
        reachability.pathUpdateHandler = { path in
            _isOnline.value = path.status == .satisfied
        }
        reachability.start(queue: .main)
    }
    
    fun stopMonitoring() {
        reachability.cancel()
    }
}
```

### 5.2. Интеграция с синхронизацией

```kotlin
class SyncManager(
    private val networkMonitor: NetworkMonitor,
    private val syncRepository: SyncRepository
) {
    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    syncRepository.processSyncQueue()
                    syncRepository.pullLatestChanges()
                }
            }
        }
    }
}
```

---

## 6. Обновление существующих ViewModels

### 6.1. Изменения в архитектуре

**До:**
```
ViewModel → ApiClient → Server
```

**После:**
```
ViewModel → Repository → LocalDatabase + ApiClient
```

### 6.2. Пример обновления DashboardViewModel

```kotlin
class DashboardViewModel(
    private val repository: DashboardRepository  // Новый репозиторий
) : ViewModel() {
    
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    
    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // Сначала пробуем загрузить из локальной БД
                val localData = repository.getLocalDashboardData()
                
                if (localData != null && repository.isDataFresh()) {
                    // Данные актуальны
                    _state.value = localData.toDashboardState()
                } else {
                    // Загружаем с сервера
                    val serverData = repository.getDashboardDataFromServer()
                    repository.saveDashboardData(serverData)
                    _state.value = serverData.toDashboardState()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки данных"
                )
            }
        }
    }
}
```

---

## 7. План реализации по фазам

### Фаза 1: Подготовка и инфраструктура (1-2 дня)
- [ ] Добавить зависимости SQLDelight в build.gradle.kts
- [ ] Создать структуру пакетов: `data/local/`
- [ ] Создать SQLDelight database driver
- [ ] Создать DAO интерфейсы для всех сущностей
- [ ] Создать Database класс с таблицами
- [ ] Настроить миграции схем

### Фаза 2: Repository Layer (2-3 дня)
- [ ] Создать базовый Repository интерфейс
- [ ] Реализовать LocalRepository для CRUD операций
- [ ] Создать SyncRepository для управления очередью
- [ ] Добавить NetworkMonitor (expect/actual)
- [ ] Создать SyncManager для координации

### Фаза 3: Обновление ViewModels (3-4 дня)
- [ ] Обновить DashboardViewModel
- [ ] Обновить MonthViewModel
- [ ] Обновить CategoriesViewModel
- [ ] Обновить SavingsViewModel
- [ ] Обновить SettingsViewModel
- [ ] Обновить AuthViewModel (для сохранения пользователя)

### Фаза 4: UI обновления (2-3 дня)
- [ ] Добавить индикатор синхронизации
- [ ] Добавить статус офлайн/онлайн в UI
- [ ] Обновить экраны для работы с офлайн-данными
- [ ] Добавить pull-to-refresh на основных экранах
- [ ] Показывать ошибки синхронизации

### Фаза 5: Тестирование (2-3 дня)
- [ ] Unit тесты для Repository layer
- [ ] Unit тесты для SyncManager
- [ ] Интеграционные тесты для БД
- [ ] Тестирование офлайн-режима
- [ ] Тестирование синхронизации
- [ ] Тестирование конфликтов

### Фаза 6: Оптимизация и документация (1-2 дня)
- [ ] Оптимизировать запросы к БД
- [ ] Добавить кэширование
- [ ] Написать документацию по архитектуре
- [ ] Обновить README.md

---

## 8. Зависимости для build.gradle.kts

```kotlin
// SQLDelight
implementation("app.cash.sqldelight:sql-delight:2.0.2")
implementation("app.cash.sqldelight:sql-delight-android-driver:2.0.2")
implementation("app.cash.sqldelight:sql-delight-ios-driver:2.0.2")
implementation("app.cash.sqldelight:primitive-adapters:2.0.2")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

// DateTime
implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
```

---

## 9. Потенциальные проблемы и решения

### Проблема 1: Конфликты данных
**Решение:**
- Использовать `last_sync_at` для определения актуальности
- Server-Wins для критических данных
- Добавить поле `version` для оптимистичного контроля

### Проблема 2: Большой объем данных
**Решение:**
- Пагинация при загрузке с сервера
- Ленивая загрузка для списков
- Индексы для оптимизации запросов

### Проблема 3: Сложные отношения между сущностями
**Решение:**
- Денормализация данных (избежать избыточности)
- Каскадное удаление (is_deleted вместо реального DELETE)
- Внешние ключи для целостности данных

### Проблема 4: Ошибки сети во время синхронизации
**Решение:**
- Очередь с retry механизмом
- Экспоненциальный backoff для повторных попыток
- Логирование всех ошибок для отладки

---

## 10. Метрики успеха

### Функциональные требования
- [ ] Приложение работает полностью офлайн
- [ ] Все офлайн-изменения синхронизируются при появлении интернета
- [ ] Данные не теряются при сбоях сети
- [ ] Конфликты данных разрешаются корректно
- [ ] UI показывает актуальное состояние синхронизации

### Производительность
- [ ] Загрузка данных из локальной БД < 100мс
- [ ] Синхронизация не блокирует UI
- [ ] Размер БД < 50MB для типичного пользователя
- [ ] Потребление батареи минимально

### Надежность
- [ ] Нет потери данных при краше приложения
- [ ] Корректная работа при переключении сети
- [ ] Обработка ошибок сети без крашей
- [ ] Восстановление после прерывания синхронизации

---

## 11. Следующие шаги

1. **Утвердить план архитектуры** с пользователем
2. **Начать реализацию Фазы 1** - добавление SQLDelight
3. **Создать структуру пакетов** и базовые классы
4. **Реализовать Repository Layer** (Фаза 2)
5. **Обновить ViewModels** для использования Repository (Фаза 3)
6. **Добавить UI индикаторы** офлайн/онлайн (Фаза 4)
7. **Тестирование** всех компонентов (Фаза 5)
8. **Документация** и оптимизация (Фаза 6)

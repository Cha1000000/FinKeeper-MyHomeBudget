# План рефакторинга синхронизации KMP в FinKeeper

## Цель

Убрать блокирующий UX при локальных изменениях данных и привести KMP-клиент к более зрелой модели синхронизации:

- локальная запись завершается мгновенно
- UI обновляется сразу из локальной БД/локального состояния
- синхронизация с сервером выполняется в фоне
- full-screen loader показывается только для первичной или явно пользовательской полной загрузки

Документ опирается на лучшие практики, изученные в проекте `HabitTracker`, и адаптирует их под текущую архитектуру `FinKeeper`.

## Текущее состояние проблемы

Сейчас после многих локальных мутаций ViewModel вызывают `loadData()`, который:

- выставляет `isLoading = true`
- приводит экран к `LoadingScreen()`
- блокирует дальнейшее взаимодействие с UI

Это особенно заметно на `MonthView`, где после каждого `addExpense`, `updateExpense`, `deleteExpense`, `addIncome`, `setBudget` экран временно перекрывается лоадером.

Дополнительно ситуацию усиливают:

- `syncManager.dataUpdated.collect { loadData() }`
- автоматический `scheduleProcessQueue()` после enqueue
- `syncAll()` при восстановлении сети или после логина

В результате даже быстрые локальные операции воспринимаются как «ожидание синхронизации», хотя значимая часть задержки создаётся самим UI-слоем.

## Целевой подход

### Базовые принципы

- `local-first` для всех пользовательских записей
- `queue/outbox` для отправки изменений на сервер
- фоновый неблокирующий `drain/send`
- UI подписан на локальное состояние, а не живёт через паттерн `save -> full reload -> full loader`
- remote merge не должен перетирать локальные `pending` изменения

### Что переносим из HabitTracker

- `Room/DB-first writes`
- outbox-подход с фоновым дренажом
- `KickSync`: enqueue + неблокирующий фоновый запуск отправки
- разделение экранной загрузки и фоновой синхронизации
- `pending-guard`
- `updatedAtMillis`
- `operationId/opId`

## План внедрения по фазам

## Phase 1 — UX-fix

### Цель Phase 1

Убрать fullscreen loader после локальных мутаций и после фоновых sync-событий, не ломая текущую бизнес-логику.

### Изменения Phase 1

- В ViewModel ввести разделение между:
  - полной загрузкой экрана
  - тихим локальным обновлением без fullscreen loader
- После локальных мутаций:
  - не поднимать `isLoading = true`
  - обновлять состояние тихо
- После `syncManager.dataUpdated`:
  - выполнять тихое локальное обновление UI
  - не блокировать экран
- `syncManager.isSyncing` использовать только как индикатор фоновой синхронизации, но не как причину скрывать экран целиком

### Файлы Phase 1

- `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/ui/viewmodel/MonthViewModel.kt`
- `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/ui/viewmodel/CategoriesViewModel.kt`
- `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/ui/viewmodel/SavingsViewModel.kt`
- `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/ui/viewmodel/DashboardViewModel.kt`

### Критерии готовности Phase 1

- после `add/update/delete` экран не переходит в `LoadingScreen()`
- можно быстро вносить несколько расходов подряд
- background sync продолжает работать
- при первичной загрузке экранов loader сохраняется

## Phase 2 — рефактор write-path

### Цель Phase 2

Унифицировать все операции `create/update/delete` под схему:

`local write -> queue -> background send`

### Изменения Phase 2

- убрать смешанный подход, где часть операций пытается сначала делать прямой network sync
- репозитории должны:
  - писать в локальную БД
  - помечать запись как `pending`
  - класть операцию в очередь
  - запускать фоновую отправку
- ViewModel не должны зависеть от завершения сетевой отправки

### Приоритетные репозитории Phase 2

- `ExpenseRepository`
- `IncomeRepository`
- `BudgetRepository`
- `CategoryRepository`
- `IncomeSourceRepository`
- `SavingsGoalRepository`
- `SavingsTransactionRepository`

### Отдельное замечание Phase 2

`ExpenseRepository.createExpense()` сейчас использует смешанный путь:

- локальная запись
- попытка немедленного прямого sync в API
- fallback в queue

Этот код нужно привести к единой модели `local-first + queue + background drain`.

## Phase 3 — улучшение outbox/queue

### Цель Phase 3

Сделать очередь синхронизации предсказуемой, расширяемой и удобной для отладки.

### Изменения Phase 3

- добавить `operationId/opId`
- добавить/унифицировать `updatedAtMillis`
- уточнить модель состояний:
  - `PENDING`
  - `IN_PROGRESS` / `SYNCING`
  - `FAILED`
  - `DONE` / `COMPLETED`
- централизовать retry policy
- сделать более явный API `SyncManager` вокруг enqueue/drain

### Дополнительно для Phase 3

- усилить observability:
  - размер очереди
  - retry count
  - latency отправки
  - последние ошибки

## Phase 4 — remote merge / pending-guard / conflict policy

### Цель Phase 4

Избежать перетирания локальных правок входящими серверными данными.

### Изменения Phase 4

- во всех `syncWithServer()` ветках применять pending-guard
- remote merge пропускать, если:
  - запись локально `pending`
  - или есть pending/in-progress операция для этой сущности
- по возможности нормализовать сравнение конфликтов через `updatedAtMillis`
- сохранить локальные правки как source of truth до завершения их отправки

## Порядок реализации

1. `Phase 1` — быстрый UX-fix
2. `Phase 2` — рефактор write-path
3. `Phase 3` — улучшение очереди/outbox
4. `Phase 4` — унификация merge/pending-guard

## Технический подход по шагам

### Шаг 1 — тихая загрузка

Ввести в ключевых ViewModel API вида:

- `loadData(showLoader = true, syncFromServer = true)`
- или отдельный `reloadQuietly()`

### Шаг 2 — тихий reload после мутаций

Перевести все локальные мутации на тихий reload либо оптимистичный update state.

### Шаг 3 — неблокирующая обработка sync events

Перевести `dataUpdated` обработчики на неблокирующее обновление состояния.

### Шаг 4 — унификация repository write-path

Упростить репозитории до единого `local-first` поведения.

### Шаг 5 — усиление SyncManager

Усилить `SyncManager` и queue contract.

### Шаг 6 — merge и conflict policy

Доработать remote merge и защиту от конфликтов.

## Что не должно происходить после рефакторинга

- экран не должен скрываться fullscreen loader'ом после каждого сохранения суммы
- пользователь не должен ждать завершения sync, чтобы внести следующий расход
- локальные изменения не должны теряться при позднем ответе сервера
- `syncManager.dataUpdated` не должен вызывать блокирующую полную перезагрузку экрана

## Ожидаемый результат

После завершения всех фаз KMP-клиент будет вести себя так:

- пользователь быстро вносит несколько расходов подряд
- данные появляются мгновенно
- синхронизация идёт в фоне
- статус синка виден, но не мешает работе
- конфликты и ретраи обрабатываются предсказуемо

## Следующий server-side этап после client-side Phase 3-4

### Зачем нужен отдельный server-side follow-up

Client-side часть уже стала заметно надёжнее:

- outbox хранит `opId`
- queue учитывает snapshot `updatedAt`
- старые queue-item'ы могут отбрасываться как устаревшие
- локальные `pending` изменения лучше защищены от поздних ответов

Но для полноценной идемпотентности и устойчивого multi-device sync серверу желательно начать отдавать и принимать больше sync-метаданных.

### 1. `idempotency key` / `operationId`

#### Цель `operationId`

Не допустить повторного применения одной и той же операции при retry, потере ответа или повторной отправке из очереди.

#### Что желательно добавить на сервере для `operationId`

- поддержать `operationId` / `idempotency key` для write-endpoints:
  - `POST`
  - `PUT`
  - при необходимости `DELETE`
- сохранять факт уже обработанного `operationId`
- при повторном запросе с тем же `operationId`:
  - не выполнять повторную запись
  - возвращать тот же итоговый результат

#### Где это особенно важно

- создание расходов
- создание доходов
- операции по накоплениям
- создание/обновление бюджетов

#### Минимальный server contract

- клиент передаёт `operationId`
- сервер либо:
  - сохраняет его в отдельной таблице processed operations
  - либо привязывает к самой сущности/журналу операций
- повтор с тем же `operationId` становится идемпотентным

### 2. Единый `updated_at` contract

#### Цель `updated_at`

Дать клиенту и серверу единое понятие «какая версия записи новее».

#### Что желательно добавить на сервере для `updated_at`

- у всех синхронизируемых сущностей иметь серверный `updated_at`
- возвращать `updated_at`:
  - в списках
  - в single-item ответах
  - после `create`
  - после `update`
- обновлять `updated_at` на сервере строго при каждой модификации записи

#### Что даст updated_at

- клиент сможет сравнивать локальную и серверную версии записи не только по `pending`-статусу
- conflict policy станет менее эвристическим
- появится основа для deterministic merge, а не только для `pending-guard`

### 3. Tombstones для удалений

#### Цель tombstones

Не допустить «воскрешения» удалённых записей между устройствами при асинхронном sync.

#### Почему нужны tombstones

Если одно устройство удалило запись, а другое устройство или сервер ещё держит старую копию, обычный fetch без tombstones может вернуть запись обратно.

#### Что желательно добавить на сервере для tombstones

- либо soft-delete c полями:
  - `deleted_at`
  - `is_deleted`
- либо отдельную tombstone-таблицу удалённых сущностей
- включать tombstone-информацию в sync/read API или отдельный delta endpoint

#### Что дадут tombstones

- клиент сможет понимать, что запись не «отсутствует случайно», а именно удалена
- merge станет корректнее для multi-device сценариев

### 4. Предпочтительный порядок server-side внедрения

#### Шаг A

Добавить `updated_at` во все API-ответы и гарантировать его обновление на сервере.

#### Шаг B

Добавить `operationId` / `idempotency key` на write-endpoints.

#### Шаг C

Добавить tombstones / soft-delete contract для синхронизируемых сущностей.

#### Шаг D

После этого упростить клиентский conflict policy и перейти к более строгому сравнению локальной и серверной версий.

### Что можно не делать сразу

- не обязательно сразу перестраивать все endpoints под полноценный delta-sync
- не обязательно сразу внедрять сложную CRDT-like схему
- можно идти поэтапно:
  - сначала `updated_at`
  - потом `operationId`
  - потом tombstones

### Практический итог

Текущая client-side реализация уже даёт хороший UX и заметно более зрелое поведение очереди.

Следующий качественный скачок надёжности потребует server-side поддержки:

- `operationId / idempotency key`
- унифицированного `updated_at`
- tombstones для удалений

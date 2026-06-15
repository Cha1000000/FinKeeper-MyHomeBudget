# Текущая реализация синхронизации клиентов и сервера

## Назначение документа

Этот документ описывает **текущее фактическое состояние** механизмов синхронизации в FinKeeper после завершения рефакторинга KMP sync и server-side поддержки timestamp/tombstone/idempotency.

Это **не план** и не список намерений. Ниже зафиксировано то, что реально работает в коде на данный момент.

## Scope

Документ покрывает:

- KMP client sync architecture
- локальную очередь синхронизации и merge-логику
- download/upload flow
- server-side `created_at` / `updated_at`
- server-side tombstones через `deleted_records`
- idempotent write requests через `operationId`
- backup/restore поведение, важное для sync
- текущие ограничения реализации

## Основные принципы текущей реализации

Система сейчас построена по модели **offline-first с локальной БД и очередью исходящих изменений**.

Ключевые идеи:

- клиент пишет сначала в локальную БД
- локальные изменения помечаются как требующие синхронизации
- в `sync_queue` кладётся операция `INSERT` / `UPDATE` / `DELETE`
- `SyncManager` пытается отправить изменения на сервер как можно раньше
- при полном sync сначала выполняется **download**, затем **upload**
- сервер возвращает **canonical row** после успешных write-операций
- клиент использует серверные `created_at` / `updated_at` для merge policy
- удаления распространяются через server tombstones (`deleted_records`)
- повторные write-запросы защищены `operationId` / `X-Operation-Id`

## Где находится основная логика

### KMP client

Основные файлы:

- `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/repository/SyncManager.kt`
- `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/repository/SyncService.kt`
- `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/repository/SyncStateStorage.kt`
- `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/local/dao/SyncQueueDao.kt`
- entity repositories:
  - `CategoryRepository.kt`
  - `IncomeSourceRepository.kt`
  - `IncomeRepository.kt`
  - `ExpenseRepository.kt`
  - `BudgetRepository.kt`
  - `SavingsGoalRepository.kt`
  - `SavingsTransactionRepository.kt`
- API contract:
  - `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/model/Models.kt`
  - `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/remote/ApiClient.kt`
- DI wiring:
  - `mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/di/AppModule.kt`

### Server

Основной файл:

- `server/index.js`

Схема fresh database:

- `server/db_setup.js`

## Какие сущности реально участвуют в sync

Текущая синхронизация покрывает:

- `category`
- `income_source`
- `month`
- `income`
- `expense`
- `budget`
- `savings_goal`
- `savings_transaction`
- `planned_override` — исключения план-слоя (пропуск/override регулярного платежа на месяц)

### Особенность `planned_override`

`planned_override` — offline-first исключения план-слоя (skip / override суммы или дня).

- entity_id в очереди кодирует ключ `(тип шаблона, локальный id шаблона, локальный id месяца)` через `PlannedQueueKey`; payload не используется
- при отправке (`syncPlannedOverrideToServer`) читается АКТУАЛЬНОЕ состояние строки из БД (last-write-wins) и резолвятся серверные id месяца/шаблона; PUT идемпотентен, «всё дефолтное» сервер превращает в удаление
- pull: сырое состояние месяца берётся из `GET /months/:id/planned-state` (строки `planned_overrides` + зеркало `auto_created_records`); локальные `pending`-правки переживают pull, синхронизированные — заменяются серверными (server wins)
- сам «план» (виртуальные плановые платежи) на клиенте НЕ хранится, а вычисляется локально из фиксированных шаблонов минус материализованные записи — работает оффлайн

### Особенность `month`

Для `month` нет полноценного download-merge цикла.

Текущее поведение:

- локальный месяц создаётся локально при необходимости
- серверный `month` создаётся/находится через `POST /api/months/ensure`
- `MonthRepository.syncWithServer()` фактически является no-op
- server id для месяца нужен главным образом как внешний ключ для доходов/расходов/бюджетов

## Клиентская архитектура синхронизации

## 1. Локальное сохранение как источник UX

Пользовательские действия сначала меняют локальную БД.

Типичный flow:

- repository создаёт или обновляет локальную запись
- запись получает локальный `updatedAt`
- `syncStatus` переводится в dirty/pending состояние
- в `sync_queue` добавляется операция
- UI сразу видит новое локальное состояние
- `SyncManager` затем отправляет изменение на сервер

Это убирает зависимость UX от моментальной доступности сервера.

## 2. Очередь исходящих изменений (`sync_queue`)

Очередь обслуживается `SyncQueueDao`.

Для каждого элемента хранятся:

- пользователь
- тип сущности
- локальный `entityId`
- операция `INSERT` / `UPDATE` / `DELETE`
- `payload`
- служебные поля статуса и retry

### Статусы очереди

Используются состояния:

- `PENDING`
- `SYNCING`
- `FAILED`
- `COMPLETED`

### Что лежит в payload

В payload хранится не только старый raw payload, а metadata-объект `SyncQueuePayloadMetadata`:

- `opId` — operation id для server idempotency
- `entityUpdatedAt` — снимок локального `updatedAt` на момент постановки в очередь
- `deleteServerId` — server id удаляемой записи для delete/tombstone flows

Это используется для:

- дедупликации и merge очереди
- пропуска устаревших queued-операций
- сохранения dirty state, если локальная запись изменилась уже после постановки задачи в очередь
- безопасной отправки delete по server id

## 3. Merge логика внутри очереди

При `enqueueSync()` новая операция не всегда просто добавляется в конец.

`SyncManager` сначала ищет незавершённую queued-операцию для той же сущности и затем применяет merge policy:

- оставить старую операцию
- заменить старую новой
- удалить обе, если они взаимно компенсируют друг друга

Практический смысл:

- последовательность `INSERT` → `UPDATE` обычно схлопывается
- бессмысленные промежуточные операции не раздувают очередь
- отменённые изменения не уезжают на сервер зря

## 4. Полный sync: download → upload

`SyncManager.syncAll(monthId)` выполняет двустороннюю синхронизацию в два этапа:

### Этап A. Download

Сначала клиент подтягивает удалённые изменения и обновляет локальную БД:

- categories
- income sources
- savings goals
- savings transactions по каждой локальной savings goal с `serverId`
- если известен текущий локальный месяц:
  - incomes
  - expenses
  - budgets
  - planned-state (`GET /months/:id/planned-state`): зеркало `auto_created_records` + `planned_overrides`
- затем отдельно применяются tombstones из `deleted_records`

> **Сериализация pull.** Каждый репозиторный `syncWithServer()` обёрнут в `Mutex` (`syncPullMutex`): Dashboard и Month ViewModel стартуют параллельно, и без мьютекса две гонящиеся insert-ветки дублировали локальные записи. Мьютекс выстраивает параллельные pull в очередь.

### Этап B. Upload

После download:

- `FAILED` элементы под лимитом попыток переводятся обратно в `pending` (`retryRetriableFailed(MAX_AUTO_RETRY_COUNT)`); перманентно падающие (исчерпавшие лимит) — пропускаются, чтобы не штормить сервер
- берутся `PENDING` элементы очереди (в порядке `created_at` — родитель раньше ребёнка)
- каждый элемент отправляется через `syncItemToServer()`
- успешные операции помечаются `COMPLETED`
- completed элементы очищаются

Тот же авто-ретрай под лимитом выполняется и в лёгком пути `scheduleProcessQueue()` (после каждой локальной мутации), поэтому упавшая по временной причине операция (родитель ещё не синхронизирован, обрыв сети) подхватывается сразу, а не ждёт следующего полного sync. Ручной ретрай из Настроек (`retryFailed()`) сбрасывает счётчик попыток.

### Защита от зависания

В `SyncManager` есть защита от stuck state:

- `_isSyncing`
- `syncStartedAt`
- safety reset примерно через 90 секунд

Это используется и в `syncAll()`, и в `scheduleProcessQueue()`.

## 5. Немедленный upload после локальной записи

Помимо полного sync, есть `scheduleProcessQueue()`.

Он запускается сразу после `enqueueSync()` и пытается отправить pending изменения без полного download-прохода.

Поведение:

- небольшая задержка перед запуском
- ожидание завершения текущего `syncAll()`, если он идёт
- отправка pending queue элементов
- очистка completed
- очистка `lastSyncError`, если failed элементов больше нет

Иными словами:

- обычный user action старается синхронизироваться сразу
- полная синхронизация нужна для согласования удалённых изменений и фонового выравнивания состояния

## 6. Автосинхронизация (`SyncService`)

`SyncService` запускает синхронизацию автоматически:

- при появлении сети
- один раз после логина, когда становится доступен `userId`
- при ручном `forceSync()`

Текущий post-login flow:

- сервис каждые 5 секунд проверяет, появился ли `userId`
- как только пользователь залогинен и сеть доступна, выполняется `syncAll(currentMonthId)`
- это происходит один раз за lifecycle сервиса

## Merge policy на download-стороне

## 1. Server timestamps как source of truth для сравнения версий

Для синхронизируемых remote models клиент теперь использует серверные:

- `createdAt`
- `updatedAt`

Они проброшены в:

- remote models
- DAO insert/update paths
- server snapshot apply methods
- repository download merge

## 2. Правило применения server snapshot

Во всех основных repository download flows используется helper `shouldApplyRemoteServerSnapshot(localUpdatedAt, remoteUpdatedAt)`.

Смысл правила:

- если у сервера нет `updatedAt`, snapshot допускается
- если у локальной записи нет `updatedAt`, snapshot допускается
- если оба timestamp есть, серверный snapshot применяется только когда он не старее локального состояния

Это позволяет:

- не перетирать более новую локальную запись старым server snapshot
- использовать server `updated_at` как общий признак версии данных

## 3. Pending guard

Перед применением удалённой записи клиент проверяет:

- локальная запись уже `SYNCED` или ещё локально dirty
- есть ли активная queued операция для этой сущности
- для parent сущностей — есть ли активные queued изменения у зависимых child записей

Если локальная сущность ещё не синхронизирована или по ней есть активная очередь, download merge пропускает серверное состояние.

Это важно для multi-device сценариев, чтобы:

- не откатывать локальные правки вторым устройством
- не восстанавливать сущность, которую пользователь только что удалил локально

## 4. Budget merge как upsert

`BudgetRepository.syncWithServer()` работает через upsert-поиск:

- сначала ищет budget по `serverId`
- если не найден — по паре `monthId + categoryId`
- затем решает update/insert

Это специально нужно для бюджета, потому что логически бюджет уникален по `month + category`, и delete/reinsert здесь создавал бы лишние конфликты.

## Upload-side canonical snapshot application

После успешного server write клиент не просто ставит `syncStatus = SYNCED`, а по возможности применяет обратно **canonical server row**.

Это сделано в `SyncManager` через набор методов вроде:

- `applyCategoryServerSnapshot(...)`
- `applyIncomeSourceServerSnapshot(...)`
- `applyIncomeServerSnapshot(...)`
- `applyExpenseServerSnapshot(...)`
- `applyBudgetServerSnapshot(...)`
- `applySavingsGoalServerSnapshot(...)`
- `applySavingsTransactionServerSnapshot(...)`

Зачем это нужно:

- сервер сам определяет итоговый `updated_at`
- сервер может вернуть окончательный `id` и нормализованные поля
- локальная запись становится максимально близкой к canonical server state

### Но есть важная защита

Если локальная сущность была изменена ещё раз **после** постановки queued операции, то `SyncManager` не должен затереть эти новые локальные изменения server snapshot'ом от старой операции.

Для этого используются:

- `shouldSkipOutdatedQueueItem(...)`
- `shouldPreserveDirtyStateAfterSuccessfulSync(...)`

Механика:

- queued item хранит `entityUpdatedAt` на момент постановки в очередь
- перед отправкой можно понять, не устарела ли queued задача
- после успешного ответа можно понять, нужно ли оставлять запись dirty

Итог:

- старый queued upload не уничтожает более новое локальное состояние
- запись может получить `serverId`, но при этом остаться dirty для следующего sync

## Обработка удалений и tombstones

## 1. Server tombstones

Сервер хранит удаления в таблице `deleted_records`:

- `user_id`
- `entity_type`
- `entity_id`
- `deleted_at`

Удаление записи на сервере:

- либо soft-delete + tombstone
- либо hard-delete + tombstone
- для savings goal сервер дополнительно пишет tombstones и для дочерних savings transactions

API:

- `GET /api/deleted_records`
- поддерживает фильтрацию по `entity_type`
- поддерживает incremental курсор `since`

## 2. Клиентский курсор по tombstones

На клиенте курсор хранится в `SyncStateStorage`:

- `lastDeletedRecordsSyncAt`

Storage scoped по:

- `userId`
- `serverUrl`

Это не даёт пересекаться состоянию sync между:

- разными пользователями
- разными серверами

## 3. Применение tombstones на клиенте

`SyncManager.applyDeletedRecordsFromServer()`:

- запрашивает удалённые записи с `since`
- сортирует их по приоритету сущностей
- применяет tombstones последовательно
- после успешного применения обновляет `lastDeletedRecordsSyncAt`

### Приоритет применения

Сначала применяются leaf сущности:

- `income`
- `expense`
- `savings_transaction`

Потом parent/reference level:

- `category`
- `income_source`

Потом верхний уровень:

- `savings_goal`

Это снижает риск конфликтов при каскадных удалениях.

## 4. Guards при применении tombstones

Перед физическим удалением локальной записи клиент проверяет:

- есть ли активная queued операция по этой записи
- для parent сущностей — есть ли queued операции у зависимых записей
- находится ли запись в `SYNCED`, а не в локально грязном состоянии

Если условия небезопасны, tombstone логируется и пропускается.

### Специальные случаи

- для `category` учитываются зависимые `expense` и `budget`
- для `income_source` учитываются зависимые `income`
- для `savings_goal` учитываются зависимые `savings_transaction`
- при применении tombstone для `savings_goal` клиент также удаляет все локальные transactions этой цели

## Server-side timestamps

## 1. Runtime migration

При старте сервера вызывается `ensureSchemaUpToDate()`.

Что делает migration helper:

- создаёт таблицы `idempotency_keys` и `deleted_records`, если их нет
- проверяет sync tables:
  - `categories`
  - `income_sources`
  - `months`
  - `incomes`
  - `expenses`
  - `budgets`
  - `savings_goals`
  - `savings_transactions`
- добавляет отсутствующие `created_at` / `updated_at`
- backfill'ит пустые timestamp-поля
- пишет подробный лог по каждой таблице

Сервер использует единый helper `nowIso()` и хранит время в ISO-8601 UTC строках.

## 2. Canonical write responses

Текущее правило write endpoints:

- после успешного `INSERT` / `UPDATE` сервер по возможности перечитывает строку из БД
- клиент получает именно canonical row
- этот row содержит server timestamps и реальные серверные значения

Это уже используется для:

- categories
- income sources
- months/ensure
- incomes
- expenses
- budgets
- savings goals
- savings transactions

## 3. Update semantics

`updated_at` меняется при любом логическом изменении записи.

В частности, уже покрыты:

- create
- update
- reorder там, где `sort_order` является частью состояния
- изменения, вызванные server-side savings logic

## Server-side idempotency

## 1. Client contract

KMP client передаёт `operationId` в заголовке:

- `X-Operation-Id`

Это делает `ApiClient.applyOperationId(...)`.

## 2. Server contract

На сервере это обрабатывает `executeIdempotent(req, res, execute)`.

Механика:

- сервер достаёт `X-Operation-Id` или `X-Idempotency-Key`
- строит request signature из method + path + body
- ищет `(user_id, operation_id)` в `idempotency_keys`
- если operation уже была с тем же signature:
  - возвращает сохранённый response
- если operation уже была, но signature другой:
  - возвращает `409`
- если операции ещё нет:
  - выполняет write
  - для успешного `2xx` сохраняет response в `idempotency_keys`

Это защищает от повторного применения одной и той же queued операции при retry и сетевых сбоях.

## Savings-specific sync semantics

`savings_transactions` имеют дополнительную server-side доменную логику.

### При создании savings transaction

Сервер:

- вставляет запись в `savings_transactions`
- обновляет `savings_goals.current_amount`
- обновляет `savings_goals.updated_at`
- если это положительное пополнение и указан `month_id`:
  - создаёт или находит скрытую категорию `Пополнение копилки`
  - создаёт скрытый expense, уменьшающий доступный баланс месяца

### При удалении savings transaction

Сервер:

- уменьшает `current_amount` у цели
- удаляет соответствующий скрытый expense
- пишет tombstone для `savings_transaction`
- удаляет transaction

### При обновлении savings transaction

Сервер:

- откатывает старый вклад в `current_amount`
- удаляет старый скрытый expense, если он был
- обновляет строку `savings_transactions` и её `updated_at`
- применяет новый вклад в `current_amount`
- при необходимости создаёт новый скрытый expense
- возвращает canonical row

Это важно, потому что sync здесь синхронизирует не только одну запись, но и связанный derived state.

## Backup / Restore и их влияние на sync

## Backup

`createBackup(userId)` сохраняет полные `SELECT *` snapshots для пользовательских данных, включая timestamp-поля.

В backup входят:

- categories
- income_sources
- savings_goals
- months
- incomes
- expenses
- budgets
- savings_transactions

## Restore

`POST /api/user/restore`:

- удаляет текущие пользовательские данные в корректном порядке
- очищает `deleted_records` для пользователя
- вставляет backup rows с сохранением исходных `id`
- восстанавливает `created_at` / `updated_at` с fallback-логикой
- отправляет websocket broadcast `restored`

Это важно для sync, потому что:

- backup/restore не теряет timestamp-метаданные
- восстановленные записи сохраняют server ids и связи
- после restore клиенты могут снова выровнять состояние без потери версии записи

## Реальное поведение ошибок

## 1. Ошибки очереди

Если upload падает:

- ошибка логируется
- `lastSyncError` становится видимым для UI
- элемент очереди получает `FAILED`
- `FAILED` под лимитом попыток переводится обратно в `pending` при следующем sync ИЛИ `scheduleProcessQueue` (после ближайшей мутации); исчерпавшие лимит ждут ручного ретрая из Настроек

## 2. Специальная обработка `404`

Если server write возвращает `404`:

- элемент не ретраится бесконечно
- он помечается `COMPLETED`
- ошибка сохраняется как информационная

Это защищает очередь от вечных циклов на уже несуществующих server records.

## 3. Debug visibility

В sync-слое есть подробные `println`-логи для:

- запуска/завершения sync
- queue processing
- tombstone application
- ошибок
- stuck-state reset

На desktop они дополнительно могут уходить в persistent debug log.

## Текущее состояние серверного API с точки зрения sync

Сейчас сервер уже поддерживает:

- canonical rows в write responses для основных sync-сущностей
- `created_at` / `updated_at` contract
- incremental tombstones через `since`
- idempotent write requests по `operationId`
- backup/restore с timestamp preservation

Это означает, что KMP client уже работает не только как локальная очередь, но как система с server-coordinated merge policy.

## Ограничения текущей реализации

Ниже не баги-деградации, а честные ограничения текущей версии.

### 1. Months sync остаётся специальным случаем

`MonthRepository.syncWithServer()` пока не делает полноценный remote merge.

Сейчас `month`:

- создаётся/находится через `ensureMonth`
- используется как инфраструктурная сущность для остальных month-bound данных

### 2. Download flow в основном list-based, а не fully incremental

Для большинства сущностей download идёт через обычные list endpoints, а не через общий incremental cursor по `updated_at`.

Incremental cursor сейчас реализован именно для tombstones (`deleted_records?since=...`).

### 3. Не все серверные delete responses являются canonical rows

Для `DELETE` endpoints сервер обычно возвращает служебный success/result, а не полную удалённую строку.

Для текущего sync это допустимо, потому что delete подтверждается:

- status code
- tombstone propagation
- локальным удалением/очисткой queue

### 4. Web client не использует весь этот merge stack

Сложная sync-логика относится прежде всего к KMP client.

Web client в основном продолжает жить в модели reload-after-mutation и от новых timestamp-полей только выигрывает за счёт additivity API.

## Итого

Текущая реализация синхронизации в FinKeeper уже состоит из следующих рабочих слоёв:

- offline-first локальная запись
- outbox/queue с merge логикой
- immediate upload после локальных изменений
- полный sync в порядке download → upload
- server timestamp aware merge policy
- upload-side canonical snapshot application
- server tombstones + client tombstone cursor
- parent/child guards против опасного применения delete
- server idempotency через `operationId`
- backup/restore с сохранением timestamp-метаданных

Именно эта комбинация сейчас образует **текущую production-логику синхронизации клиента и сервера**.

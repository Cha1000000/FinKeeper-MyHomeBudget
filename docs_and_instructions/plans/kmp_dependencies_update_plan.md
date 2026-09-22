# KMP: обновление зависимостей (после AGP 9.4.1)

Дата: 2026-09-22 · Модуль: `mobile_app_client/composeApp` · Порядок одобрен Володей

Версии «стабильная» взяты из `maven-metadata.xml` репозиториев (Maven Central, Google Maven,
Gradle Plugin Portal, VK artifactory), без alpha/beta/rc.

## Шаг 0. Перевести `composeApp/build.gradle` на каталог `libs.versions.toml`

Сейчас модуль каталог не использует: версии продублированы в `buildscript.ext` и строками
в `dependencies`, плагины Kotlin/SQLDelight — с жёсткими версиями. Для KMP ограничений нет
(каталог работает в `sourceSets { … dependencies { implementation(libs.x) } }` и для
`plugins { alias(libs.plugins.x) }`). Делаю **без смены версий**: сверяю, что граф зависимостей
до и после совпадает (`:composeApp:dependencies` для android/desktop), сборка + тесты. Дальше
каждый бамп — одна строка в каталоге.
Если что-то окажется рискованным (например, Groovy-DSL + `alias` в `plugins`) — оставляю
как есть и сообщаю.

## Шаги бампа (каждый — отдельный коммит)

| # | Что | Сейчас → цель | Риск |
|---|---|---|---|
| 1 | coroutines, serialization, activity-compose, RuStore appupdate, security-crypto (с alpha на релиз), multiplatform-settings, credential-secure-storage | см. таблицу в чате | низкий, пачкой |
| 2 | Kotlin + плагины compose-compiler/serialization | 2.2.10 → 2.4.20 | высокий |
| 3 | Compose Multiplatform + material3 + lifecycle (JB/androidx) | 1.6.10 → 1.12.1, lifecycle → 2.11.0 | высокий |
| 4 | Koin (+ koin-compose с Beta4) | 3.5.6 → 4.2.2 | высокий, мажорная |
| 5 | Ktor | 3.0.3 → 3.6.0 | средний |
| 6 | SQLDelight | 2.0.2 → 2.4.0 | средний |
| 7 | kotlinx-datetime | 0.6.1 → 0.7+/0.8 | высокий: `Instant`/`Clock` → `kotlin.time` |
| 8 | reorderable | 2.3.3 → 3.1.0 | средний, мажорная |

Совместимость (Kotlin ↔ CMP ↔ AGP, CMP ↔ material3/lifecycle) проверяю по документации
перед каждым шагом, а не по памяти. Если цель несовместима — беру максимальную совместимую
и пишу почему.

## Проверка после каждого шага

1. `assembleDebug` (Android) + `compileKotlinDesktop` + `testDebugUnitTest` + `desktopTest`.
2. Предупреждения компиляции — новые deprecation фиксирую, критичные чиню сразу.
3. После рискованных шагов (2, 3, 4, 6, 7, 8) — запуск на **эмуляторе** (debug-сборка,
   `DEFAULT_SERVER_URL=http://10.0.2.2:3002`, локальный сервер с временной БД — не боевой):
   логин, «Обзор», «Месяц» (плашка лимита + подсказка, диалог лимитов, drag-and-drop категорий
   для шага 8), «Копилки», «Категории», тёмная тема. + десктоп в песочнице
   (`FINKEEPER_USER_HOME`) для шагов 3 и 6.
4. Для шага 6 — отдельно миграция/чтение существующей БД (песочная БД с данными).

Поломки чиню в рамках шага. Если шаг требует переписывать много кода (например, datetime) —
останавливаюсь и описываю объём, прежде чем продолжать.

## Вне рамок (заметки)

- `network_security_config.xml` разрешает HTTP и в релизе — отдельная задача безопасности.
- Deprecation Gradle 10 и `AlertDialog` → `BasicAlertDialog` — после бампов, по результатам.
- Пуш — в конце, после проверки Володей.

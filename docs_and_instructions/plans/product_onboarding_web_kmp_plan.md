# Product Onboarding Plan (Web + KMP)

## Цель

Закрыть `P2-04` по onboarding как отдельный продуктовый слой:

- дать пользователю понятный первый путь после входа,
- объяснить ключевые риски/действия (защита аккаунта, recovery email, backup/sync),
- сделать это единообразно для Web и KMP без изменения серверных контрактов.

## Базовые ограничения и допущения

- Серверный контракт уже содержит нужные поля (`recoverabilityStatus`, `canSelfRecover`, `recoveryEmail*`).
- Security/recovery блок в `Settings` уже реализован в Web и KMP.
- Onboarding должен быть неблокирующим: пользователь может пропустить и продолжить работу.
- Никаких изменений в финансовой доменной логике (income/expense/budget/savings) в рамках onboarding.

## Что считаем onboarding в рамках этого плана

1. Post-login security prompt для `unprotected` аккаунта.
2. Первичный guidance-блок с короткими action-ориентированными подсказками.
3. Повторное мягкое напоминание (без спама) до момента защиты аккаунта.
4. Единая логика видимости и частоты показа в Web и KMP.

## Не входит в scope

- Новый обязательный registration wizard.
- Жёсткая блокировка приложения до добавления email.
- Изменения OAuth backend contract.
- Полноценная in-app analytics/telemetry платформа (только точки интеграции, если нужны).

## UX-принципы

- Ясность: объясняем риск потери доступа простым языком.
- Неблокирующий поток: всегда есть `Позже` или `Пропустить`.
- Action-first: CTA ведёт сразу в `Settings -> Security`.
- Низкая навязчивость: показываем редко и контекстно.
- Консистентность: одинаковая смысловая модель в Web и KMP.

## Канонические состояния

### S1. Protected

- Условие: `recoverabilityStatus === 'protected'`.
- Поведение: onboarding prompt не показывается.

### S2. Unprotected (new session)

- Условие: `recoverabilityStatus === 'unprotected'` после успешного входа.
- Поведение: показываем основной onboarding prompt 1 раз за сессию.

### S3. Unprotected (returning user)

- Условие: аккаунт всё ещё unprotected, пользователь ранее уже закрывал prompt.
- Поведение: показываем мягкое напоминание по cooldown (рекомендуемо не чаще 1 раза в 7 дней).

## Реализация по платформам

## Web

### Web: поверхности

1. Post-login banner/card на главном защищённом экране (`Layout`/`Dashboard` контур).
2. CTA: `Добавить email` -> переход на `/settings`.
3. Secondary action: `Позже`.

### Web: хранение локального состояния показа

- `localStorage` ключ, например: `onboarding_security_prompt_v1`.
- Структура: `lastShownAt`, `dismissCount`, `lastStatus`.

### Web: поведение

- Если `protected`: удаляем или сбрасываем локальный onboarding state.
- Если `unprotected` и cooldown истёк: показываем prompt.
- После dismiss: записываем `lastShownAt`, увеличиваем `dismissCount`.

## KMP (Android/iOS/Desktop)

### KMP: поверхности

1. Неблокирующий onboarding card/dialog после успешного входа на первом основном экране.
2. CTA: `Перейти в настройки`.
3. Secondary action: `Позже`.

### KMP: хранение локального состояния показа

- Использовать существующее локальное settings/storage хранилище.
- Аналогичные поля: `lastShownAt`, `dismissCount`, `lastStatus`.

### KMP: поведение

- Та же state-модель (`protected`/`unprotected`) и тот же cooldown policy.
- При переходе в `protected` локальный onboarding state сбрасывается.

## Фазы внедрения

### Phase A - Core onboarding prompt (MVP)

- Web: добавить post-login prompt для `unprotected`.
- KMP: добавить post-login prompt для `unprotected`.
- Единый набор текстов и CTA.

Готово, если:

- prompt показывается только `unprotected` пользователям,
- переход в `Settings` работает,
- protected пользователи не видят prompt.

### Phase B - Frequency control и anti-spam

- Добавить cooldown policy (рекомендация: 7 дней).
- Добавить «не чаще 1 раза за сессию».
- Сброс state при переходе аккаунта в protected.

Готово, если:

- prompt не раздражает повторными показами,
- при защите аккаунта prompt исчезает полностью.

### Phase C - UX polish

- Уточнить copy и визуальные состояния.
- Добавить контекстный reminder в безопасных зонах интерфейса (не на каждом экране).
- Проверить локализацию и консистентность формулировок Web/KMP.

Готово, если:

- onboarding воспринимается как полезный, а не навязчивый,
- тексты и сценарии совпадают по смыслу на Web и KMP.

## Декомпозиция реализации (Web-first -> KMP)

### Wave 1 (P0) - Web MVP

Цель: сначала закрыть onboarding value на Web и зафиксировать рабочий эталон поведения.

1. `P0-WEB-01` Добавить post-login onboarding prompt для `unprotected` аккаунта.
2. `P0-WEB-02` Добавить CTA `Добавить email` с переходом в `/settings` (security block).
3. `P0-WEB-03` Добавить action `Позже` с закрытием prompt без блокировки сценария.
4. `P0-WEB-04` Ограничить показ prompt: не чаще 1 раза за сессию.

Гейт перехода к следующей wave:

- prompt стабильно показывается только для `unprotected`,
- переход в `Settings` и dismiss работают,
- отсутствует блокировка основного пользовательского flow.

### Wave 2 (P1) - Web anti-spam + polish

1. `P1-WEB-01` Добавить cooldown хранения показа (рекомендация: 7 дней).
2. `P1-WEB-02` Сбрасывать onboarding state при переходе аккаунта в `protected`.
3. `P1-WEB-03` Выровнять копирайт prompt и helper-тексты c recovery UX формулировками.
4. `P1-WEB-04` Добавить мягкий reminder в безопасной зоне (без дублирования на каждом экране).

Гейт перехода к KMP:

- Web-сценарий завершён end-to-end,
- UX и частота показа подтверждены ручным smoke,
- тексты согласованы с `account-recoverability-ux-plan.md`.

### Wave 3 (P1) - KMP parity (после Web)

1. `P1-KMP-01` Добавить аналогичный post-login prompt/card/dialog для `unprotected`.
2. `P1-KMP-02` Добавить CTA перехода в `Settings` (security/recovery section).
3. `P1-KMP-03` Добавить action `Позже` и неблокирующее закрытие.
4. `P1-KMP-04` Ограничить показ: 1 раз за сессию + cooldown policy, как в Web.
5. `P1-KMP-05` Сбрасывать onboarding state при `protected`.

Гейт перехода к финальной волне:

- логика KMP функционально эквивалентна Web,
- ключевые сценарии повторены на KMP smoke-проверке.

### Wave 4 (P2) - Cross-platform унификация и финализация

1. `P2-XP-01` Проверить единый смысл текстов и CTA между Web и KMP.
2. `P2-XP-02` Зафиксировать финальные UX-правила показа onboarding в документации.
3. `P2-XP-03` Обновить Batch 14 статус после завершения фактической реализации.

**Фактический статус Wave 4:**

- `P2-XP-01` выполнен: тексты и CTA подтверждены в Web и KMP Android demo (`Добавьте email`, `Позже`, `Перейти в настройки`).
- `P2-XP-02` выполнен: финальные правила показа закреплены в текущем документе и реализованы в обеих клиентских ветках.
- `P2-XP-03` выполнен: Batch 14 должен отражать завершённую onboarding-реализацию как часть более широкого долгосрочного product batch.

## Итоговый порядок выполнения

1. `P0-WEB-*`
2. `P1-WEB-*`
3. `P1-KMP-*`
4. `P2-XP-*`

Этот порядок обязателен для текущего цикла: сначала доводим Web до стабильного эталона, затем переносим ту же модель в KMP.

## Критерии готовности (Definition of Done)

- Есть отдельный onboarding flow для `unprotected` аккаунтов в Web и KMP.
- Flow неблокирующий и имеет управляемую частоту показа.
- Есть прямой путь к настройке recovery email.
- После перехода в `protected` onboarding prompt больше не показывается.
- Smoke-сценарии ручной проверки выполнены для обеих платформенных веток.

## Минимальный checklist проверки

1. Новый `unprotected` пользователь логинится и видит prompt.
2. Нажимает `Позже` и prompt скрывается, не всплывая повторно в той же сессии.
3. Повторный вход до истечения cooldown не показывает prompt.
4. Переход в `Settings`, добавление+подтверждение email -> статус становится `protected`.
5. Следующий вход с `protected` статусом -> prompt не появляется.
6. Те же сценарии повторены в KMP клиенте.

## Связанные документы

- `docs_and_instructions/production_prepare_implementation_plan.md` (Batch 14)
- `docs_and_instructions/account-recoverability-ux-plan.md`
- `docs_and_instructions/adr/0001-email-based-account-recovery.md`
- `docs_and_instructions/adr/0003-social-login-strategy.md`

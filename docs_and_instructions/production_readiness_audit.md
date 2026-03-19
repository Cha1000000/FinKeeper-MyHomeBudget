# 🔍 Production Readiness Audit — FinKeeper (v2, обновлён)

> **Дата**: текущая сессия  
> **Роли**: Analyst + Reviewer (Quality + Security)  
> **Проверяемые артефакты**: `server/`, `client/`, `mobile_app_client/`  
> **Базовый документ**: [production_prepare_implementation_plan.md](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/docs_and_instructions/production_prepare_implementation_plan.md)

---

## 📊 Executive Summary

| Область | Статус | Комментарий |
|---------|--------|-------------|
| **P0 — Security hardening** | ✅ Выполнено | JWT secret обязателен, helmet, CORS, rate limiting, body limits |
| **P0 — Auth/session** | ✅ Выполнено | Refresh tokens, rotation, httpOnly cookies, dual transport |
| **P0 — Token storage (Web)** | ✅ Выполнено | Session/memory-first, TTL-проверка, remember me → localStorage |
| **P0 — Token storage (KMP)** | ✅ Выполнено | SecureTokenStorage interface + EncryptedSharedPreferences / Keychain / Credential Manager |
| **P0 — Server URL restriction (KMP)** | ✅ Выполнено | `BuildConfig.SHOW_SERVER_SETTINGS = false` в production |
| **P0 — Destructive ops UX** | ✅ Выполнено | Confirmation text для restore, rate limiting |
| **P1 — Input validation** | ✅ Выполнено | [authValidation.js](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/validation/authValidation.js) с полной валидацией всех auth-полей |
| **P1 — Structured logging** | ✅ Выполнено | JSON logger с уровнями, event-based, serialization ошибок |
| **P1 — Health check** | ✅ Выполнено | `/api/health` — DB/schema/mail/uptime проверки |
| **P1 — Smoke test** | ✅ Выполнено | [scripts/smoke-health.js](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/scripts/smoke-health.js) с isolated DB |
| **P2 — Account recovery** | ✅ Выполнено | Email-based recovery, verification, ADR-0001 |
| **P2 — Social login** | ✅ Выполнено | Google + Yandex, native KMP flow, exchange codes, ADR-0003 |
| **P2 — Onboarding** | ✅ Выполнено | Security prompt с cooldown и dismiss logic |
| **Observability (Batch 13)** | ⚠️ Частично | Health check есть, но нет metrics endpoint и алертинга |

> [!IMPORTANT]
> Проект **готов к production-развёртыванию** для ограниченной аудитории/закрытой бета. Ниже перечислены конкретные находки, нюансы и рекомендации.

---

## ✅ Подтверждённые реализации (по батчам плана)

### Batch 1–2: Critical Security Fixes
- ✅ **JWT_SECRET обязателен** — [index.js:138-140](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L138-L140): `throw new Error('JWT_SECRET environment variable is required')`. Hardcoded fallback **удалён**.
- ✅ **Helmet** установлен и настроен — [index.js:467-474](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L467-L474): CSP, `x-powered-by` отключён.
- ✅ **CORS ограничен** — [index.js:475-484](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L475-L484): `allowedOrigins` из env, в production обязателен `ALLOWED_ORIGINS`.
- ✅ **JSON body limit** — [index.js:485](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L485): `1mb` по умолчанию, настраиваемый.
- ✅ **Rate limiting** — 6 отдельных rate limiter'ов для auth, password change, restore, email verification, password recovery request/confirm.

### Batch 3–4: Auth & Session (Server + Web)
- ✅ **Short-lived access tokens** — [index.js:31](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L31): TTL 60 минут по умолчанию.
- ✅ **Refresh tokens в httpOnly cookies** — [index.js:791-798](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L791-L798): `secure` в production, `sameSite: lax`, `path: /api/auth`.
- ✅ **Token rotation** — [index.js:2183-2186](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L2183-L2186): старый session ревокается при каждом refresh.
- ✅ **Dual transport** — `X-Refresh-Transport: body` для KMP, cookie для Web.
- ✅ **Token storage (Web)** — [tokenStorage.ts](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/client/src/auth/tokenStorage.ts): memory-first → sessionStorage → conditional localStorage. TTL-проверка через JWT exp.
- ✅ **Session recovery (Web)** — [sessionRecovery.ts](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/client/src/auth/sessionRecovery.ts): refresh с singleton promise.
- ✅ **401/403 interceptor** — [api/index.ts:62-126](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/client/src/api/index.ts#L62-L126): auto-refresh с queue, force logout при failure.
- ✅ **AuthNotice** — session-expired notice через sessionStorage.

### Batch 5–6: Auth & Session (KMP)
- ✅ **SecureTokenStorage interface** — [SecureTokenStorage.kt](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/remote/SecureTokenStorage.kt)
- ✅ **Android** — [AndroidSecureTokenStorage.kt](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/mobile_app_client/composeApp/src/androidMain/kotlin/ru/homebudget/finkeeper/data/remote/AndroidSecureTokenStorage.kt): EncryptedSharedPreferences с AES-256-GCM.
- ✅ **iOS** — [IosSecureTokenStorage.kt](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/mobile_app_client/composeApp/src/iosMain/kotlin/ru/homebudget/finkeeper/data/remote/IosSecureTokenStorage.kt): Keychain через `KeychainSettings`.
- ✅ **Desktop** — [DesktopSecureTokenStorage.kt](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/mobile_app_client/composeApp/src/desktopMain/kotlin/ru/homebudget/finkeeper/data/remote/DesktopSecureTokenStorage.kt): Microsoft Credential Manager + in-memory fallback.
- ✅ **Legacy token migration** — [TokenStorage.kt:30-33](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/remote/TokenStorage.kt#L30-L33): при чтении из Settings — мигрирует в secure storage.
- ✅ **Server URL restriction** — [TokenStorage.kt:65-77](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/remote/TokenStorage.kt#L65-L77): `BuildConfig.SHOW_SERVER_SETTINGS` контролирует доступ.
- ✅ **Auth retry interceptor (KMP)** — [ApiClient.kt:51-63](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/data/remote/ApiClient.kt#L51-L63): mutex-protected refresh.

### Batch 7–8: Server Hardening
- ✅ **Request ID tracking** — [index.js:486-507](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L486-L507): UUID, HTTP request logging.
- ✅ **Structured logging** — [logger.js](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/logger.js): JSON output, error serialization.
- ✅ **Centralized error handler** — [index.js:3716-3730](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L3716-L3730): unhandled errors → 500 generic response + logging.
- ✅ **Validation layer** — [authValidation.js](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/validation/authValidation.js): factory-based, pure validation + length limits.
- ✅ **Mailer** — [mailer.js](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/mailer.js): lazy, configurable, with HTML escaping.

### Batch 9–10: Account Recovery & Email Verification
- ✅ **Password recovery** — full flow: request → email → token → confirm → revoke all sessions.
- ✅ **Email verification** — full flow: set email → send verification → confirm → update `email_confirmed_at`.
- ✅ **Recoverability status** — `protected` / `unprotected` в [buildUserPayload](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#1014-1039).
- ✅ **Recovery info resolution** — account email → social provider email fallback.

### Batch 11–12: Social Login
- ✅ **Google + Yandex OAuth** — full server implementations with token exchange, userinfo fetch.
- ✅ **Web flow** — OAuth state cookie, redirect to frontend callback.
- ✅ **Native (KMP) flow** — attempt token polling, exchange codes, completion HTML pages.
- ✅ **Social user creation** — auto-generated display username, disabled password, identity linking.
- ✅ **Username sync** — [shouldRefreshSocialUsername](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#1463-1480) logic for keeping display names fresh.
- ✅ **Auth identities table** — `auth_identities` with provider, email, verified status.

### Batch 14: Onboarding & Recovery UX
- ✅ **Security onboarding prompt** — [securityPrompt.ts](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/client/src/onboarding/securityPrompt.ts): 7-day cooldown, per-session flag, dismiss counter.
- ✅ **Web routes** — `/password-recovery`, `/verify-email`, `/auth/*/callback`.

### Batch 13: Observability (Partial)
- ✅ **Health check endpoint** — `/api/health` with DB, schema, mail, env checks.
- ✅ **Smoke test** — [scripts/smoke-health.js](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/scripts/smoke-health.js) — isolated DB, auto-cleanup.
- ⚠️ **Metrics/monitoring** — нет Prometheus endpoint или structured metrics.

---

## ⚠️ Находки и рекомендации

### ~~🔴 Высокий приоритет~~ → ✅ Исправлено

#### 1. ~~WebSocket auth — нет проверки типа токена~~ → ✅ ИСПРАВЛЕНО

**Файл**: [index.js:3748-3751](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L3748-L3751)

Добавлена проверка `decoded.type !== 'access'` — теперь WebSocket отклоняет подключения с refresh/другими JWT, аналогично REST middleware.

#### 2. ~~Rate limiter — in-memory, без cleanup~~ → ✅ ИСПРАВЛЕНО

**Файл**: [index.js:2069-2081](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L2069-L2081)

Добавлен `setInterval` (60 сек), который очищает просроченные bucket'ы из `rateLimitBuckets`. Пустые ключи удаляются из Map.

#### ~~3. Отсутствие `.env.example`~~ → ✅ Уже есть

Файлы [.env.local.example](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/.env.local.example), [.env.production.example](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/.env.production.example), [.env.remote.example](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/.env.remote.example) присутствуют в корне проекта.

### 🟡 Средний приоритет

#### 4. [index.js](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js) — монолитный файл (3800 строк)

Весь серверный код — один файл. Для поддержки и development experience рекомендуется:
- Вынести маршруты в `routes/auth.js`, `routes/user.js`, `routes/data.js`
- Вынести бизнес-логику в `services/`
- Вынести DB-helpers в `db/helpers.js`

> [!NOTE]
> Это **не блокирует production**, но значительно усложняет code review, diff'ы и onboarding новых контрибьюторов.

#### 5. Нет graceful shutdown

При `SIGTERM` (PM2 restart, deploy) нет корректного закрытия DB и WebSocket connections. Рекомендация:

```javascript
process.on('SIGTERM', () => {
  server.close(() => {
    wss.close(() => {
      db.close();
      process.exit(0);
    });
  });
});
```

#### 6. Нет CSRF-защиты для cookie-based refresh

Refresh token передаётся через httpOnly cookie с `sameSite: lax`. Для POST-запросов это даёт базовую защиту, но для полной безопасности стоит рассмотреть double-submit cookie pattern или проверку `Origin` header.

> [!NOTE]
> `sameSite: lax` **не отправляет cookies** при cross-origin POST (только top-level navigation), поэтому это уже достаточная защита для текущего использования. Дополнительная CSRF-защита нужна только при усложнении модели угроз.

#### 7. Приложение (KMP) BuildConfig содержит IP-адрес сервера

**Файл**: [BuildConfig.kt:5](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/mobile_app_client/composeApp/build/generated/buildconfig/commonMain/kotlin/ru/homebudget/finkeeper/BuildConfig.kt#L5)

```kotlin
const val DEFAULT_SERVER_URL = "http://217.114.8.82:3002"
```

> [!TIP]
> В production-билде лучше использовать domain: `https://finkeeper.ru`. Это уже настроено (SHOW_SERVER_SETTINGS=false), но сам URL всё ещё на IP без HTTPS.

#### 8. Smoke test покрывает только health check

Скрипт [smoke-health.js](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/scripts/smoke-health.js) проверяет только `/api/health`. Для production confidence стоит добавить:
- Register → Login → Refresh → Logout flow
- Basic CRUD проверку (create category → get → delete)

### 🟢 Низкий приоритет (nice-to-have)

#### 9. Password hash rounds = 8

[index.js:2117](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L2117): `bcrypt.hashSync(password, 8)` — 8 rounds. Обычная рекомендация — минимум 10-12 для production. При 8 rounds хеширование быстрее, что даёт преимущество brute-force.

> [!TIP]
> Для текущего масштаба (личное использование, rate-limited endpoints) это допустимо. При росте можно увеличить до 10+ и добавить migration для существующих хешей при следующем логине.

#### 10. Нет Content-Security-Policy report-uri

Helmet CSP настроен, но без `report-uri` — нарушения CSP не логируются. Для production-мониторинга полезно добавить.

#### 11. Token debug preview в development

[index.js:1865-1874](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js#L1865-L1874): `authDebugTokenPreviewEnabled` — корректно отключается в production (`!isProduction`). ✅ Безопасно.

#### 12. Нет automated E2E тестов

Web-клиент не имеет test runner (package.json placeholder). Для уверенности в regression-ах рекомендуется Playwright/Vitest.

---

## 📋 Чеклист готовности к development

| Проверка | Статус |
|----------|--------|
| JWT secret обязателен | ✅ |
| CORS ограничен | ✅ |
| Rate limiting на auth endpoints | ✅ |
| Helmet middleware | ✅ |
| Body size limit | ✅ |
| Access token TTL ≤ 60 мин | ✅ |
| Refresh token в httpOnly cookie | ✅ |
| Token rotation при refresh | ✅ |
| KMP: secure token storage | ✅ |
| KMP: server URL скрыт в production | ✅ |
| Input validation | ✅ |
| Structured logging | ✅ |
| Health check endpoint | ✅ |
| Smoke test | ✅ |
| Email-based recovery | ✅ |
| Social login (Google, Yandex) | ✅ |
| Security onboarding prompt | ✅ |
| Error handler (global) | ✅ |
| Protected routes (Web) | ✅ |
| Auth retry/refresh interceptor | ✅ |

## 📋 Чеклист «до широкого публичного релиза»

| Проверка | Статус | Приоритет |
|----------|--------|-----------|
| WebSocket — проверка типа токена | ✅ Исправлено | ~🔴~ |
| Rate limiter — cleanup expired | ✅ Исправлено | ~🔴~ |
| `.env.example` файлы | ✅ Уже есть | ~🟡~ |
| Graceful shutdown | ❌ | 🟡 |
| BuildConfig: HTTPS domain | ⚠️ | 🟡 |
| Расширенные smoke/integration тесты | ❌ | 🟡 |
| Refactoring [index.js](file:///Users/racerkafa/Documents/MyProjects/Web/My%20Home%20Budget/server/index.js) | ❌ | 🟢 |
| Bcrypt rounds ≥ 10 | ⚠️ | 🟢 |
| CSP report-uri | ❌ | 🟢 |
| E2E тесты | ❌ | 🟢 |
| Metrics/monitoring endpoint | ❌ | 🟢 |

---

## 🎯 Вердикт

> [!IMPORTANT]
> **FinKeeper готов к production-развёртыванию** для целевого сценария: персональное/семейное использование, закрытая бета, ограниченная аудитория.
>
> Все **P0-задачи** (критические security fixes) из implementation plan **выполнены корректно**. Архитектура auth/session, security hardening и recovery flows реализованы на хорошем уровне.
>
> **Обновление**: оба красных пункта (WebSocket auth check, rate limiter cleanup) и пункт .env.example **закрыты**. Оставшиеся рекомендации — жёлтого/зелёного приоритета (graceful shutdown, расширенные тесты, рефакторинг).

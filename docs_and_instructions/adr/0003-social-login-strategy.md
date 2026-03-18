# ADR-0003: Social login strategy for Google, Yandex, and optional Mail.ru

## Status

Accepted

## Context

FinKeeper currently authenticates users through a local `username + password` model.

The current auth architecture already includes:

- server-issued access token + refresh session flow
- Web `AuthContext` and session-based token persistence
- KMP auth flows using `AuthViewModel`, `ApiClient`, and secure token storage
- optional confirmed email for account recovery

However, there is no federated identity layer yet:

- `users` are modeled as local accounts
- `users.password_hash` is required
- auth endpoints are centered around `/api/auth/login` and `/api/auth/register`
- there is no identity-linking table for external providers

The product roadmap explicitly calls out a future evolution of auth UX with social login for `Google`, `Яндекс`, and possibly `Mail.ru`.

Because FinKeeper spans Web and KMP clients, the solution must:

- work consistently across server, Web, and KMP
- avoid provider-specific auth fragmentation inside clients
- preserve existing local accounts and recovery flows
- avoid unsafe account merges and provider-driven lock-in

## Decision Drivers

- **Must preserve existing local auth** for current users
- **Must support Web and KMP consistently**
- **Must keep server authoritative** for session issuance
- **Must not trust raw client-only provider identity assertions without server validation**
- **Must allow a user to have multiple auth methods over time**
- **Must minimize accidental account takeover or unsafe auto-linking**
- **Should prioritize providers with the strongest product value and implementation maturity**
- **Should support phased rollout instead of a large auth rewrite in one batch**

## Considered Options

### Option 1: Keep only local `username + password`

- **Pros**:
  - no schema change
  - no OAuth/OIDC operational complexity
  - no provider dependencies
- **Cons**:
  - weaker onboarding convenience
  - no lower-friction sign-in path for new users
  - misses the roadmap direction for auth UX evolution

### Option 2: Implement provider-specific login separately in each client

- **Pros**:
  - clients can use native SDKs directly
  - potentially faster first prototype on one platform
- **Cons**:
  - inconsistent Web vs KMP behavior
  - duplicated auth logic across clients
  - harder backend trust model
  - higher long-term maintenance cost

### Option 3: Backend-mediated social login with provider identity binding table

- **Pros**:
  - server remains the single issuer of FinKeeper sessions
  - unified strategy for Web and KMP
  - supports multiple providers per user
  - creates a clean migration path from local-only auth
  - avoids pushing sensitive provider logic into every client
- **Cons**:
  - requires schema changes and new auth endpoints
  - requires callback/deep-link coordination
  - introduces OAuth provider setup and operational burden

### Option 4: Add all providers in the first wave (`Google`, `Яндекс`, `Mail.ru`)

- **Pros**:
  - maximum provider coverage immediately
- **Cons**:
  - materially larger rollout risk
  - more configuration, testing, and failure modes
  - weaker focus on the most valuable providers first

## Decision

FinKeeper will adopt **backend-mediated social login with provider identity binding**, rolled out in phases.

The strategic decisions are:

- **Google** will be the first-class provider in wave 1
- **Yandex** will be the second provider in wave 1 or wave 1.5, depending on implementation bandwidth
- **Mail.ru** will remain **optional and later-wave only**, not part of the first mandatory rollout
- the server will remain the **only authority that creates FinKeeper auth sessions**
- local auth will remain supported; social login is an additional auth method, not a replacement
- external identities will be stored in a dedicated identity-binding table rather than overloaded into `users`
- initial unauthenticated social sign-in will **not automatically merge by email** into an existing local account
- linking a provider to an existing FinKeeper account should be an **explicit authenticated user action** in a later phase

## Provider Priority

### 1. Google

Google is the best first provider because it has:

- the most mature documentation and ecosystem
- strong OIDC support
- straightforward backend token verification patterns
- high user familiarity across Web and mobile

### 2. Yandex

Yandex is strategically important for FinKeeper because the product is Russian-first.

It is a strong second provider because:

- it is relevant for the target audience
- it supports OAuth flows with PKCE
- it can complement Google for users who prefer local ecosystem identities

### 3. Mail.ru

Mail.ru should be treated as **optional** and only added if justified by real demand.

Reasons:

- lower immediate product leverage than Google + Yandex together
- additional integration and support burden
- weaker justification for first-wave complexity
- the current ecosystem path appears more operationally awkward than the first two providers

## Auth Architecture Direction

### Core Principle

External providers authenticate the user to the provider.

FinKeeper does **not** delegate its application session model to those providers. Instead:

1. The user completes provider authentication
2. The server validates the provider result
3. The server resolves or creates the FinKeeper user
4. The server issues the standard FinKeeper auth session
   - access token
   - refresh session / refresh token transport

This keeps the existing session architecture compatible with both local and social login.

### Required Data Model Changes

#### 1. `users`

`users` should continue to represent the canonical FinKeeper account.

Recommended change:

- make `password_hash` nullable so that social-only accounts are represented cleanly

`username` should remain a FinKeeper-level profile identifier, not a provider identity.

#### 2. `auth_identities`

Add a dedicated table such as:

- `id`
- `user_id`
- `provider` (`google`, `yandex`, `mailru`)
- `provider_user_id`
- `provider_email`
- `provider_email_verified`
- `created_at`
- `last_login_at`
- optional provider metadata fields if truly needed later

Constraints:

- unique `(provider, provider_user_id)`
- foreign key to `users(id)`

This becomes the canonical mapping between provider accounts and FinKeeper users.

#### 3. `auth_login_exchange_codes`

Add a one-time short-lived exchange-code table for callback handoff between server and clients.

Purpose:

- avoid putting FinKeeper access tokens in URL query parameters
- support both Web callback completion and KMP deep-link completion
- keep backend authoritative

Suggested fields:

- `id`
- `code_hash`
- `user_id`
- `provider`
- `expires_at`
- `created_at`
- `used_at`
- optional client type marker

## Login and Linking Rules

### Unauthenticated social sign-in

When the user is not already signed in:

1. If `(provider, provider_user_id)` already exists:
   - sign in that FinKeeper account
2. If no identity exists:
   - create a new FinKeeper user
   - create an `auth_identities` row
   - issue a normal FinKeeper session

### Existing local accounts

For the first strategy version:

- do **not** auto-link a social identity to an existing local account solely by matching email during unauthenticated sign-in

Reason:

- automatic email-based linking increases account-takeover and account-confusion risk
- it is safer to require explicit linking from inside an already authenticated account

### Explicit provider linking

A later phase should support:

- user logs into FinKeeper with existing local credentials
- user opens Settings / Security / Connected accounts
- user explicitly connects Google or Yandex
- server attaches the provider identity to the existing `user_id`

This is the preferred path for existing accounts that want social login without duplicate account creation.

## Username Strategy

Social providers should not become the canonical username authority.

Recommended approach:

- FinKeeper continues to require an internal `username`
- on first social account creation, create a provisional unique username
- after first sign-in, prompt the user to review or change it to a preferred final value

This keeps product identity stable even if provider profile names change.

## Web Flow Direction

Web should use a backend-mediated authorization flow:

1. User clicks `Continue with Google` or `Continue with Yandex`
2. Frontend opens a backend start endpoint such as `/api/auth/oauth/:provider/start`
3. Server prepares provider auth request
4. User authenticates with provider
5. Provider redirects to backend callback
6. Backend validates provider response and resolves FinKeeper user
7. Backend creates a one-time exchange code
8. Backend redirects to frontend callback route with that exchange code
9. Frontend exchanges the one-time code for standard `AuthData`
10. Frontend stores session normally via existing auth model

This avoids placing long-lived application tokens in browser URL parameters.

## KMP Flow Direction

KMP should use the same backend-mediated strategy with platform-appropriate shell behavior:

1. App starts auth in external system browser / custom tab
2. Flow goes through backend start endpoint
3. Provider redirects to backend callback
4. Backend validates provider response and creates a one-time exchange code
5. Backend redirects to app deep link
6. App exchanges the code for standard FinKeeper `AuthData`
7. App persists access token / refresh token through existing KMP auth storage

This keeps provider auth outside the embedded app UI and avoids duplicating provider session logic in KMP clients.

## Security Rules

- use authorization code flow with PKCE where applicable
- validate provider identity server-side before issuing FinKeeper session
- never expose FinKeeper access tokens directly in redirect URLs
- one-time exchange codes must be short-lived and single-use
- social auth start/callback/exchange endpoints must have rate limiting and structured logs
- store only the minimum provider identity data needed for account binding
- explicit provider linking must require an authenticated FinKeeper session
- unlinking the last usable auth method must be protected from accidental self-lockout

## Consequences

### Positive

- consistent social auth architecture across Web and KMP
- server remains the single session authority
- clean coexistence of local and social auth
- safe path for future account-linking UX
- rollout can start with the highest-value providers first

### Negative

- requires schema changes and new auth endpoints
- social-only accounts complicate current `password_hash NOT NULL` model
- no automatic email-based merge means some users may create duplicate accounts before linking exists
- KMP rollout depends on deep-link and browser-hand-off quality

## Rollout Plan

### Phase 1 — architecture foundation

- add `auth_identities`
- make `users.password_hash` nullable or otherwise cleanly support social-only accounts
- add one-time auth exchange-code mechanism
- add provider config layer in server env

### Phase 2 — Google on Web

- add Google start/callback/exchange flow
- add Google sign-in button to Web login screen
- validate session creation and rollback behavior

### Phase 3 — Yandex on Web

- add Yandex provider using the same backend pattern
- reuse the same callback/exchange architecture

### Phase 4 — KMP social login

- implement browser + deep-link completion flow
- support Google first, then Yandex

### Phase 5 — explicit provider linking

- add `Connected accounts` UX in Settings
- support linking a provider to an existing local account
- support safe unlink rules

### Phase 6 — Mail.ru decision gate

Only proceed if real demand justifies it.

Decision gate inputs:

- user demand from target audience
- provider reliability and operational clarity
- acceptable added maintenance cost

## Explicit Non-Goals for This ADR

- implementing social login immediately in this ADR
- replacing local auth with provider-only auth
- automatic merge of existing local accounts by email during initial sign-in
- deciding exact button copy and final UI layout
- choosing Mail.ru as a first-wave mandatory provider

## Related Future Work

- Settings UX for linked providers
- session model alignment between local and social auth
- possible future rule requiring at least one recovery-capable auth method
- long-term re-evaluation of whether email should become mandatory for newly created accounts

## References

- `docs_and_instructions/production_prepare_implementation_plan.md`
- `docs_and_instructions/adr/0001-email-based-account-recovery.md`
- Google Identity / OIDC server-side verification documentation
- Yandex ID OAuth documentation with PKCE
- Mail.ru OAuth / VK ID business documentation

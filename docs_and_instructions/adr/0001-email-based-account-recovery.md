# ADR-0001: Email-based account recovery with confirmed email requirement

## Status

Accepted

## Context

FinKeeper currently supports authentication with `username + password` but has no recovery path for forgotten credentials.

This creates a product and security gap:

- users can permanently lose access to their account
- the product has no safe self-service recovery mechanism
- a `username` alone is not a reliable recovery factor
- ad hoc manual recovery does not scale and introduces security risk

The project supports Web, Android/iOS/Desktop KMP clients, so the recovery model must be understandable and implementable consistently across all clients.

## Decision Drivers

- **Must provide a realistic self-service recovery path** for forgotten password cases
- **Must not rely on `username` as a recovery identifier**
- **Must avoid user enumeration** in recovery endpoints and UX
- **Must work consistently for Web and KMP clients**
- **Should minimize operational/manual support burden**
- **Should allow phased rollout without breaking current auth flows**

## Considered Options

### Option 1: Recovery by `username`

- **Pros**:
  - no extra account field required
  - minimal schema change
- **Cons**:
  - `username` is not a trustworthy recovery factor
  - easy to enumerate or guess
  - cannot safely prove account ownership
  - poor UX for users who forgot both username and password

### Option 2: Email-based recovery with optional email introduction

- **Pros**:
  - familiar and understandable recovery pattern
  - gives a clear verified recovery factor
  - portable across Web and KMP
  - supports phased rollout for existing `username + password` users
- **Cons**:
  - requires adding email storage and email confirmation flow
  - introduces mail delivery dependency
  - some existing users will remain non-recoverable until they add email

### Option 3: Manual recovery by operator/support

- **Pros**:
  - no immediate email integration required
  - can help in exceptional edge cases
- **Cons**:
  - weak scalability
  - hard to verify identity safely
  - creates operational and security burden
  - unsuitable as the primary recovery mechanism

### Option 4: Recovery codes as the primary factor

- **Pros**:
  - strong user-controlled fallback
  - does not require email delivery
- **Cons**:
  - high UX burden for a finance app at current maturity
  - easy for users to ignore or lose
  - unsuitable as the first recovery mechanism for the current product stage

## Decision

FinKeeper will adopt **email-based self-service account recovery**.

The recovery model is:

- accounts may continue to exist without email during the rollout phase
- **self-service recovery is available only for accounts with a confirmed email**
- accounts without confirmed email are **not recoverable in self-service mode**
- the product must clearly communicate recoverability status and prompt users to add email after login
- `username` will not be used as a standalone recovery factor
- manual recovery, if ever introduced, is an exceptional fallback and not the default product path

## Proposed Flow

### Account security state

Each account has a recoverability state:

- **Protected**: confirmed email is present
- **Unprotected**: no confirmed email

### Forgot password request

1. User opens `Forgot password`
2. User enters email
3. Server always returns a generic success-style response
4. If the email exists and is confirmed:
   - server creates a one-time recovery token
   - stores only the token hash in the database
   - sends a reset link to the email address

### Password reset confirmation

1. User opens the reset link
2. User submits a new password
3. Server validates token, TTL, and usage state
4. Server updates the password hash
5. Server invalidates active refresh sessions
6. Server marks the recovery token as used

## Security Rules

- recovery request endpoint must have rate limiting
- recovery confirmation endpoint must have rate limiting
- recovery responses must be generic and non-enumerating
- reset token must be single-use
- reset token must have TTL
- only token hash should be stored server-side
- successful password reset must revoke existing refresh sessions
- recovery attempts and successful resets should be logged in structured logs

## Consequences

### Positive

- product gets a credible self-service recovery path
- recovery model is consistent across Web and KMP
- `username` remains usable for sign-in without becoming a security liability in recovery
- phased rollout avoids breaking existing auth model immediately

### Negative

- some legacy accounts remain unrecoverable until users add and confirm email
- implementation requires email verification, reset token persistence, and email delivery
- support text and settings UX must be expanded to explain recoverability

## Product Mitigations

To reduce the risk for existing accounts without email:

- after login, show a prompt encouraging the user to add and confirm email
- add a security block in Settings showing recoverability status
- explicitly state in UX copy that accounts without confirmed email cannot use self-service recovery

## Rollout Plan

### Phase 1

- add optional email field to account profile
- add email confirmation flow
- add recoverability status to user-facing settings
- introduce forgot-password flow for confirmed emails only

### Phase 2

- monitor adoption of confirmed email
- evaluate whether email should become mandatory for new accounts
- consider optional secondary protections such as recovery codes

### Phase 3

- only if justified later, evaluate exceptional manual recovery policy and operational safeguards

## API and Data Model Notes

Expected future server-side additions:

- user email field and confirmation status
- password recovery token table with hashed tokens, expiry, usage state, timestamps
- endpoints for:
  - requesting password reset
  - confirming password reset
  - managing/confirming account email
  - exposing recoverability status for clients

## Explicit Non-Goals for This ADR

- implementing the full password reset flow right now
- making email mandatory immediately
- enabling recovery by username only
- defining provider-specific social login implementation

## Implementation Status

For the current production scope, this decision is implemented in `server`, `Web`, and `KMP`:

- account recoverability status is exposed to clients and reflected in Settings/onboarding UX
- self-service password recovery is available only for accounts with confirmed email
- email verification and password reset use token-based flows with single-use tokens and TTL
- authenticated email verification actions now explicitly expose delivery result instead of masking failed delivery as unconditional success
- Web and KMP Settings show a visible failure-state if the confirmation email could not be sent
- server health reporting now includes recovery mail readiness signals (`recoveryDelivery`, `appBaseUrl`)

Verification completed for the current scope:

- `node --check server/index.js`
- `npm run build` in `client/`
- `./gradlew composeApp:compileKotlinMetadata` in `mobile_app_client/`

## Related Future Work

- onboarding and settings UX for account protection messaging
- optional social login evaluation for Google / Yandex / possibly Mail.ru
- long-term auth strategy alignment between Web and KMP

## References

- `docs_and_instructions/production_prepare_implementation_plan.md`
- Batch 14 scope (`P2-01`, `P2-04`)

# Account Recoverability UX Plan

## Goal

Introduce clear and consistent recoverability UX across Web and KMP so users understand whether their account can be recovered and what they need to do to protect it.

The UX plan is based on the accepted rule from `ADR-0001`:

- self-service recovery is available only for accounts with a confirmed email
- accounts without confirmed email are not recoverable in self-service mode

## Primary UX States

### State 1: Protected account

Definition:

- account has a confirmed email

Expected UX:

- show positive security state
- show that password recovery is available
- allow email management actions if/when supported

Suggested wording direction:

- `Аккаунт защищён`
- `Восстановление пароля доступно через подтверждённый email`

### State 2: Unprotected account

Definition:

- account has no email or email is not confirmed

Expected UX:

- show neutral-to-warning security state
- clearly state that self-service recovery is unavailable
- offer a direct action to add or confirm email

Suggested wording direction:

- `Аккаунт не защищён`
- `Без подтверждённого email восстановление доступа недоступно`

### State 3: Recovery request sent

Definition:

- user submitted forgot-password flow for a recoverable account

Expected UX:

- always show generic confirmation message
- do not reveal whether the email exists

Suggested wording direction:

- `Если аккаунт с таким email существует и email подтверждён, мы отправили инструкцию по восстановлению`

## Required UX Surfaces

### 1. Web login / registration area

Additions to plan:

- add `Forgot password` entry point on login screen
- after registration/login, gently promote adding email if account is unprotected
- on registration screen, optionally explain that adding email later is recommended to protect account access

Non-goal at this stage:

- do not overload initial registration with mandatory email unless product decision changes later

### 2. KMP login / registration screens

Additions to plan:

- mirror `Forgot password` entry point where supported
- add helper text explaining that recovery requires confirmed email
- after successful auth, if account is unprotected, surface a security reminder in a non-blocking way

### 3. Settings / security section

Create or expand a dedicated security block containing:

- recoverability status
- confirmed email status
- action to add/change email
- action to confirm email
- short explanation of why it matters

Expected information architecture:

- **Security**
  - recoverability status
  - email status
  - account protection explanation
  - password update action

## Product Messaging Rules

- never imply that recovery is available when email is not confirmed
- never reveal on forgot-password screen whether an email exists in the system
- keep security messaging simple, not overly technical
- prefer actionable copy over abstract warnings

## Prompting Strategy

### Post-login prompt for unprotected accounts

Trigger:

- user authenticates successfully
- recoverability state is unprotected

Behavior:

- show a lightweight banner/card/dialog depending on platform conventions
- explain risk of losing access
- offer direct navigation to security/email setup
- allow dismissal without blocking core app usage

Suggested message direction:

- `Добавьте email, чтобы восстановление доступа было доступно, если вы забудете пароль`

### Repeat prompting

- do not hard-block app usage
- avoid showing the prompt too aggressively on every screen
- prefer:
  - one prompt after login
  - persistent status in settings
  - occasional reminder in account/security surfaces

## Empty / Error / Edge States

### No email configured

- show unprotected state
- CTA: `Добавить email`

### Email added but not confirmed

- show partially protected state or still unprotected state with explicit explanation
- CTA: `Подтвердить email`
- CTA: `Отправить письмо повторно`

### Forgot password requested for unknown email

- show the same generic success message as for known email

### Expired or invalid reset token

- show clear next step:
  - `Ссылка недействительна или устарела`
  - `Запросите новое письмо для восстановления`

## Implementation Notes for Future Server/API Work

Clients will eventually need a simple recoverability payload, for example:

- `email`: string or null
- `emailConfirmed`: boolean
- `recoverabilityStatus`: `protected` | `unprotected`
- `canSelfRecover`: boolean

This should be exposed through authenticated user/account APIs so both Web and KMP can render the same state model.

## Rollout Recommendation

### Phase 1

- expose recoverability status in authenticated profile/settings APIs
- update settings UI with security block
- add forgot-password entry point placeholders where appropriate

### Phase 2

- implement full forgot-password and reset flows
- add post-login prompt for unprotected accounts
- refine helper texts and empty states

### Phase 3

- evaluate whether onboarding should push email setup more strongly
- align with future social login strategy if introduced

## Related Documents

- `docs_and_instructions/adr/0001-email-based-account-recovery.md`
- `docs_and_instructions/production_prepare_implementation_plan.md`

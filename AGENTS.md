# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Role Routing

For automatic role selection (Architect/Orchestrator/Coder/Analyst/Designer/Layout/Reviewer/QA) and matching skill selection, use:
`.agents/roles/Roles.md`

Project-local custom skills live in:
`.agents/skills`

Current custom UI skills:
- `stitch-design` — Stitch-driven design generation/editing and design-system synthesis
- `enhance-prompt` — improve vague UI ideas into stronger Stitch-ready prompts
- `react-components` — convert Stitch-oriented outputs into modular React/Vite components
- `shadcn-ui` — implement or adapt UI using shadcn/ui patterns

Priority order:
1. Explicit user role/skills in prompt.
2. Role auto-routing rules from `Roles.md`.
3. Project conventions from this `AGENTS.md`.

## Project Overview

FinKeeper is a multi-platform personal finance tracking application (Russian: "Домашняя бухгалтерия"). It supports Web (React), Android/iOS (Kotlin Multiplatform), and Desktop (macOS/Windows/Linux via Compose Desktop).

**Currency**: RUB (Russian Ruble)  
**Language**: Russian UI  
**Date Format**: DD.MM.YYYY  
**Money Format**: `1 000 000 ₽` (non-breaking spaces, no decimals)

## Repository Structure

```
├── client/              # Web frontend (React 19 + Vite 7 + TypeScript + Tailwind CSS 4)
├── server/              # Backend (Express 5 + SQLite + JWT + WebSocket)
├── mobile_app_client/   # KMP app (Android/iOS/Desktop) - has its own AGENTS.md
├── start.sh             # Dev server startup (remote IP mode, background)
├── start_local.sh       # Dev server startup (localhost mode, foreground)
└── start_prod.sh        # Production startup
```

## Common Commands

### Web Development

```bash
# Start dev servers (backend :3002 + frontend :5174)
npm start

# Production build and run
npm run start:prod

# Client-only commands (cd client/)
npm run dev          # Vite dev server
npm run build        # TypeScript compile + Vite build
npm run lint         # ESLint
npm run preview      # Preview production build
```

### Backend

```bash
cd server/
node index.js        # Start server on port 3002
node db_setup.js     # Initialize/recreate database schema
```

### Mobile/Desktop (KMP)

See `mobile_app_client/AGENTS.md` for detailed KMP commands.

```bash
cd mobile_app_client/
./gradlew :composeApp:assembleDebug       # Android debug APK
./gradlew :composeApp:assembleRelease     # Android release APK
./gradlew :composeApp:run                 # Desktop run
./gradlew :composeApp:packageDistributionForCurrentOS  # Desktop packaging
./gradlew composeApp:testDebugUnitTest    # Run tests
```

## Architecture

### Backend (server/)

**Stack**: Express 5, better-sqlite3, JWT (jsonwebtoken), bcryptjs, ws (WebSocket)

**Key architectural patterns**:
- Modular routes: `routes/auth.js` (mostly public: register/login/refresh/OAuth/password-recovery/email-verification), `routes/user.js` and `routes/data.js` are protected via `router.use(authenticateToken)`
- `JWT_SECRET` is required from env (`config.js`, `index.js`) — the server refuses to start without it (no hardcoded fallback)
- Security middleware: `helmet` (with CSP + `/api/csp-report`), CORS allowlist via `ALLOWED_ORIGINS` (required in production), JSON body limit via `JSON_BODY_LIMIT`
- Rate limiting on register/login, password change, backup restore, email verification and password recovery (`429` + `Retry-After`)
- Auth flow: short-lived access token + refresh sessions, email verification, password recovery, social/OAuth login (see `auth_*` tables)
- WebSocket server for real-time updates between clients
- Automatic backups: max 5 JSON snapshots per user, created on login and hourly token checks
- Database: SQLite with foreign keys (see `db_setup.js` for schema)

**Important tables**:
- `users` - user accounts with password_hash
- `months` - unique (year, month, user_id) combinations
- `categories`, `income_sources` - soft-delete via `is_active` flag
- `expenses`, `incomes` - linked to month_id
- `budgets` - per-category monthly limits
- `savings_goals`, `savings_transactions` - savings tracking
- `user_backups` - JSON data snapshots

**Savings logic**: When adding to savings, a hidden expense is created in category "Пополнение копилки" (`is_active=0`) to reduce available monthly balance. Withdrawals create negative transactions without hidden expenses.

### Web Client (client/)

**Stack**: React 19, TypeScript, Vite 7, Tailwind CSS 4, React Router 7, Recharts 3, Axios, @dnd-kit

**Key architectural patterns**:
- Auth via `AuthContext` (JWT stored in localStorage)
- Protected routes via `RequireAuth` component
- API layer in `src/api/` (Axios instance with Bearer token)
- Event-driven updates: `savingsUpdated` custom event for cross-component sync
- Financial summary refreshes every 30 seconds + on events

**Routing**:
- `/login` - public
- `/` (Dashboard), `/month`, `/categories`, `/savings`, `/settings` - protected

**Layout**:
- Desktop: Collapsible glassmorphism sidebar (emerald gradient)
- Mobile: Fixed bottom navigation bar

### Mobile/Desktop Client (mobile_app_client/)

**Stack**: Kotlin 2.2.10, Compose Multiplatform 1.6.10, Ktor Client 3.0.3, Koin 3.5.6, SQLDelight 2.0.2

**Key architectural patterns**:
- MVVM: ViewModels with `StateFlow` state exposure
- Offline-first: Local SQLite + sync queue (`SyncManager`, `SyncService`)
- Repository pattern with local/remote data sources
- WebSocket for real-time server updates
- Platform-specific modules via Koin (`androidAppModule`, `desktopAppModule`, etc.)
- Expect/actual for platform-specific code (NetworkMonitor, DatabaseDriverFactory)

**Themes**: Light, Cyberpunk (dark), Dark (night), Dark Night (deep night), Auto (system)

## Data Flow

1. **Month-centric**: All income/expense/budget data linked to `month_id` via `ensureMonth()` pattern
2. **Multi-user**: All tables have `user_id`, middleware checks access
3. **Real-time**: WebSocket broadcasts updates to connected clients
4. **Offline sync** (mobile/desktop): Operations queued locally, synced when online

## Important Implementation Notes

- **Soft delete**: Categories and income sources use `is_active=0` instead of DELETE
- **Sort order**: Categories have manual drag-and-drop ordering via `sort_order` field
- **Default data**: New users get initial categories and income sources from `server/default_data.js`
- **Backup restore**: Transactional - clears all user data before restoring snapshot
- **CORS**: allowlist via `ALLOWED_ORIGINS` (required in production); requests from other origins are rejected

## Testing

- Web: No test runner configured (package.json has placeholder)
- KMP: `./gradlew composeApp:testDebugUnitTest` - uses kotlin.test

## CI/CD

GitHub Actions workflows, all run manually via `workflow_dispatch`:

- `build-desktop.yml` (Linux+Windows+macOS), `build-linux.yml` (`.deb`/`.rpm`), `build-windows.yml` (`.msi`), `build-mac.yml` (`.app`) — build desktop packages.
- `publish-aur.yml` — publish/update the AUR package `finkeeper24-bin` (run after the new `.deb` is uploaded to finkeeper24.ru; sha256 is taken from the live file). Details: `mobile_app_client/README.md` → «Релиз и публикация», `docs_and_instructions/aur-ci-publish-plan.md`.

## Second-brain sync (start & end of session)

Vladimir keeps a **knowledge card** for this project in his Obsidian "second brain" (an LLM Wiki
in the spirit of Karpathy). It lives in a separate folder, **outside this repo**:

- CachyOS: `~/YandexDisk/Obsidian/My Vault/projects/FinKeeper24.md`
- Other OSes (macOS/Windows): the vault path differs — confirm with Vladimir or check the vault's
  `reference/Рабочие репозитории.md` registry.

**At the START of a working session** on this project:

1. **Read that card first** to recover prior context — past decisions, what was already done,
   open questions, follow-ups, and the working rules. It complements this repo's own memory and
   git history; treat it as the running log of our work together.
2. Reading is read-only (just open the absolute path) — no permission to write is needed.
3. If the path doesn't exist (different OS, or the vault isn't synced yet), ask Vladimir for the
   vault path instead of guessing — but don't block the work if he prefers to skip it.

**At the END of a working session** (after finishing a task, fix, or feature):

1. **Proactively ask** whether to record the outcome into that card. Do **not** write to it
   automatically or silently.
2. If Vladimir agrees, **append** to the `## Решения и заметки` section (newest entry on top,
   dated), **in Russian**: a short 1–3 line summary — what changed, key decisions, version/branch,
   any follow-ups. Add `[[wiki-links]]` where relevant and bump the card's `updated:` field.
3. The card lives outside this repo, so editing it means writing to an **absolute path** —
   request permission as usual.
4. **Never** stage or commit the card edit into this repo's git history. The card belongs to the
   vault (synced by Yandex.Disk, no git there).

Reminder: **do not commit or push** until Vladimir has verified the build locally and explicitly
approved.

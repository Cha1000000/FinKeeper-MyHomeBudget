# My Home Budget

## Role Routing Rules

When handling tasks, apply role routing and skill mapping from:
`.agents/roles/Roles.md`

If the user explicitly asks for a role or names specific skills, that explicit instruction overrides automatic routing.

Project-local custom skills live in:
`.agents/skills`

Current custom UI skills:
- `stitch-design` — Stitch-driven design generation/editing and design-system synthesis
- `enhance-prompt` — improve vague UI ideas into stronger Stitch-ready prompts
- `react-components` — convert Stitch-oriented outputs into modular React/Vite components
- `shadcn-ui` — implement or adapt UI using shadcn/ui patterns

A full-stack web application for personal finance management, replacing Excel spreadsheets with a modern SPA interface. It handles income, expenses, savings goals, and monthly budgeting.

## Project Structure

- **`client/`**: Frontend application (React, Vite, TypeScript, Tailwind CSS).
- **`server/`**: Backend API (Node.js, Express, SQLite).
- **`docs_and_instructions/`**: Project documentation and implementation details.
- **`start.sh`**: Helper script to clean ports and start the application.

## Tech Stack

- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS, Recharts, React Router.
- **Backend:** Node.js, Express.
- **Database:** SQLite (via `better-sqlite3`). stored in `server/database.sqlite`.

## Getting Started

### Prerequisites

- Node.js (v18+)
- npm

### Installation

1.  Install dependencies for both root and sub-projects:
    ```bash
    npm install
    cd client && npm install && cd ..
    cd server && npm install && cd ..
    ```

### Running the Application

**Development Mode (Recommended):**

This starts both the backend API and frontend dev server concurrently with hot-reloading.

```bash
npm start
```
*   **Frontend:** [http://localhost:5174](http://localhost:5174)
*   **Backend:** [http://localhost:3002](http://localhost:3002)

**Alternative Start (Shell Script):**

The provided script helps clean up stuck ports before starting.

```bash
./start.sh
```

**Production Build:**

Builds the frontend and serves it via the Node.js backend.

```bash
npm run start:prod
```

## Key Files & Configuration

-   **`package.json`**: Root scripts for orchestration.
-   **`client/vite.config.ts`**: Frontend build configuration.
-   **`server/index.js`**: Backend entry point and API definition.
-   **`server/db_setup.js`**: Database initialization script (runs automatically).
-   **`DEPLOYMENT.md`**: Detailed instructions for deploying to a server (Ubuntu/PM2).

## Development Conventions

-   **Database:** The SQLite database is created automatically at `server/database.sqlite` on the first run.
-   **Ports:**
    -   `3002`: Backend API & Static file serving (Prod).
    -   `5174`: Frontend Dev Server.
-   **Styling:** Tailwind CSS is used for all styling.
-   **State Management:** React hooks and standard props/context.
-   **API:** RESTful JSON API.

## Documentation

-   [Implementation Plan](docs_and_instructions/implementation_plan.md): Original project goals and database schema.
-   [Walkthrough](docs_and_instructions/walkthrough.md): User guide and feature overview.

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

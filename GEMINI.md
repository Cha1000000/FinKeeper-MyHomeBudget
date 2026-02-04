# My Home Budget

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

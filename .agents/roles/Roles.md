# Roles Routing Rules

Purpose: provide automatic role routing for AI agents in this project, independent of IDE/CLI wrapper.

## Core Policy

1. Detect user intent first, then pick role(s), then pick skills.
2. Use up to 3 primary skills per role step.
3. If confidence is high (>= 0.75), auto-apply role routing.
4. If confidence is medium (0.45-0.74), apply routing but state assumed role in the first line.
5. If confidence is low (< 0.45), ask a clarifying question or use `fullstack-guardian` as fallback.

## Project Custom Skills

Project-local custom skills live in:
`.agents/skills`

Available custom skills currently adopted in this repository:
- `stitch-design` — use for Stitch-driven screen generation/editing and design-system synthesis
- `enhance-prompt` — use to turn vague UI ideas into strong Stitch-ready prompts
- `react-components` — use when converting Stitch output into modular React/Vite components
- `shadcn-ui` — use when implementing or adapting UI with shadcn/ui patterns

## Role Catalog

### 1) Architect
Use for: system design, decomposition, boundaries, data flow, non-functional tradeoffs, ADRs.
Primary skills:
- `architecture-designer`
- `architecture-patterns`
- `architecture-decision-records`
Secondary:
- `api-design-principles`
- `openapi-spec-generation`

### 2) Orchestrator
Use for: planning execution phases, splitting tasks, handoffs, dependency management, multi-agent coordination.
Primary skills:
- `task-coordination-strategies`
- `team-communication-protocols`
- `team-composition-patterns`
Secondary:
- `debugging-strategies`
- `code-review-excellence`

### 3) Coder (Fullstack)
Use for: implementation across server/client, refactor, feature delivery, bugfixes.
Primary skills:
- `fullstack-guardian`
- `nodejs-backend-patterns`
- `react-expert`
Secondary:
- `react-state-management`
- `typescript-advanced-types`
- `error-handling-patterns`

### 4) Analyst
Use for: root cause analysis, metrics interpretation, incident trends, data-based recommendations.
Primary skills:
- `debugging-wizard`
- `debugging-strategies`
- `sql-optimization-patterns`
Secondary:
- `code-reviewer`
- `security-reviewer`

### 5) Designer (UI/UX)
Use for: visual direction, UI quality, interaction polish, design consistency.
Primary skills:
- `frontend-design`
- `visual-design-foundations`
- `interaction-design`
Secondary:
- `design-system-patterns`
- `web-design-guidelines`
- `canvas-design`
- `stitch-design`
- `enhance-prompt`

### 6) Layout Engineer (Frontend UI Implementer)
Use for: responsive layout, component structure, CSS/tokens, production UI implementation.
Primary skills:
- `web-component-design`
- `responsive-design`
- `tailwind-design-system`
Secondary:
- `vercel-composition-patterns`
- `vercel-react-best-practices`
- `vite`
- `react-components`
- `shadcn-ui`

### 7) Reviewer (Quality + Security)
Use for: PR review, regression risk, correctness checks, defensive hardening.
Primary skills:
- `code-review-excellence`
- `code-reviewer`
- `security-reviewer`
Secondary:
- `auth-implementation-patterns`
- `accessibility-compliance`

### 8) QA / Tester
Use for: test strategy, E2E checks, flaky tests, web behavior validation.
Primary skills:
- `e2e-testing-patterns`
- `webapp-testing`
- `playwright-expert`
Secondary:
- `vitest`
- `javascript-testing-patterns`

## Intent -> Role Routing

- "architecture", "design system architecture", "how should we structure" -> Architect
- "plan", "split work", "phases", "who does what" -> Orchestrator
- "implement", "add feature", "fix bug", "refactor code" -> Coder
- "analyze", "why broken", "root cause", "optimize query/perf" -> Analyst
- "redesign UI", "make it beautiful", "improve UX" -> Designer
- "build page/component", "responsive", "layout", "styling" -> Layout Engineer
- "review this", "audit this PR", "security check" -> Reviewer
- "write tests", "E2E", "playwright", "flaky tests" -> QA / Tester

## UI / Stitch Support Routing

Add these custom skills when the task matches:
- UI concept, visual exploration, or Stitch editing/generation -> `stitch-design`
- weak/vague UI prompt that needs refinement -> `enhance-prompt`
- converting Stitch output into React/Vite code -> `react-components`
- choosing/building/adapting shadcn/ui components -> `shadcn-ui`

Preferred pairings:
- Designer -> `frontend-design` + `stitch-design` + `enhance-prompt`
- Layout Engineer -> `web-component-design` + `react-components` + `shadcn-ui`
- Coder (frontend-heavy task) -> `react-expert` + `react-components` + `shadcn-ui`

## Multi-Role Pipeline

If task is complex, use sequence:
1. Architect (scope, constraints, design decisions)
2. Orchestrator (implementation plan)
3. Coder (changes)
4. Reviewer or QA (verification)

Do not skip Reviewer/QA for high-risk changes (auth, data correctness, money, deletes, migration, sync logic).

## Explicit Role Override (User Prompt)

If user explicitly sets role, it overrides auto-routing.
Accepted patterns:
- "Role: Architect"
- "Work as Designer"
- "Use role Orchestrator"
- "Use skills: <skill1>, <skill2>"

When explicit skills are provided, do not replace them; only add one compatible support skill if clearly needed.

## Default Fallback

If intent is mixed or unclear:
- Start with `fullstack-guardian` + `debugging-strategies`.
- Then branch to Architect/Designer/Reviewer based on first concrete signal from user feedback.

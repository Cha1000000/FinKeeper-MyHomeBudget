# Roles Quick Start

This folder enables role routing via:

- `Roles.md` (routing policy and role-to-skill mapping)

Project-local custom skills are stored in:

- `/Users/racerkafa/Documents/MyProjects/Web/My Home Budget/.agents/skills`

Current custom UI skills:

- `stitch-design`
- `enhance-prompt`
- `react-components`
- `shadcn-ui`

Use these prompt patterns in Windsurf/Kilo/Qwen/Qoder/CLI agents.

## General Pattern

Use:
`Role: <RoleName>. Skills: <skill1>, <skill2>. Task: <what to do>. Output: <expected artifact>. Constraints: <limits>.`

Example:
`Role: Architect. Skills: architecture-designer, architecture-patterns. Task: propose sync refactor plan for savings module. Output: phased plan + risks + migration steps. Constraints: no DB schema break.`

## Role Prompts

### Architect
`Role: Architect. Skills: architecture-designer, architecture-patterns, architecture-decision-records. Build a target architecture for <feature>. Output ADR + tradeoffs.`

### Orchestrator
`Role: Orchestrator. Skills: task-coordination-strategies, team-communication-protocols. Split <feature> into parallel tracks with dependency graph and checkpoints.`

### Coder
`Role: Coder. Skills: fullstack-guardian, nodejs-backend-patterns, react-expert. Implement <feature>. Output code + tests + brief change summary.`

### Analyst
`Role: Analyst. Skills: debugging-wizard, debugging-strategies, sql-optimization-patterns. Find root cause of <issue>. Output evidence, hypothesis, fix options.`

### Designer
`Role: Designer. Skills: frontend-design, stitch-design, enhance-prompt. Redesign <screen>. Output UI concept + tokens + interaction notes.`

### Layout Engineer
`Role: Layout Engineer. Skills: web-component-design, react-components, shadcn-ui. Implement responsive layout for <screen>.`

## Custom UI Skill Patterns

### Stitch-driven design generation or editing
`Role: Designer. Skills: stitch-design, enhance-prompt. Task: generate or refine a Stitch-ready UI concept for <screen>. Output: enhanced prompt + design direction.`

### Convert Stitch output into code
`Role: Layout Engineer. Skills: react-components, shadcn-ui. Task: convert Stitch output into modular React/Vite components for <screen>. Output: component structure + implementation plan.`

### Reviewer
`Role: Reviewer. Skills: code-review-excellence, code-reviewer, security-reviewer. Review this diff for regressions, security, and test gaps.`

### QA / Tester
`Role: QA. Skills: e2e-testing-patterns, webapp-testing, playwright-expert. Create and run test scenarios for <flow>.`

## Multi-Role Pipeline Prompt

`Use pipeline: Architect -> Orchestrator -> Coder -> Reviewer.`
`Task: <feature>.`
`Output:`
`1) Architecture decisions`
`2) Execution plan`
`3) Implementation`
`4) Review findings`

## Override Rule

If you explicitly set role/skills in prompt, agents should prioritize that over automatic routing.

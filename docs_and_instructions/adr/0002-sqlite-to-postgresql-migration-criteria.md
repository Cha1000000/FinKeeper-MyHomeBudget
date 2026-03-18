# ADR-0002: Criteria and readiness plan for SQLite to PostgreSQL migration

## Status

Accepted

## Context

FinKeeper currently uses SQLite on the server side. At the current product stage this remains an acceptable trade-off because:

- the operational footprint is small
- deployment complexity is low
- the application is still maturing across Web, KMP, and server layers

However, SQLite should not remain an implicit forever decision. The project needs explicit criteria for when PostgreSQL becomes the better choice, so migration can be planned before reliability or scalability become active problems.

## Decision Drivers

- **Must keep current operations simple** while user count is still relatively small
- **Must define migration triggers before incidents force a rushed database change**
- **Must protect auth, financial data integrity, backups, and restore workflows during any future migration**
- **Should avoid premature migration if SQLite still fits the workload**
- **Should define readiness signals, not only business ambition statements**

## Considered Options

### Option 1: Keep SQLite indefinitely without formal migration criteria

- **Pros**:
  - no planning overhead now
  - minimal operational complexity
- **Cons**:
  - migration becomes reactive and risky
  - architecture decisions stay ambiguous
  - scalability bottlenecks may be discovered too late

### Option 2: Migrate to PostgreSQL immediately

- **Pros**:
  - stronger long-term foundation
  - better concurrency and ecosystem options
- **Cons**:
  - higher operational complexity now
  - migration effort may be premature for current scale
  - distracts from still-open product hardening and auth work

### Option 3: Keep SQLite for now, but define explicit migration criteria and readiness plan

- **Pros**:
  - preserves low current complexity
  - creates a deliberate path to PostgreSQL
  - reduces risk of emergency migration later
- **Cons**:
  - still requires discipline to monitor the triggers
  - some engineering time goes into planning before implementation

## Decision

FinKeeper will **keep SQLite as the current primary database** while adopting an explicit **migration-trigger and readiness framework** for a future move to PostgreSQL.

The team should not migrate merely because PostgreSQL is more scalable in theory. Migration becomes justified when one or more concrete trigger groups are met and the readiness checklist indicates that a controlled cutover is feasible.

## Migration Trigger Groups

### Group A: Reliability and recovery pressure

Migration should be actively prioritized if one or more of the following begin to appear repeatedly:

- backup/restore operations become too slow for acceptable recovery expectations
- database corruption or lock-related incidents appear in production-like use
- maintenance operations require unsafe downtime windows
- observability shows increasing difficulty diagnosing DB-level failures

### Group B: Concurrency and write contention

Migration should be actively prioritized if:

- auth, sync, or financial write flows begin contending regularly
- lock waits or `database is locked` style failures become operationally relevant
- background work, real-time updates, and interactive traffic start competing for the same write path
- deployment topology starts needing more concurrent server processes than SQLite comfortably supports

### Group C: Product and scale growth

Migration should be actively prioritized if:

- user growth moves beyond a small/single-node comfort zone
- usage patterns require stronger concurrent write behavior
- analytics/reporting needs start creating heavier database pressure
- upcoming roadmap items clearly depend on more robust database concurrency or operational tooling

### Group D: Operational maturity requirements

Migration should be actively prioritized if the product needs:

- managed backups and point-in-time recovery expectations beyond the current SQLite workflow
- stronger role separation between application runtime and database operations
- more formalized staging/production parity for database behavior
- infrastructure patterns where PostgreSQL becomes the standard operational fit

## Non-Triggers

The following alone should **not** trigger migration:

- generic preference for "more enterprise" technology
- speculative scaling concerns without observed pressure
- isolated performance anxieties without metrics or incidents
- the existence of PostgreSQL expertise in the abstract

## Readiness Checklist Before Migration Work Starts

### Product and business readiness

- migration solves an observed or clearly imminent problem
- migration scope is prioritized against other product work
- acceptable maintenance/cutover strategy is agreed

### Technical readiness

- current SQLite schema is documented and normalized enough to port safely
- migration scripts and rollback strategy are designed before cutover
- auth sessions, backups, deleted-records sync logic, and idempotency flows are explicitly covered
- smoke and regression checks exist for critical auth and financial paths
- data validation plan exists for pre/post migration comparison

### Operational readiness

- PostgreSQL hosting/operations model is selected
- secret management and connection configuration are prepared
- backup and restore procedures are defined for PostgreSQL
- monitoring and alerting expectations are defined for the new database layer

## Migration Approach Recommendation

When migration becomes justified, prefer a phased plan:

1. define PostgreSQL schema and compatibility layer
2. create repeatable migration/backfill tooling
3. validate migrated data against SQLite snapshots
4. test auth, sync, backup/restore, and critical month flows end-to-end
5. perform controlled cutover with rollback path
6. decommission SQLite-only production dependency after stabilization

## Consequences

### Positive

- current simplicity is preserved
- the project gets a deliberate growth path
- database migration becomes evidence-driven rather than reactive

### Negative

- trigger monitoring must actually be maintained
- some ambiguity remains until concrete metrics are formalized further

## Related Future Work

- define a more explicit operational metric set for DB pressure
- create migration design doc once trigger threshold is approached
- align server smoke/regression checks with future database portability needs

## References

- `docs_and_instructions/production_prepare_implementation_plan.md`
- `docs_and_instructions/adr/0001-email-based-account-recovery.md`

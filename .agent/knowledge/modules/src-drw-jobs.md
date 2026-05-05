# `src/drw/jobs/`

## Purpose

Owns offline jobs that run domain maintenance without introducing direct delivery side effects outside the established client helpers.

## Key files

- `src/drw/jobs/sla_reaper.clj` - runs one SLA sweep and returns checked/breached counts.
- `src/drw/domain/sla.clj` - finds overdue non-terminal disputes, claims each SLA breach idempotently, appends timeline/audit rows, and emits the Notification Hub event helper.
- `test/drw/domain/sla_test.clj` - covers overdue detection, idempotent breach marking, terminal dispute exclusion, and Hub-disabled behavior.

## Dependencies

- Upstream: `drw.domain.sla`, `drw.domain.state`, `drw.domain.disputes`, `drw.audit.recorder`, `drw.ecosystem.hub-client`.
- Downstream: future scheduler wiring should call `drw.jobs.sla-reaper/run-once!` rather than scanning domain atoms directly.

## Tests

- Domain SLA tests verify one breach per dispute/due-at pair, audit/timeline side effects, and ignored terminal disputes.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-domain.md`, `.agent/knowledge/modules/src-drw-ecosystem.md`
- Related foundation primitives: `.agent/knowledge/foundation/audit-append-only.md`, `.agent/knowledge/foundation/ecosystem-client-stubs.md`

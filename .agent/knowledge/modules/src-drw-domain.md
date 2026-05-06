# `src/drw/domain/`

## Purpose

Owns the in-memory domain data abstraction for the core manual queue: counterparties, disputes, exceptions, adapter/Hub ingestion, ingestion source settings/runs, playbook settings, correlation scoring/candidates/review decisions, resolution orchestration, report sources, timeline rows, audit rows, per-tenant dispute references, and SLA breach claims.

## Key files

- `src/drw/domain/state.clj` - process-local atoms for domain entities, correlation candidates, ingestion source settings/runs, append-only timeline, and audit append helpers.
- `src/drw/domain/counterparties.clj` - counterparty CRUD, normalized-name matching, and source external-ref resolution.
- `src/drw/domain/correlations.clj` - tenant-scoped correlation candidate reads, hydrated detail lookup, and terminal accept/reject decisions.
- `src/drw/domain/correlator.clj` - pure tenant-scoped candidate scoring across source-ref, entity-id, counterparty, currency, amount, date, and category signals with review/auto-merge threshold bands and deterministic explanations.
- `src/drw/domain/disputes.clj` - dispute creation, assignment, status transitions, comments, exception attach side effects, and tenant-scoped readers.
- `src/drw/domain/exceptions.clj` - manual exception creation, source-system/kind validation, duplicate source-ref prevention, tenant-scoped listing, attach flow, correlation candidate listing, and `ingest!` for normalized adapter/Hub exceptions.
- `src/drw/domain/hub_events.clj` - shared Notification Hub payload builders for dispute lifecycle and correlation-pending events.
- `src/drw/domain/ingestion_sources.clj` - tenant-scoped ingestion source registry materialized from runtime config, settings persistence, pull-now execution through existing poll jobs, and run history.
- `src/drw/domain/playbooks.clj` - tenant-scoped resolution playbook create/update/list/disable with duplicate-code protection and audit rows.
- `src/drw/domain/reports.clj` - tenant-scoped dispute audit Selmer HTML rendering, ready/failed PDF artifact lifecycle, stored SHA-256/path metadata, and two-tenant identity leakage smoke checks.
- `src/drw/domain/resolution.clj` - starts active tenant playbooks through Workflow Engine, stores execution ids, polls terminal execution status, applies resolved/investigating transitions, and emits terminal Hub events.
- `src/drw/domain/sla.clj` - overdue SLA detection, idempotent breach claiming, audit/timeline rows, and Notification Hub event emission helper.

## Dependencies

- Upstream: `drw.db.scope`, `drw.db.schema`, `drw.audit.recorder`, `drw.tenants.snapshot`, `hiccup2.core`.
- Downstream: API handlers, UI handlers, offline jobs, ingestion, and resolution modules should call this domain layer instead of mutating atoms directly.

## Tests

- `test/drw/domain/core_queue_test.clj` covers two-tenant isolation, duplicate source refs, illegal dispute transitions, terminal attach rejection, timeline rows, and audit rows.
- `test/drw/domain/correlator_test.clj` covers strong-signal scoring, business-signal review candidates, weak candidate filtering, cross-tenant collision rejection, and stable tie ordering.
- `test/drw/domain/ingestion_pipeline_test.clj` covers unmatched dispute creation, duplicate source-ref rejection without extra audit/correlation side effects, source-ref uniqueness scoped by tenant and source system, pending candidates with Hub emission, opt-in auto-merge, and cross-tenant source/entity collision rejection.
- `test/drw/domain/ingestion_sources_test.clj` covers default source materialization, tenant isolation, settings saves, disabled/failure pull-now results, cursor updates, source refs, and run filters.
- `test/drw/domain/playbooks_test.clj` covers tenant-scoped playbook saves, updates, soft-disable, validation, duplicate-code rejection, and cross-tenant misses.
- Correlation review behavior is covered through `test/drw/api/workbench_handlers_test.clj` and real HTTP E2E tests: pending list/detail, cross-tenant 404, accept attach side effects, duplicate terminal rejection, and reject-without-attach.
- `test/drw/domain/reports_test.clj` covers tenant-scoped dispute audit template rendering, injected PDF generation, ready/failed artifacts, cross-tenant fail-closed behavior, and tenant identity leakage checks.
- `test/drw/domain/resolution_test.clj` covers Workflow Engine resolution starts, invalid start rejection, successful completion polling, failed execution retry behavior, and terminal Hub emissions.
- `test/drw/domain/sla_test.clj` covers overdue SLA breach side effects, idempotency, terminal dispute exclusion, and disabled Hub behavior.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/db-datomic-schema.md`, `.agent/knowledge/foundation/tenant-scope-and-snapshot.md`, `.agent/knowledge/foundation/audit-append-only.md`

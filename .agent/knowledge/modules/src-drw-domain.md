# `src/drw/domain/`

## Purpose

Owns the in-memory domain data abstraction for the core manual queue: counterparties, disputes, exceptions, adapter ingestion, correlation scoring/candidates, report sources, timeline rows, audit rows, per-tenant dispute references, and SLA breach claims.

## Key files

- `src/drw/domain/state.clj` - process-local atoms for domain entities and correlation candidates plus append-only timeline and audit append helpers.
- `src/drw/domain/counterparties.clj` - counterparty CRUD, normalized-name matching, and source external-ref resolution.
- `src/drw/domain/correlator.clj` - pure tenant-scoped candidate scoring across source-ref, entity-id, counterparty, currency, amount, date, and category signals with review/auto-merge threshold bands and deterministic explanations.
- `src/drw/domain/disputes.clj` - dispute creation, assignment, status transitions, comments, exception attach side effects, and tenant-scoped readers.
- `src/drw/domain/exceptions.clj` - manual exception creation, duplicate source-ref prevention, tenant-scoped listing, attach flow, correlation candidate listing, and `ingest!` for normalized adapter exceptions.
- `src/drw/domain/reports.clj` - tenant-scoped dispute audit PDF-source HTML rendering and two-tenant identity leakage smoke checks.
- `src/drw/domain/sla.clj` - overdue SLA detection, idempotent breach claiming, audit/timeline rows, and Notification Hub event emission helper.

## Dependencies

- Upstream: `drw.db.scope`, `drw.db.schema`, `drw.audit.recorder`, `drw.tenants.snapshot`, `hiccup2.core`.
- Downstream: API handlers, offline jobs, future UI, ingestion, and resolution modules should call this domain layer instead of mutating atoms directly.

## Tests

- `test/drw/domain/core_queue_test.clj` covers two-tenant isolation, duplicate source refs, illegal dispute transitions, terminal attach rejection, timeline rows, and audit rows.
- `test/drw/domain/correlator_test.clj` covers strong-signal scoring, business-signal review candidates, weak candidate filtering, cross-tenant collision rejection, and stable tie ordering.
- `test/drw/domain/ingestion_pipeline_test.clj` covers unmatched dispute creation, duplicate source-ref rejection before correlation side effects, pending candidates, opt-in auto-merge, and cross-tenant correlation rejection.
- `test/drw/domain/reports_test.clj` covers tenant-scoped dispute audit PDF-source rendering, cross-tenant fail-closed behavior, and tenant identity leakage checks.
- `test/drw/domain/sla_test.clj` covers overdue SLA breach side effects, idempotency, terminal dispute exclusion, and disabled Hub behavior.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/db-datomic-schema.md`, `.agent/knowledge/foundation/tenant-scope-and-snapshot.md`, `.agent/knowledge/foundation/audit-append-only.md`

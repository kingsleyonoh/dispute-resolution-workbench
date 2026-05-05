# `src/drw/domain/`

## Purpose

Owns the in-memory domain data abstraction for the core manual queue: counterparties, disputes, exceptions, timeline rows, audit rows, and per-tenant dispute references.

## Key files

- `src/drw/domain/state.clj` - process-local atoms for domain entities plus append-only timeline and audit append helpers.
- `src/drw/domain/counterparties.clj` - counterparty CRUD, normalized-name matching, and source external-ref resolution.
- `src/drw/domain/disputes.clj` - dispute creation, assignment, status transitions, comments, exception attach side effects, and tenant-scoped readers.
- `src/drw/domain/exceptions.clj` - manual exception creation, duplicate source-ref prevention, tenant-scoped listing, and attach flow.

## Dependencies

- Upstream: `drw.db.scope`, `drw.db.schema`, `drw.audit.recorder`.
- Downstream: future API, UI, SLA, ingestion, and resolution modules should call this domain layer instead of mutating atoms directly.

## Tests

- `test/drw/domain/core_queue_test.clj` covers two-tenant isolation, duplicate source refs, illegal dispute transitions, terminal attach rejection, timeline rows, and audit rows.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/db-datomic-schema.md`, `.agent/knowledge/foundation/tenant-scope-and-snapshot.md`, `.agent/knowledge/foundation/audit-append-only.md`

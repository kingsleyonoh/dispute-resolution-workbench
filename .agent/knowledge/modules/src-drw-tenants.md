# `src/drw/tenants/`

## Purpose

Captures immutable tenant identity snapshots for reports, templates, and audit-sensitive re-rendering.

## Key files

- `src/drw/tenants/snapshot.clj` - maps tenant attributes into snapshot keys, requires all identity fields, and exposes literal extraction for leakage tests.

## Dependencies

- Upstream: `drw.db.scope`, `drw.fixtures`, `clojure.string`.
- Downstream: future report rendering, email/PDF surfaces, and tenant leakage checks.

## Tests

- `test/drw/tenants/snapshot_test.clj` covers snapshot capture, missing tenant failure, and cross-tenant literal exclusion.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/tenant-scope-and-snapshot.md`


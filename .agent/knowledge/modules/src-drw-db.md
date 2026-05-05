# `src/drw/db/`

## Purpose

Owns Datomic schema resource loading, status transition validation, and minimal tenant-scoped entity filtering.

## Key files

- `src/drw/db/schema.clj` - loads `resources/datomic/schema.edn`, exposes attributes and tx-function specs, and validates status transitions.
- `src/drw/db/scope.clj` - provides tenant-id lookup and tenant filtering helpers.
- `resources/datomic/schema.edn` - Section 4 Datomic attributes and documented tx-function specs.

## Dependencies

- Upstream: `clojure.java.io`, `clojure.edn`.
- Downstream: `drw.tenants.snapshot` uses tenant lookup; later DB/query modules should use this surface instead of ad hoc tenant filtering.

## Tests

- `test/drw/db/schema_test.clj` validates schema resource shape and legal/illegal status transitions.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/db-datomic-schema.md`, `.agent/knowledge/foundation/tenant-scope-and-snapshot.md`


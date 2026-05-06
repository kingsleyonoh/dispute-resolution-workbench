# `src/drw/tenants/`

## Purpose

Captures immutable tenant identity snapshots and owns the current in-memory tenant lookup/API-key store used by HTTP endpoints and Hub ingress.

## Key files

- `src/drw/tenants/snapshot.clj` - maps tenant attributes into snapshot keys, requires all identity fields, and exposes literal extraction for leakage tests.
- `src/drw/tenants/store.clj` - loads fixture tenants into an atom, stores API-key hashes and prefixes, resolves tenants by id/slug/API key, registers tenants, rotates keys, and records audit transactions.

## Dependencies

- Upstream: `drw.db.scope`, `drw.fixtures`, `drw.audit.recorder`, `clojure.string`, Java crypto/time/UUID primitives.
- Downstream: tenant API handlers, auth interceptor, Hub ingress tenant resolution, report rendering, email/PDF surfaces, and tenant leakage checks.

## Tests

- `test/drw/tenants/snapshot_test.clj` covers snapshot capture, missing tenant failure, and cross-tenant literal exclusion.
- `test/drw/http/interceptors_test.clj` and `test/drw/e2e_api/tenant_endpoints_test.clj` cover API-key lookup, disabled tenants, registration, and rotation behavior.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/tenant-scope-and-snapshot.md`, `.agent/knowledge/foundation/http-api-auth-tenants.md`

# API Module

## Purpose

Provides thin JSON API handlers that validate requests, call tenant-domain helpers, and return Pedestal response maps.

## Key files

- `src/drw/api/tenants.clj` - tenant self-registration, authenticated profile lookup, and API-key rotation handlers.
- `test/drw/e2e_api/tenant_endpoints_test.clj` - real HTTP coverage for tenant endpoint behavior.

## Dependencies

- Upstream: `drw.http.json`, `drw.tenants.store`, `clojure.string`.
- Downstream: `src/drw/http/routes.clj` wires handlers into the Pedestal route table.

## Tests

- E2E tenant endpoint tests cover registration validation, auth-required profile lookup, and key rotation through the real server.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/http-api-auth-tenants.md`
- Related modules: `.agent/knowledge/modules/src-drw-http.md`, `.agent/knowledge/modules/src-drw-tenants.md`

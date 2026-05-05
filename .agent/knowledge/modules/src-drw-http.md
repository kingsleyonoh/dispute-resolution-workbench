# HTTP Module

## Purpose

Provides the Pedestal service map, route table, JSON helpers, API interceptors, health endpoint, tenant endpoints, workbench API endpoints, and server-rendered root page.

## Key files

- `src/drw/http/server.clj` - Pedestal service-map, server start, and server stop helpers.
- `src/drw/http/routes.clj` - route table for root, health, tenant lifecycle, dispute, exception, and counterparty routes.
- `src/drw/http/handlers.clj` - JSON health handler and Hiccup-rendered home handler.
- `src/drw/http/json.clj` - small JSON encoder, response helper, error response helper, and string-body parser.
- `src/drw/http/interceptors/` - request id, JSON encoding, rate limit, audit, API-key auth, and tenant binding interceptors.
- `test/drw/http/routes_test.clj` - route-table behavior.
- `test/drw/http/interceptors_test.clj` - interceptor behavior contracts.
- `test/drw/e2e_api/health_test.clj` - real HTTP E2E coverage for health and root routes.
- `test/drw/e2e_api/tenant_endpoints_test.clj` - real HTTP E2E coverage for tenant endpoints.
- `test/drw/e2e_api/workbench_endpoints_test.clj` - real HTTP E2E coverage for dispute, exception, and counterparty endpoints.

## Dependencies

- Upstream: `io.pedestal.http`, `io.pedestal.interceptor`, `drw.api.tenants`, `drw.api.disputes`, `drw.api.exceptions`, `drw.api.counterparties`, `drw.tenants.store`, `drw.ui.pages`, `hiccup2.core`.
- Downstream: `src/drw/core.clj` starts the server via `requiring-resolve`.

## Tests

- Route tests verify wired tenant/workbench routes and invalid dev-route flag handling.
- Interceptor tests verify request-id propagation, auth failures, tenant binding, and rate limiting.
- E2E tests start a real Pedestal server and request health, root, tenant lifecycle, dispute, exception, and counterparty endpoints.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/http-server-skeleton.md`, `.agent/knowledge/foundation/http-api-auth-tenants.md`

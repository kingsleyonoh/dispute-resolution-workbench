# HTTP Module

## Purpose

Provides the Pedestal service map, route table, and initial handlers for the health endpoint and server-rendered root page.

## Key files

- `src/drw/http/server.clj` - Pedestal service-map, server start, and server stop helpers.
- `src/drw/http/routes.clj` - route table for `GET /` and `GET /api/health`.
- `src/drw/http/handlers.clj` - JSON health handler and Hiccup-rendered home handler.
- `test/drw/http/routes_test.clj` - route-table behavior.
- `test/drw/e2e_api/health_test.clj` - real HTTP E2E coverage for health and root routes.

## Dependencies

- Upstream: `io.pedestal.http`, `drw.ui.pages`, `hiccup2.core`.
- Downstream: `src/drw/core.clj` starts the server via `requiring-resolve`.

## Tests

- Route tests verify the wired route table and invalid dev-route flag handling.
- E2E tests start a real Pedestal server and request `GET /api/health` and `GET /`.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/http-server-skeleton.md`

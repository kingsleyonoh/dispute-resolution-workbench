# HTTP Server Skeleton

## What it establishes

The app starts a Pedestal Jetty server from a service map whose route table is defined in `drw.http.routes`.

## Files

- `src/drw/http/server.clj` - Pedestal service-map and start/stop helpers.
- `src/drw/http/routes.clj` - route table.
- `src/drw/http/handlers.clj` - initial handlers.
- `test/drw/http/routes_test.clj` - route behavior tests.
- `test/drw/e2e_api/health_test.clj` - real HTTP route verification.

## When to read this

Before writing any code that:
- Adds or changes a route.
- Starts or stops the server.
- Changes handler response formats or resource serving.

## Contract

- New handlers must be wired into `drw.http.routes` in the same batch.
- OpenAPI and MCP route metadata must match routes actually wired in code.
- E2E coverage is required for route changes once the server surface is touched.
- Static assets are served from the Pedestal resource path `/public`.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-http.md`

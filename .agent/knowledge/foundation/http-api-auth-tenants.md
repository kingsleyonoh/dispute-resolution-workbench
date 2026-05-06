# HTTP API Auth And Tenants

## What it establishes

Pedestal API routes use a thin interceptor chain for request id, JSON body encoding, rate limiting, audit context, API-key auth, and tenant binding. Tenant self-registration and key rotation return raw API keys only once and store only hash plus prefix.

## Files

- `src/drw/http/interceptors/` - request id, rate-limit, audit, auth, and tenant interceptors.
- `src/drw/tenants/store.clj` - in-memory tenant profile, API-key hash, prefix lookup, and audit-log backing store.
- `src/drw/api/tenants.clj` - tenant registration, profile, and key rotation handlers.
- `test/drw/http/interceptors_test.clj` - interceptor behavior contracts.
- `test/drw/e2e_api/tenant_endpoints_test.clj` - real HTTP tenant endpoint coverage.

## When to read this

Before writing any code that:
- Adds or changes protected API routes.
- Reads `:current-tenant` from an HTTP request.
- Registers tenants or rotates API keys.
- Adds rate-limit or audit behavior to an endpoint.

## Contract

- Protected endpoints reject missing or invalid `X-API-Key` with `401 UNAUTHORIZED`.
- Disabled tenants return `403 TENANT_DISABLED`.
- Public endpoints remain explicit in `drw.http.interceptors.auth/public-routes`.
- Tenant profile responses must not expose `:tenant/api-key-hash` or raw keys.
- Raw API keys are returned only by registration and rotation responses.
- Route changes require real HTTP E2E coverage.

## Cross-references

- PRD sections: 5.1, 8b, 9, 14.

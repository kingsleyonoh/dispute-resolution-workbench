# API Module

## Purpose

Provides thin JSON API handlers that validate requests, call tenant-domain helpers, and return Pedestal response maps.

## Key files

- `src/drw/api/tenants.clj` - tenant self-registration, authenticated profile lookup, and API-key rotation handlers.
- `src/drw/api/common.clj` - shared API request parsing, actor/current-tenant lookup, response helpers, and domain error mapping.
- `src/drw/api/serializers.clj` - JSON response shapes for disputes, exceptions, correlations, timeline entries, and counterparties.
- `src/drw/api/disputes.clj` - dispute list/create/get plus assign, transition, comment, and attach-exception handlers.
- `src/drw/api/exceptions.clj` - manual exception list/create handlers.
- `src/drw/api/correlations.clj` - correlation candidate list/detail plus accept/reject handlers.
- `src/drw/api/counterparties.clj` - counterparty list/get/merge handlers.
- `test/drw/e2e_api/tenant_endpoints_test.clj` - real HTTP coverage for tenant endpoint behavior.
- `test/drw/e2e_api/workbench_endpoints_test.clj` - real HTTP coverage for dispute, exception, correlation, and counterparty endpoints.

## Dependencies

- Upstream: `drw.http.json`, `drw.tenants.store`, `drw.domain.disputes`, `drw.domain.exceptions`, `drw.domain.correlations`, `drw.domain.counterparties`, `clojure.string`.
- Downstream: `src/drw/http/routes.clj` wires handlers into the Pedestal route table.

## Tests

- E2E tenant endpoint tests cover registration validation, auth-required profile lookup, and key rotation through the real server.
- Workbench handler and E2E tests cover tenant isolation, validation errors, duplicate exception source refs, illegal transitions, cross-tenant 404s, and correlation accept/reject terminal behavior.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/http-api-auth-tenants.md`
- Related modules: `.agent/knowledge/modules/src-drw-http.md`, `.agent/knowledge/modules/src-drw-tenants.md`

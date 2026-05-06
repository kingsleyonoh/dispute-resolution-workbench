# API Module

## Purpose

Provides thin JSON API handlers that validate requests, call tenant-domain helpers, and return Pedestal response maps for tenant, workbench, ingestion, and playbook controls.

## Key files

- `src/drw/api/tenants.clj` - tenant self-registration, authenticated profile lookup, and API-key rotation handlers.
- `src/drw/api/common.clj` - shared API request parsing, actor/current-tenant lookup, response helpers, and domain error mapping.
- `src/drw/api/serializers.clj` - JSON response shapes for disputes, exceptions, correlations, timeline entries, counterparties, ingestion sources/runs, and playbooks.
- `src/drw/api/disputes.clj` - dispute list/create/get plus assign, transition, comment, and attach-exception handlers.
- `src/drw/api/exceptions.clj` - manual exception list/create handlers plus public HMAC-verified Hub exception ingestion.
- `src/drw/api/correlations.clj` - correlation candidate list/detail plus accept/reject handlers.
- `src/drw/api/ingestion.clj` - ingestion source list/save, pull-now, and run-history handlers.
- `src/drw/api/playbooks.clj` - playbook list/create/update/disable handlers.
- `src/drw/api/counterparties.clj` - counterparty list/get/merge handlers.
- `test/drw/api/ingestion_handlers_test.clj` - handler coverage for tenant-scoped ingestion source/runs APIs.
- `test/drw/api/hub_exception_ingress_test.clj` - handler coverage for Hub HMAC, tenant slug, validation, duplicate, and success cases.
- `test/drw/api/playbooks_handlers_test.clj` - handler coverage for tenant-scoped playbook CRUD, duplicate-code validation, and cross-tenant isolation.
- `test/drw/e2e_api/tenant_endpoints_test.clj` - real HTTP coverage for tenant endpoint behavior.
- `test/drw/e2e_api/workbench_endpoints_test.clj` - real HTTP coverage for dispute, exception, correlation, ingestion, and counterparty endpoints.
- `test/drw/e2e_api/hub_exception_ingress_test.clj` - real HTTP coverage for signed Hub exception ingestion.
- `test/drw/e2e_api/playbooks_endpoints_test.clj` - real HTTP coverage for playbook API create/list/update/disable and cross-tenant isolation.

## Dependencies

- Upstream: `drw.http.json`, `drw.security.hmac`, `drw.tenants.store`, `drw.domain.disputes`, `drw.domain.exceptions`, `drw.domain.correlations`, `drw.domain.ingestion-sources`, `drw.domain.playbooks`, `drw.domain.counterparties`, `clojure.string`.
- Downstream: `src/drw/http/routes.clj` wires handlers into the Pedestal route table.

## Tests

- E2E tenant endpoint tests cover registration validation, auth-required profile lookup, and key rotation through the real server.
- Workbench handler and E2E tests cover tenant isolation, validation errors, duplicate exception/source/playbook refs, illegal transitions, cross-tenant 404s, correlation decisions, ingestion controls, playbook controls, and Hub-routed HMAC ingress.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/http-api-auth-tenants.md`
- Related modules: `.agent/knowledge/modules/src-drw-http.md`, `.agent/knowledge/modules/src-drw-tenants.md`, `.agent/knowledge/modules/src-drw-security.md`

# `test/drw/test_containers.clj`

## Purpose

Provides reusable Testcontainers helpers for integration tests that need real local infrastructure or an HTTP upstream stub.

## Key files

- `test/drw/test_containers.clj` - Postgres/Redis image constants plus nginx upstream stub helpers, real Java HTTP recording transport, and path/query capture.
- `test/drw/integration/adapter_upstream_test.clj` - invoice reconciliation poll job integration test using the nginx upstream stub.
- `test/drw/integration/upstream_stub/api/discrepancies` - deterministic EDN response copied into nginx's static file root.

## Dependencies

- Upstream: `clj-test-containers.core`, `clojure.edn`, and JDK `java.net.http`.
- Downstream: integration tests that need container-hosted services, currently the adapter upstream coverage for invoice reconciliation polling.

## Tests

- `test/drw/integration/adapter_upstream_test.clj` starts nginx through Testcontainers, runs the invoice poll job against the mapped base URL, records the real HTTP request path/query and headers, and verifies stored exception data plus tenant isolation.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-adapters.md`, `.agent/knowledge/modules/src-drw-jobs.md`
- Related pattern: `.agent/knowledge/patterns/002-container-backed-upstream-adapter-tests.md`
- Related gotcha: `.agent/knowledge/gotchas/2026-05-05-testcontainers-docker-socket.md`

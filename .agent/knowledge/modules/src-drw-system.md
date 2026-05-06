# Runtime System Checks Module

## Purpose

Owns setup-time health checks and storage configuration for Datomic Local, Datomic SQL storage, PostgreSQL, and Redis.

## Key files

- `src/drw/system.clj` - Datomic Local client options, SQL transactor config parsing, Postgres smoke query, and Redis ping.
- `resources/datomic/sql-transactor.properties` - Datomic Pro SQL storage properties for local Postgres.
- `resources/datomic/README.md` - Datomic Local and SQL storage setup notes.
- `test/drw/system_config_test.clj` - config construction and validation tests.
- `test/drw/integration/smoke_test.clj` - smoke-test surface.

## Dependencies

- Upstream: `datomic.client.api`, `next.jdbc`, `taoensso.carmine`, `clojure.java.io`.
- Downstream: `src/drw/setup.clj` and setup/integration tests.

## Tests

- `test/drw/system_config_test.clj` covers Datomic Local URI validation, absolute storage-dir conversion, and SQL storage config.

## Cross-references

- Related gotchas: `.agent/knowledge/gotchas/2026-05-05-datomic-local-storage-dir.md`
- Related foundation primitives: `.agent/knowledge/foundation/runtime-system-checks.md`

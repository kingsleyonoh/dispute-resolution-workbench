# Core And Setup Entrypoints

## Purpose

Provides executable entry points for starting the app and running first-run smoke checks.

## Key files

- `src/drw/core.clj` - `-main` loads config and starts the Pedestal server.
- `src/drw/setup.clj` - `-main` loads config and runs setup smoke checks.
- `build.clj` - uberjar build task.
- `Dockerfile` - asset, dependency, dev, build, and production image stages.
- `docker-compose.yml` - local Postgres, Redis, Datomic Local volume, and app profile.

## Dependencies

- Upstream: `drw.config`, `drw.system`, and `drw.http.server`.
- Downstream: `clojure -M:dev`, `clojure -M:setup`, Docker Compose, and the production jar entry point.

## Tests

- `test/drw/setup_tooling_test.clj` checks setup/deployment files.
- `test/drw/integration/smoke_test.clj` covers smoke setup behavior.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/core-config-loading.md`, `.agent/knowledge/foundation/runtime-system-checks.md`, `.agent/knowledge/foundation/http-server-skeleton.md`

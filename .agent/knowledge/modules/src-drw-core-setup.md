# Core And Setup Entrypoints

## Purpose

Provides executable entry points for starting the app and running first-run smoke checks.

## Key files

- `src/drw/core.clj` - `-main` loads config and starts the Pedestal server.
- `src/drw/setup.clj` - `run-first-run!` executes structured schema, status, tenant render, Datomic, Postgres, and Redis setup checks; `-main` prints the summary and exits non-zero on failure.
- `build.clj` - uberjar build task.
- `Dockerfile` - asset, dependency, dev, build, and production image stages.
- `docker-compose.yml` - local Postgres, Redis, Datomic Local volume, and app profile.
- `scripts/first-run-setup.ps1` - PowerShell wrapper that delegates to `clojure -M:setup`.

## Dependencies

- Upstream: `drw.config`, `drw.db.schema`, `drw.domain.reports`, `drw.fixtures`, `drw.system`, and `drw.http.server`.
- Downstream: `clojure -M:dev`, `clojure -M:setup`, Docker Compose, and the production jar entry point.

## Tests

- `test/drw/setup_tooling_test.clj` checks setup/deployment files.
- `test/drw/setup_first_run_test.clj` covers structured first-run evidence, failure reporting, and the PowerShell wrapper.
- `test/drw/integration/smoke_test.clj` covers smoke setup behavior.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/core-config-loading.md`, `.agent/knowledge/foundation/runtime-system-checks.md`, `.agent/knowledge/foundation/http-server-skeleton.md`

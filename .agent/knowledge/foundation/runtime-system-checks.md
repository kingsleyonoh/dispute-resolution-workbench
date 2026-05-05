# Runtime System Checks

## What it establishes

Setup checks use the same runtime config shape as the app and validate local Datomic, SQL storage, Postgres, and Redis assumptions early.

## Files

- `src/drw/system.clj` - service checks and Datomic SQL storage config parser.
- `src/drw/setup.clj` - first-run setup command.
- `resources/datomic/sql-transactor.properties` - Datomic Pro SQL storage settings.
- `test/drw/system_config_test.clj` - system config contract tests.

## When to read this

Before writing any code that:
- Creates Datomic client options or Datomic SQL storage config.
- Adds a first-run setup check.
- Changes local Postgres, Redis, or Datomic environment variables.

## Contract

- Datomic Local storage directories must be absolute before constructing client options.
- Datomic Local URIs must include both system and database name.
- Setup checks must read from normalized config, not parallel test-only config.
- Services controlled locally should be exercised through real clients where feasible.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-system.md`
- Related gotchas: `.agent/knowledge/gotchas/2026-05-05-datomic-local-storage-dir.md`

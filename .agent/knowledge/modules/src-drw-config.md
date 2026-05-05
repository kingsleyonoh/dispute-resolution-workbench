# Runtime Config Module

## Purpose

Loads required runtime configuration from the process environment or an explicit `.env` file and normalizes values for startup, setup checks, tenant API settings, and ecosystem client toggles.

## Key files

- `src/drw/config.clj` - config loader, required env validation, integer parsing, `.env` parsing, tenant self-registration/API-key prefix settings, and Notification Hub/Workflow Engine toggles.
- `test/drw/config_test.clj` - config parsing and required-env coverage.

## Dependencies

- Upstream: `clojure.string`.
- Downstream: `src/drw/core.clj`, `src/drw/setup.clj`, `src/drw/http/routes.clj`, ecosystem clients, and tests that derive config from `.env.example`.

## Tests

- `test/drw/config_test.clj` verifies required env handling and typed config normalization.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/core-config-loading.md`

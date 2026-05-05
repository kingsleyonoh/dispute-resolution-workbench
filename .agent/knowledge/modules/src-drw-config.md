# Runtime Config Module

## Purpose

Loads required runtime configuration from the process environment or an explicit `.env` file and normalizes values for startup, setup checks, tenant API settings, ecosystem client toggles, and reconciliation adapter poll settings.

## Key files

- `src/drw/config.clj` - config loader, required env validation, integer parsing, `.env` parsing, tenant self-registration/API-key prefix settings, Notification Hub/Workflow Engine toggles, and Invoice/Transaction Reconciliation adapter settings.
- `test/drw/config_test.clj` - config parsing and required-env coverage.

## Dependencies

- Upstream: `clojure.string`.
- Downstream: `src/drw/core.clj`, `src/drw/setup.clj`, `src/drw/http/routes.clj`, ecosystem clients, reconciliation poll jobs, and tests that derive config from `.env.example`.

## Tests

- `test/drw/config_test.clj` verifies required env handling, typed config normalization, default reconciliation poll intervals, and explicit reconciliation adapter env parsing.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/core-config-loading.md`

# Runtime Config Module

## Purpose

Loads required runtime configuration from the process environment or an explicit `.env` file and normalizes values for startup, setup checks, tenant API settings, ecosystem client toggles, reconciliation/contract/Webhook adapter settings, and NATS settings.

## Key files

- `src/drw/config.clj` - config loader, required env validation, integer parsing, `.env` parsing, tenant self-registration/API-key prefix settings, Notification Hub/Workflow Engine toggles, Invoice/Transaction Reconciliation settings, Contract Lifecycle settings, Webhook Engine DLQ poll settings, and NATS settings.
- `test/drw/config_test.clj` - config parsing and required-env coverage.

## Dependencies

- Upstream: `clojure.string`.
- Downstream: `src/drw/core.clj`, `src/drw/setup.clj`, `src/drw/http/routes.clj`, ecosystem clients, reconciliation poll jobs, Contract Lifecycle backfill/NATS jobs, Webhook Engine DLQ poll job, and tests that derive config from `.env.example`.

## Tests

- `test/drw/config_test.clj` verifies required env handling, typed config normalization, default reconciliation/contract/Webhook poll intervals, NATS defaults, and explicit adapter env parsing.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/core-config-loading.md`

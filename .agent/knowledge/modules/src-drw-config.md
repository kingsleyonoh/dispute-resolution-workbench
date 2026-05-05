# Runtime Config Module

## Purpose

Loads required runtime configuration from the process environment or an explicit `.env` file and normalizes values for startup and setup checks.

## Key files

- `src/drw/config.clj` - config loader, required env validation, integer parsing, and `.env` parsing.
- `test/drw/config_test.clj` - config parsing and required-env coverage.

## Dependencies

- Upstream: `clojure.string`.
- Downstream: `src/drw/core.clj`, `src/drw/setup.clj`, and tests that derive config from `.env.example`.

## Tests

- `test/drw/config_test.clj` verifies required env handling and typed config normalization.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/core-config-loading.md`

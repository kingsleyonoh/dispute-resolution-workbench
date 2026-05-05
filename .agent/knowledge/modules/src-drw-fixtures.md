# `src/drw/fixtures.clj`

## Purpose

Loads production resource-backed tenant fixtures and enforces required tenant identity fields.

## Key files

- `src/drw/fixtures.clj` - validates and loads tenants from `resources/fixtures/tenants.edn`.
- `resources/fixtures/tenants.edn` - seed-quality tenant identity data with Acme and Globex fixtures.

## Dependencies

- Upstream: `clojure.java.io`, `clojure.edn`, `clojure.string`.
- Downstream: snapshot tests and any future setup/seed path that needs canonical tenant fixture data.

## Tests

- `test/drw/fixtures_test.clj` asserts at least two distinct tenants and required identity-field validation.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/tenant-fixtures.md`


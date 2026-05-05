# `src/drw/templates/`

## Purpose

Provides strict undefined-token lookup for config-driven templates.

## Key files

- `src/drw/templates/strict_fetch.clj` - resolves nested token paths and throws `:strict-undefined` on missing tokens.

## Dependencies

- Upstream: `clojure.string`.
- Downstream: future Selmer/report rendering code should use this strict lookup contract.

## Tests

- `test/drw/templates/strict_fetch_test.clj` verifies present tokens resolve and missing token paths throw with token metadata.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/template-strict-fetch.md`


# Datomic Schema Resource

## What it establishes

Datomic schema lives in the production resource `resources/datomic/schema.edn`, with pure Clojure status-transition validators until Pro tx-function installation is wired.

## Files

- `resources/datomic/schema.edn` - Section 4 attributes and documented tx-function specs.
- `src/drw/db/schema.clj` - schema loader and transition validator API.
- `test/drw/db/schema_test.clj` - schema shape and transition tests.

## When to read this

Before writing any code that:
- Adds or changes Datomic attributes.
- Uses dispute, correlation, or report status transitions.
- Installs or invokes Datomic tx functions.

## Contract

- Keep schema in the resource file, not in parallel test-only definitions.
- `attributes` excludes `drw.tx` specs; `tx-function-specs` returns only `drw.tx` entries.
- Illegal status transitions throw `:illegal-status-transition`.
- Executable Datomic Pro tx-function installation is not wired yet; do not claim transactor-level enforcement until that integration exists.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-db.md`


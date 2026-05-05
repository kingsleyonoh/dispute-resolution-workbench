# `src/drw/audit/`

## Purpose

Builds tenant-scoped append-only audit transaction maps.

## Key files

- `src/drw/audit/recorder.clj` - validates required audit event fields, encodes before/after state as JSON strings, and rejects retraction ops.

## Dependencies

- Upstream: `clojure.string`, Java `Instant`, Java `UUID`.
- Downstream: future mutation handlers and workflow callbacks should route audit writes through this recorder.

## Tests

- `test/drw/audit/recorder_test.clj` covers valid audit tx construction, required tenant scope, and append-only enforcement.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/audit-append-only.md`


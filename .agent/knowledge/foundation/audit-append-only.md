# Audit Append Only

## What it establishes

Audit writes are tenant-scoped insert-only records with encoded before/after state.

## Files

- `src/drw/audit/recorder.clj` - audit transaction construction and append-only assertion.
- `test/drw/audit/recorder_test.clj` - required-field and no-retract tests.

## When to read this

Before writing any code that:
- Records mutation history.
- Builds timeline/audit integration.
- Adds workflow or notification callbacks with auditable side effects.

## Contract

- `:tenant-id`, actor, action, entity kind, and entity id are required.
- Audit tx data must not contain `:db/retract`.
- Before/after state is encoded as JSON strings by the recorder.
- Consumers should call the recorder rather than constructing audit maps inline.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-audit.md`


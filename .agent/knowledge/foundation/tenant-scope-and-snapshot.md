# Tenant Scope And Snapshot

## What it establishes

Tenant-scoped lookups fail closed and tenant identity snapshots are immutable inputs for config-driven surfaces.

## Files

- `src/drw/db/scope.clj` - tenant-scoped collection lookup helpers.
- `src/drw/tenants/snapshot.clj` - tenant identity snapshot capture.
- `test/drw/tenants/snapshot_test.clj` - missing tenant and cross-tenant literal tests.

## When to read this

Before writing any code that:
- Looks up tenant-scoped entities.
- Renders report, email, PDF, or template surfaces.
- Captures tenant identity for audit/history-sensitive artifacts.

## Contract

- Use tenant-scope helpers instead of ad hoc default/fallback lookup.
- Missing tenant ids throw `:tenant/not-found`.
- Snapshot capture must require every mapped identity field.
- Re-renderable artifacts should use captured snapshots, not live tenant lookup.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-db.md`, `.agent/knowledge/modules/src-drw-tenants.md`


# Tenant Fixtures

## What it establishes

Tenant fixtures are resource-backed production data, not parallel test-only shape definitions.

## Files

- `resources/fixtures/tenants.edn` - Acme and Globex tenant identity fixtures.
- `src/drw/fixtures.clj` - fixture loading and identity-field validation.
- `test/drw/fixtures_test.clj` - two-tenant and required-field tests.

## When to read this

Before writing any code that:
- Seeds tenant data.
- Builds tests for tenant-scoped data.
- Adds identity fields used in templates, reports, emails, or audit artifacts.

## Contract

- Keep at least two tenants with intentionally distinct identity literals.
- Add new required tenant identity fields to `tenant-identity-fields`, `required-tenant-fields`, and the EDN resource together.
- Tests must derive from `resources/fixtures/tenants.edn`, not private fixture maps.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-fixtures.md`


# Template Strict Fetch

## What it establishes

Template token lookup is strict: missing tokens throw instead of rendering empty strings.

## Files

- `src/drw/templates/strict_fetch.clj` - nested token lookup and Selmer-compatible missing-value hook.
- `test/drw/templates/strict_fetch_test.clj` - present-token and missing-token tests.

## When to read this

Before writing any code that:
- Adds a template renderer.
- Introduces template tokens for reports, email, PDF, or exports.
- Handles missing template data.

## Contract

- Missing tokens throw `:strict-undefined`.
- Do not hardcode tenant identity literals to satisfy missing template data.
- Extend schema/snapshot fields first when a required token has no backing data.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-templates.md`


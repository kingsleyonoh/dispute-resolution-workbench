# `src/drw/templates/`

## Purpose

Provides strict undefined-token lookup and Selmer-backed report rendering for config-driven templates.

## Key files

- `src/drw/templates/strict_fetch.clj` - resolves nested token paths and throws `:strict-undefined` on missing tokens.
- `src/drw/templates/renderer.clj` - renders Selmer templates with strict options, supports injected PDF bytes, shells out to `wkhtmltopdf` by default, stores PDFs, and computes SHA-256.
- `resources/templates/pdf/dispute_audit.html` - audit PDF HTML template using frozen tenant snapshot, dispute, exceptions, timeline, and audit-log data.

## Dependencies

- Upstream: `clojure.string`, `selmer.parser`, `clojure.java.shell`.
- Downstream: `drw.domain.reports` uses strict Selmer rendering and PDF storage helpers.

## Tests

- `test/drw/templates/strict_fetch_test.clj` verifies present tokens resolve and missing token paths throw with token metadata.
- `test/drw/domain/reports_test.clj` exercises the report renderer through template-backed audit HTML and injected PDF artifact generation.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/template-strict-fetch.md`

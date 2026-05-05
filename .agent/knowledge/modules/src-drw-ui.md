# UI Module

## Purpose

Defines the initial server-rendered Hiccup shell and HTMX-ready home page for the operations console.

## Key files

- `src/drw/ui/layout.clj` - shared HTML shell, Tailwind stylesheet link, and HTMX script include.
- `src/drw/ui/pages.clj` - root workbench page.
- `resources/assets/styles/app.css` - Tailwind input stylesheet.
- `resources/public/assets/app.css` - generated Tailwind CSS served from Pedestal resources.
- `tailwind.config.js` - Tailwind content scan config.
- `test/drw/ui/layout_test.clj` - shell rendering behavior.

## Dependencies

- Upstream: `clojure.string` and Hiccup rendering through HTTP handlers.
- Downstream: `src/drw/http/handlers.clj`.

## Tests

- `test/drw/ui/layout_test.clj` verifies page-title validation and shell content.
- `test/drw/e2e_api/health_test.clj` verifies root HTML over real HTTP.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/ui-htmx-shell.md`

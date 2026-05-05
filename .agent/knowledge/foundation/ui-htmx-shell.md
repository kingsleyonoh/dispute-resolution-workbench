# UI HTMX Shell

## What it establishes

The UI is server-rendered Hiccup with HTMX loaded in the shared shell and Tailwind CSS generated into `resources/public/assets/app.css`.

## Files

- `src/drw/ui/layout.clj` - shared shell.
- `src/drw/ui/pages.clj` - initial root page.
- `resources/assets/styles/app.css` - Tailwind input.
- `resources/public/assets/app.css` - generated CSS output.
- `package.json` - Tailwind build scripts.
- `test/drw/ui/layout_test.clj` - shell tests.

## When to read this

Before writing any code that:
- Adds a server-rendered page.
- Changes global UI chrome, HTMX loading, or asset paths.
- Updates Tailwind build inputs or output location.

## Contract

- Pages must render through `layout/app-shell` unless they are a deliberate non-HTML endpoint.
- `app-shell` requires a non-blank page title.
- Tailwind output belongs under `resources/public/assets/` so Pedestal can serve it.
- Keep the UI server-rendered unless a PRD change explicitly introduces a client app.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-ui.md`

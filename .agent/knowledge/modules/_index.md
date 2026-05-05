# Modules — Index

> **One file per module.** This index is a human-readable catalog, rewritten by the AI whenever a sibling file is added, renamed, or removed. Never append to a single growing table — write a new sibling instead. See `.agent/rules/CODING_STANDARDS.md` — "Append-Only Knowledge Files Banned."

## Catalog

| File | Summary |
|------|---------|
| `src-drw-config.md` | Runtime config loading from env data or `.env` files. |
| `src-drw-system.md` | Datomic, Postgres, and Redis setup-time system checks. |
| `src-drw-http.md` | Pedestal service map, route table, and initial handlers. |
| `src-drw-ui.md` | Hiccup/HTMX/Tailwind UI shell and root page. |
| `src-drw-core-setup.md` | App startup, setup smoke command, Docker, and build entry points. |
| `EXAMPLE.md` | Template showing the expected shape — delete once a real module exists. |

## How to add a new module

1. Filename pattern: mirror the source path, converting slashes to hyphens (e.g. `src/documents/composer/` → `src-documents-composer.md`).
2. Use the Purpose / Key files / Dependencies / Tests shape from `EXAMPLE.md`.
3. Add one row to the `## Catalog` table above.
4. When the module is removed or renamed, delete or rename this file in the same batch — never leave stale module files.

## Why directory-per-kind

A `## Key Modules` table in `CODEBASE_CONTEXT.md` has to cover every module in the project. Small projects get away with a single table; real projects accumulate 20-100 modules and the table becomes unreadable. One file per module keeps each description scoped to its own context, and deletion is trivial when the module is removed.

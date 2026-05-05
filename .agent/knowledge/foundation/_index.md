# Shared Foundation — Index

> **One file per foundation primitive.** This index is a human-readable catalog, rewritten by the AI whenever a sibling file is added, renamed, or removed. Never append to a single growing table — write a new sibling instead. See `.agent/rules/CODING_STANDARDS.md` — "Append-Only Knowledge Files Banned."

## Catalog

| File | Summary |
|------|---------|
| `core-config-loading.md` | Runtime config is loaded and normalized at the boundary before startup consumers use it. |
| `runtime-system-checks.md` | Setup checks validate Datomic, SQL storage, Postgres, and Redis assumptions from normalized config. |
| `db-datomic-schema.md` | Datomic schema lives in a production resource with pure status-transition validators. |
| `tenant-fixtures.md` | Tenant fixtures are resource-backed and must include two distinct identity sets. |
| `tenant-scope-and-snapshot.md` | Tenant lookups fail closed and tenant snapshots freeze identity for config-driven surfaces. |
| `template-strict-fetch.md` | Template token lookup throws on missing values instead of rendering empty strings. |
| `audit-append-only.md` | Audit transactions are tenant-scoped insert-only records. |
| `http-server-skeleton.md` | Pedestal starts from a route table that must stay aligned with OpenAPI and E2E coverage. |
| `ui-htmx-shell.md` | Server-rendered Hiccup pages use the shared HTMX shell and generated Tailwind asset path. |
| `EXAMPLE.md` | Template showing the expected shape — delete once a real foundation primitive exists. |

## What belongs here

Primitives imported by 3+ modules or that establish a project-wide contract. Examples: config loading, DB pool bootstrap, HTTP server bootstrap, auth middleware, shared error types, logging, feature flags, i18n.

## Mandatory reading rule

`CODING_STANDARDS.md` requires these files to be read **in full** before writing any new code that touches the surface they establish. The individual files in this directory replace the old flat `## Shared Foundation` table in `CODEBASE_CONTEXT.md`.

## How to add a new foundation primitive

1. Filename pattern: `category-slug.md` (e.g. `core-config-loading.md`, `db-pool-singleton.md`, `plugin-auth.md`).
2. Use the What it establishes / Files / When to read shape from `EXAMPLE.md`.
3. Add one row to the `## Catalog` table above.

## Why directory-per-kind

Shared Foundation grows every time a new cross-cutting primitive lands. One row per primitive in a flat table becomes impossible to maintain once the project has 10+ primitives. Directory-per-kind scales — and each file is the right size to read "in full" without triggering context pressure.

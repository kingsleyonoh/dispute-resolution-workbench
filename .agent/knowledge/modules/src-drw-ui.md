# UI Module

## Purpose

Defines the server-rendered Hiccup/Tailwind operator console: login, dashboard, dispute queue/detail actions, manual exception attachment, counterparty pages, form parsing, and tenant session lookup.

## Key files

- `src/drw/ui/layout.clj` - shared HTML shell, Tailwind stylesheet link, and HTMX script include.
- `src/drw/ui/pages.clj` - login, dashboard, dispute list/detail, action forms, exception attach, and counterparty pages.
- `src/drw/ui/handlers.clj` - Pedestal page handlers for login/logout, tenant-gated pages, and POST/303 operator actions.
- `src/drw/ui/request.clj` - URL-encoded form parser plus UUID, keyword, long, and instant coercion helpers.
- `src/drw/ui/session.clj` - process-local UI sessions keyed by `drw_session`, with `X-DRW-Session` and `X-API-Key` lookup support.
- `resources/assets/styles/app.css` - Tailwind input stylesheet.
- `resources/public/assets/app.css` - generated Tailwind CSS served from Pedestal resources.
- `tailwind.config.js` - Tailwind content scan config.
- `test/drw/ui/layout_test.clj` - shell rendering behavior.
- `test/drw/ui/pages_test.clj` - login, dashboard, dispute action, exception, and counterparty rendering behavior.
- `test/drw/e2e_api/ui_flow_test.clj` - real HTTP operator flow through login, create, assign, transition, comment, attach exception, and counterparty pages.

## Dependencies

- Upstream: Hiccup, `drw.domain.disputes`, `drw.domain.exceptions`, `drw.domain.counterparties`, and `drw.tenants.store`.
- Downstream: `src/drw/http/routes.clj` wires page routes through `drw.ui.handlers`.

## Tests

- `test/drw/ui/layout_test.clj` verifies page-title validation and shell content.
- `test/drw/ui/pages_test.clj` verifies rendered page surfaces and dispute/counterparty actions.
- `test/drw/e2e_api/health_test.clj` verifies root HTML over real HTTP.
- `test/drw/e2e_api/ui_flow_test.clj` verifies the first tenant operator flow over a running Pedestal server.

## Notes

- UI handlers deliberately call the process-local domain helpers directly with the resolved tenant id and actor slug; they do not call JSON API handlers or keep a separate UI store.
- Authenticated UI requests accept the normal session cookie and an API-key fallback for server-rendered form tests or HTMX-style authenticated requests.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/ui-htmx-shell.md`

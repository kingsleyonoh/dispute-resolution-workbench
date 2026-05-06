# UI Module

## Purpose

Defines the server-rendered Hiccup/Tailwind operator console: login, dashboard, dispute queue/detail actions, manual exception attachment, counterparty pages, correlation review, ingestion/playbook settings, form parsing, and tenant session lookup.

## Key files

- `src/drw/ui/layout.clj` - shared HTML shell, Tailwind stylesheet link, and HTMX script include.
- `src/drw/ui/pages.clj` - login, dashboard, dispute list/detail, action forms, exception attach, counterparty pages, correlation queue page shell, and ingestion settings page shell.
- `src/drw/ui/correlations.clj` - pending correlation queue section with accept/reject POST forms.
- `src/drw/ui/ingestion.clj` - source setting forms, pull-now buttons, and recent ingestion run table.
- `src/drw/ui/playbooks.clj` - playbook settings page with add/edit/disable forms.
- `src/drw/ui/resolution.clj` - dispute-detail Start Resolution panel backed by active tenant playbooks.
- `src/drw/ui/handlers.clj` - Pedestal page handlers for login/logout, tenant-gated pages, and POST/303 operator actions including correlation decisions and ingestion settings.
- `src/drw/ui/playbook_handlers.clj` - tenant-gated Playbooks settings GET/POST handlers with CSRF validation.
- `src/drw/ui/resolution_handlers.clj` - CSRF-protected UI start-resolution POST handler.
- `src/drw/ui/request.clj` - URL-encoded form parser plus UUID, keyword, long, and instant coercion helpers.
- `src/drw/ui/session.clj` - process-local UI sessions keyed by `drw_session`, with `X-DRW-Session` and `X-API-Key` lookup support.
- `resources/assets/styles/app.css` - Tailwind input stylesheet.
- `resources/public/assets/app.css` - generated Tailwind CSS served from Pedestal resources.
- `tailwind.config.js` - Tailwind content scan config.
- `test/drw/ui/layout_test.clj` - shell rendering behavior.
- `test/drw/ui/pages_test.clj` - login, dashboard, dispute action, exception, counterparty, correlation, and ingestion settings rendering behavior.
- `test/drw/ui/playbooks_test.clj` - playbook settings rendering behavior.
- `test/drw/e2e_api/ui_flow_test.clj` - real HTTP operator flow through login, create, assign, transition, comment, attach exception, counterparty pages, correlation review, and ingestion settings.
- `test/drw/e2e_api/ui_playbooks_flow_test.clj` - real HTTP Playbooks settings add/disable flow.
- `test/drw/e2e_api/ui_start_resolution_flow_test.clj` - real HTTP dispute-detail start-resolution form flow.

## Dependencies

- Upstream: Hiccup, `drw.domain.disputes`, `drw.domain.exceptions`, `drw.domain.correlations`, `drw.domain.ingestion-sources`, `drw.domain.playbooks`, `drw.domain.counterparties`, and `drw.tenants.store`.
- Downstream: `src/drw/http/routes.clj` wires page routes through `drw.ui.handlers`.

## Tests

- `test/drw/ui/layout_test.clj` verifies page-title validation and shell content.
- `test/drw/ui/pages_test.clj` verifies rendered page surfaces and dispute/counterparty/correlation/ingestion actions.
- `test/drw/e2e_api/health_test.clj` verifies root HTML over real HTTP.
- `test/drw/e2e_api/ui_flow_test.clj` verifies the first tenant operator flow, correlation acceptance, and ingestion settings/pull-now over a running Pedestal server. `ui_playbooks_flow_test.clj` verifies Playbooks settings over real HTTP.

## Notes

- UI handlers deliberately call the process-local domain helpers directly with the resolved tenant id and actor slug; they do not call JSON API handlers or keep a separate UI store.
- Authenticated UI requests accept the normal session cookie and an API-key fallback for server-rendered form tests or HTMX-style authenticated requests.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/ui-htmx-shell.md`

# Dispute Resolution Workbench - Codebase Context

> Last updated: 2026-05-05
> Template synced: 2026-05-04
> Source PRD: `docs/dispute-resolution-workbench_prd.md`

## Project Summary
Tenant-scoped dispute operations system for finance teams, covering manual exceptions, dispute queues, SLA tracking, future ecosystem feeds, Workflow Engine resolutions, Notification Hub events, and immutable Datomic audit history.

## Tech Stack
| Layer | Technology |
|---|---|
| Language | Clojure 1.12 |
| Framework | Pedestal 0.7.2 |
| Database | Datomic Local for setup smoke checks; Datomic Pro SQL storage config for PostgreSQL 16; resource-backed Datomic Section 4 schema |
| Cache / Queue | Redis 7 with Carmine |
| SQL access | next.jdbc |
| UI | Hiccup 2, HTMX 2.0.4, Tailwind CSS 3.4 |
| Auth | X-API-Key tenant interceptor; UI session auth planned |
| Tests | clojure.test, Kaocha, Testcontainers, Playwright via clj-chrome-devtools |
| Jobs | core.async and Carmine-backed scheduler |
| Deploy | Docker Compose on Hetzner VPS at `disputes.kingsleyonoh.com` |
| Observability | Sentry, Axiom/timbre, Prometheus/iapetos, BetterStack |

## Commands
| Task | Command |
|---|---|
| Install deps | `clojure -P` |
| Run dev server | `clojure -M:dev` |
| Run tests | `clojure -M:test` |
| Run tests (unit only) | `clojure -M:test --focus unit` |
| Run tests (integration only) | `clojure -M:test --focus integration` |
| Run tests (E2E only) | `clojure -M:test:e2e` |
| Lint | `clojure -M:clj-kondo --lint src test` |
| Format check | `clojure -M:cljfmt check` |
| Build | `clojure -T:build uber` |
| Build CSS | `npm run build:css` |
| Watch CSS | `npm run watch:css` |
| Start infra | `docker compose up -d postgres redis` |
| Stop infra | `docker compose down` |
| Check infra | `docker compose ps` |
| Start app via Compose | `docker compose --profile app up app` |
| First-run setup | `clojure -M:setup` |

## Project Structure
| Path | Purpose |
|---|---|
| `src/drw/core.clj` | Application entry point |
| `src/drw/config.clj` | Environment and `.env` config loading |
| `src/drw/system.clj` | Datomic Local, Datomic SQL storage, Postgres, and Redis smoke helpers |
| `src/drw/setup.clj` | First-run setup smoke command |
| `src/drw/db/` | Datomic schema loading, status transition validation, and tenant-scoped collection helpers |
| `src/drw/domain/` | Process-local core domain layer for counterparties, disputes, manual exceptions, timeline rows, and audit rows |
| `src/drw/jobs/` | Offline jobs, currently the SLA overdue dispute reaper |
| `src/drw/fixtures.clj` | Resource-backed tenant fixture loader with identity-field validation |
| `src/drw/tenants/` | Tenant identity snapshot capture for config-driven surfaces |
| `src/drw/templates/` | Strict template token lookup helpers |
| `src/drw/audit/` | Append-only audit transaction construction |
| `src/drw/api/` | JSON API handlers for tenant lifecycle plus dispute, exception, and counterparty queue operations |
| `src/drw/ecosystem/` | Disabled-by-default Notification Hub and Workflow Engine client stubs |
| `src/drw/http/` | Pedestal server, routes, JSON helpers, and interceptor chain |
| `src/drw/ui/` | Hiccup layout and HTMX-ready pages |
| `resources/assets/styles/app.css` | Tailwind input stylesheet |
| `resources/public/assets/app.css` | Generated Tailwind output served by Pedestal |
| `resources/datomic/` | Datomic Local notes, SQL transactor properties, and Section 4 schema EDN |
| `resources/fixtures/` | Seed-quality tenant identity fixtures used by tests and snapshot helpers |
| `test/drw/` | Unit, integration, system, UI, route, and E2E tests |
| `.agent/knowledge/modules/` | One file per source module |

## Database Overview
`resources/datomic/schema.edn` defines Section 4 Datomic attributes for tenants, users, counterparties, disputes, exceptions, correlation candidates, timeline entries, SLA policies, playbooks, ingestion sources/runs, audit log rows, and report artifacts. `drw.db.schema` loads the resource, separates tx-function specs, and validates dispute, correlation, and report status transitions as pure Clojure until executable Datomic Pro tx-function installation is wired.

## Environment Variables
See `.env.example`. Runtime-required keys enforced by `drw.config`: `APP_ENV`, `PORT`, `DATABASE_URL`, `DATOMIC_URI`, `REDIS_URL`, `SESSION_SECRET`. Setup also reads `DATABASE_POOL`, `DATOMIC_STORAGE_DIR`, and `DATOMIC_SQL_TRANSACTOR_PROPERTIES`. Tenant API settings include `SELF_REGISTRATION_ENABLED` and `API_KEY_PREFIX`. Ecosystem client stubs read `NOTIFICATION_HUB_ENABLED`, `NOTIFICATION_HUB_URL`, `NOTIFICATION_HUB_API_KEY`, `WORKFLOW_ENGINE_ENABLED`, `WORKFLOW_ENGINE_URL`, and `WORKFLOW_ENGINE_API_KEY`.

## External Integrations
| System | Direction | Method | Env |
|---|---|---|---|
| Invoice Reconciliation Engine | inbound pull | REST | `INVOICE_RECON_URL`, `INVOICE_RECON_API_KEY` |
| Contract Lifecycle Engine | inbound pull/subscribe | REST + NATS | `CONTRACT_LIFECYCLE_URL`, `NATS_URL` |
| Transaction Reconciliation Engine | inbound pull | REST | `TRANSACTION_RECON_URL` |
| Webhook Ingestion Engine | inbound pull | REST | `WEBHOOK_ENGINE_URL` |
| Notification Hub | outbound | REST events | `NOTIFICATION_HUB_URL`, `NOTIFICATION_HUB_API_KEY` |
| Workflow Automation Engine | outbound | REST workflow execute | `WORKFLOW_ENGINE_URL`, `WORKFLOW_ENGINE_API_KEY` |
| Hub Ingress | inbound | HMAC webhook | `HUB_INGRESS_SECRET` |

## API Surface
| Route | Handler | Purpose |
|---|---|---|
| `GET /` | `drw.http.handlers/home` | Server-rendered HTMX console skeleton |
| `GET /api/health` | `drw.http.handlers/health` | JSON liveness check |
| `POST /api/tenants/register` | `drw.api.tenants/register-handler` | Public self-registration that returns the raw API key once |
| `GET /api/tenants/me` | `drw.api.tenants/profile-handler` | Authenticated tenant profile lookup |
| `GET /tenants/me` | `drw.api.tenants/profile-handler` | Compatibility tenant profile lookup |
| `POST /api/tenants/rotate-key` | `drw.api.tenants/rotate-key-handler` | Authenticated API key rotation |
| `GET /api/disputes` | `drw.api.disputes/list-handler` | List disputes with queue filters |
| `POST /api/disputes` | `drw.api.disputes/create-handler` | Create a manual dispute |
| `GET /api/disputes/:id` | `drw.api.disputes/get-handler` | Fetch dispute detail |
| `PATCH /api/disputes/:id/assign` | `drw.api.disputes/assign-handler` | Assign a dispute to a user |
| `PATCH /api/disputes/:id/transition` | `drw.api.disputes/transition-handler` | Apply status transition |
| `POST /api/disputes/:id/comments` | `drw.api.disputes/comment-handler` | Append timeline comment |
| `POST /api/disputes/:id/attach-exception` | `drw.api.disputes/attach-exception-handler` | Attach exception |
| `GET /api/exceptions` | `drw.api.exceptions/list-handler` | List tenant-scoped manual exceptions |
| `POST /api/exceptions` | `drw.api.exceptions/create-handler` | Create a manual exception |
| `GET /api/counterparties` | `drw.api.counterparties/list-handler` | List tenant-scoped counterparties |
| `GET /api/counterparties/:id` | `drw.api.counterparties/get-handler` | Fetch counterparty detail |
| `POST /api/counterparties/:id/merge` | `drw.api.counterparties/merge-handler` | Merge one counterparty into another |

## Tenant Model
API requests use `X-API-Key` prefix lookup and constant-time hash comparison. Public routes are explicit in `drw.http.interceptors.auth/public-routes`. Protected routes require `:current-tenant`, rate limits are applied per route, request ids are propagated through `X-Request-Id`, and tenant lifecycle mutations append audit rows. UI requests will use buddy-auth sessions that resolve to the same tenant context. Cross-tenant misses return 404.

## Data Contracts
Tenant fixture data lives in `resources/fixtures/tenants.edn` and must keep at least two tenants with distinct identity literals. Tenant snapshot generation uses `drw.db.scope/entity-by-tenant-id` and fails closed on missing tenant identity fields. Template lookup uses strict undefined behavior through `drw.templates.strict-fetch`; missing tokens throw instead of rendering empty strings. Audit rows are append-only insert maps built by `drw.audit.recorder`. Current domain functions use process-local atoms in `drw.domain.state` for counterparties, disputes, manual exceptions, timeline entries, audit rows, and SLA breach claims until durable Datomic mutation wiring is introduced. The SLA reaper scans non-terminal disputes with overdue SLA clocks, appends `:sla-breached` timeline/audit rows once per `[dispute-id due-at]`, and emits `dispute.sla_breached` through the Notification Hub client helper.

## Deep References
| Area | Planned path |
|---|---|
| Runtime entry points | `.agent/knowledge/modules/src-drw-core-setup.md` |
| Config loading | `.agent/knowledge/modules/src-drw-config.md` |
| System checks | `.agent/knowledge/modules/src-drw-system.md` |
| Datomic schema and tenant scope | `.agent/knowledge/modules/src-drw-db.md` |
| Core domain queue | `.agent/knowledge/modules/src-drw-domain.md` |
| Offline jobs | `.agent/knowledge/modules/src-drw-jobs.md` |
| Tenant fixtures | `.agent/knowledge/modules/src-drw-fixtures.md` |
| Tenant snapshots | `.agent/knowledge/modules/src-drw-tenants.md` |
| Strict template lookup | `.agent/knowledge/modules/src-drw-templates.md` |
| Audit recorder | `.agent/knowledge/modules/src-drw-audit.md` |
| Tenant API handlers | `.agent/knowledge/modules/src-drw-api.md` |
| Ecosystem client stubs | `.agent/knowledge/modules/src-drw-ecosystem.md` |
| HTTP server and routes | `.agent/knowledge/modules/src-drw-http.md` |
| UI shell | `.agent/knowledge/modules/src-drw-ui.md` |
| Shared foundation primitives | `.agent/knowledge/foundation/` |
| Gotchas | `.agent/knowledge/gotchas/` |
| Tests | `test/drw/` |

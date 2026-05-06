# Dispute Resolution Workbench - Codebase Context

> Last updated: 2026-05-05
> Template synced: 2026-05-04
> Source PRD: `docs/dispute-resolution-workbench_prd.md`

## Project Summary
Tenant-scoped dispute operations system for finance teams: manual exceptions, dispute queues, operator workflows, SLA tracking, ecosystem feeds, Workflow Engine resolutions, Notification Hub events, and immutable Datomic audit history.

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
| Tests | clojure.test, Kaocha, Testcontainers including nginx upstream stubs, Playwright via clj-chrome-devtools |
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
| `src/drw/config.clj` | Env and `.env` config loading, including ecosystem toggles and Hub ingress secret |
| `src/drw/system.clj` | Datomic Local, SQL storage, Postgres, and Redis smoke helpers |
| `src/drw/setup.clj` | Structured first-run setup checks and setup CLI |
| `src/drw/db/` | Schema loading, status validation, setup summaries, and tenant scope |
| `src/drw/domain/` | Process-local counterparties, disputes, exceptions, ingestion source controls/runs, correlation review, reports, timeline, and audit |
| `src/drw/jobs/` | Offline jobs for SLA reaping plus adapter/NATS ingestion through the domain pipeline |
| `src/drw/fixtures.clj` | Tenant fixture loader with identity-field validation |
| `src/drw/tenants/` | Tenant identity snapshots for config-driven surfaces |
| `src/drw/templates/` | Strict template token lookup helpers |
| `src/drw/audit/` | Append-only audit transaction construction |
| `src/drw/adapters/` | Exception adapter protocol/fetcher plus invoice, transaction, Contract Lifecycle, and Webhook Engine adapters |
| `src/drw/api/` | JSON tenant, dispute, exception, correlation, ingestion source/run, and counterparty handlers |
| `src/drw/ecosystem/` | Disabled-by-default Notification Hub, Workflow Engine, and dependency-light NATS boundaries |
| `src/drw/security/` | Shared security helpers, currently HMAC-SHA256 signature verification for Hub ingress |
| `src/drw/http/` | Pedestal server, JSON helpers, routes, interceptors, and UI/API wiring |
| `src/drw/ui/` | Hiccup layout, session lookup, forms, handlers, and operator pages including correlation review and ingestion settings |
| `resources/assets/styles/app.css` | Tailwind input stylesheet |
| `resources/public/assets/app.css` | Generated Tailwind output served by Pedestal |
| `resources/datomic/` | Datomic Local notes, SQL transactor properties, and Section 4 schema EDN |
| `resources/fixtures/` | Seed-quality tenant identity fixtures used by tests and snapshot helpers |
| `test/drw/` | Unit, integration, system, UI, route, setup, domain, Testcontainers upstream stub, and E2E tests |
| `scripts/first-run-setup.ps1` | PowerShell wrapper around `clojure -M:setup` |
| `.agent/knowledge/modules/` | One file per source module |

## Database Overview
`resources/datomic/schema.edn` defines Section 4 Datomic attributes for tenants, users, counterparties, disputes, exceptions, correlation candidates, timelines, SLA policies, playbooks, ingestion sources/runs, audit rows, and reports. `drw.db.schema` loads the resource and validates status transitions in Clojure until Datomic Pro tx-function installation is wired.

## Environment Variables
See `.env.example`. Required keys: `APP_ENV`, `PORT`, `DATABASE_URL`, `DATOMIC_URI`, `REDIS_URL`, `SESSION_SECRET`. Setup reads Datomic/Postgres storage settings. Tenant settings: `SELF_REGISTRATION_ENABLED`, `API_KEY_PREFIX`. Ecosystem settings cover Hub, Workflow, reconciliation, Contract Lifecycle, NATS, and Webhook Engine URLs/API keys plus poll intervals. Public Hub ingress verifies `HUB_INGRESS_SECRET`.

## External Integrations
| System | Direction | Method | Env |
|---|---|---|---|
| Invoice Reconciliation Engine | inbound pull | REST adapter | `INVOICE_RECON_URL`, `INVOICE_RECON_API_KEY` |
| Contract Lifecycle Engine | inbound pull/subscribe | REST + NATS | `CONTRACT_LIFECYCLE_URL`, `NATS_URL` |
| Transaction Reconciliation Engine | inbound pull | REST adapter | `TRANSACTION_RECON_URL`, `TRANSACTION_RECON_API_KEY` |
| Webhook Ingestion Engine | inbound pull | REST DLQ poll | `WEBHOOK_ENGINE_URL`, `WEBHOOK_ENGINE_API_KEY` |
| Notification Hub | outbound | REST events | `NOTIFICATION_HUB_URL`, `NOTIFICATION_HUB_API_KEY` |
| Workflow Automation Engine | outbound | REST workflow execute | `WORKFLOW_ENGINE_URL`, `WORKFLOW_ENGINE_API_KEY` |
| Hub Ingress | inbound | HMAC webhook | `HUB_INGRESS_SECRET` |

## HTTP Surface
| Surface | Routes | Purpose |
|---|---|---|
| UI pages | `GET /`, `/login`, `/disputes`, `/disputes/:id`, `/counterparties`, `/counterparties/:id`, `/correlations`, `/settings/ingestion` | Server-rendered tenant console pages |
| UI form actions | `POST /login`, `/logout`, dispute actions, correlation decisions, ingestion source save/pull-now | Login and operator actions using POST/303 redirects |
| Health API | `GET /api/health` | JSON liveness check |
| Tenant API | `/api/tenants/register`, `/api/tenants/me`, `/tenants/me`, `/api/tenants/rotate-key` | Registration, profile, compatibility profile, and key rotation |
| Workbench API | `/api/disputes*`, `/api/exceptions`, `/api/correlations*`, `/api/ingestion-*`, `/api/counterparties*` | JSON dispute, exception, correlation, ingestion source/run, and counterparty operations documented in `openapi.yaml` |
| Public ingress API | `POST /api/exceptions/from-hub` | Hub-routed exception ingestion using `X-Hub-Signature-256` HMAC and `X-Hub-Tenant-Slug` |

## Tenant Model
API requests use `X-API-Key` prefix lookup and constant-time hash comparison. Public routes are explicit in `drw.http.interceptors.auth/public-routes`; Hub ingress resolves tenant by `X-Hub-Tenant-Slug` after HMAC verification. Protected API routes require `:current-tenant`, rate limits use route keys, request ids propagate through `X-Request-Id`, and tenant lifecycle mutations append audit rows. UI requests resolve tenant context from `drw_session`, `X-DRW-Session`, or `X-API-Key`. Cross-tenant misses return 404.

## Data Contracts
Tenant fixtures use at least two distinct tenants. Tenant snapshots fail closed, templates use strict undefined lookup, and audit rows are append-only maps. Domain state is process-local until durable Datomic mutations are wired. Reports fail closed on cross-tenant dispute ids; SLA breach claims are idempotent. Correlator scoring is pure, tenant-scoped, threshold-banded, and deterministic. Ingestion stores normalized exceptions, creates unmatched disputes, records pending candidates, and auto-merges only when configured. Ingestion source overrides, pull-now history, and correlation review decisions are tenant-scoped. Adapter fetches are disabled-safe, tenant/source scoped, retryable, timeout-classified, and circuit-isolated. Invoice, transaction, Contract Lifecycle, Webhook Engine, and HMAC Hub ingress normalize upstream exceptions through ingestion and reject same-tenant duplicate source refs. Adapter integration coverage includes a real nginx Testcontainers upstream stub.

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
| Adapter foundation | `.agent/knowledge/modules/src-drw-adapters.md` |
| Tenant API handlers | `.agent/knowledge/modules/src-drw-api.md` |
| Ecosystem client stubs | `.agent/knowledge/modules/src-drw-ecosystem.md` |
| Security helpers | `.agent/knowledge/modules/src-drw-security.md` |
| HTTP server and routes | `.agent/knowledge/modules/src-drw-http.md` |
| UI shell | `.agent/knowledge/modules/src-drw-ui.md` |
| Integration test containers | `.agent/knowledge/modules/test-drw-test-containers.md` |
| Shared foundation primitives | `.agent/knowledge/foundation/` |
| Gotchas | `.agent/knowledge/gotchas/` |
| Tests | `test/drw/` |

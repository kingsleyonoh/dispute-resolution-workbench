# Dispute Resolution Workbench - Codebase Context

> Last updated: 2026-05-05
> Template synced: 2026-05-04
> Source PRD: `docs/dispute-resolution-workbench_prd.md`

## Project Summary
Single-queue dispute operations system for finance teams. It consolidates exceptions from Invoice Reconciliation, Contract Lifecycle, Transaction Reconciliation, and optional Webhook delivery failures into a tenant-scoped workflow with SLA tracking, explicit correlation review, Workflow Engine resolution playbooks, Notification Hub events, and immutable Datomic audit history.

## Tech Stack
| Layer | Technology |
|---|---|
| Language | Clojure 1.12 |
| Framework | Pedestal 0.7.2 |
| Database | Datomic Local for setup smoke checks; Datomic Pro SQL storage config for PostgreSQL 16 |
| Cache / Queue | Redis 7 with Carmine |
| SQL access | next.jdbc |
| UI | Hiccup 2, HTMX 2.0.4, Tailwind CSS 3.4 |
| Auth | X-API-Key tenant interceptor plus buddy-auth session cookie |
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
| `src/drw/http/` | Pedestal server, routes, and handlers |
| `src/drw/ui/` | Hiccup layout and HTMX-ready pages |
| `resources/assets/styles/app.css` | Tailwind input stylesheet |
| `resources/public/assets/app.css` | Generated Tailwind output served by Pedestal |
| `resources/datomic/` | Datomic Local notes and SQL transactor properties |
| `test/drw/` | Unit, integration, system, UI, route, and E2E tests |
| `.agent/knowledge/modules/` | One file per source module |

## Database Overview
Datomic attribute-centric schema for tenants, users, counterparties, disputes, exceptions, correlation candidates, timeline entries, SLA policies, playbooks, ingestion sources/runs, audit log, and report artifacts is still planned. Batch 002 added Datomic Local smoke-client setup and Datomic Pro SQL storage properties, but no Datomic schema file exists yet.

## Environment Variables
See `.env.example`. Runtime-required keys enforced by `drw.config`: `APP_ENV`, `PORT`, `DATABASE_URL`, `DATOMIC_URI`, `REDIS_URL`, `SESSION_SECRET`. Setup also reads `DATABASE_POOL`, `DATOMIC_STORAGE_DIR`, and `DATOMIC_SQL_TRANSACTOR_PROPERTIES`. Ecosystem integrations are feature-flagged with `*_ENABLED=false` defaults.

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

## Tenant Model
API requests use `X-API-Key` prefix lookup and constant-time hash comparison. UI requests use buddy-auth sessions that resolve to the same tenant context. Every mutation records tenant-scoped audit data, and cross-tenant misses return 404.

## Deep References
| Area | Planned path |
|---|---|
| Runtime entry points | `.agent/knowledge/modules/src-drw-core-setup.md` |
| Config loading | `.agent/knowledge/modules/src-drw-config.md` |
| System checks | `.agent/knowledge/modules/src-drw-system.md` |
| HTTP server and routes | `.agent/knowledge/modules/src-drw-http.md` |
| UI shell | `.agent/knowledge/modules/src-drw-ui.md` |
| Shared foundation primitives | `.agent/knowledge/foundation/` |
| Gotchas | `.agent/knowledge/gotchas/` |
| Tests | `test/drw/` |

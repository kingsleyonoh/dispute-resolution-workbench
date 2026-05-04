# Dispute Resolution Workbench - Codebase Context

> Last updated: 2026-05-04
> Template synced: 2026-05-04
> Source PRD: `docs/dispute-resolution-workbench_prd.md`

## Project Summary
Single-queue dispute operations system for finance teams. It consolidates exceptions from Invoice Reconciliation, Contract Lifecycle, Transaction Reconciliation, and optional Webhook delivery failures into a tenant-scoped workflow with SLA tracking, explicit correlation review, Workflow Engine resolution playbooks, Notification Hub events, and immutable Datomic audit history.

## Tech Stack
| Layer | Technology |
|---|---|
| Language | Clojure 1.12 |
| Framework | Pedestal 0.7 |
| Database | Datomic Pro with PostgreSQL 16 SQL storage |
| Cache / Queue | Redis 7 with Carmine |
| HTTP client | Hato |
| Serialization | Jsonista |
| Validation | Malli |
| UI | Hiccup, HTMX 2.0, Tailwind CSS 3 |
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
| Lint | `clojure -M:clj-kondo --lint src test` |
| Format check | `clojure -M:cljfmt check` |
| Build | `clojure -T:build uber` |
| Start infra | `docker compose up -d postgres redis` |
| Stop infra | `docker compose down` |
| Check infra | `docker compose ps` |
| First-run setup | `clojure -M:setup` |

## Project Structure
| Path | Purpose |
|---|---|
| `src/drw/core.clj` | Application entry point |
| `src/drw/system.clj` | Runtime component graph |
| `src/drw/config.clj` | Config loading |
| `src/drw/http/` | Pedestal server, interceptors, routes |
| `src/drw/api/` | Thin HTTP handlers |
| `src/drw/ui/` | Hiccup and HTMX screens |
| `src/drw/domain/` | Dispute, exception, SLA, correlation, resolution, report logic |
| `src/drw/adapters/` | Upstream exception adapters |
| `src/drw/ecosystem/` | Hub, Workflow Engine, and NATS clients |
| `src/drw/tenants/` | Tenant snapshot and identity helpers |
| `src/drw/audit/` | Audit recorder |
| `src/drw/templates/` | Strict template rendering |
| `src/drw/jobs/` | Scheduled jobs |
| `resources/datomic/schema.edn` | Datomic schema |
| `resources/templates/` | PDF and email-adjacent templates |
| `test/drw/` | Unit, integration, API, UI, and system tests |

## Key Modules
| Module | PRD section | Notes |
|---|---|---|
| Tenant & Auth Interceptor | 5.1 | Resolves API key/session to current tenant |
| Dispute Lifecycle Engine | 5.2 | Owns state machine, assignment, comments, SLA due dates |
| Exception Ingestion Pipeline | 5.3 | Normalizes exceptions and creates disputes/candidates |
| Upstream Adapters | 5.4 | Invoice, contract, transaction, webhook, manual adapters |
| Correlator | 5.5 | Pure scoring for proposed merges |
| Resolution Orchestrator | 5.6 | Triggers Workflow Engine playbooks |
| Reporting | 5.7 | Frozen tenant snapshots and audit PDFs |
| SLA Reaper | 5.8 | Emits breach timeline entries and Hub events |
| Notifications | 5.9 / 7b | Emits Notification Hub event envelopes |
| HTMX Console | 5.10 / 5b | Operations UI |

## Database Overview
Datomic attribute-centric schema for tenants, users, counterparties, disputes, exceptions, correlation candidates, timeline entries, SLA policies, playbooks, ingestion sources/runs, audit log, and report artifacts. Every data-bearing entity includes tenant identity, and query helpers must scope by tenant first.

## Environment Variables
See `.env.example`. Required local baseline: `DATABASE_URL`, `DATOMIC_URI`, `REDIS_URL`, `SESSION_SECRET`, `API_KEY_PREFIX`. Ecosystem integrations are feature-flagged with `*_ENABLED=false` defaults.

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

## Tenant Model
API requests use `X-API-Key` prefix lookup and constant-time hash comparison. UI requests use buddy-auth sessions that resolve to the same tenant context. Every mutation records tenant-scoped audit data, and cross-tenant misses return 404.

## Shared Foundation
| Primitive | Path | Why it exists |
|---|---|---|
| Tenant scope helper | `src/drw/db/scope.clj` | Forces tenant predicates into Datomic queries |
| Tenant snapshot | `src/drw/tenants/snapshot.clj` | Freezes tenant identity for reports |
| Hub client | `src/drw/ecosystem/hub_client.clj` | Shared Notification Hub event emitter |
| Workflow client | `src/drw/ecosystem/workflow_client.clj` | Shared Workflow Engine trigger/poller |
| NATS connection | `src/drw/ecosystem/nats_connection.clj` | Shared Contract Lifecycle event stream |
| Adapter fetcher | `src/drw/adapters/fetcher.clj` | Shared Hato client and circuit breaker |
| Audit recorder | `src/drw/audit/recorder.clj` | Cross-cutting immutable audit writes |
| Strict template renderer | `src/drw/templates/strict_fetch.clj` | Fails on unresolved tenant/report tokens |

## Key Patterns & Conventions
- Core manual queue must run with all adapters disabled.
- Adapters are independently feature-flagged and fail gracefully.
- Correlator is pure and does not import ingestion or UI modules.
- Resolution playbook execution is delegated to Workflow Engine.
- Notification delivery goes only through Notification Hub.
- HTMX server-rendered UI is preferred over SPA state.

## Deep References
| Area | Planned path |
|---|---|
| HTTP interceptors | `src/drw/http/interceptors/` |
| API handlers | `src/drw/api/` |
| UI pages | `src/drw/ui/` |
| Domain logic | `src/drw/domain/` |
| Adapters | `src/drw/adapters/` |
| Ecosystem clients | `src/drw/ecosystem/` |
| Background jobs | `src/drw/jobs/` |
| Tests | `test/drw/` |

# `src/drw/jobs/`

## Purpose

Owns offline jobs that run domain maintenance, adapter polling, stale source detection, and event ingestion without introducing direct delivery side effects outside established client/domain helpers. Adapter jobs route normalized exceptions through the shared domain ingestion pipeline.

## Key files

- `src/drw/jobs/sla_reaper.clj` - runs one SLA sweep and returns checked/breached counts.
- `src/drw/jobs/adapter_poll.clj` - shared poll-job runner that calls an `ExceptionAdapter`, ingests normalized exceptions through `drw.domain.exceptions/ingest!`, counts stored/skipped/rejected rows, returns source refs, and returns a run map.
- `src/drw/jobs/invoice_recon_poll.clj` - builds tenant/source config from invoice reconciliation env values and runs the invoice adapter with adapter actor metadata.
- `src/drw/jobs/transaction_recon_poll.clj` - builds tenant/source config from transaction reconciliation env values and runs the transaction adapter with adapter actor metadata.
- `src/drw/jobs/contract_lifecycle_backfill.clj` - builds tenant/source config from Contract Lifecycle env values and runs the contract adapter through the shared poll runner.
- `src/drw/jobs/contract_lifecycle_nats_consumer.clj` - subscribes to contract breach/conflict subjects through the NATS boundary, parses events through the contract adapter, and ingests/skips/rejects per tenant.
- `src/drw/jobs/webhook_engine_dlq_poll.clj` - builds tenant/source config from Webhook Engine env values and runs the DLQ adapter with adapter actor metadata.
- `src/drw/jobs/resolution_poller.clj` - thin job wrapper that polls active Workflow Engine resolutions through `drw.domain.resolution` with a stable job actor.
- `src/drw/jobs/stale_source_detector.clj` - scans enabled ingestion sources with last successful pulls older than 24 hours and emits `dispute.ingestion_source_stale`.
- `src/drw/domain/sla.clj` - finds overdue non-terminal disputes, claims each SLA breach idempotently, appends timeline/audit rows, and emits the Notification Hub event helper.
- `test/drw/domain/sla_test.clj` - covers overdue detection, idempotent breach marking, terminal dispute exclusion, and Hub-disabled behavior.
- `test/drw/jobs/reconciliation_poll_test.clj` - covers adapter poll storage, duplicate-source-ref skips including duplicates within one poll, disabled runs, upstream failure isolation across tenants/sources, tenant isolation, and source-system-scoped dedupe.
- `test/drw/jobs/contract_lifecycle_test.clj` - covers Contract Lifecycle backfill, disabled/failure behavior, NATS subscription handling, duplicate skipping, tenant mismatch rejection, and cross-tenant source-ref isolation.
- `test/drw/jobs/webhook_engine_dlq_poll_test.clj` - covers DLQ job storage, disabled/failure behavior, duplicate skipping, cursor preservation, and cross-tenant source-ref isolation.
- `test/drw/jobs/resolution_poller_test.clj` - covers job-driven active resolution polling into dispute terminal state.
- `test/drw/jobs/stale_source_detector_test.clj` - covers stale source Hub emission and fresh/disabled source exclusion.
- `test/drw/integration/adapter_upstream_test.clj` - covers invoice reconciliation polling against a real nginx/Testcontainers upstream while still routing results through the production poll job and domain ingestion path.

## Dependencies

- Upstream: `drw.domain.sla`, `drw.domain.resolution`, `drw.domain.state`, `drw.domain.disputes`, `drw.domain.exceptions`, `drw.audit.recorder`, `drw.ecosystem.hub-client`, and `drw.adapters.*`.
- Downstream: future scheduler wiring and operator pull-now actions should call `drw.jobs.sla-reaper/run-once!`, reconciliation poll `run-once!` wrappers, Contract Lifecycle backfill/NATS `run-once!` wrappers, or Webhook Engine DLQ `run-once!` rather than scanning domain atoms directly.

## Tests

- Domain SLA, stale source, and resolution job tests verify one breach per dispute/due-at pair, audit/timeline side effects, ignored terminal disputes, stale ingestion source Hub events, and Workflow Engine terminal status application.
- Reconciliation, contract, Webhook Engine, and container-backed upstream ingestion tests verify successful runs route through domain ingestion, failed upstream runs do not throw or store partial rows, disabled runs preserve cursors where applicable, duplicate source refs are skipped per tenant and source system, duplicate rows in the same poll do not create extra audit/correlation side effects, tenant-mismatched contract events reject, HTTP request metadata is propagated, and Tenant A/Tenant B listings stay isolated.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-domain.md`, `.agent/knowledge/modules/src-drw-ecosystem.md`
- Related foundation primitives: `.agent/knowledge/foundation/audit-append-only.md`, `.agent/knowledge/foundation/ecosystem-client-stubs.md`

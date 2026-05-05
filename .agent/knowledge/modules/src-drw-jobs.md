# `src/drw/jobs/`

## Purpose

Owns offline jobs that run domain maintenance, adapter polling, and event ingestion without introducing direct delivery side effects outside established client/domain helpers.

## Key files

- `src/drw/jobs/sla_reaper.clj` - runs one SLA sweep and returns checked/breached counts.
- `src/drw/jobs/adapter_poll.clj` - shared poll-job runner that calls an `ExceptionAdapter`, stores normalized exceptions through the domain layer, counts stored/skipped/rejected rows, and returns a run map.
- `src/drw/jobs/invoice_recon_poll.clj` - builds tenant/source config from invoice reconciliation env values and runs the invoice adapter with adapter actor metadata.
- `src/drw/jobs/transaction_recon_poll.clj` - builds tenant/source config from transaction reconciliation env values and runs the transaction adapter with adapter actor metadata.
- `src/drw/jobs/contract_lifecycle_backfill.clj` - builds tenant/source config from Contract Lifecycle env values and runs the contract adapter through the shared poll runner.
- `src/drw/jobs/contract_lifecycle_nats_consumer.clj` - subscribes to contract breach/conflict subjects through the NATS boundary, parses events through the contract adapter, and stores/skips/rejects per tenant.
- `src/drw/jobs/webhook_engine_dlq_poll.clj` - builds tenant/source config from Webhook Engine env values and runs the DLQ adapter with adapter actor metadata.
- `src/drw/domain/sla.clj` - finds overdue non-terminal disputes, claims each SLA breach idempotently, appends timeline/audit rows, and emits the Notification Hub event helper.
- `test/drw/domain/sla_test.clj` - covers overdue detection, idempotent breach marking, terminal dispute exclusion, and Hub-disabled behavior.
- `test/drw/jobs/reconciliation_poll_test.clj` - covers adapter poll storage, duplicate-source-ref skips, disabled runs, upstream failure isolation, and tenant isolation.
- `test/drw/jobs/contract_lifecycle_test.clj` - covers Contract Lifecycle backfill, disabled/failure behavior, NATS subscription handling, duplicate skipping, tenant mismatch rejection, and cross-tenant source-ref isolation.
- `test/drw/jobs/webhook_engine_dlq_poll_test.clj` - covers DLQ job storage, disabled/failure behavior, duplicate skipping, cursor preservation, and cross-tenant source-ref isolation.

## Dependencies

- Upstream: `drw.domain.sla`, `drw.domain.state`, `drw.domain.disputes`, `drw.domain.exceptions`, `drw.audit.recorder`, `drw.ecosystem.hub-client`, and `drw.adapters.*`.
- Downstream: future scheduler wiring should call `drw.jobs.sla-reaper/run-once!`, reconciliation poll `run-once!` wrappers, Contract Lifecycle backfill/NATS `run-once!` wrappers, or Webhook Engine DLQ `run-once!` rather than scanning domain atoms directly.

## Tests

- Domain SLA tests verify one breach per dispute/due-at pair, audit/timeline side effects, and ignored terminal disputes.
- Reconciliation, contract, and Webhook Engine ingestion tests verify successful runs store pending exceptions, failed upstream runs do not throw or store partial rows, disabled runs preserve cursors where applicable, duplicate source refs are skipped per tenant, tenant-mismatched contract events reject, and Tenant A/Tenant B listings stay isolated.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-domain.md`, `.agent/knowledge/modules/src-drw-ecosystem.md`
- Related foundation primitives: `.agent/knowledge/foundation/audit-append-only.md`, `.agent/knowledge/foundation/ecosystem-client-stubs.md`

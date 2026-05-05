# `src/drw/adapters/`

## Purpose

Defines the shared ingestion adapter boundary and current poll/event adapters for external exception sources.

## Key files

- `src/drw/adapters/protocol.clj` - `ExceptionAdapter` protocol plus normalized `poll-result` and `poll-error` helpers.
- `src/drw/adapters/fetcher.clj` - disabled-safe fetch helper with config validation, request construction, retry/backoff, timeout classification, and per-tenant/source circuit state.
- `src/drw/adapters/invoice_recon.clj` - polls `/api/discrepancies`, accepts `discrepancies` or `items` response bodies, and maps invoice discrepancy payloads to `:invoice-discrepancy` exceptions.
- `src/drw/adapters/transaction_recon.clj` - polls `/api/v1/discrepancies`, accepts `discrepancies` or `items` response bodies, and maps transaction discrepancy payloads to `:payment-mismatch` exceptions.
- `src/drw/adapters/contract_lifecycle.clj` - polls `/api/obligations?status=breached,overdue`, accepts `obligations` or `items`, maps contract breach/conflict payloads, and parses NATS event payloads with tenant-mismatch rejection.
- `test/drw/adapters/protocol_test.clj` - protocol and normalized poll result contracts.
- `test/drw/adapters/fetcher_test.clj` - disabled behavior, tenant-scoped request metadata, retry, timeout, and circuit isolation contracts.
- `test/drw/adapters/reconciliation_adapters_test.clj` - invoice and transaction adapter normalization, cursor, disabled, upstream failure, and webhook-unsupported contracts.
- `test/drw/adapters/contract_lifecycle_test.clj` - Contract Lifecycle backfill normalization, cursor handling, disabled/upstream-failure behavior, webhook parsing, subject kind mapping, and tenant mismatch rejection.

## Dependencies

- Upstream: `clojure.string` and JDK `URLEncoder`; no Hato or Resilience4j dependency is present yet.
- Downstream: poll jobs and the Contract Lifecycle NATS consumer call concrete adapter singletons; future Webhook Engine adapters should implement `ExceptionAdapter` and use `fetch!` or an equivalent transport wrapper.

## Tests

- Adapter tests verify disabled adapters do not call transport, enabled configs fail loudly when incomplete, retryable upstream failures can recover, timeout exceptions become structured failed results, circuit state is isolated by `[tenant-id source-system]`, and reconciliation/contract payloads normalize to tenant-scoped exception maps.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-ecosystem.md`, `.agent/knowledge/modules/src-drw-jobs.md`

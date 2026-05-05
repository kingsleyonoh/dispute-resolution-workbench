# `src/drw/adapters/`

## Purpose

Defines the shared ingestion adapter boundary and current poll-based reconciliation adapters for external exception sources.

## Key files

- `src/drw/adapters/protocol.clj` - `ExceptionAdapter` protocol plus normalized `poll-result` and `poll-error` helpers.
- `src/drw/adapters/fetcher.clj` - disabled-safe fetch helper with config validation, request construction, retry/backoff, timeout classification, and per-tenant/source circuit state.
- `src/drw/adapters/invoice_recon.clj` - polls `/api/discrepancies`, accepts `discrepancies` or `items` response bodies, and maps invoice discrepancy payloads to `:invoice-discrepancy` exceptions.
- `src/drw/adapters/transaction_recon.clj` - polls `/api/v1/discrepancies`, accepts `discrepancies` or `items` response bodies, and maps transaction discrepancy payloads to `:payment-mismatch` exceptions.
- `test/drw/adapters/protocol_test.clj` - protocol and normalized poll result contracts.
- `test/drw/adapters/fetcher_test.clj` - disabled behavior, tenant-scoped request metadata, retry, timeout, and circuit isolation contracts.
- `test/drw/adapters/reconciliation_adapters_test.clj` - invoice and transaction adapter normalization, cursor, disabled, upstream failure, and webhook-unsupported contracts.

## Dependencies

- Upstream: `clojure.string` and JDK `URLEncoder`; no Hato or Resilience4j dependency is present yet.
- Downstream: reconciliation poll jobs call the concrete adapter singletons; future Contract Lifecycle and Webhook Engine adapters should implement `ExceptionAdapter` and use `fetch!` or an equivalent transport wrapper.

## Tests

- Adapter tests verify disabled adapters do not call transport, enabled configs fail loudly when incomplete, retryable upstream failures can recover, timeout exceptions become structured failed results, circuit state is isolated by `[tenant-id source-system]`, and reconciliation payloads normalize to tenant-scoped exception maps.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-ecosystem.md`, `.agent/knowledge/modules/src-drw-jobs.md`

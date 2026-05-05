# `src/drw/adapters/`

## Purpose

Defines the shared ingestion adapter boundary for future external exception sources without wiring concrete upstream systems yet.

## Key files

- `src/drw/adapters/protocol.clj` - `ExceptionAdapter` protocol plus normalized `poll-result` and `poll-error` helpers.
- `src/drw/adapters/fetcher.clj` - disabled-safe fetch helper with config validation, request construction, retry/backoff, timeout classification, and per-tenant/source circuit state.
- `test/drw/adapters/protocol_test.clj` - protocol and normalized poll result contracts.
- `test/drw/adapters/fetcher_test.clj` - disabled behavior, tenant-scoped request metadata, retry, timeout, and circuit isolation contracts.

## Dependencies

- Upstream: `clojure.string` and JDK `URLEncoder`; no Hato or Resilience4j dependency is present yet.
- Downstream: future Invoice Reconciliation, Transaction Reconciliation, Contract Lifecycle, and Webhook Engine adapters should implement `ExceptionAdapter` and use `fetch!` or an equivalent transport wrapper.

## Tests

- Adapter tests verify disabled adapters do not call transport, enabled configs fail loudly when incomplete, retryable upstream failures can recover, timeout exceptions become structured failed results, and circuit state is isolated by `[tenant-id source-system]`.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-ecosystem.md`, `.agent/knowledge/modules/src-drw-jobs.md`

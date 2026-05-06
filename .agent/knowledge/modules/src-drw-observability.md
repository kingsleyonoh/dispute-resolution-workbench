# Observability Module

## Purpose

Provides local, deployment-neutral observability primitives for readiness checks, Prometheus-format metrics, Axiom-compatible JSON log events, and injectable Sentry exception capture.

## Key files

- `src/drw/observability/readiness.clj` - readiness summaries for enabled ingestion adapters based on recent successful pulls.
- `src/drw/observability/metrics.clj` - Prometheus text renderer for process-local dispute and ingestion source counts.
- `src/drw/observability/logging.clj` - JSON log-event emitter using an injectable sink.
- `src/drw/observability/sentry.clj` - optional exception capture boundary using an injected capture function when `SENTRY_DSN` is configured.
- `test/drw/observability/observability_test.clj` - readiness, metrics, JSON logging, and Sentry boundary tests.

## Dependencies

- Upstream: `drw.domain.ingestion-sources`, `drw.domain.state`, `drw.domain.disputes`, `drw.api.responses`, `drw.fixtures`.
- Downstream: `drw.http.handlers` exposes readiness and metrics over HTTP.

## Tests

- Readiness returns `503` until enabled adapter sources have a fresh successful pull.
- Metrics returns Prometheus text and honors Basic auth when metrics credentials are configured.
- Logging and Sentry helpers use injected sinks so tests do not call external services.

## Notes

- This module deliberately avoids production service dependencies. Deployment-specific Sentry, Axiom, Prometheus, or BetterStack wiring can sit behind these boundaries later if the project ever needs deployment.
- `/metrics` allows access only when metrics are enabled and either no metrics password is configured or the caller supplies matching Basic auth.

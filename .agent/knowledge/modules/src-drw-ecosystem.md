# Ecosystem Module

## Purpose

Provides safe client boundaries for optional Notification Hub events, Workflow Engine executions, and dependency-light NATS messaging.

## Key files

- `src/drw/ecosystem/hub_client.clj` - Notification Hub event normalization, config validation, and injected send hook.
- `src/drw/ecosystem/workflow_client.clj` - Workflow Engine execute endpoint construction, snake-case payload conversion, and injected send hook.
- `src/drw/ecosystem/nats_connection.clj` - disabled-safe NATS connect/publish/subscribe/close boundary that validates URL and injected client functions without adding a NATS dependency.
- `test/drw/ecosystem/clients_test.clj` - disabled, missing-config, enabled-stub, and injected-send contracts.
- `test/drw/ecosystem/nats_connection_test.clj` - disabled-safe connect, missing-config, injected client option, publish, subscribe, and close contracts.

## Dependencies

- Upstream: `clojure.string`.
- Downstream: future resolution, notification, and integration workflows plus `drw.jobs.contract-lifecycle-nats-consumer`.

## Tests

- Client tests verify disabled clients are side-effect-free, enabled clients fail loudly on missing config, injected send/client functions receive normalized request maps, and NATS operations delegate to injected client functions.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/ecosystem-client-stubs.md`

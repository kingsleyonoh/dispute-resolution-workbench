# Ecosystem Module

## Purpose

Provides safe client boundaries for optional Notification Hub events and Workflow Engine executions.

## Key files

- `src/drw/ecosystem/hub_client.clj` - Notification Hub event normalization, config validation, and injected send hook.
- `src/drw/ecosystem/workflow_client.clj` - Workflow Engine execute endpoint construction, snake-case payload conversion, and injected send hook.
- `test/drw/ecosystem/clients_test.clj` - disabled, missing-config, enabled-stub, and injected-send contracts.

## Dependencies

- Upstream: `clojure.string`.
- Downstream: future resolution, notification, and integration workflows.

## Tests

- Client tests verify disabled clients are side-effect-free, enabled clients fail loudly on missing config, and injected send functions receive normalized request maps.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/ecosystem-client-stubs.md`

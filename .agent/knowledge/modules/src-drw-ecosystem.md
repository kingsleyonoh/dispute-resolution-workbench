# Ecosystem Module

## Purpose

Provides safe client boundaries for optional Notification Hub events, Workflow Engine executions, and dependency-light NATS messaging.

Notification Hub production onboarding exists for Dispute Resolution Workbench: one tenant, eight email templates, eight static email routing rules, and an ignored `.env.local` API key. Do not commit the generated tenant key.

## Key files

- `src/drw/ecosystem/hub_client.clj` - Notification Hub event normalization, config validation, and injected send hook.
- `src/drw/ecosystem/workflow_client.clj` - Workflow Engine execute endpoint construction, workflow id metadata, snake-case payload conversion, injected send hook, and disabled-safe execution status lookup.
- `src/drw/ecosystem/nats_connection.clj` - disabled-safe NATS connect/publish/subscribe/close boundary that validates URL and injected client functions without adding a NATS dependency.
- `test/drw/ecosystem/clients_test.clj` - disabled, missing-config, enabled-stub, and injected-send contracts.
- `test/drw/ecosystem/nats_connection_test.clj` - disabled-safe connect, missing-config, injected client option, publish, subscribe, and close contracts.

## Dependencies

- Upstream: `clojure.string`.
- Downstream: `drw.domain.resolution`, future notification/integration workflows, and `drw.jobs.contract-lifecycle-nats-consumer`.

## Tests

- Client tests verify disabled clients are side-effect-free, enabled clients fail loudly on missing config, injected send/client functions receive normalized request maps, and NATS operations delegate to injected client functions.
- Batch 024 verified a live `dispute.created` test event through the Hub with notification status `sent`; tracked files intentionally store only redacted onboarding evidence.

## Cross-references

- Related foundation primitives: `.agent/knowledge/foundation/ecosystem-client-stubs.md`

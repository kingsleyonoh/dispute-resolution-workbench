# Ecosystem Client Stubs

## What it establishes

Notification Hub and Workflow Engine clients are disabled-by-default boundaries. Disabled clients never require URLs or keys; enabled clients validate config before returning a structured stub result or invoking an injected send function.

## Files

- `src/drw/ecosystem/hub_client.clj` - Notification Hub event helper.
- `src/drw/ecosystem/workflow_client.clj` - Workflow Engine execution helper.
- `test/drw/ecosystem/clients_test.clj` - disabled, missing-config, and enabled-stub contracts.

## When to read this

Before writing any code that:
- Emits Notification Hub events.
- Triggers Workflow Engine executions.
- Adds ecosystem integration config or health checks.

## Contract

- Keep `*_ENABLED=false` safe and side-effect-free.
- Enabled clients must fail loudly when URL or API key config is missing.
- Tests must not call real external services; inject a send function when behavior beyond the stub is needed.
- Do not let ecosystem clients import domain or HTTP handler namespaces.

## Cross-references

- PRD sections: 5.6, 5.8, 5.9, 6b, 14.

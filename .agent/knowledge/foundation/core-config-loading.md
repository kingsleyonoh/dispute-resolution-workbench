# Core Config Loading

## What it establishes

Runtime config is loaded once from explicit env data, an env file, or the process environment, then normalized before startup code consumes it.

## Files

- `src/drw/config.clj` - config loader and normalization.
- `.env.example` - documented local and integration variables.
- `test/drw/config_test.clj` - config contract tests.

## When to read this

Before writing any code that:
- Adds or consumes a new environment variable.
- Changes startup, setup, or deployment configuration.
- Parses config values into typed runtime values.

## Contract

- Add new required variables to `required-env` only when startup cannot run without them.
- Document every new variable in `.env.example`.
- Keep config values derived from env or `.env` data; do not hardcode deploy-specific values in source.
- Parse typed values at the boundary so downstream modules receive normalized config.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-config.md`, `.agent/knowledge/modules/src-drw-core-setup.md`

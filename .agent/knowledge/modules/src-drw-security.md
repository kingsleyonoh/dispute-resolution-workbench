# Security Module

## Purpose

Provides small security primitives shared by HTTP/API boundaries.

## Key files

- `src/drw/security/hmac.clj` - HMAC-SHA256 signing and constant-time signature verification for Hub ingress payloads.
- `test/drw/api/hub_exception_ingress_test.clj` - handler-level HMAC success/failure coverage through the public Hub exception route.
- `test/drw/e2e_api/hub_exception_ingress_test.clj` - real HTTP coverage for signed Hub exception ingestion.

## Dependencies

- Upstream: `clojure.string`, Java `MessageDigest`, `javax.crypto.Mac`, and `SecretKeySpec`.
- Downstream: `src/drw/api/exceptions.clj` verifies `X-Hub-Signature-256` before parsing or ingesting Hub-routed exceptions.

## Tests

- Hub ingress tests cover missing signatures, invalid signatures, malformed JSON, missing tenant slug, disabled/missing tenants, duplicate source refs, and successful domain ingestion.

## Cross-references

- Related modules: `.agent/knowledge/modules/src-drw-api.md`, `.agent/knowledge/modules/src-drw-http.md`, `.agent/knowledge/modules/src-drw-config.md`

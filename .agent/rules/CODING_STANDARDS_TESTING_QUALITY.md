# Dispute Resolution Workbench - Coding Standards: Test Quality

> Split from CODING_STANDARDS_TESTING.md during Mode C sync to keep rules files under the 10K character gate.

## Test Quality Checklist (Anti-False-Confidence)

Before moving from RED → GREEN, verify ALL applicable categories have tests:

| # | Category | What to Test |
|---|----------|-------------|
| 1 | Happy path | Does it work with valid, normal input? |
| 2 | Required fields | Does it reject None/blank for required fields? |
| 3 | Uniqueness | Does it enforce unique constraints? |
| 4 | Defaults | Do default values apply correctly when field is omitted? |
| 5 | FK relationships | Do foreign keys enforce CASCADE/PROTECT correctly? |
| 6 | Tenant isolation | Can Tenant A see Tenant B's data? (MANDATORY if multi-tenant — see Multi-Tenant Fixtures Mandatory below; includes template / email / invoice / PDF rendering) |
| 7 | Edge cases | Empty strings, zero, negative, very long strings, special chars |
| 8 | Error paths | What happens when external APIs fail, DB is down, input is malformed? |
| 9 | String representation | Does `__str__` / `__repr__` return something meaningful? |
| 10 | Meta options | Are ordering, indexes, and constraints working? |

**If a category applies and you skip it, you're cheating.** If RED phase shows fewer than 2 failures, add more tests — you're probably not testing enough.

### Performance Awareness
- Correctness tests alone don't catch latency regressions — a page can pass all tests while making 10× the necessary network calls
- When a single page/endpoint triggers 3+ backend operations, consider asserting call count or response time
- After every batch of 5+ features, do a compound load check: load real pages and verify total I/O matches expectations

### Multi-Tenant Fixtures Mandatory (CRITICAL — Catches Cross-Tenant Leakage)

If the project is multi-tenant (PRD §2 Architecture Principles mandates `tenant_id`), every test suite that touches tenant-scoped data MUST load **at least TWO distinct tenants** with different literal values for every tenant-identity column (legal_name, full_legal_name, display_name, address, registration, contact, wordmark).

**Why:** A template that hardcodes "Acme Corp LLC" passes every test when the fixture only loads Acme. It fails the moment Globex is onboarded. Two-tenant fixtures expose this at RED phase, not in production.

**Rules:**

1. **Fixtures file (`tests/fixtures/tenants.*` or equivalent) MUST define ≥2 tenants** with intentionally-different identity values. Include edge cases: non-ASCII characters, longer addresses, different jurisdictions.
2. **Template / email / invoice / PDF tests MUST parametrize over both tenants** (pytest parametrize, table-driven tests, etc.) and assert that rendering Tenant A's snapshot does NOT include any Tenant B literal value and vice versa.
3. **Cross-tenant leakage grep (runs in suite):** Add a test that reads the generated artifact and greps for EVERY literal identity value of the OTHER tenant. Any match fails the test with message `TENANT_IDENTITY_LEAK: field=X expected=A actual_included=B`.
4. **Tenant isolation test per module:** Category 6 in the Test Quality Checklist above becomes MANDATORY (was conditional "if multi-tenant"). Every query, every API response, every job run must be asserted to respect `tenant_id` scoping.

**This rule is non-optional for config-driven surfaces.** Skipping it means the template-hardcoding bug class (a surface hardcodes one tenant's literal identity, tests pass under a single-tenant fixture, leaks to production when a second tenant onboards) will re-occur project-by-project until tests catch it at RED.

## Edge Case Coverage Guide

### Models
- Every field from the spec → at least 1 test per constraint
- Every FK → test CASCADE behavior
- Every choice field → test all valid values + 1 invalid value

### Services (when applicable)
- Boundary values (min, max, zero, negative)
- Invalid input types
- Idempotency (running twice = same result)
- Mock external API failures

### Views/Pages (when applicable)
- Authenticated vs unauthenticated access
- Correct HTTP methods (GET/POST/PUT/DELETE)
- Response format validation
- Tenant scoping (if multi-tenant)

## Test Modularity Rules
1. **One test class per model/service** — never mix models in one class
2. **Max 300 lines per test file** — split if larger
3. **`setUp` creates only what that class needs** — no global fixtures
4. **Tests are independent** — no shared state, no ordering dependency
5. **Any single test can run in isolation** — `clojure -M:test tests/test_x.py::TestClass::test_method`
6. **Test names describe business behavior** — not technical actions
7. **No test helpers longer than 10 lines** — extract to a `tests/factories.py` if needed

## Business-Context Testing
- Tests must reflect the BUSINESS PURPOSE described in the spec.
- Every test must answer: Does this protect data? Apply rules correctly? Handle failure? Match the spec?
- Test names must describe business behavior, not technical actions.

> **Integration, Component, E2E, and Mock Policy rules** → see `CODING_STANDARDS_TESTING_LIVE.md` (Part 3 of 4).

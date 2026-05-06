# Dashboard 10k-Dispute Performance Audit

Date: 2026-05-06

## Scope

Local audit for the server-rendered operations dashboard with a 10,000-dispute tenant fixture. Deployment and production monitoring were out of scope.

## Finding

The previous dashboard rendered every tenant dispute into the "My open disputes" table. With 10,000 disputes this produced 10,000 dispute-detail links and an unnecessarily large HTML response.

## Change

Dashboard rendering now:

- Counts all tenant disputes for the summary metrics.
- Counts assigned disputes for the assigned metric.
- Renders only the first 50 open disputes in the dashboard preview table.
- Leaves the full dispute queue available through `/disputes`.

## Guard

`drw.ui.pages-test/dashboard-10k-fixture-renders-bounded-preview` seeds 10,000 process-local disputes and asserts:

- The dashboard summary still displays `10000`.
- The dashboard HTML contains at most 50 dispute-detail links.

## Evidence

- RED: `1 tests, 2 assertions, 1 failures` because the dashboard rendered `10000` dispute links.
- GREEN focused: `1 tests, 2 assertions, 0 failures`.
- GREEN UI suite: `5 tests, 35 assertions, 0 failures`.
- GREEN full regression: `157 tests, 853 assertions, 0 failures`.
- GREEN E2E alias: `157 tests, 853 assertions, 0 failures`.

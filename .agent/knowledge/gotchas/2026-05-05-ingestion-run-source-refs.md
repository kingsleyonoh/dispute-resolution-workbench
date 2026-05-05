# Ingestion Run Source Refs

- **Symptom:** Pull-now API/UI tests pass aggregate counts but the run history is not useful enough to connect a pull to upstream items.
- **Cause:** The first run record shape stored status, counts, cursor, and errors, but dropped the adapter result's `source-refs`.
- **Solution:** Preserve source refs from the job result in `:ingestion-run/source-refs`, serialize them as `sourceRefs`, document them in OpenAPI, and render them in the UI run table.
- **Discovered in:** Dispute Resolution Workbench, Batch 016, 2026-05-05.
- **Affects:** Any ingestion control surface that records run history separately from normalized exception rows.

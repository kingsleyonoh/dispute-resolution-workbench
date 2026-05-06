# Operator Pull-Now Reuses Scheduled Job

## Purpose

Expose manual operator-triggered ingestion without creating a second importer path.

## When to use

- An operator action should run the same ingestion, backfill, or polling behavior that a scheduler already runs.
- The UI/API needs immediate execution plus inspectable run history.
- Tests need injected transports or config while production keeps normal runtime settings.

## How it works

- Keep source settings tenant-scoped and materialize defaults from runtime config.
- Resolve the source for the current tenant before execution.
- Call the same job `run-once!` wrapper used by scheduled polling.
- Store the returned job result as a run record with status, counts, cursor, error, and upstream source refs.
- Update the source cursor and last-success/error fields from the run result.

## Example

```clojure
(defn pull-now! [tenant-id source-id cfg]
  (let [source (get-source tenant-id source-id cfg)
        spec (source-registry (:ingestion-source/source-system source))
        result ((:run-fn spec) (effective-cfg cfg source)
                               (pull-opts cfg source))
        run (run-record result started finished)]
    (store-run! run)
    (update-source-after-run source run)
    run))
```

## Cross-references

- Originating batch: Batch 016.
- Related modules: `.agent/knowledge/modules/src-drw-domain.md`, `.agent/knowledge/modules/src-drw-jobs.md`, `.agent/knowledge/modules/src-drw-ui.md`

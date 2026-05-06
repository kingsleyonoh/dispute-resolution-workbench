# Datomic Local Storage Dir Must Be Absolute

- **Symptom:** Datomic Local smoke tests fail with `You must specify an absolute path or the keyword :mem under :storage-dir`.
- **Cause:** `datomic.client.api/client` does not accept a relative `:storage-dir` path when the storage directory is passed directly in client options.
- **Solution:** Convert configured `DATOMIC_STORAGE_DIR` values to an absolute path before building Datomic Local client options, or use `:mem` for throwaway smoke clients.
- **Discovered in:** Dispute Resolution Workbench, Batch 002, 2026-05-05.
- **Affects:** Datomic Local client options built from env config, especially under Docker-mounted workspaces.

# Testcontainers Needs Docker Socket Access In Dockerized Test Runs

- **Symptom:** A focused Testcontainers run fails with `Could not find a valid Docker environment` when launched from the Clojure Docker runner.
- **Cause:** The test process is already running inside Docker and cannot discover or control the host Docker daemon without the socket and host override settings.
- **Solution:** Run the Clojure test container with `/var/run/docker.sock` mounted, `TESTCONTAINERS_RYUK_DISABLED=true`, and `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. Keep Testcontainers integration runs sequential when local Datomic locks or fixed service ports may collide.
- **Discovered in:** Dispute Resolution Workbench, Batch 019, 2026-05-05.
- **Affects:** `clj-test-containers` integration tests launched inside the repo's Dockerized Clojure runner on Windows/WSL-style host Docker setups.

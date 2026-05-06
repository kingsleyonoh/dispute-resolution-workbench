# Container-Backed Upstream Adapter Tests

## Purpose

Exercise adapter poll jobs against a real local HTTP service while keeping upstream responses deterministic and side-effect free.

## When to use

- An adapter or poll job needs proof that request construction, headers, cursor query params, response parsing, and domain ingestion work across a real HTTP boundary.
- The upstream is owned by the ecosystem or can be represented locally, so an in-memory fake would hide integration failures.
- The test should avoid external network dependency, rate limits, and irreversible upstream side effects.

## How it works

- Serve a deterministic fixture from a tiny Testcontainers service such as `nginx:1.27-alpine`.
- Copy the classpath fixture into the container before start and use the mapped host/port as the adapter base URL.
- Keep the production adapter/job path in place; inject only the transport function when the existing code already supports it.
- Record request metadata from the real HTTP call and assert path/query, headers, cursor advancement, stored source refs, normalized exception fields, and tenant isolation.
- Run these tests with Docker socket access and sequentially when Datomic Local locks or fixed ports could collide.

## Example

```clojure
(tc/call-with-upstream-http-stub
 (fn [{:keys [base-url]}]
   (let [requests (atom [])
         result (invoice-poll/run-once!
                 {:invoice-recon-enabled true
                  :invoice-recon-url base-url
                  :invoice-recon-api-key "example_key"}
                 {:tenant-id tenant-id
                  :cursor "cursor-1"
                  :http-send-fn (tc/recording-edn-http-send-fn requests)})]
     (is (= :succeeded (:status result)))
     (is (= ["/api/discrepancies?since=cursor-1&limit=100"]
            (mapv :path-and-query @requests))))))
```

## Cross-references

- Originating batch: Batch 019.
- Related modules: `.agent/knowledge/modules/test-drw-test-containers.md`, `.agent/knowledge/modules/src-drw-adapters.md`, `.agent/knowledge/modules/src-drw-jobs.md`

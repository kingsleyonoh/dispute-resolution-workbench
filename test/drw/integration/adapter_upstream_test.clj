(ns drw.integration.adapter-upstream-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.jobs.invoice-recon-poll :as invoice-poll]
            [drw.test-containers :as tc]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))

(defn- invoice-cfg [base-url]
  {:invoice-recon-enabled true
   :invoice-recon-url base-url
   :invoice-recon-api-key "example_key"})

(deftest ^:integration invoice-poll-job-fetches-from-containerized-upstream
  (state/reset-store!)
  (tc/call-with-upstream-http-stub
   (fn [{:keys [base-url]}]
     (let [requests (atom [])
           result (invoice-poll/run-once!
                   (invoice-cfg base-url)
                   {:tenant-id tenant-a
                    :cursor "container-cursor-1"
                    :http-send-fn (tc/recording-edn-http-send-fn requests)})]
       (is (= :succeeded (:status result)))
       (is (= "container-cursor-2" (:cursor result)))
       (is (= ["INV-TC-100"] (:source-refs result)))
       (is (= 1 (:exceptions-stored result)))
       (is (= ["/api/discrepancies?since=container-cursor-1&limit=100"]
              (mapv :path-and-query @requests)))
       (is (= ["example_key"] (mapv #(get-in % [:headers "X-API-Key"])
                                    @requests)))
       (let [stored (exceptions/list-by-tenant tenant-a
                                               {:source-system :invoice-recon})]
         (is (= 1 (count stored)))
         (is (= "INV-TC-100" (:exception/source-ref (first stored))))
         (is (= "EUR" (:exception/currency (first stored)))))
       (is (empty? (exceptions/list-by-tenant tenant-b
                                              {:source-system :invoice-recon})))))))

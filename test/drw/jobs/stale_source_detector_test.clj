(ns drw.jobs.stale-source-detector-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.ingestion-sources :as ingestion]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.jobs.stale-source-detector :as detector]))

(def tenant-id (:tenant/id (get (fixtures/tenants-by-slug) "acme-gmbh-de")))

(def cfg
  {:invoice-recon-enabled true
   :invoice-recon-url "https://invoice.example.invalid"
   :transaction-recon-enabled true
   :transaction-recon-url "https://transactions.example.invalid"
   :contract-lifecycle-enabled false
   :webhook-engine-enabled false})

(defn- mark-success! [source instant]
  (swap! state/ingestion-sources*
         assoc-in [(:ingestion-source/id source)
                   :ingestion-source/last-successful-pull-at]
         instant))

(deftest stale-source-detector-emits-hub-events-for-enabled-stale-sources
  (state/reset-store!)
  (let [now #inst "2026-05-06T12:00:00.000-00:00"
        old #inst "2026-05-05T10:30:00.000-00:00"
        fresh #inst "2026-05-06T08:00:00.000-00:00"
        sent (atom [])
        invoice (first (filter #(= :invoice-recon
                                   (:ingestion-source/source-system %))
                               (ingestion/list-sources tenant-id cfg)))
        transaction (first (filter #(= :transaction-recon
                                       (:ingestion-source/source-system %))
                                   (ingestion/list-sources tenant-id cfg)))
        _ (mark-success! invoice old)
        _ (mark-success! transaction fresh)
        result (detector/run-once!
                (assoc cfg
                       :notification-hub-enabled true
                       :notification-hub-url "https://notify.example.invalid"
                       :notification-hub-api-key "example_key"
                       :notification-hub-send-fn #(swap! sent conj %))
                {:now now :tenant-ids [tenant-id]})]
    (is (= {:checked 4 :stale 1 :emitted 1} result))
    (is (= ["dispute.ingestion_source_stale"]
           (map #(get-in % [:event :event_type]) @sent)))
    (is (= "invoice-recon"
           (get-in (first @sent)
                   [:event :payload :source_system])))))

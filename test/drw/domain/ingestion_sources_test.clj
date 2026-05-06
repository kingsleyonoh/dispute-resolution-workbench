(ns drw.domain.ingestion-sources-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.ingestion-sources :as ingestion]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.jobs.contract-lifecycle-backfill :as contract-poll]
            [drw.jobs.invoice-recon-poll :as invoice-poll]
            [drw.jobs.transaction-recon-poll :as transaction-poll]
            [drw.jobs.webhook-engine-dlq-poll :as webhook-poll]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))

(def invoice-body
  {:discrepancies [{:invoice_id "INV-PULL-1"
                    :vendor_id "vendor-7"
                    :discrepancy_amount_cents 4500
                    :currency "EUR"
                    :observed_at #inst "2026-05-05T10:00:00.000-00:00"}]
   :next_cursor "invoice-cursor-2"})

(def transaction-body
  {:discrepancies [{:discrepancy_id "TRX-PULL-1"
                    :counterparty_name "Bank Partner"
                    :amount_cents -2500
                    :currency "USD"
                    :observed_at #inst "2026-05-05T11:00:00.000-00:00"}]
   :next_cursor "transaction-cursor-2"})

(def contract-body
  {:obligations [{:obligation_id "CON-PULL-1"
                  :counterparty_id "customer-9"
                  :financial_exposure_cents 9900
                  :currency "EUR"
                  :observed_at #inst "2026-05-05T12:00:00.000-00:00"}]
   :next_cursor "contract-cursor-2"})

(def webhook-body
  {:dead_letters [{:dead_letter_id "DLQ-PULL-1"
                   :counterparty_id "customer-10"
                   :currency "EUR"
                   :observed_at #inst "2026-05-05T13:00:00.000-00:00"}]
   :next_cursor "webhook-cursor-2"})

(def cfg
  {:invoice-recon-enabled true
   :invoice-recon-url "https://invoice.example.invalid"
   :invoice-recon-api-key "example_key"
   :transaction-recon-enabled true
   :transaction-recon-url "https://transactions.example.invalid"
   :transaction-recon-api-key "example_key"
   :contract-lifecycle-enabled true
   :contract-lifecycle-url "https://contracts.example.invalid"
   :contract-lifecycle-api-key "example_key"
   :webhook-engine-enabled true
   :webhook-engine-url "https://webhooks.example.invalid"
   :webhook-engine-api-key "example_key"
   :ingestion-http-send-fns
   {:invoice-recon (fn [_] {:status 200 :body invoice-body})
    :transaction-recon (fn [_] {:status 200 :body transaction-body})
    :contract-lifecycle (fn [_] {:status 200 :body contract-body})
    :webhook-engine (fn [_] {:status 200 :body webhook-body})}
   :ingestion-source-registry
   {:invoice-recon {:run-fn invoice-poll/run-once!}
    :transaction-recon {:run-fn transaction-poll/run-once!}
    :contract-lifecycle {:run-fn contract-poll/run-once!}
    :webhook-engine {:run-fn webhook-poll/run-once!}}
   :ingestion-max-attempts 1})

(defn- reset-domain! []
  (state/reset-store!))

(defn- source-by-system [tenant-id source-system]
  (first (filter #(= source-system (:ingestion-source/source-system %))
                 (ingestion/list-sources tenant-id cfg))))

(deftest lists-tenant-scoped-default-sources-from-runtime-config
  (reset-domain!)
  (let [sources-a (ingestion/list-sources tenant-a cfg)
        sources-b (ingestion/list-sources tenant-b cfg)]
    (is (= #{:invoice-recon :transaction-recon
             :contract-lifecycle :webhook-engine}
           (set (map :ingestion-source/source-system sources-a))))
    (is (every? #(= tenant-a (:ingestion-source/tenant-id %)) sources-a))
    (is (every? #(= tenant-b (:ingestion-source/tenant-id %)) sources-b))
    (is (not= (set (map :ingestion-source/id sources-a))
              (set (map :ingestion-source/id sources-b))))))

(deftest updates-settings-and-keeps-other-tenants-isolated
  (reset-domain!)
  (let [source (source-by-system tenant-a :invoice-recon)
        updated (ingestion/save-settings!
                 tenant-a
                 (:ingestion-source/id source)
                 {:enabled? false
                  :base-url "https://tenant-a.invoice.example"
                  :poll-interval-seconds 120})
        cross (ingestion/get-source tenant-b (:ingestion-source/id source) cfg)]
    (is (= false (:ingestion-source/is-enabled updated)))
    (is (= 120 (get-in updated [:ingestion-source/config
                                :poll-interval-seconds])))
    (is (= "https://tenant-a.invoice.example"
           (get-in updated [:ingestion-source/config :base-url])))
    (is (nil? cross))))

(deftest pull-now-records-run-history-for-every-adapter-source
  (reset-domain!)
  (doseq [source-system [:invoice-recon :transaction-recon
                         :contract-lifecycle :webhook-engine]]
    (let [source (source-by-system tenant-a source-system)
          run (ingestion/pull-now!
               tenant-a (:ingestion-source/id source) cfg)]
      (is (= :succeeded (:ingestion-run/status run)))
      (is (= source-system (:ingestion-run/source-system run)))
      (is (= 1 (:ingestion-run/exceptions-attempted run)))
      (is (= 1 (:ingestion-run/exceptions-stored run)))))
  (is (= 4 (count (ingestion/list-runs tenant-a {}))))
  (is (empty? (ingestion/list-runs tenant-b {}))))

(deftest disabled-and-failed-pulls-update-source_status_without_leaking
  (reset-domain!)
  (let [source (source-by-system tenant-a :invoice-recon)
        disabled-source (ingestion/save-settings!
                         tenant-a (:ingestion-source/id source)
                         {:enabled? false})
        disabled-run (ingestion/pull-now!
                      tenant-a (:ingestion-source/id disabled-source) cfg)
        failed-source (source-by-system tenant-b :invoice-recon)
        failed-run (ingestion/pull-now!
                    tenant-b (:ingestion-source/id failed-source)
                    (assoc cfg :ingestion-http-send-fns
                           {:invoice-recon (fn [_]
                                             {:status 503 :body "busy"})}))]
    (is (= :disabled (:ingestion-run/status disabled-run)))
    (is (= false (:ingestion-source/is-enabled
                  (ingestion/get-source tenant-a
                                        (:ingestion-source/id source)
                                        cfg))))
    (is (= :failed (:ingestion-run/status failed-run)))
    (is (= :upstream-http-error
           (get-in failed-run [:ingestion-run/error :reason])))
    (is (= 1 (count (ingestion/list-runs tenant-a {}))))
    (is (= 1 (count (ingestion/list-runs tenant-b {}))))))

(ns drw.jobs.reconciliation-poll-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.jobs.invoice-recon-poll :as invoice-poll]
            [drw.jobs.transaction-recon-poll :as transaction-poll]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))

(def invoice-cfg
  {:invoice-recon-enabled true
   :invoice-recon-url "https://invoice.example.invalid"
   :invoice-recon-api-key "example_key"})

(def transaction-cfg
  {:transaction-recon-enabled true
   :transaction-recon-url "https://transactions.example.invalid"
   :transaction-recon-api-key "example_key"})

(def invoice-body
  {:discrepancies [{:invoice_id "INV-100"
                    :vendor_id "vendor-7"
                    :discrepancy_amount_cents 12500
                    :currency "EUR"
                    :observed_at #inst "2026-05-05T10:00:00.000-00:00"}]
   :next_cursor "cursor-2"})

(def transaction-body
  {:discrepancies [{:discrepancy_id "TRX-100"
                    :counterparty_name "Globex LLC"
                    :amount_cents -5000
                    :currency "USD"
                    :observed_at #inst "2026-05-05T11:00:00.000-00:00"}]
   :next_cursor "trx-cursor-2"})

(defn- reset-domain! []
  (state/reset-store!))

(deftest invoice-poll-job-stores-normalized-exceptions-and-advances-cursor
  (reset-domain!)
  (let [result (invoice-poll/run-once!
                invoice-cfg
                {:tenant-id tenant-a
                 :cursor "cursor-1"
                 :http-send-fn (fn [_] {:status 200 :body invoice-body})})
        stored (exceptions/list-by-tenant tenant-a {:source-system :invoice-recon})]
    (is (= :succeeded (:status result)))
    (is (= "cursor-2" (:cursor result)))
    (is (= 1 (:exceptions-attempted result)))
    (is (= 1 (:exceptions-stored result)))
    (is (= 0 (:exceptions-skipped result)))
    (is (= 1 (count stored)))
    (is (= "INV-100" (:exception/source-ref (first stored))))
    (is (= "EUR" (:exception/currency (first stored))))
    (is (= 0 (count (exceptions/list-by-tenant tenant-b
                                               {:source-system :invoice-recon}))))))

(deftest transaction-poll-job-stores-normalized-exceptions-and-advances-cursor
  (reset-domain!)
  (let [result (transaction-poll/run-once!
                transaction-cfg
                {:tenant-id tenant-b
                 :cursor "trx-cursor-1"
                 :http-send-fn (fn [_] {:status 200 :body transaction-body})})
        stored (exceptions/list-by-tenant tenant-b
                                          {:source-system :transaction-recon})]
    (is (= :succeeded (:status result)))
    (is (= "trx-cursor-2" (:cursor result)))
    (is (= 1 (:exceptions-attempted result)))
    (is (= 1 (:exceptions-stored result)))
    (is (= 1 (count stored)))
    (is (= "TRX-100" (:exception/source-ref (first stored))))
    (is (= "USD" (:exception/currency (first stored))))
    (is (= 0 (count (exceptions/list-by-tenant tenant-a
                                               {:source-system :transaction-recon}))))))

(deftest reconciliation-poll-jobs-are-disabled-without-side-effects
  (reset-domain!)
  (let [calls (atom 0)
        invoice (invoice-poll/run-once!
                 (assoc invoice-cfg :invoice-recon-enabled false)
                 {:tenant-id tenant-a
                  :cursor "cursor-1"
                  :http-send-fn #(swap! calls inc)})
        transaction (transaction-poll/run-once!
                     (assoc transaction-cfg :transaction-recon-enabled false)
                     {:tenant-id tenant-a
                      :cursor "trx-cursor-1"
                      :http-send-fn #(swap! calls inc)})]
    (is (= 0 @calls))
    (is (= :disabled (:status invoice)))
    (is (= "cursor-1" (:cursor invoice)))
    (is (= :disabled (:status transaction)))
    (is (= "trx-cursor-1" (:cursor transaction)))
    (is (empty? (exceptions/list-by-tenant tenant-a)))))

(deftest upstream-failures-return-failed-runs-without-crashing-or-storing
  (reset-domain!)
  (let [invoice (invoice-poll/run-once!
                 invoice-cfg
                 {:tenant-id tenant-a
                  :cursor "cursor-1"
                  :http-send-fn (fn [_] {:status 503 :body "busy"})
                  :max-attempts 1})
        transaction (transaction-poll/run-once!
                     transaction-cfg
                     {:tenant-id tenant-b
                      :cursor "trx-cursor-1"
                      :http-send-fn
                      (fn [_]
                        (throw (ex-info "timeout"
                                        {:type :adapter/timeout})))
                      :max-attempts 1})]
    (is (= :failed (:status invoice)))
    (is (= :upstream-http-error (get-in invoice [:error :reason])))
    (is (= :failed (:status transaction)))
    (is (= :timeout (get-in transaction [:error :reason])))
    (is (empty? (exceptions/list-by-tenant tenant-a)))
    (is (empty? (exceptions/list-by-tenant tenant-b)))))

(deftest duplicate-source-refs-are-skipped-per-tenant
  (reset-domain!)
  (let [send-fn (fn [_] {:status 200 :body invoice-body})
        first-run (invoice-poll/run-once!
                   invoice-cfg
                   {:tenant-id tenant-a :http-send-fn send-fn})
        second-run (invoice-poll/run-once!
                    invoice-cfg
                    {:tenant-id tenant-a :http-send-fn send-fn})
        other-tenant (invoice-poll/run-once!
                      invoice-cfg
                      {:tenant-id tenant-b :http-send-fn send-fn})]
    (is (= 1 (:exceptions-stored first-run)))
    (is (= 0 (:exceptions-stored second-run)))
    (is (= 1 (:exceptions-skipped second-run)))
    (is (= 1 (:exceptions-stored other-tenant)))
    (is (= 1 (count (exceptions/list-by-tenant tenant-a
                                               {:source-system :invoice-recon}))))
    (is (= 1 (count (exceptions/list-by-tenant tenant-b
                                               {:source-system :invoice-recon}))))))

(ns drw.adapters.reconciliation-adapters-test
  (:require [clojure.test :refer [deftest is]]
            [drw.adapters.invoice-recon :as invoice-recon]
            [drw.adapters.protocol :as adapter]
            [drw.adapters.transaction-recon :as transaction-recon]))

(def invoice-cfg
  {:enabled? true
   :tenant-id "tenant-a"
   :base-url "https://invoice.example.invalid"
   :api-key "example_key"
   :http-send-fn (fn [_] {:status 200 :body {}})})

(def transaction-cfg
  {:enabled? true
   :tenant-id "tenant-a"
   :base-url "https://transactions.example.invalid"
   :api-key "example_key"
   :http-send-fn (fn [_] {:status 200 :body {}})})

(deftest invoice-recon-poll-normalizes-successful-discrepancies-and-cursor
  (let [requests (atom [])
        result (adapter/poll!
                invoice-recon/adapter
                (assoc invoice-cfg
                       :http-send-fn
                       (fn [request]
                         (swap! requests conj request)
                         {:status 200
                          :body {:discrepancies
                                 [{:invoice_id "INV-100"
                                   :vendor_id "vendor-7"
                                   :source_url "https://invoice.example/INV-100"
                                   :discrepancy_amount_cents 12500
                                   :currency "EUR"
                                   :observed_at #inst "2026-05-05T10:00:00.000-00:00"}]
                                 :next_cursor "cursor-2"}}))
                "cursor-1")]
    (is (= false (:error? result)))
    (is (= "cursor-2" (:cursor result)))
    (is (= "https://invoice.example.invalid/api/discrepancies?since=cursor-1&limit=100"
           (:url (first @requests))))
    (is (= [{:tenant-id "tenant-a"
             :source-system :invoice-recon
             :source-ref "INV-100"
             :source-url "https://invoice.example/INV-100"
             :kind :invoice-discrepancy
             :raw-payload {:invoice_id "INV-100"
                           :vendor_id "vendor-7"
                           :source_url "https://invoice.example/INV-100"
                           :discrepancy_amount_cents 12500
                           :currency "EUR"
                           :observed_at #inst "2026-05-05T10:00:00.000-00:00"}
             :counterparty-external-ref "vendor-7"
             :monetary-impact-cents 12500
             :currency "EUR"
             :observed-at #inst "2026-05-05T10:00:00.000-00:00"}]
           (:exceptions result)))))

(deftest transaction-recon-poll-normalizes-successful-discrepancies-and-cursor
  (let [requests (atom [])
        result (adapter/poll!
                transaction-recon/adapter
                (assoc transaction-cfg
                       :tenant-id "tenant-b"
                       :http-send-fn
                       (fn [request]
                         (swap! requests conj request)
                         {:status 200
                          :body {:discrepancies
                                 [{:discrepancy_id "TRX-100"
                                   :counterparty_name "Globex LLC"
                                   :amount_cents -5000
                                   :currency "USD"
                                   :observed_at #inst "2026-05-05T11:00:00.000-00:00"}]
                                 :next_cursor "trx-cursor-2"}}))
                "trx-cursor-1")]
    (is (= false (:error? result)))
    (is (= "trx-cursor-2" (:cursor result)))
    (is (= "https://transactions.example.invalid/api/v1/discrepancies?since=trx-cursor-1&limit=100"
           (:url (first @requests))))
    (is (= [{:tenant-id "tenant-b"
             :source-system :transaction-recon
             :source-ref "TRX-100"
             :source-url nil
             :kind :payment-mismatch
             :raw-payload {:discrepancy_id "TRX-100"
                           :counterparty_name "Globex LLC"
                           :amount_cents -5000
                           :currency "USD"
                           :observed_at #inst "2026-05-05T11:00:00.000-00:00"}
             :counterparty-name "Globex LLC"
             :monetary-impact-cents -5000
             :currency "USD"
             :observed-at #inst "2026-05-05T11:00:00.000-00:00"}]
           (:exceptions result)))))

(deftest disabled-reconciliation-adapters-do-not-fetch-and-keep-cursor
  (let [calls (atom 0)
        invoice (adapter/poll!
                 invoice-recon/adapter
                 (assoc invoice-cfg
                        :enabled? false
                        :http-send-fn #(swap! calls inc))
                 "cursor-1")
        transaction (adapter/poll!
                     transaction-recon/adapter
                     (assoc transaction-cfg
                            :enabled? false
                            :http-send-fn #(swap! calls inc))
                     "trx-cursor-1")]
    (is (= 0 @calls))
    (is (= false (:error? invoice)))
    (is (= [] (:exceptions invoice)))
    (is (= "cursor-1" (:cursor invoice)))
    (is (= :invoice-recon (:source-system invoice)))
    (is (= false (:error? transaction)))
    (is (= [] (:exceptions transaction)))
    (is (= "trx-cursor-1" (:cursor transaction)))
    (is (= :transaction-recon (:source-system transaction)))))

(deftest upstream-fetch-failures-become-poll-errors-without-throwing
  (let [invoice (adapter/poll!
                 invoice-recon/adapter
                 (assoc invoice-cfg
                        :max-attempts 1
                        :http-send-fn (fn [_] {:status 503 :body "busy"}))
                 "cursor-1")
        transaction (adapter/poll!
                     transaction-recon/adapter
                     (assoc transaction-cfg
                            :max-attempts 1
                            :http-send-fn
                            (fn [_]
                              (throw (ex-info "timeout"
                                              {:type :adapter/timeout}))))
                     "trx-cursor-1")]
    (is (= true (:error? invoice)))
    (is (= :upstream-http-error (get-in invoice [:error :reason])))
    (is (= "cursor-1" (get-in invoice [:error :cursor])))
    (is (= true (:error? transaction)))
    (is (= :timeout (get-in transaction [:error :reason])))
    (is (= "trx-cursor-1" (get-in transaction [:error :cursor])))))

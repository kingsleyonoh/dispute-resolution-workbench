(ns drw.adapters.contract-lifecycle-test
  (:require [clojure.test :refer [deftest is]]
            [drw.adapters.contract-lifecycle :as contract]
            [drw.adapters.protocol :as adapter]))

(def contract-cfg
  {:enabled? true
   :tenant-id "tenant-a"
   :base-url "https://contracts.example.invalid"
   :api-key "example_key"
   :http-send-fn (fn [_] {:status 200 :body {}})})

(deftest contract-backfill-normalizes-obligations-and-cursor
  (let [requests (atom [])
        result (adapter/poll!
                contract/adapter
                (assoc contract-cfg
                       :http-send-fn
                       (fn [request]
                         (swap! requests conj request)
                         {:status 200
                          :body {:obligations
                                 [{:obligation_id "OBL-100"
                                   :counterparty_id "customer-7"
                                   :source_url "https://contracts/OBL-100"
                                   :financial_exposure_cents 22000
                                   :currency "EUR"
                                   :observed_at #inst "2026-05-05T12:00:00.000-00:00"
                                   :kind "obligation_breached"}]
                                 :next_cursor "contract-cursor-2"}}))
                "contract-cursor-1")]
    (is (= false (:error? result)))
    (is (= "contract-cursor-2" (:cursor result)))
    (is (= "https://contracts.example.invalid/api/obligations?status=breached%2Coverdue&since=contract-cursor-1&limit=100"
           (:url (first @requests))))
    (is (= [{:tenant-id "tenant-a"
             :source-system :contract-lifecycle
             :source-ref "OBL-100"
             :source-url "https://contracts/OBL-100"
             :kind :contract-breach
             :raw-payload {:obligation_id "OBL-100"
                           :counterparty_id "customer-7"
                           :source_url "https://contracts/OBL-100"
                           :financial_exposure_cents 22000
                           :currency "EUR"
                           :observed_at #inst "2026-05-05T12:00:00.000-00:00"
                           :kind "obligation_breached"}
             :counterparty-external-ref "customer-7"
             :monetary-impact-cents 22000
             :currency "EUR"
             :observed-at #inst "2026-05-05T12:00:00.000-00:00"}]
           (:exceptions result)))))

(deftest disabled-contract-adapter-does-not-fetch-and-keeps-cursor
  (let [calls (atom 0)
        result (adapter/poll!
                contract/adapter
                (assoc contract-cfg
                       :enabled? false
                       :http-send-fn #(swap! calls inc))
                "contract-cursor-1")]
    (is (= 0 @calls))
    (is (= false (:error? result)))
    (is (= [] (:exceptions result)))
    (is (= "contract-cursor-1" (:cursor result)))
    (is (= :contract-lifecycle (:source-system result)))))

(deftest upstream-contract-failures-become-poll-errors
  (let [result (adapter/poll!
                contract/adapter
                (assoc contract-cfg
                       :max-attempts 1
                       :http-send-fn (fn [_] {:status 503 :body "busy"}))
                "contract-cursor-1")]
    (is (= true (:error? result)))
    (is (= :upstream-http-error (get-in result [:error :reason])))
    (is (= "contract-cursor-1" (get-in result [:error :cursor])))))

(deftest contract-webhook-events-normalize-subject-specific-kinds
  (let [breach (adapter/parse-webhook
                contract/adapter
                {:tenant-id "tenant-a"}
                {:obligation_id "OBL-200"
                 :counterparty_id "customer-9"
                 :financial_exposure_cents 4500
                 :currency "USD"
                 :occurred_at #inst "2026-05-05T13:00:00.000-00:00"}
                {:subject "contract.obligation.breached"})
        conflict (adapter/parse-webhook
                  contract/adapter
                  {:tenant-id "tenant-a"}
                  {:conflict_id "CONFLICT-7"
                   :counterparty_id "customer-9"
                   :financial_exposure_cents 0
                   :currency "USD"
                   :observed_at #inst "2026-05-05T14:00:00.000-00:00"}
                  {:subject "contract.conflict.detected"})]
    (is (= :contract-breach (:kind breach)))
    (is (= "OBL-200" (:source-ref breach)))
    (is (= :contract-conflict (:kind conflict)))
    (is (= "CONFLICT-7" (:source-ref conflict)))))

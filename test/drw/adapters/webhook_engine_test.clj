(ns drw.adapters.webhook-engine-test
  (:require [clojure.test :refer [deftest is]]
            [drw.adapters.protocol :as adapter]
            [drw.adapters.webhook-engine :as webhook-engine]))

(def webhook-cfg
  {:enabled? true
   :tenant-id "tenant-a"
   :base-url "https://webhooks.example.invalid"
   :api-key "example_key"
   :http-send-fn (fn [_] {:status 200 :body {}})})

(deftest webhook-dlq-poll-normalizes-dead-letters-and-cursor
  (let [requests (atom [])
        result (adapter/poll!
                webhook-engine/adapter
                (assoc webhook-cfg
                       :http-send-fn
                       (fn [request]
                         (swap! requests conj request)
                         {:status 200
                          :body {:dead_letters
                                 [{:dead_letter_id "DLQ-100"
                                   :counterparty_id "customer-7"
                                   :source_url "https://webhooks/DLQ-100"
                                   :delivery_attempts 8
                                   :last_status 500
                                   :currency "EUR"
                                   :observed_at #inst "2026-05-05T15:00:00.000-00:00"}]
                                 :next_cursor "dlq-cursor-2"}}))
                "dlq-cursor-1")]
    (is (= false (:error? result)))
    (is (= "dlq-cursor-2" (:cursor result)))
    (is (= "https://webhooks.example.invalid/api/dead-letters?since=dlq-cursor-1&limit=100"
           (:url (first @requests))))
    (is (= [{:tenant-id "tenant-a"
             :source-system :webhook-engine
             :source-ref "DLQ-100"
             :source-url "https://webhooks/DLQ-100"
             :kind :delivery-failure
             :raw-payload {:dead_letter_id "DLQ-100"
                           :counterparty_id "customer-7"
                           :source_url "https://webhooks/DLQ-100"
                           :delivery_attempts 8
                           :last_status 500
                           :currency "EUR"
                           :observed_at #inst "2026-05-05T15:00:00.000-00:00"}
             :counterparty-external-ref "customer-7"
             :monetary-impact-cents 0
             :currency "EUR"
             :observed-at #inst "2026-05-05T15:00:00.000-00:00"}]
           (:exceptions result)))))

(deftest disabled-webhook-adapter-does-not-fetch-and-keeps-cursor
  (let [calls (atom 0)
        result (adapter/poll!
                webhook-engine/adapter
                (assoc webhook-cfg
                       :enabled? false
                       :http-send-fn #(swap! calls inc))
                "dlq-cursor-1")]
    (is (= 0 @calls))
    (is (= false (:error? result)))
    (is (= [] (:exceptions result)))
    (is (= "dlq-cursor-1" (:cursor result)))
    (is (= :webhook-engine (:source-system result)))))

(deftest upstream-webhook-failures-become-poll-errors
  (let [result (adapter/poll!
                webhook-engine/adapter
                (assoc webhook-cfg
                       :max-attempts 1
                       :http-send-fn (fn [_] {:status 503 :body "busy"}))
                "dlq-cursor-1")]
    (is (= true (:error? result)))
    (is (= :upstream-http-error (get-in result [:error :reason])))
    (is (= "dlq-cursor-1" (get-in result [:error :cursor])))))

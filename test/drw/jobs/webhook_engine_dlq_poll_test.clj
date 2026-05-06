(ns drw.jobs.webhook-engine-dlq-poll-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.jobs.webhook-engine-dlq-poll :as dlq-poll]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))

(def webhook-cfg
  {:webhook-engine-enabled true
   :webhook-engine-url "https://webhooks.example.invalid"
   :webhook-engine-api-key "example_key"})

(def dead-letter-body
  {:dead_letters [{:dead_letter_id "DLQ-100"
                   :counterparty_id "customer-7"
                   :delivery_attempts 8
                   :last_status 500
                   :currency "EUR"
                   :observed_at #inst "2026-05-05T15:00:00.000-00:00"}]
   :next_cursor "dlq-cursor-2"})

(defn- reset-domain! []
  (state/reset-store!))

(deftest webhook-dlq-job-stores-exceptions-and-advances-cursor
  (reset-domain!)
  (let [result (dlq-poll/run-once!
                webhook-cfg
                {:tenant-id tenant-a
                 :cursor "dlq-cursor-1"
                 :http-send-fn (fn [_] {:status 200 :body dead-letter-body})})
        stored (exceptions/list-by-tenant tenant-a
                                          {:source-system :webhook-engine})]
    (is (= :succeeded (:status result)))
    (is (= "dlq-cursor-2" (:cursor result)))
    (is (= 1 (:exceptions-attempted result)))
    (is (= 1 (:exceptions-stored result)))
    (is (= 1 (count stored)))
    (is (= "DLQ-100" (:exception/source-ref (first stored))))
    (is (= :delivery-failure (:exception/kind (first stored))))
    (is (= 0 (count (exceptions/list-by-tenant
                     tenant-b
                     {:source-system :webhook-engine}))))))

(deftest webhook-dlq-disabled-and-upstream-failure-are-isolated
  (reset-domain!)
  (let [calls (atom 0)
        disabled (dlq-poll/run-once!
                  (assoc webhook-cfg :webhook-engine-enabled false)
                  {:tenant-id tenant-a
                   :cursor "dlq-cursor-1"
                   :http-send-fn #(swap! calls inc)})
        failed (dlq-poll/run-once!
                webhook-cfg
                {:tenant-id tenant-a
                 :cursor "dlq-cursor-1"
                 :http-send-fn (fn [_] {:status 503 :body "busy"})
                 :max-attempts 1})]
    (is (= 0 @calls))
    (is (= :disabled (:status disabled)))
    (is (= "dlq-cursor-1" (:cursor disabled)))
    (is (= :failed (:status failed)))
    (is (= :upstream-http-error (get-in failed [:error :reason])))
    (is (empty? (exceptions/list-by-tenant tenant-a)))))

(deftest webhook-dlq-source-refs-are-deduped-per-tenant
  (reset-domain!)
  (let [send-fn (fn [_] {:status 200 :body dead-letter-body})
        first-run (dlq-poll/run-once!
                   webhook-cfg
                   {:tenant-id tenant-a :http-send-fn send-fn})
        second-run (dlq-poll/run-once!
                    webhook-cfg
                    {:tenant-id tenant-a :http-send-fn send-fn})
        other-tenant (dlq-poll/run-once!
                      webhook-cfg
                      {:tenant-id tenant-b :http-send-fn send-fn})]
    (is (= 1 (:exceptions-stored first-run)))
    (is (= 0 (:exceptions-stored second-run)))
    (is (= 1 (:exceptions-skipped second-run)))
    (is (= 1 (:exceptions-stored other-tenant)))
    (is (= 1 (count (exceptions/list-by-tenant
                     tenant-a
                     {:source-system :webhook-engine}))))
    (is (= 1 (count (exceptions/list-by-tenant
                     tenant-b
                     {:source-system :webhook-engine}))))))

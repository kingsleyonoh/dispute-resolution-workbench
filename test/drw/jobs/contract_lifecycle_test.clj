(ns drw.jobs.contract-lifecycle-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.jobs.contract-lifecycle-backfill :as backfill]
            [drw.jobs.contract-lifecycle-nats-consumer :as consumer]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))

(def contract-cfg
  {:contract-lifecycle-enabled true
   :contract-lifecycle-url "https://contracts.example.invalid"
   :contract-lifecycle-api-key "example_key"
   :nats-enabled true
   :nats-url "nats://localhost:4222"
   :nats-stream-name "ECOSYSTEM_EVENTS"})

(def obligation-body
  {:obligations [{:obligation_id "OBL-100"
                  :counterparty_id "customer-7"
                  :financial_exposure_cents 22000
                  :currency "EUR"
                  :observed_at #inst "2026-05-05T12:00:00.000-00:00"}]
   :next_cursor "contract-cursor-2"})

(defn- reset-domain! []
  (state/reset-store!))

(deftest contract-backfill-job-stores-exceptions-and-advances-cursor
  (reset-domain!)
  (let [result (backfill/run-once!
                contract-cfg
                {:tenant-id tenant-a
                 :cursor "contract-cursor-1"
                 :http-send-fn (fn [_] {:status 200 :body obligation-body})})
        stored (exceptions/list-by-tenant tenant-a
                                          {:source-system :contract-lifecycle})]
    (is (= :succeeded (:status result)))
    (is (= "contract-cursor-2" (:cursor result)))
    (is (= 1 (:exceptions-attempted result)))
    (is (= 1 (:exceptions-stored result)))
    (is (= 1 (count stored)))
    (is (= "OBL-100" (:exception/source-ref (first stored))))
    (is (= 0 (count (exceptions/list-by-tenant
                     tenant-b
                     {:source-system :contract-lifecycle}))))))

(deftest contract-backfill-disabled-and-upstream-failure-are-isolated
  (reset-domain!)
  (let [calls (atom 0)
        disabled (backfill/run-once!
                  (assoc contract-cfg :contract-lifecycle-enabled false)
                  {:tenant-id tenant-a
                   :cursor "contract-cursor-1"
                   :http-send-fn #(swap! calls inc)})
        failed (backfill/run-once!
                contract-cfg
                {:tenant-id tenant-a
                 :cursor "contract-cursor-1"
                 :http-send-fn (fn [_] {:status 503 :body "busy"})
                 :max-attempts 1})]
    (is (= 0 @calls))
    (is (= :disabled (:status disabled)))
    (is (= "contract-cursor-1" (:cursor disabled)))
    (is (= :failed (:status failed)))
    (is (= :upstream-http-error (get-in failed [:error :reason])))
    (is (empty? (exceptions/list-by-tenant tenant-a)))))

(deftest nats-consumer-stores-events-idempotently-per-tenant
  (reset-domain!)
  (let [events [{:subject "contract.obligation.breached"
                 :payload {:obligation_id "OBL-200"
                           :counterparty_id "customer-9"
                           :financial_exposure_cents 4500
                           :currency "USD"
                           :observed_at #inst "2026-05-05T13:00:00.000-00:00"}}
                {:subject "contract.obligation.breached"
                 :payload {:obligation_id "OBL-200"
                           :counterparty_id "customer-9"
                           :financial_exposure_cents 4500
                           :currency "USD"
                           :observed_at #inst "2026-05-05T13:00:00.000-00:00"}}]
        connect-fn (fn [_]
                     {:subscribe-fn
                      (fn [subject handler _]
                        (doseq [event events
                                :when (= subject (:subject event))]
                          (handler event))
                        {:subject subject})})
        first-run (consumer/run-once!
                   contract-cfg
                   {:tenant-id tenant-a :nats-connect-fn connect-fn})
        other-tenant (consumer/run-once!
                      contract-cfg
                      {:tenant-id tenant-b :nats-connect-fn connect-fn})]
    (is (= :subscribed (:status first-run)))
    (is (= 1 (:exceptions-stored first-run)))
    (is (= 1 (:exceptions-skipped first-run)))
    (is (= 1 (:exceptions-stored other-tenant)))
    (is (= 1 (count (exceptions/list-by-tenant
                     tenant-a
                     {:source-system :contract-lifecycle}))))
    (is (= 1 (count (exceptions/list-by-tenant
                     tenant-b
                     {:source-system :contract-lifecycle}))))))

(deftest nats-consumer-disabled-or-tenant-mismatched-events-do-not-store
  (reset-domain!)
  (let [calls (atom 0)
        disabled (consumer/run-once!
                  (assoc contract-cfg :nats-enabled false)
                  {:tenant-id tenant-a
                   :nats-connect-fn #(swap! calls inc)})
        mismatch (consumer/handle-message!
                  {:tenant-id tenant-a}
                  {:subject "contract.conflict.detected"
                   :payload {:tenant_id tenant-b
                             :conflict_id "CONFLICT-9"
                             :financial_exposure_cents 0
                             :currency "USD"
                             :observed_at #inst "2026-05-05T14:00:00.000-00:00"}})]
    (is (= 0 @calls))
    (is (= :disabled (:status disabled)))
    (is (= :rejected (:status mismatch)))
    (is (empty? (exceptions/list-by-tenant tenant-a)))
    (is (empty? (exceptions/list-by-tenant tenant-b)))))

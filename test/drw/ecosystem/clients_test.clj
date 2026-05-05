(ns drw.ecosystem.clients-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.ecosystem.hub-client :as hub]
            [drw.ecosystem.workflow-client :as workflow]))

(deftest hub-client-is-safe-when-disabled
  (is (= {:status :disabled
          :sent? false
          :service :notification-hub
          :event-type "dispute.created"}
         (hub/emit-event! {:notification-hub-enabled false}
                          {:event-type "dispute.created"
                           :payload {:dispute-id "DIS-1"}}))))

(deftest hub-client-fails-loudly-when-enabled-without-config
  (testing "missing URL or key never silently no-ops"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Notification Hub configuration is incomplete"
         (hub/emit-event! {:notification-hub-enabled true}
                          {:event-type "dispute.created"
                           :payload {}})))))

(deftest hub-client-enabled-stub-builds-event-request-without-network
  (let [result (hub/emit-event!
                {:notification-hub-enabled true
                 :notification-hub-url "https://notify.example.invalid"
                 :notification-hub-api-key "example_key"}
                {:event-type "dispute.created"
                 :event-id "evt-1"
                 :payload {:dispute-id "DIS-1"}})]
    (is (= :stubbed (:status result)))
    (is (false? (:sent? result)))
    (is (= "https://notify.example.invalid/api/events" (:endpoint result)))
    (is (= "dispute.created" (get-in result [:event :event_type])))))

(deftest workflow-client-is-safe-when-disabled
  (is (= {:status :disabled
          :sent? false
          :service :workflow-engine
          :workflow-id "wf-credit-note"}
         (workflow/trigger-workflow!
          {:workflow-engine-enabled false}
          "wf-credit-note"
          {:dispute-id "DIS-1"}))))

(deftest workflow-client-fails-loudly-when-enabled-without-config
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"Workflow Engine configuration is incomplete"
       (workflow/trigger-workflow!
        {:workflow-engine-enabled true}
        "wf-credit-note"
        {:dispute-id "DIS-1"}))))

(deftest workflow-client-enabled-stub-builds-execution-request-without-network
  (let [result (workflow/trigger-workflow!
                {:workflow-engine-enabled true
                 :workflow-engine-url "https://workflows.example.invalid"
                 :workflow-engine-api-key "example_key"}
                "wf-credit-note"
                {:dispute-id "DIS-1"})]
    (is (= :stubbed (:status result)))
    (is (false? (:sent? result)))
    (is (= "https://workflows.example.invalid/api/workflows/wf-credit-note/execute"
           (:endpoint result)))
    (is (= {:dispute_id "DIS-1"} (:trigger_data result)))))

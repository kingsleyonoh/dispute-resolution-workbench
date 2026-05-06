(ns drw.domain.resolution-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.disputes :as disputes]
            [drw.domain.resolution :as resolution]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenant-id (:tenant/id (get (fixtures/tenants-by-slug) "acme-gmbh-de")))
(def actor {:actor-kind :user :actor-id "operator-1"})

(def playbook
  {:playbook/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
   :playbook/tenant-id tenant-id
   :playbook/code "credit-note-and-refund"
   :playbook/workflow-engine-workflow-id "wf-credit-note"
   :playbook/is-active true})

(defn- reset-domain! []
  (state/reset-store!))

(defn- dispute! []
  (let [dispute (disputes/create-dispute!
                 {:tenant-id tenant-id
                  :title "Resolution target"
                  :description "Needs workflow"
                  :category :billing
                  :severity :high
                  :currency "EUR"
                  :created-by :user}
                 actor)]
    (disputes/assign! tenant-id (:dispute/id dispute)
                      {:user-id #uuid "33333333-3333-3333-3333-333333333333"}
                      actor)
    (disputes/transition! tenant-id (:dispute/id dispute)
                          {:to :investigating}
                          actor)))

(deftest starts-resolution-through-workflow-engine
  (reset-domain!)
  (let [dispute (dispute!)
        sent (atom nil)
        result (resolution/start-resolution!
                tenant-id
                (:dispute/id dispute)
                playbook
                {:refund_cents 1200}
                {:workflow-engine-enabled true
                 :workflow-engine-url "https://workflows.example.invalid"
                 :workflow-engine-api-key "example_key"
                 :workflow-engine-send-fn
                 (fn [request]
                   (reset! sent request)
                   {:status :started :execution-id "exec-1"})}
                actor)
        updated (disputes/get-by-id tenant-id (:dispute/id dispute))]
    (is (= :started (:status result)))
    (is (= :resolving (:dispute/status updated)))
    (is (= "exec-1" (:dispute/workflow-execution-id updated)))
    (is (= "wf-credit-note" (:workflow-id result)))
    (is (= (:dispute/id dispute)
           (get-in @sent [:trigger-data :dispute_id])))
    (is (= :workflow-triggered
           (last (map :timeline/kind
                      (disputes/list-timeline tenant-id
                                              (:dispute/id dispute))))))))

(deftest rejects-invalid-resolution-starts
  (reset-domain!)
  (let [dispute (dispute!)
        cfg {:workflow-engine-enabled false}]
    (is (= :resolution/workflow-disabled
           (:type (ex-data
                   (try
                     (resolution/start-resolution!
                      tenant-id (:dispute/id dispute) playbook {} cfg actor)
                     (catch clojure.lang.ExceptionInfo ex ex))))))
    (disputes/transition! tenant-id (:dispute/id dispute) {:to :withdrawn} actor)
    (is (= :dispute/terminal
           (:type (ex-data
                   (try
                     (resolution/start-resolution!
                      tenant-id (:dispute/id dispute) playbook {} cfg actor)
                     (catch clojure.lang.ExceptionInfo ex ex))))))))

(deftest poll-updates-completed-and-failed-resolutions
  (reset-domain!)
  (let [completed (dispute!)
        failed (dispute!)
        cfg {:workflow-engine-enabled true
             :workflow-engine-url "https://workflows.example.invalid"
             :workflow-engine-api-key "example_key"
             :workflow-engine-send-fn
             (fn [_] {:status :started :execution-id (str (random-uuid))})
             :workflow-engine-execution-fn
             (fn [{:keys [execution-id]}]
               (if (= execution-id "exec-complete")
                 {:status :succeeded :summary "Credit note issued."}
                 {:status :failed :summary "Refund step failed."}))}]
    (disputes/mark-workflow-started!
     tenant-id (:dispute/id completed) "exec-complete" actor)
    (disputes/mark-workflow-started!
     tenant-id (:dispute/id failed) "exec-failed" actor)
    (is (= 2 (count (resolution/poll-active! cfg actor))))
    (is (= :resolved (:dispute/status
                      (disputes/get-by-id tenant-id (:dispute/id completed)))))
    (is (= :investigating (:dispute/status
                           (disputes/get-by-id tenant-id (:dispute/id failed)))))
    (is (= "Credit note issued."
           (:dispute/resolution-summary
            (disputes/get-by-id tenant-id (:dispute/id completed)))))))

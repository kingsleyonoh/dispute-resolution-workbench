(ns drw.jobs.resolution-poller-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.disputes :as disputes]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.jobs.resolution-poller :as poller]))

(def tenant-id (:tenant/id (get (fixtures/tenants-by-slug) "acme-gmbh-de")))
(def actor {:actor-kind :user :actor-id "operator-1"})

(deftest poller-drives-active-resolution-statuses
  (state/reset-store!)
  (let [created (disputes/create-dispute!
                 {:tenant-id tenant-id
                  :title "Poller target"
                  :description "Workflow active"
                  :category :billing
                  :severity :medium
                  :currency "EUR"
                  :created-by :user}
                 actor)
        assigned (disputes/assign!
                  tenant-id (:dispute/id created)
                  {:user-id #uuid "33333333-3333-3333-3333-333333333333"}
                  actor)
        dispute (disputes/transition!
                 tenant-id (:dispute/id assigned) {:to :investigating} actor)]
    (disputes/mark-workflow-started!
     tenant-id (:dispute/id dispute) "exec-poller" actor)
    (is (= [{:execution-id "exec-poller"
             :status :succeeded
             :dispute-id (:dispute/id dispute)}]
           (poller/run-once!
            {:workflow-engine-enabled true
             :workflow-engine-url "https://workflows.example.invalid"
             :workflow-engine-api-key "example_key"
             :workflow-engine-execution-fn
             (fn [_] {:status :succeeded :summary "Done"})})))
    (is (= :resolved (:dispute/status
                      (disputes/get-by-id tenant-id (:dispute/id dispute)))))))

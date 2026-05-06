(ns ^:e2e drw.e2e-api.full-flow-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.disputes :as disputes]
            [drw.domain.playbooks :as playbooks]
            [drw.domain.reports :as reports]
            [drw.domain.resolution :as resolution]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.jobs.invoice-recon-poll :as invoice-poll]
            [drw.jobs.resolution-poller :as resolution-poller]))

(def tenants (fixtures/load-tenants))
(def tenant-id (:tenant/id (get (fixtures/tenants-by-slug) "acme-gmbh-de")))
(def actor {:actor-kind :user :actor-id "full-flow"})

(def invoice-body
  {:discrepancies [{:invoice_id "FULL-FLOW-INV-1"
                    :vendor_id "full-flow-vendor"
                    :discrepancy_amount_cents 4200
                    :currency "EUR"
                    :observed_at #inst "2026-05-05T10:00:00.000-00:00"}]
   :next_cursor "full-flow-cursor"})

(def cfg
  {:invoice-recon-enabled true
   :invoice-recon-url "https://invoice.example.invalid"
   :invoice-recon-api-key "example_key"
   :workflow-engine-enabled true
   :workflow-engine-url "https://workflows.example.invalid"
   :workflow-engine-api-key "example_key"
   :notification-hub-enabled true
   :notification-hub-url "https://notify.example.invalid"
   :notification-hub-api-key "example_key"})

(defn- created-dispute []
  (first (filter #(str/includes? (:dispute/title %) "FULL-FLOW-INV-1")
                 (disputes/list-by-tenant tenant-id))))

(deftest adapter-to-resolution-to-pdf-flow-runs-locally
  (state/reset-store!)
  (let [hub-events (atom [])
        pdf-requests (atom [])
        workflow-starts (atom [])
        playbook (playbooks/save!
                  tenant-id
                  {:code "full-flow-credit"
                   :display-name "Full flow credit"
                   :workflow-engine-workflow-id "wf-full-flow"}
                  actor)
        poll (invoice-poll/run-once!
              cfg
              {:tenant-id tenant-id
               :http-send-fn (fn [_] {:status 200 :body invoice-body})})
        dispute (created-dispute)
        assigned (disputes/assign!
                  tenant-id
                  (:dispute/id dispute)
                  {:user-id #uuid "33333333-3333-3333-3333-333333333333"}
                  actor)
        investigating (disputes/transition!
                       tenant-id
                       (:dispute/id dispute)
                       {:to :investigating}
                       actor)
        started (resolution/start-resolution!
                 tenant-id (:dispute/id dispute) playbook {}
                 (assoc cfg
                        :workflow-engine-send-fn
                        (fn [request]
                          (swap! workflow-starts conj request)
                          {:status :started :execution-id "exec-full-flow"}))
                 actor)
        poll-results (resolution-poller/run-once!
                      (assoc cfg
                             :workflow-engine-execution-fn
                             (fn [_] {:status :succeeded
                                      :summary "Credit issued from full flow."})
                             :notification-hub-send-fn
                             #(swap! hub-events conj %)))
        resolved (disputes/get-by-id tenant-id (:dispute/id dispute))
        report (reports/generate-dispute-audit-pdf!
                {:report-storage-dir "target/full-flow-reports"
                 :pdf-render-fn
                 (fn [request]
                   (swap! pdf-requests conj request)
                   (.getBytes (str "PDF:" (:html request)) "UTF-8"))}
                tenants
                tenant-id
                (:dispute/id dispute)
                actor)]
    (is (= :succeeded (:status poll)))
    (is (= 1 (:exceptions-stored poll)))
    (is (some? dispute))
    (is (= :assigned (:dispute/status assigned)))
    (is (= :investigating (:dispute/status investigating)))
    (is (= :started (:status started)))
    (is (= "wf-full-flow" (:workflow-id (first @workflow-starts))))
    (is (= [{:execution-id "exec-full-flow"
             :status :succeeded
             :dispute-id (:dispute/id dispute)}]
           poll-results))
    (is (= :resolved (:dispute/status resolved)))
    (is (= ["dispute.resolved"]
           (map #(get-in % [:event :event_type]) @hub-events)))
    (is (= :ready (:report/status report)))
    (is (str/includes? (-> @pdf-requests first :html)
                       "Credit issued from full flow."))
    (is (str/includes? (-> @pdf-requests first :html)
                       "FULL-FLOW-INV-1"))))

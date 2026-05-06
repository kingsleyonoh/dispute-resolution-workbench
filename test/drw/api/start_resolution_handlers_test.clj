(ns drw.api.start-resolution-handlers-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.api.disputes :as api-disputes]
            [drw.domain.disputes :as disputes]
            [drw.domain.playbooks :as playbooks]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))

(defn- req [tenant-id body path-params]
  {:current-tenant {:tenant-id tenant-id :slug "tenant"}
   :body body
   :path-params (or path-params {})
   :query-params {}})

(defn- body-includes? [response value]
  (str/includes? (str (:body response)) value))

(defn- seed-investigating-dispute! [tenant-id]
  (let [actor {:actor-kind :user :actor-id "seed"}
        dispute (disputes/create-dispute!
                 {:tenant-id tenant-id
                  :title "Resolution target"
                  :description "Needs workflow."
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

(deftest dispute-start-resolution-api-triggers-workflow-and-enforces-tenancy
  (state/reset-store!)
  (let [actor {:actor-kind :user :actor-id "seed"}
        dispute (seed-investigating-dispute! tenant-a)
        playbook (playbooks/save!
                  tenant-a
                  {:code "credit-note-and-refund"
                   :display-name "Credit note and refund"
                   :workflow-engine-workflow-id "wf-credit"
                   :required-inputs-schema "{}"}
                  actor)
        other-playbook (playbooks/save!
                        tenant-b
                        {:code "other"
                         :display-name "Other"
                         :workflow-engine-workflow-id "wf-other"}
                        actor)
        sent (atom nil)
        cfg {:workflow-engine-enabled true
             :workflow-engine-url "https://workflows.example.invalid"
             :workflow-engine-api-key "example_key"
             :workflow-engine-send-fn
             (fn [request]
               (reset! sent request)
               {:status :started :execution-id "exec-api"})}
        path {:id (str (:dispute/id dispute))}
        started ((api-disputes/start-resolution-handler cfg)
                 (req tenant-a
                      (str "{\"playbook_id\":\"" (:playbook/id playbook)
                           "\",\"inputs_json\":\"{\\\"refund\\\":true}\"}")
                      path))
        duplicate ((api-disputes/start-resolution-handler cfg)
                   (req tenant-a
                        (str "{\"playbook_id\":\"" (:playbook/id playbook) "\"}")
                        path))
        cross ((api-disputes/start-resolution-handler cfg)
               (req tenant-a
                    (str "{\"playbook_id\":\"" (:playbook/id other-playbook) "\"}")
                    path))
        updated (disputes/get-by-id tenant-a (:dispute/id dispute))]
    (is (= 201 (:status started)))
    (is (body-includes? started "\"executionId\":\"exec-api\""))
    (is (= :resolving (:dispute/status updated)))
    (is (= "exec-api" (:dispute/workflow-execution-id updated)))
    (is (= (:dispute/id dispute)
           (get-in @sent [:trigger-data :dispute_id])))
    (is (= 409 (:status duplicate)))
    (is (= 404 (:status cross)))))

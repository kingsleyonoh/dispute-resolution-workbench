(ns drw.domain.playbooks-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.playbooks :as playbooks]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))
(def actor {:actor-kind :user :actor-id "operator-1"})

(def attrs
  {:code "credit-note-and-refund"
   :display-name "Credit note and refund"
   :description "Issue a credit note and refund the buyer."
   :workflow-engine-workflow-id "wf-credit-refund"
   :required-inputs-schema "{\"type\":\"map\"}"
   :is-active true})

(deftest saves-lists-updates-and-disables-playbooks-per-tenant
  (state/reset-store!)
  (let [created (playbooks/save! tenant-a attrs actor)
        updated (playbooks/save! tenant-a
                                 (assoc attrs
                                        :id (:playbook/id created)
                                        :display-name "Refund package")
                                 actor)
        disabled (playbooks/disable! tenant-a (:playbook/id created) actor)
        tenant-b-item (playbooks/save! tenant-b attrs actor)]
    (is (= "Credit note and refund" (:playbook/display-name created)))
    (is (= "Refund package" (:playbook/display-name updated)))
    (is (false? (:playbook/is-active disabled)))
    (is (= 1 (count (playbooks/list-by-tenant tenant-a {}))))
    (is (= (:playbook/id tenant-b-item)
           (:playbook/id (first (playbooks/list-by-tenant tenant-b {})))))
    (is (nil? (playbooks/get-by-id tenant-a (:playbook/id tenant-b-item))))))

(deftest rejects-invalid-and-duplicate-playbooks
  (state/reset-store!)
  (let [created (playbooks/save! tenant-a attrs actor)]
    (is (= :validation-error
           (:type (ex-data
                   (try
                     (playbooks/save! tenant-a (dissoc attrs :code) actor)
                     (catch clojure.lang.ExceptionInfo ex ex))))))
    (is (= :playbook/duplicate-code
           (:type (ex-data
                   (try
                     (playbooks/save! tenant-a
                                      (assoc attrs
                                             :display-name "Duplicate")
                                      actor)
                     (catch clojure.lang.ExceptionInfo ex ex))))))
    (is (= :playbook/not-found
           (:type (ex-data
                   (try
                     (playbooks/disable! tenant-b (:playbook/id created) actor)
                     (catch clojure.lang.ExceptionInfo ex ex))))))))

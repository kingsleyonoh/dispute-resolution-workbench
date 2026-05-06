(ns drw.ui.playbooks-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.playbooks :as playbooks]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.ui.playbooks :as ui-playbooks]))

(def tenant-id (:tenant/id (get (fixtures/tenants-by-slug) "acme-gmbh-de")))
(def actor {:actor-kind :user :actor-id "operator-1"})

(deftest settings-section-renders-playbook-forms
  (state/reset-store!)
  (playbooks/save! tenant-id
                   {:code "credit-note-and-refund"
                    :display-name "Credit note and refund"
                    :description "Issue credit note"
                    :workflow-engine-workflow-id "wf-credit"
                    :required-inputs-schema "{}"
                    :is-active true}
                   actor)
  (let [html (str (ui-playbooks/settings-page tenant-id))]
    (is (str/includes? html "Playbook settings"))
    (is (str/includes? html "credit-note-and-refund"))
    (is (str/includes? html "/settings/playbooks"))
    (is (str/includes? html "/settings/playbooks/")
        "Existing playbooks render disable actions")))

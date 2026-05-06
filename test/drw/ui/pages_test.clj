(ns drw.ui.pages-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [hiccup2.core :as h]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.ingestion-sources :as ingestion]
            [drw.domain.playbooks :as playbooks]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.ui.pages :as pages]))

(def tenant-id (:tenant/id (get (fixtures/tenants-by-slug) "acme-gmbh-de")))
(def actor {:actor-kind :user :actor-id "ui-test"})

(defn- html [node]
  (str (h/html node)))

(defn- seed-dispute! []
  (state/reset-store!)
  (let [counterparty (counterparties/create!
                      {:tenant-id tenant-id
                       :canonical-name "UI Vendor"
                       :kind :vendor}
                      actor)
        dispute (disputes/create-dispute!
                 {:tenant-id tenant-id
                  :title "UI invoice mismatch"
                  :description "Totals differ."
                  :category :billing
                  :severity :high
                  :currency "EUR"
                  :counterparty-id (:counterparty/id counterparty)
                  :created-by :user}
                 actor)
        exception (exceptions/create-manual!
                   {:tenant-id tenant-id
                    :dispute-id (:dispute/id dispute)
                    :source-ref "UI-MAN-1"
                    :kind :manual
                    :currency "EUR"
                    :observed-at #inst "2026-05-05T10:00:00.000-00:00"}
                   actor)]
    (playbooks/save! tenant-id
                     {:code "credit-note-and-refund"
                      :display-name "Credit note and refund"
                      :workflow-engine-workflow-id "wf-credit"}
                     actor)
    {:counterparty counterparty
     :dispute dispute
     :exception exception}))

(deftest renders-login-and-dashboard-console-surfaces
  (let [{:keys [dispute]} (seed-dispute!)
        login-html (html (pages/login-page nil))
        dashboard-html (html (pages/dashboard-page {:tenant-id tenant-id
                                                    :tenant-name "Acme"}))]
    (is (str/includes? login-html "Sign in"))
    (is (str/includes? login-html "name=\"api_key\""))
    (is (str/includes? dashboard-html "Operations dashboard"))
    (is (str/includes? dashboard-html "UI invoice mismatch"))
    (is (str/includes? dashboard-html (str (:dispute/id dispute))))))

(deftest renders-dispute-detail-actions-and-counterparty-directory
  (let [{:keys [counterparty dispute exception]} (seed-dispute!)
        detail-html (html (pages/dispute-detail-page tenant-id
                                                     (:dispute/id dispute)
                                                     nil))
        list-html (html (pages/dispute-list-page tenant-id {}))
        counterparties-html (html (pages/counterparties-page tenant-id))
        counterparty-html (html (pages/counterparty-detail-page
                                 tenant-id (:counterparty/id counterparty)))]
    (is (str/includes? list-html "Dispute queue"))
    (is (str/includes? detail-html "Assign owner"))
    (is (str/includes? detail-html "Transition status"))
    (is (str/includes? detail-html "Add comment"))
    (is (str/includes? detail-html "Attach manual exception"))
    (is (str/includes? detail-html "Start resolution"))
    (is (str/includes? detail-html "Credit note and refund"))
    (is (str/includes? detail-html (:exception/source-ref exception)))
    (is (str/includes? counterparties-html "Counterparties"))
    (is (str/includes? counterparties-html "UI Vendor"))
    (is (str/includes? counterparty-html "Counterparty history"))))

(deftest renders-correlation-queue-with-decision-actions
  (let [{:keys [counterparty]} (seed-dispute!)
        result (exceptions/ingest!
                {:tenant-id tenant-id
                 :source-system :invoice-recon
                 :source-ref "UI-CORR-1"
                 :kind :invoice-discrepancy
                 :currency "EUR"
                 :counterparty-id (:counterparty/id counterparty)
                 :observed-at #inst "2026-05-05T10:30:00.000-00:00"
                 :monetary-impact-cents 0}
                actor)
        queue-html (html (pages/correlations-page tenant-id))]
    (is (= :correlation-pending (:status result)))
    (is (str/includes? queue-html "Correlation queue"))
    (is (str/includes? queue-html "UI-CORR-1"))
    (is (str/includes? queue-html "UI invoice mismatch"))
    (is (str/includes? queue-html "/correlations/"))
    (is (str/includes? queue-html "/accept"))
    (is (str/includes? queue-html "/reject"))))

(deftest renders-ingestion-settings-with-source_controls_and_runs
  (state/reset-store!)
  (let [source (first (ingestion/list-sources
                       tenant-id
                       {:invoice-recon-enabled false
                        :invoice-recon-poll-interval-seconds 600}))
        run (ingestion/pull-now!
             tenant-id
             (:ingestion-source/id source)
             {:invoice-recon-enabled false})
        settings-html (html (pages/ingestion-settings-page tenant-id {}))]
    (is (= :disabled (:ingestion-run/status run)))
    (is (str/includes? settings-html "Ingestion settings"))
    (is (str/includes? settings-html "Invoice Reconciliation"))
    (is (str/includes? settings-html "name=\"source_system\""))
    (is (str/includes? settings-html "name=\"is_enabled\""))
    (is (str/includes? settings-html "/settings/ingestion/"))
    (is (str/includes? settings-html "/pull-now"))
    (is (str/includes? settings-html "disabled"))))

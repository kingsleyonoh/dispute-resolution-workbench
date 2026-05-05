(ns drw.ui.pages-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [hiccup2.core :as h]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
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
    (is (str/includes? detail-html (:exception/source-ref exception)))
    (is (str/includes? counterparties-html "Counterparties"))
    (is (str/includes? counterparties-html "UI Vendor"))
    (is (str/includes? counterparty-html "Counterparty history"))))

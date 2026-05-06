(ns drw.domain.core-queue-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))
(def actor {:actor-kind :user :actor-id "operator-1"})
(def now #inst "2026-05-05T10:00:00.000-00:00")

(defn reset-domain! []
  (state/reset-store!))

(defn ex-type [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (:type (ex-data e)))))

(deftest counterparty-crud-resolves-normalized-names-per-tenant
  (reset-domain!)
  (let [created (counterparties/create!
                 {:tenant-id tenant-a
                  :canonical-name "ACME Trading, GmbH!"
                  :kind :vendor
                  :tax-id "DE-123"
                  :country-code "DE"
                  :external-refs {:invoice-recon "vendor-42"}
                  :created-at now}
                 actor)]
    (is (= "acme trading gmbh"
           (counterparties/normalize-name " ACME Trading, GmbH! ")))
    (is (= "acme trading gmbh" (:counterparty/normalized-name created)))
    (is (= (:counterparty/id created)
           (:counterparty/id
            (counterparties/resolve-counterparty
             {:tenant-id tenant-a
              :source-system :invoice-recon
              :external-ref "vendor-42"}))))
    (is (= (:counterparty/id created)
           (:counterparty/id
            (counterparties/resolve-counterparty
             {:tenant-id tenant-a
              :canonical-name "Acme Trading GmbH"}))))
    (is (= "ACME Trading GmbH Europe"
           (:counterparty/canonical-name
            (counterparties/update!
             tenant-a
             (:counterparty/id created)
             {:canonical-name "ACME Trading GmbH Europe"
              :country-code "DE"}
             actor))))
    (is (= 1 (count (counterparties/list-by-tenant tenant-a))))
    (is (= :deleted
           (:status (counterparties/delete! tenant-a
                                            (:counterparty/id created)
                                            actor))))
    (is (nil? (counterparties/get-by-id tenant-a (:counterparty/id created))))))

(deftest counterparty-unhappy-paths-and-tenant-isolation
  (reset-domain!)
  (is (= :validation-error
         (ex-type #(counterparties/create!
                    {:tenant-id tenant-a :canonical-name "" :kind :vendor}
                    actor))))
  (counterparties/create!
   {:tenant-id tenant-a :canonical-name "Globex LLC" :kind :vendor}
   actor)
  (is (= :counterparty/duplicate-normalized-name
         (ex-type #(counterparties/create!
                    {:tenant-id tenant-a
                     :canonical-name "globex-llc"
                     :kind :vendor}
                    actor))))
  (is (some? (counterparties/create!
              {:tenant-id tenant-b :canonical-name "Globex LLC" :kind :vendor}
              actor)))
  (is (= 1 (count (counterparties/list-by-tenant tenant-a))))
  (is (= 1 (count (counterparties/list-by-tenant tenant-b)))))

(deftest dispute-lifecycle-enforces-status-gate-and-tenant-scope
  (reset-domain!)
  (let [counterparty (counterparties/create!
                      {:tenant-id tenant-a
                       :canonical-name "Lifecycle Vendor"
                       :kind :vendor}
                      actor)
        dispute (disputes/create-dispute!
                 {:tenant-id tenant-a
                  :title "Invoice mismatch"
                  :description "Invoice total does not match contract."
                  :category :billing
                  :severity :high
                  :currency "EUR"
                  :counterparty-id (:counterparty/id counterparty)
                  :created-by :user
                  :created-at now}
                 actor)
        assigned (disputes/assign!
                  tenant-a
                  (:dispute/id dispute)
                  {:user-id #uuid "33333333-3333-3333-3333-333333333333"
                   :sla-policies [{:sla-policy/tenant-id tenant-a
                                   :sla-policy/category :billing
                                   :sla-policy/severity :high
                                   :sla-policy/target-minutes 60}]
                   :assigned-at now}
                  actor)
        investigating (disputes/transition!
                       tenant-a
                       (:dispute/id dispute)
                       {:to :investigating :occurred-at now}
                       actor)]
    (is (= :open (:dispute/status dispute)))
    (is (= "DIS-2026-0001" (:dispute/reference dispute)))
    (is (= :assigned (:dispute/status assigned)))
    (is (= #inst "2026-05-05T11:00:00.000-00:00"
           (:dispute/sla-due-at assigned)))
    (is (= :investigating (:dispute/status investigating)))
    (is (= 1 (count (disputes/list-by-tenant tenant-a))))
    (is (= 0 (count (disputes/list-by-tenant tenant-b))))
    (is (= [:dispute-created :assigned :status-changed]
           (map :timeline/kind
                (disputes/list-timeline tenant-a (:dispute/id dispute)))))
    (is (= ["dispute.created"
            "dispute.assigned"
            "dispute.status_changed"]
           (map :audit/action (disputes/list-audit-log tenant-a))))
    (is (= :illegal-status-transition
           (ex-type #(disputes/transition!
                      tenant-a
                      (:dispute/id dispute)
                      {:to :assigned :occurred-at now}
                      actor))))
    (is (nil? (disputes/get-by-id tenant-b (:dispute/id dispute))))))

(deftest terminal-disputes-reject-state-changes-and-comments
  (reset-domain!)
  (let [dispute (disputes/create-dispute!
                 {:tenant-id tenant-a
                  :title "Withdrawn dispute"
                  :description "Invalid source report."
                  :category :multi
                  :severity :low
                  :currency "USD"
                  :created-by :user
                  :created-at now}
                 actor)]
    (disputes/transition! tenant-a (:dispute/id dispute) {:to :withdrawn} actor)
    (is (= :dispute/terminal
           (ex-type #(disputes/comment!
                      tenant-a
                      (:dispute/id dispute)
                      {:body "one more note"}
                      actor))))
    (is (= :illegal-status-transition
           (ex-type #(disputes/transition!
                      tenant-a
                      (:dispute/id dispute)
                      {:to :assigned}
                      actor))))))

(ns drw.domain.reports-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.disputes :as disputes]
            [drw.domain.reports :as reports]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.tenants.snapshot :as snapshot]))

(def tenants (fixtures/load-tenants))
(def tenants-by-slug (fixtures/tenants-by-slug))
(def tenant-a (get tenants-by-slug "acme-gmbh-de"))
(def tenant-b (get tenants-by-slug "globex-inc-us"))
(def actor {:actor-kind :user :actor-id "report-test"})

(defn- seed-dispute! [tenant title]
  (disputes/create-dispute!
   {:tenant-id (:tenant/id tenant)
    :title title
    :description "PDF source render smoke."
    :category :billing
    :severity :medium
    :currency "EUR"
    :created-by :user
    :created-at #inst "2026-05-05T10:00:00.000-00:00"}
   actor))

(defn- ex-type [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (:type (ex-data e)))))

(deftest dispute-audit-pdf-source-render-is-tenant-scoped
  (state/reset-store!)
  (let [a-dispute (seed-dispute! tenant-a "Acme invoice audit")
        b-dispute (seed-dispute! tenant-b "Globex invoice audit")
        a-artifact (reports/render-dispute-audit-pdf-source
                    tenants (:tenant/id tenant-a) (:dispute/id a-dispute))
        b-artifact (reports/render-dispute-audit-pdf-source
                    tenants (:tenant/id tenant-b) (:dispute/id b-dispute))]
    (is (= :dispute-audit-pdf (:report/kind a-artifact)))
    (is (= "text/html; charset=utf-8" (:content-type a-artifact)))
    (is (str/includes? (:html a-artifact) (:tenant/legal-name tenant-a)))
    (is (str/includes? (:html b-artifact) (:tenant/legal-name tenant-b)))
    (doseq [literal (snapshot/tenant-identity-literals tenant-b)]
      (is (not (str/includes? (:html a-artifact) literal))
          (str "TENANT_IDENTITY_LEAK: Tenant A render included " literal)))
    (doseq [literal (snapshot/tenant-identity-literals tenant-a)]
      (is (not (str/includes? (:html b-artifact) literal))
          (str "TENANT_IDENTITY_LEAK: Tenant B render included " literal)))))

(deftest dispute-audit-pdf-source-fails-closed-for-cross-tenant-dispute
  (state/reset-store!)
  (let [a-dispute (seed-dispute! tenant-a "Private Tenant A dispute")]
    (is (= :report/dispute-not-found
           (ex-type #(reports/render-dispute-audit-pdf-source
                      tenants
                      (:tenant/id tenant-b)
                      (:dispute/id a-dispute)))))))

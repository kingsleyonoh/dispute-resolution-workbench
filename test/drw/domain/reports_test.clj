(ns drw.domain.reports-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.reports :as reports]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.tenants.snapshot :as snapshot])
  (:import [java.nio.file Files]))

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

(defn- attach-exception! [tenant dispute]
  (exceptions/create-manual!
   {:tenant-id (:tenant/id tenant)
    :source-system :manual
    :source-ref (str "PDF-" (:dispute/reference dispute))
    :kind :invoice-discrepancy
    :entity-id "INV-PDF"
    :counterparty-name "PDF Vendor"
    :raw-payload {:description "Audit packet exception."}
    :monetary-impact-cents 12500
    :currency "EUR"
    :observed-at #inst "2026-05-05T11:00:00.000-00:00"
    :dispute-id (:dispute/id dispute)}
   actor))

(defn- temp-dir []
  (str (Files/createTempDirectory "drw-report-test"
                                  (make-array java.nio.file.attribute.FileAttribute 0))))

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

(deftest dispute-audit-html-uses-template-data
  (state/reset-store!)
  (let [dispute (seed-dispute! tenant-a "Template backed audit")
        _ (attach-exception! tenant-a dispute)
        _ (disputes/comment! (:tenant/id tenant-a) (:dispute/id dispute)
                             {:body "Operator note."} actor)
        artifact (reports/render-dispute-audit-pdf-source
                  tenants (:tenant/id tenant-a) (:dispute/id dispute))
        html (:html artifact)]
    (is (str/includes? html "data-template=\"dispute-audit\""))
    (is (str/includes? html "Template backed audit"))
    (is (str/includes? html "Audit packet exception."))
    (is (str/includes? html "Operator note."))
    (is (str/includes? html (:tenant/brand-primary-hex tenant-a)))))

(deftest generate-dispute-audit-pdf-stores-ready-artifact
  (state/reset-store!)
  (let [dispute (seed-dispute! tenant-a "Frozen tenant report")
        storage-dir (temp-dir)
        report (reports/generate-dispute-audit-pdf!
                {:report-storage-dir storage-dir
                 :pdf-render-fn (fn [{:keys [html]}]
                                  (.getBytes (str "PDF:" html) "UTF-8"))}
                tenants
                (:tenant/id tenant-a)
                (:dispute/id dispute)
                actor)]
    (is (= :ready (:report/status report)))
    (is (= :dispute-audit-pdf (:report/kind report)))
    (is (= 64 (count (:report/content-sha256 report))))
    (is (str/includes? (:report/tenant-snapshot report)
                       (:tenant/legal-name tenant-a)))
    (is (.exists (java.io.File. (:report/storage-path report))))
    (is (= report (reports/get-report (:tenant/id tenant-a)
                                      (:report/id report))))))

(deftest failed-pdf-render-stores-failed-artifact
  (state/reset-store!)
  (let [dispute (seed-dispute! tenant-a "Failed report")
        report (reports/generate-dispute-audit-pdf!
                {:report-storage-dir (temp-dir)
                 :pdf-render-fn (fn [_]
                                  (throw (ex-info "wkhtmltopdf failed"
                                                  {:exit 1})))}
                tenants
                (:tenant/id tenant-a)
                (:dispute/id dispute)
                actor)]
    (is (= :failed (:report/status report)))
    (is (str/includes? (:report/error report) "wkhtmltopdf failed"))
    (is (nil? (:report/storage-path report)))))

(deftest dispute-audit-pdf-source-fails-closed-for-cross-tenant-dispute
  (state/reset-store!)
  (let [a-dispute (seed-dispute! tenant-a "Private Tenant A dispute")]
    (is (= :report/dispute-not-found
           (ex-type #(reports/render-dispute-audit-pdf-source
                      tenants
                      (:tenant/id tenant-b)
                      (:dispute/id a-dispute)))))))

(deftest two-tenant-render-check-reports-audited-tenants
  (let [result (reports/two-tenant-pdf-render-check tenants)]
    (is (= :ok (:status result)))
    (is (= (set (map :tenant/id tenants))
           (set (:checked-tenant-ids result))))))

(deftest two-tenant-render-check-rejects-cross-tenant-literals
  (let [leaky-tenant-a (assoc tenant-a
                              :tenant/address
                              (:tenant/legal-name tenant-b))]
    (is (= :report/tenant-identity-leak
           (ex-type #(reports/two-tenant-pdf-render-check
                      [leaky-tenant-a tenant-b]))))))

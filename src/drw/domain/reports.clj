(ns drw.domain.reports
  (:require [clojure.string :as str]
            [drw.audit.recorder :as recorder]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state]
            [drw.templates.renderer :as renderer]
            [drw.tenants.snapshot :as snapshot])
  (:import [java.util UUID]))

(def report-kind :dispute-audit-pdf)
(def content-type "text/html; charset=utf-8")

(defn- reject! [message data]
  (throw (ex-info message data)))

(defn- safe-name [value]
  (cond
    (keyword? value) (name value)
    (nil? value) ""
    :else (str value)))

(defn- tenant-context [tenant-snapshot]
  {"legal-name" (:legal-name tenant-snapshot)
   "full-legal-name" (:full-legal-name tenant-snapshot)
   "display-name" (:display-name tenant-snapshot)
   "address" (:address tenant-snapshot)
   "registration" (:registration tenant-snapshot)
   "contact" (:contact tenant-snapshot)
   "wordmark-url" (:wordmark-url tenant-snapshot)
   "brand-primary-hex" (:brand-primary-hex tenant-snapshot)
   "locale" (:locale tenant-snapshot)
   "timezone" (:timezone tenant-snapshot)})

(defn- dispute-context [dispute]
  {"reference" (:dispute/reference dispute)
   "title" (:dispute/title dispute)
   "description" (:dispute/description dispute)
   "status" (safe-name (:dispute/status dispute))
   "category" (safe-name (:dispute/category dispute))
   "severity" (safe-name (:dispute/severity dispute))
   "currency" (:dispute/currency dispute)
   "resolution-summary" (or (:dispute/resolution-summary dispute) "")})

(defn- exception-context [exception]
  {"source-system" (safe-name (:exception/source-system exception))
   "source-ref" (:exception/source-ref exception)
   "description" (or (:exception/description exception)
                     (:exception/raw-payload exception)
                     "")
   "monetary-impact-cents" (:exception/monetary-impact-cents exception)
   "currency" (:exception/currency exception)})

(defn- timeline-context [entry]
  {"occurred-at" (str (:timeline/occurred-at entry))
   "kind" (safe-name (:timeline/kind entry))
   "body" (:timeline/body entry)})

(defn- audit-context [entry]
  {"occurred-at" (str (:audit/occurred-at entry))
   "action" (:audit/action entry)
   "actor-kind" (safe-name (:audit/actor-kind entry))
   "actor-id" (:audit/actor-id entry)})

(defn- report-context [tenant-snapshot dispute timeline audit-log exceptions]
  {"tenant" (tenant-context tenant-snapshot)
   "dispute" (dispute-context dispute)
   "exceptions" (mapv exception-context exceptions)
   "timeline" (mapv timeline-context timeline)
   "audit-log" (mapv audit-context audit-log)})

(defn- audit-html [tenant-snapshot dispute timeline audit-log exceptions]
  (renderer/render-file
   renderer/dispute-audit-template
   (report-context tenant-snapshot dispute timeline audit-log exceptions)))

(defn- audit-log-for [tenant-id dispute-id]
  (filter #(= dispute-id (:audit/entity-id %))
          (disputes/list-audit-log tenant-id)))

(defn- exceptions-for [tenant-id dispute-id]
  (exceptions/list-by-tenant tenant-id {:dispute-id dispute-id}))

(defn- source-filters [dispute-id]
  {:dispute-id dispute-id})

(defn get-report [tenant-id report-id]
  (let [report (get @state/reports* report-id)]
    (when (= tenant-id (:report/tenant-id report))
      report)))

(defn- put-report! [report]
  (swap! state/reports* assoc (:report/id report) report)
  report)

(defn render-dispute-audit-pdf-source
  [tenant-source tenant-id dispute-id]
  (let [tenant-snapshot (snapshot/capture-tenant-snapshot tenant-source
                                                          tenant-id)
        dispute (or (disputes/get-by-id tenant-id dispute-id)
                    (reject! "dispute not found for tenant"
                             {:type :report/dispute-not-found
                              :tenant-id tenant-id
                              :dispute-id dispute-id}))
        timeline (disputes/list-timeline tenant-id dispute-id)
        audit-log (audit-log-for tenant-id dispute-id)
        exceptions (exceptions-for tenant-id dispute-id)]
    {:report/kind report-kind
     :tenant-id tenant-id
     :dispute-id dispute-id
     :tenant-snapshot tenant-snapshot
     :content-type content-type
     :html (audit-html tenant-snapshot dispute timeline audit-log exceptions)}))

(defn- generating-report [tenant-source tenant-id dispute-id actor]
  (let [tenant-snapshot (snapshot/capture-tenant-snapshot tenant-source tenant-id)
        _ (or (disputes/get-by-id tenant-id dispute-id)
              (reject! "dispute not found for tenant"
                       {:type :report/dispute-not-found
                        :tenant-id tenant-id
                        :dispute-id dispute-id}))]
    {:report/id (UUID/randomUUID)
     :report/tenant-id tenant-id
     :report/dispute-id dispute-id
     :report/kind report-kind
     :report/status :generating
     :report/tenant-snapshot (recorder/encode-json tenant-snapshot)
     :report/source-filters (recorder/encode-json (source-filters dispute-id))
     :report/generated-by-user-id (:actor-id actor)
     :report/generated-at (java.util.Date.)}))

(defn- mark-ready! [report stored]
  (put-report!
   (assoc report
          :report/status :ready
          :report/storage-path (:storage-path stored)
          :report/content-sha256 (:content-sha256 stored))))

(defn- mark-failed! [report ex]
  (put-report!
   (assoc report
          :report/status :failed
          :report/failed-at (java.util.Date.)
          :report/error (.getMessage ex))))

(defn generate-dispute-audit-pdf!
  [cfg tenant-source tenant-id dispute-id actor]
  (let [report (put-report! (generating-report tenant-source tenant-id
                                               dispute-id actor))]
    (try
      (let [source (render-dispute-audit-pdf-source tenant-source
                                                    tenant-id dispute-id)
            bytes (renderer/render-pdf-bytes!
                   cfg
                   {:html (:html source)
                    :report-id (:report/id report)
                    :tenant-id tenant-id
                    :dispute-id dispute-id})
            stored (renderer/store-pdf! cfg (:report/id report) bytes)]
        (mark-ready! report stored))
      (catch Exception ex
        (mark-failed! report ex)))))

(defn- render-tenant-smoke-source [tenant-source tenant]
  (let [tenant-snapshot (snapshot/capture-tenant-snapshot
                         tenant-source
                         (:tenant/id tenant))]
    (audit-html tenant-snapshot
                {:dispute/reference "SMOKE"
                 :dispute/title "First-run PDF render smoke"
                 :dispute/description "Tenant identity smoke"
                 :dispute/status :new
                 :dispute/category :billing
                 :dispute/severity :low
                 :dispute/currency "EUR"}
                [] [] [])))

(defn- leaked-literals [html tenant]
  (filter #(str/includes? html %)
          (snapshot/tenant-identity-literals tenant)))

(defn two-tenant-pdf-render-check [tenant-source]
  (let [tenants (vec tenant-source)]
    (when (< (count tenants) 2)
      (reject! "two tenant fixtures are required"
               {:type :tenant-fixtures/insufficient-count
                :tenant-count (count tenants)}))
    (doseq [tenant tenants
            other (remove #(= (:tenant/id tenant) (:tenant/id %)) tenants)]
      (let [rendered (render-tenant-smoke-source tenant-source tenant)
            leaks (vec (leaked-literals rendered other))]
        (when (seq leaks)
          (reject! "tenant identity leaked into PDF render"
                   {:type :report/tenant-identity-leak
                    :tenant-id (:tenant/id tenant)
                    :leaks leaks}))))
    {:status :ok
     :tenant-count (count tenants)
     :checked-tenant-ids (mapv :tenant/id tenants)
     :rendered-kind report-kind}))

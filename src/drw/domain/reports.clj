(ns drw.domain.reports
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [drw.domain.disputes :as disputes]
            [drw.tenants.snapshot :as snapshot]))

(def report-kind :dispute-audit-pdf)
(def content-type "text/html; charset=utf-8")

(defn- reject! [message data]
  (throw (ex-info message data)))

(defn- html [node]
  (str (h/html node)))

(defn- tenant-identity-section [tenant-snapshot]
  [:section
   [:h2 "Tenant identity"]
   [:dl
    [:dt "Legal name"]
    [:dd (:legal-name tenant-snapshot)]
    [:dt "Full legal name"]
    [:dd (:full-legal-name tenant-snapshot)]
    [:dt "Display name"]
    [:dd (:display-name tenant-snapshot)]
    [:dt "Address"]
    [:dd (:address tenant-snapshot)]
    [:dt "Registration"]
    [:dd (:registration tenant-snapshot)]
    [:dt "Contact"]
    [:dd (:contact tenant-snapshot)]]])

(defn- audit-html [tenant-snapshot dispute timeline audit-log]
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:title "Dispute audit PDF source"]]
    [:body {:data-report-kind (name report-kind)
            :style (str "--tenant-accent:"
                        (:brand-primary-hex tenant-snapshot))}
     [:header
      [:img {:src (:wordmark-url tenant-snapshot)
             :alt (:display-name tenant-snapshot)}]
      [:h1 "Dispute audit"]]
     (tenant-identity-section tenant-snapshot)
     [:section
      [:h2 "Dispute"]
      [:p (:dispute/reference dispute)]
      [:p (:dispute/title dispute)]
      [:p (:dispute/description dispute)]
      [:p (name (:dispute/status dispute))]]
     [:section
      [:h2 "Timeline"]
      [:ol
       (for [entry timeline]
         [:li
          [:span (name (:timeline/kind entry))]
          [:span " " (:timeline/body entry)]])]]
     [:section
      [:h2 "Audit"]
      [:ol
       (for [entry audit-log]
         [:li (:audit/action entry)])]]]]))

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
        audit-log (filter #(= dispute-id (:audit/entity-id %))
                          (disputes/list-audit-log tenant-id))]
    {:report/kind report-kind
     :tenant-id tenant-id
     :dispute-id dispute-id
     :tenant-snapshot tenant-snapshot
     :content-type content-type
     :html (audit-html tenant-snapshot dispute timeline audit-log)}))

(defn- render-tenant-smoke-source [tenant-source tenant]
  (let [tenant-snapshot (snapshot/capture-tenant-snapshot
                         tenant-source
                         (:tenant/id tenant))]
    (html
     [:html
      [:body
       [:h1 "First-run PDF render smoke"]
       (tenant-identity-section tenant-snapshot)]])))

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
     :rendered-kind report-kind}))

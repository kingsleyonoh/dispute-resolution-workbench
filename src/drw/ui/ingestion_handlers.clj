(ns drw.ui.ingestion-handlers
  (:require [drw.domain.ingestion-sources :as ingestion]
            [drw.jobs.ingestion-registry :as registry]
            [drw.ui.csrf :as csrf]
            [drw.ui.handlers :as handlers]
            [drw.ui.pages :as pages]
            [drw.ui.request :as ui-req]))

(defn ingestion-settings [cfg]
  (fn [request]
    (handlers/require-tenant
     request
     (fn [tenant]
       (handlers/html
        (pages/ingestion-settings-page (:tenant/id tenant) cfg))))))

(defn- ingestion-settings-attrs [form]
  {:enabled? (= "true" (:is-enabled form))
   :base-url (:base-url form)
   :poll-interval-seconds (ui-req/long-value
                           (:poll-interval-seconds form))})

(defn save-ingestion-settings [cfg]
  (fn [request]
    (handlers/require-tenant
     request
     (fn [tenant]
       (csrf/with-form
         request
         (fn [form]
           (ingestion/save-settings-by-system!
            (:tenant/id tenant)
            (ui-req/keyword-value (:source-system form))
            (ingestion-settings-attrs form)
            (registry/with-source-registry cfg))
           (handlers/redirect "/settings/ingestion")))))))

(defn pull-ingestion-now [cfg]
  (fn [request]
    (handlers/require-tenant
     request
     (fn [tenant]
       (csrf/with-form
         request
         (fn [_form]
           (ingestion/pull-now!
            (:tenant/id tenant)
            (handlers/path-id request)
            (registry/with-source-registry cfg))
           (handlers/redirect "/settings/ingestion")))))))

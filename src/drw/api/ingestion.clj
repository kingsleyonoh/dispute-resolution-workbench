(ns drw.api.ingestion
  (:require [drw.api.common :as api]
            [drw.api.serializers :as serializers]
            [drw.domain.ingestion-sources :as ingestion]))

(defn- source-id [request]
  (api/uuid-value (get-in request [:path-params :id])))

(defn- source-system [value]
  (some-> value api/keyword-value))

(defn- settings [body]
  {:enabled? (api/value body :is_enabled :isEnabled)
   :base-url (api/value body :base_url :baseUrl)
   :poll-interval-seconds (api/value body :poll_interval_seconds
                                     :pollIntervalSeconds)})

(defn- filters [query]
  {:source-system (source-system (api/value query :source_system
                                            :sourceSystem))
   :status (source-system (api/value query :status))})

(defn list-sources-handler [cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)]
      (api/ok {:sources (mapv serializers/ingestion-source
                              (ingestion/list-sources tenant-id cfg))
               :nextCursor nil}))))

(defn save-source-handler [cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            source (ingestion/save-settings-by-system!
                    tenant-id
                    (source-system (api/value body :source_system
                                              :sourceSystem))
                    (settings body)
                    cfg)]
        (api/ok {:source (serializers/ingestion-source source)})))))

(defn pull-now-handler [cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            run (ingestion/pull-now! tenant-id (source-id request) cfg)]
        (api/created {:run (serializers/ingestion-run run)})))))

(defn list-runs-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)
          runs (ingestion/list-runs tenant-id (filters (:query-params request)))]
      (api/ok {:runs (mapv serializers/ingestion-run runs)
               :nextCursor nil}))))

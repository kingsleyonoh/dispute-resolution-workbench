(ns drw.api.correlations
  (:require [drw.api.common :as api]
            [drw.api.serializers :as serializers]
            [drw.domain.correlations :as correlations]))

(defn- correlation-id [request]
  (api/uuid-value (get-in request [:path-params :id])))

(defn- filters [query]
  {:status (api/keyword-value (api/value query :status))
   :exception-id (some-> (api/value query :exception_id :exceptionId)
                         api/uuid-value)
   :dispute-id (some-> (api/value query :dispute_id :disputeId)
                       api/uuid-value)})

(defn- response-body [tenant-id correlation]
  (serializers/correlation-detail
   (correlations/hydrate tenant-id correlation)))

(defn list-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)
          candidates (correlations/list-by-tenant
                      tenant-id
                      (filters (:query-params request)))]
      (api/ok {:correlations (mapv #(response-body tenant-id %) candidates)
               :nextCursor nil}))))

(defn get-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)]
      (if-let [candidate (correlations/get-by-id tenant-id
                                                 (correlation-id request))]
        (api/ok (response-body tenant-id candidate))
        (api/not-found "Correlation not found")))))

(defn accept-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            candidate (correlations/accept! tenant-id
                                            (correlation-id request)
                                            (api/actor request))]
        (api/ok (response-body tenant-id candidate))))))

(defn reject-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            candidate (correlations/reject! tenant-id
                                            (correlation-id request)
                                            (api/actor request))]
        (api/ok (response-body tenant-id candidate))))))

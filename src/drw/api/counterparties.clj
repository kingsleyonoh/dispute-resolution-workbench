(ns drw.api.counterparties
  (:require [drw.api.common :as api]
            [drw.api.serializers :as serializers]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]))

(defn- counterparty-id [request]
  (api/uuid-value (get-in request [:path-params :id])))

(defn list-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)]
      (api/ok {:counterparties (mapv serializers/counterparty
                                     (counterparties/list-by-tenant tenant-id))
               :nextCursor nil}))))

(defn get-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)
          id (counterparty-id request)]
      (if-let [counterparty (counterparties/get-by-id tenant-id id)]
        (api/ok {:counterparty (serializers/counterparty counterparty)
                 :disputes (mapv serializers/dispute
                                 (filter #(= id (:dispute/counterparty-id %))
                                         (disputes/list-by-tenant tenant-id)))})
        (api/not-found "Counterparty not found")))))

(defn merge-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            target-id (api/uuid-value (api/value body :merge_into_id
                                                 :mergeIntoId))
            counterparty (counterparties/merge!
                          tenant-id
                          (counterparty-id request)
                          target-id
                          (api/actor request))]
        (api/ok {:counterparty (serializers/counterparty counterparty)})))))

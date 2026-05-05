(ns drw.api.exceptions
  (:require [drw.api.common :as api]
            [drw.api.serializers :as serializers]
            [drw.domain.exceptions :as exceptions]))

(defn- create-attrs [tenant-id body]
  {:tenant-id tenant-id
   :dispute-id (some-> (api/value body :dispute_id :disputeId) api/uuid-value)
   :source-system (or (api/keyword-value (api/value body :source_system
                                                    :sourceSystem))
                      :manual)
   :source-ref (api/value body :source_ref :sourceRef)
   :source-url (api/value body :source_url :sourceUrl)
   :kind (api/keyword-value (api/value body :kind))
   :raw-payload (api/value body :raw_payload :rawPayload)
   :monetary-impact-cents (or (api/value body :monetary_impact_cents
                                         :monetaryImpactCents)
                              0)
   :currency (api/value body :currency)
   :observed-at (some-> (api/value body :observed_at :observedAt)
                        api/instant-value)})

(defn list-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)
          query (:query-params request)
          filters {:source-system (api/keyword-value
                                   (api/value query :source_system
                                              :sourceSystem))
                   :dispute-id (some-> (api/value query :dispute_id
                                                  :disputeId)
                                       api/uuid-value)}
          exceptions (exceptions/list-by-tenant tenant-id filters)]
      (api/ok {:exceptions (mapv serializers/exception exceptions)
               :nextCursor nil}))))

(defn create-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            exception (exceptions/create-manual!
                       (create-attrs tenant-id body)
                       (api/actor request))]
        (api/created {:exception (serializers/exception exception)
                      :disputeCreated false})))))

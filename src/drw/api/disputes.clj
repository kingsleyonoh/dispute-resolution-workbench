(ns drw.api.disputes
  (:require [drw.api.common :as api]
            [drw.api.serializers :as serializers]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]))

(defn- dispute-id [request]
  (api/uuid-value (get-in request [:path-params :id])))

(defn- create-attrs [tenant-id body]
  {:tenant-id tenant-id
   :title (api/value body :title)
   :description (api/value body :description)
   :category (api/keyword-value (api/value body :category))
   :severity (api/keyword-value (api/value body :severity))
   :currency (api/value body :currency)
   :counterparty-id (some-> (api/value body :counterparty_id :counterpartyId)
                            api/uuid-value)
   :created-by :user})

(defn- filtered-disputes [tenant-id query]
  (let [status (api/keyword-value (api/value query :status))
        category (api/keyword-value (api/value query :category))
        severity (api/keyword-value (api/value query :severity))
        counterparty-id (some-> (api/value query :counterparty_id
                                           :counterpartyId)
                                api/uuid-value)]
    (->> (disputes/list-by-tenant tenant-id)
         (filter #(or (nil? status) (= status (:dispute/status %))))
         (filter #(or (nil? category) (= category (:dispute/category %))))
         (filter #(or (nil? severity) (= severity (:dispute/severity %))))
         (filter #(or (nil? counterparty-id)
                      (= counterparty-id (:dispute/counterparty-id %))))
         vec)))

(defn list-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)
          disputes (filtered-disputes tenant-id (:query-params request))]
      (api/ok {:disputes (mapv serializers/dispute disputes)
               :nextCursor nil}))))

(defn create-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            dispute (disputes/create-dispute!
                     (create-attrs tenant-id body)
                     (api/actor request))]
        (api/created {:dispute (serializers/dispute dispute)})))))

(defn get-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)
          id (dispute-id request)]
      (if-let [dispute (disputes/get-by-id tenant-id id)]
        (api/ok {:dispute (serializers/dispute dispute)
                 :exceptions (mapv serializers/exception
                                   (exceptions/list-by-tenant
                                    tenant-id {:dispute-id id}))
                 :timeline (mapv serializers/timeline-entry
                                 (disputes/list-timeline tenant-id id))})
        (api/not-found "Dispute not found")))))

(defn assign-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            dispute (disputes/assign!
                     tenant-id
                     (dispute-id request)
                     {:user-id (api/uuid-value (api/value body :user_id :userId))}
                     (api/actor request))]
        (api/ok {:dispute (serializers/dispute dispute)})))))

(defn transition-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            dispute (disputes/transition!
                     tenant-id
                     (dispute-id request)
                     {:to (api/keyword-value (api/value body :to_status :toStatus))}
                     (api/actor request))]
        (api/ok {:dispute (serializers/dispute dispute)})))))

(defn comment-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            entry (disputes/comment!
                   tenant-id
                   (dispute-id request)
                   {:body (api/value body :body)}
                   (api/actor request))]
        (api/created {:timelineEntry (serializers/timeline-entry entry)})))))

(defn attach-exception-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            attached (exceptions/attach-to-dispute!
                      tenant-id
                      {:exception-id (api/uuid-value
                                      (api/value body :exception_id
                                                 :exceptionId))
                       :dispute-id (dispute-id request)}
                      (api/actor request))
            dispute (disputes/get-by-id tenant-id (dispute-id request))]
        (api/ok {:exception (serializers/exception attached)
                 :dispute (serializers/dispute dispute)})))))

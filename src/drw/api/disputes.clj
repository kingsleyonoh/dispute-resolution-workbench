(ns drw.api.disputes
  (:require [drw.api.common :as api]
            [drw.api.serializers :as serializers]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.hub-events :as hub-events]
            [drw.domain.playbooks :as playbooks]
            [drw.domain.resolution :as resolution]))

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

(defn create-handler [cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            dispute (disputes/create-dispute!
                     (create-attrs tenant-id body)
                     (api/actor request))]
        (hub-events/emit-dispute! cfg "dispute.created" dispute)
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

(defn assign-handler [cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            dispute (disputes/assign!
                     tenant-id
                     (dispute-id request)
                     {:user-id (api/uuid-value (api/value body :user_id :userId))}
                     (api/actor request))]
        (hub-events/emit-dispute! cfg "dispute.assigned" dispute)
        (api/ok {:dispute (serializers/dispute dispute)})))))

(defn transition-handler [cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            dispute (disputes/transition!
                     tenant-id
                     (dispute-id request)
                     {:to (api/keyword-value (api/value body :to_status :toStatus))}
                     (api/actor request))]
        (hub-events/emit-dispute! cfg "dispute.status_changed" dispute)
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

(defn- resolution-inputs [body]
  (if-let [raw (api/value body :inputs_json :inputsJson)]
    {:raw raw}
    {}))

(defn start-resolution-handler [cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            playbook (playbooks/get-by-id
                      tenant-id
                      (api/uuid-value (api/value body :playbook_id
                                                 :playbookId)))
            result (resolution/start-resolution!
                    tenant-id
                    (dispute-id request)
                    playbook
                    (resolution-inputs body)
                    cfg
                    (api/actor request))
            dispute (disputes/get-by-id tenant-id (dispute-id request))]
        (api/created {:executionId (:execution-id result)
                      :workflowId (:workflow-id result)
                      :dispute (serializers/dispute dispute)})))))

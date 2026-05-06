(ns drw.domain.hub-events
  (:require [drw.ecosystem.hub-client :as hub])
  (:import [java.util UUID]))

(defn- dispute-payload [dispute]
  {:tenant_id (str (:dispute/tenant-id dispute))
   :dispute_id (str (:dispute/id dispute))
   :reference (:dispute/reference dispute)
   :status (name (:dispute/status dispute))
   :category (name (:dispute/category dispute))
   :severity (name (:dispute/severity dispute))
   :assigned_user_id (some-> (:dispute/assigned-user-id dispute) str)
   :workflow_execution_id (:dispute/workflow-execution-id dispute)
   :deep_link (str "/disputes/" (:dispute/id dispute))})

(defn emit-dispute! [cfg event-type dispute]
  (hub/emit-event!
   cfg
   {:event-type event-type
    :event-id (str event-type "-" (UUID/randomUUID))
    :payload (dispute-payload dispute)}))

(defn emit-correlation-pending! [cfg tenant-id correlations]
  (when (seq correlations)
    (hub/emit-event!
     cfg
     {:event-type "dispute.correlation_pending"
      :event-id (str "dispute.correlation_pending-" (UUID/randomUUID))
      :payload {:tenant_id (str tenant-id)
                :pending_count (count correlations)
                :correlation_ids (mapv #(str (:correlation/id %))
                                       correlations)}})))

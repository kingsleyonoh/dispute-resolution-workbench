(ns drw.domain.correlations
  (:require [drw.db.schema :as schema]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- now-date []
  (java.util.Date/from (Instant/now)))

(defn- throw-domain! [message data]
  (throw (ex-info message data)))

(defn list-by-tenant
  ([tenant-id] (list-by-tenant tenant-id {}))
  ([tenant-id filters]
   (exceptions/list-correlations tenant-id filters)))

(defn get-by-id [tenant-id correlation-id]
  (some #(when (= correlation-id (:correlation/id %)) %)
        (list-by-tenant tenant-id)))

(defn- require-correlation! [tenant-id correlation-id]
  (or (get-by-id tenant-id correlation-id)
      (throw-domain! "correlation not found"
                     {:type :correlation/not-found})))

(defn- audit! [tenant-id action entity before after actor]
  (state/append-audit!
   {:tenant-id tenant-id
    :actor-kind (:actor-kind actor)
    :actor-id (:actor-id actor)
    :action action
    :entity-kind :correlation
    :entity-id (:correlation/id entity)
    :before-state before
    :after-state after}))

(defn- accepted-timeline! [tenant-id correlation actor]
  (state/append-timeline!
   {:timeline/id (UUID/randomUUID)
    :timeline/dispute-id (:correlation/target-dispute-id correlation)
    :timeline/tenant-id tenant-id
    :timeline/kind :correlation-accepted
    :timeline/actor-kind (:actor-kind actor)
    :timeline/actor-id (:actor-id actor)
    :timeline/body "correlation accepted"
    :timeline/occurred-at (or (:correlation/decided-at correlation)
                              (now-date))}))

(defn- transition! [tenant-id correlation to actor]
  (schema/transition-correlation-status-tx
   (assoc correlation :db/id (:correlation/id correlation))
   to)
  (let [before correlation
        after (assoc correlation
                     :correlation/status to
                     :correlation/decided-by-user-id (:actor-id actor)
                     :correlation/decided-at (now-date))]
    (swap! state/correlations* assoc (:correlation/id after) after)
    (audit! tenant-id (str "correlation." (name to)) after before after actor)
    after))

(defn accept! [tenant-id correlation-id actor]
  (let [correlation (require-correlation! tenant-id correlation-id)]
    (schema/transition-correlation-status-tx
     (assoc correlation :db/id (:correlation/id correlation))
     :accepted)
    (exceptions/attach-to-dispute!
     tenant-id
     {:exception-id (:correlation/new-exception-id correlation)
      :dispute-id (:correlation/target-dispute-id correlation)}
     actor)
    (let [accepted (transition! tenant-id correlation :accepted actor)]
      (accepted-timeline! tenant-id accepted actor)
      accepted)))

(defn reject! [tenant-id correlation-id actor]
  (transition! tenant-id
               (require-correlation! tenant-id correlation-id)
               :rejected
               actor))

(defn hydrate [tenant-id correlation]
  {:correlation correlation
   :exception (exceptions/get-by-id tenant-id
                                    (:correlation/new-exception-id correlation))
   :target-dispute (disputes/get-by-id
                    tenant-id
                    (:correlation/target-dispute-id correlation))})

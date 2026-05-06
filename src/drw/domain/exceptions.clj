(ns drw.domain.exceptions
  (:require [drw.audit.recorder :as recorder]
            [drw.db.scope :as scope]
            [drw.domain.correlator :as correlator]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.domain.exception-validation :as validation]
            [drw.domain.hub-events :as hub-events]
            [drw.domain.state :as state])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- now-date []
  (java.util.Date/from (Instant/now)))

(defn- reject! [message data]
  (throw (ex-info message data)))

(declare attach-to-dispute!)

(defn list-by-tenant
  ([tenant-id] (list-by-tenant tenant-id {}))
  ([tenant-id {:keys [source-system dispute-id]}]
   (->> (scope/filter-by-tenant (vals @state/exceptions*)
                                tenant-id
                                :exception/tenant-id)
        (filter #(or (nil? source-system)
                     (= source-system (:exception/source-system %))))
        (filter #(or (nil? dispute-id)
                     (= dispute-id (:exception/dispute-id %))))
        vec)))

(defn get-by-id [tenant-id exception-id]
  (some #(when (= exception-id (:exception/id %)) %)
        (list-by-tenant tenant-id)))

(defn list-correlations
  ([tenant-id] (list-correlations tenant-id {}))
  ([tenant-id {:keys [status exception-id dispute-id]}]
   (->> (scope/filter-by-tenant (vals @state/correlations*)
                                tenant-id
                                :correlation/tenant-id)
        (filter #(or (nil? status) (= status (:correlation/status %))))
        (filter #(or (nil? exception-id)
                     (= exception-id (:correlation/new-exception-id %))))
        (filter #(or (nil? dispute-id)
                     (= dispute-id (:correlation/target-dispute-id %))))
        vec)))

(defn- duplicate-source-ref? [tenant-id source-system source-ref]
  (some #(and (= source-system (:exception/source-system %))
              (= source-ref (:exception/source-ref %)))
        (list-by-tenant tenant-id)))

(defn- audit! [tenant-id action entity before after actor]
  (state/append-audit!
   {:tenant-id tenant-id
    :actor-kind (:actor-kind actor)
    :actor-id (:actor-id actor)
    :action action
    :entity-kind :exception
    :entity-id (:exception/id entity)
    :before-state before
    :after-state after}))

(defn create-manual! [attrs actor]
  (let [attrs (assoc attrs :source-system (or (:source-system attrs) :manual))]
    (validation/require-create-fields! attrs)
    (when (duplicate-source-ref? (:tenant-id attrs)
                                 (:source-system attrs)
                                 (:source-ref attrs))
      (reject! "duplicate exception source reference"
               {:type :exception/duplicate-source-ref}))
    (let [entity {:exception/id (or (:id attrs) (UUID/randomUUID))
                  :exception/dispute-id (:dispute-id attrs)
                  :exception/tenant-id (:tenant-id attrs)
                  :exception/source-system (:source-system attrs)
                  :exception/source-ref (:source-ref attrs)
                  :exception/source-url (:source-url attrs)
                  :exception/kind (:kind attrs)
                  :exception/entity-id (:entity-id attrs)
                  :exception/counterparty-id (:counterparty-id attrs)
                  :exception/category (:category attrs)
                  :exception/raw-payload (recorder/encode-json
                                          (:raw-payload attrs))
                  :exception/monetary-impact-cents
                  (:monetary-impact-cents attrs 0)
                  :exception/currency (:currency attrs)
                  :exception/observed-at (:observed-at attrs)
                  :exception/ingested-at (or (:ingested-at attrs)
                                             (now-date))}]
      (swap! state/exceptions* assoc (:exception/id entity) entity)
      (audit! (:tenant-id attrs) "exception.created" entity nil entity actor)
      entity)))

(defn- category-for [attrs]
  (or (:category attrs)
      (get correlator/kind-categories (:kind attrs))
      :multi))

(defn- resolve-counterparty-id [attrs]
  (or (:counterparty-id attrs)
      (:counterparty/id
       (counterparties/resolve-counterparty
        {:tenant-id (:tenant-id attrs)
         :source-system (:source-system attrs)
         :external-ref (:counterparty-external-ref attrs)
         :canonical-name (:counterparty-name attrs)}))))

(defn- enrich-normalized [attrs]
  (assoc attrs
         :counterparty-id (resolve-counterparty-id attrs)
         :category (category-for attrs)))

(defn- dispute-title [exception]
  (str (name (:exception/kind exception))
       " "
       (:exception/source-ref exception)))

(defn- dispute-description [exception]
  (str "Created from "
       (name (:exception/source-system exception))
       " exception "
       (:exception/source-ref exception)
       "."))

(defn- create-dispute-from-exception! [exception actor]
  (let [tenant-id (:exception/tenant-id exception)
        dispute (disputes/create-dispute!
                 {:tenant-id tenant-id
                  :title (dispute-title exception)
                  :description (dispute-description exception)
                  :category (:exception/category exception)
                  :severity :medium
                  :currency (:exception/currency exception)
                  :counterparty-id (:exception/counterparty-id exception)
                  :created-by :adapter
                  :created-at (:exception/ingested-at exception)}
                 actor)
        attached (attach-to-dispute!
                  tenant-id
                  {:exception-id (:exception/id exception)
                   :dispute-id (:dispute/id dispute)}
                  actor)]
    {:status :dispute-created
     :exception attached
     :dispute (disputes/get-by-id tenant-id (:dispute/id dispute))}))

(defn- audit-correlation! [tenant-id action entity before after actor]
  (state/append-audit!
   {:tenant-id tenant-id
    :actor-kind (:actor-kind actor)
    :actor-id (:actor-id actor)
    :action action
    :entity-kind :correlation
    :entity-id (:correlation/id entity)
    :before-state before
    :after-state after}))

(defn- create-correlation! [tenant-id exception candidate status actor]
  (let [now (now-date)
        entity {:correlation/id (UUID/randomUUID)
                :correlation/tenant-id tenant-id
                :correlation/new-exception-id (:exception/id exception)
                :correlation/target-dispute-id (:dispute-id candidate)
                :correlation/score (:score candidate)
                :correlation/rationale (:rationale candidate)
                :correlation/status status
                :correlation/decided-by-user-id nil
                :correlation/decided-at (when (= :auto-merged status) now)
                :correlation/created-at now}]
    (swap! state/correlations* assoc (:correlation/id entity) entity)
    (audit-correlation! tenant-id "correlation.created" entity nil entity actor)
    entity))

(defn- score-existing-disputes [tenant-id exception opts]
  (correlator/score-candidates
   tenant-id
   exception
   (disputes/list-by-tenant tenant-id)
   (list-by-tenant tenant-id)
   opts))

(defn- auto-merge? [candidate cfg]
  (and (:auto-merge-enabled cfg)
       (= :auto-merge (:band candidate))))

(defn- correlate-or-create! [exception actor cfg]
  (let [tenant-id (:exception/tenant-id exception)
        candidates (score-existing-disputes tenant-id exception cfg)
        best (first candidates)]
    (cond
      (nil? best) (create-dispute-from-exception! exception actor)
      (auto-merge? best cfg)
      (let [correlation (create-correlation! tenant-id exception best
                                             :auto-merged actor)
            attached (attach-to-dispute!
                      tenant-id
                      {:exception-id (:exception/id exception)
                       :dispute-id (:correlation/target-dispute-id
                                    correlation)}
                      actor)]
        {:status :auto-merged
         :exception attached
         :correlation correlation})
      :else
      (let [correlations (mapv #(create-correlation! tenant-id exception %
                                                     :pending actor)
                               candidates)]
        (hub-events/emit-correlation-pending! cfg tenant-id correlations)
        {:status :correlation-pending
         :exception exception
         :correlations correlations}))))

(defn ingest!
  ([attrs actor] (ingest! attrs actor {}))
  ([attrs actor opts]
   (let [cfg (merge validation/default-ingestion-config opts)
         exception (create-manual! (enrich-normalized attrs) actor)]
     (correlate-or-create! exception actor cfg))))

(defn attach-to-dispute! [tenant-id {:keys [exception-id dispute-id]} actor]
  (let [exception (or (get-by-id tenant-id exception-id)
                      (reject! "exception not found"
                               {:type :exception/not-found}))
        dispute (or (disputes/get-by-id tenant-id dispute-id)
                    (reject! "dispute not found"
                             {:type :dispute/not-found}))]
    (disputes/ensure-attachable! dispute)
    (let [before exception
          after (assoc exception :exception/dispute-id dispute-id)]
      (swap! state/exceptions* assoc exception-id after)
      (audit! tenant-id "exception.attached" after before after actor)
      (let [updated-dispute (disputes/update-after-exception-attach!
                             tenant-id dispute-id after actor)]
        (assoc after
               :dispute/monetary-impact-cents
               (:dispute/monetary-impact-cents updated-dispute))))))

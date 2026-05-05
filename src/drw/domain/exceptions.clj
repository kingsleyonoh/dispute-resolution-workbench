(ns drw.domain.exceptions
  (:require [clojure.string :as str]
            [drw.audit.recorder :as recorder]
            [drw.db.scope :as scope]
            [drw.domain.disputes :as disputes]
            [drw.domain.state :as state])
  (:import [java.time Instant]
           [java.util UUID]))

(def valid-source-systems
  #{:invoice-recon :contract-lifecycle :transaction-recon :webhook-engine :manual})

(defn- now-date []
  (java.util.Date/from (Instant/now)))

(defn- blank? [value]
  (or (nil? value) (and (string? value) (str/blank? value))))

(defn- reject! [message data]
  (throw (ex-info message data)))

(defn- require-create-fields! [attrs]
  (doseq [field [:tenant-id :source-ref :kind :currency :observed-at]]
    (when (blank? (get attrs field))
      (reject! (str (name field) " is required")
               {:type :validation-error :field field})))
  (when-not (contains? valid-source-systems
                       (or (:source-system attrs) :manual))
    (reject! "source-system is invalid"
             {:type :validation-error :field :source-system})))

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
    (require-create-fields! attrs)
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

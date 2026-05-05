(ns drw.domain.counterparties
  (:require [clojure.string :as str]
            [drw.db.scope :as scope]
            [drw.domain.state :as state])
  (:import [java.time Instant]
           [java.util UUID]))

(def valid-kinds #{:customer :vendor :bank :internal})

(defn normalize-name [value]
  (-> value
      (or "")
      str/lower-case
      (str/replace #"[^a-z0-9]+" " ")
      str/trim
      (str/replace #"\s+" " ")))

(defn- now-date []
  (java.util.Date/from (Instant/now)))

(defn- blank? [value]
  (or (nil? value) (and (string? value) (str/blank? value))))

(defn- reject! [message data]
  (throw (ex-info message data)))

(defn- require-create-fields! [{:keys [tenant-id canonical-name kind]}]
  (when (nil? tenant-id)
    (reject! "tenant-id is required" {:type :validation-error :field :tenant-id}))
  (when (blank? canonical-name)
    (reject! "canonical-name is required"
             {:type :validation-error :field :canonical-name}))
  (when-not (contains? valid-kinds kind)
    (reject! "kind is invalid" {:type :validation-error :field :kind})))

(defn list-by-tenant [tenant-id]
  (vec (scope/filter-by-tenant (vals @state/counterparties*)
                               tenant-id
                               :counterparty/tenant-id)))

(defn get-by-id [tenant-id counterparty-id]
  (some #(when (= counterparty-id (:counterparty/id %)) %)
        (list-by-tenant tenant-id)))

(defn- duplicate-normalized? [tenant-id normalized except-id]
  (some #(and (= normalized (:counterparty/normalized-name %))
              (not= except-id (:counterparty/id %)))
        (list-by-tenant tenant-id)))

(defn- audit! [tenant-id action entity before after actor]
  (state/append-audit!
   {:tenant-id tenant-id
    :actor-kind (:actor-kind actor)
    :actor-id (:actor-id actor)
    :action action
    :entity-kind :counterparty
    :entity-id (:counterparty/id entity)
    :before-state before
    :after-state after}))

(defn create! [attrs actor]
  (require-create-fields! attrs)
  (let [tenant-id (:tenant-id attrs)
        normalized (normalize-name (:canonical-name attrs))]
    (when (duplicate-normalized? tenant-id normalized nil)
      (reject! "duplicate normalized counterparty name"
               {:type :counterparty/duplicate-normalized-name}))
    (let [entity {:counterparty/id (or (:id attrs) (UUID/randomUUID))
                  :counterparty/tenant-id tenant-id
                  :counterparty/canonical-name (:canonical-name attrs)
                  :counterparty/normalized-name normalized
                  :counterparty/kind (:kind attrs)
                  :counterparty/tax-id (:tax-id attrs)
                  :counterparty/country-code (:country-code attrs)
                  :counterparty/external-refs (:external-refs attrs)
                  :counterparty/created-at (or (:created-at attrs) (now-date))}]
      (swap! state/counterparties* assoc (:counterparty/id entity) entity)
      (audit! tenant-id "counterparty.created" entity nil entity actor)
      entity)))

(defn update! [tenant-id counterparty-id attrs actor]
  (let [before (get-by-id tenant-id counterparty-id)]
    (when-not before
      (reject! "counterparty not found" {:type :counterparty/not-found}))
    (let [canonical (or (:canonical-name attrs)
                        (:counterparty/canonical-name before))
          normalized (normalize-name canonical)]
      (when (duplicate-normalized? tenant-id normalized counterparty-id)
        (reject! "duplicate normalized counterparty name"
                 {:type :counterparty/duplicate-normalized-name}))
      (let [after (merge before
                         (when (:canonical-name attrs)
                           {:counterparty/canonical-name canonical
                            :counterparty/normalized-name normalized})
                         (when (contains? attrs :kind)
                           {:counterparty/kind (:kind attrs)})
                         (when (contains? attrs :tax-id)
                           {:counterparty/tax-id (:tax-id attrs)})
                         (when (contains? attrs :country-code)
                           {:counterparty/country-code (:country-code attrs)})
                         (when (contains? attrs :external-refs)
                           {:counterparty/external-refs (:external-refs attrs)}))]
        (swap! state/counterparties* assoc counterparty-id after)
        (audit! tenant-id "counterparty.updated" after before after actor)
        after))))

(defn delete! [tenant-id counterparty-id actor]
  (let [before (get-by-id tenant-id counterparty-id)]
    (when-not before
      (reject! "counterparty not found" {:type :counterparty/not-found}))
    (swap! state/counterparties* dissoc counterparty-id)
    (audit! tenant-id "counterparty.deleted" before before nil actor)
    {:status :deleted :counterparty-id counterparty-id}))

(defn merge! [tenant-id source-id target-id actor]
  (when (= source-id target-id)
    (reject! "counterparty cannot merge into itself"
             {:type :validation-error :field :merge-into-id}))
  (let [source (or (get-by-id tenant-id source-id)
                   (reject! "source counterparty not found"
                            {:type :counterparty/not-found}))
        target (or (get-by-id tenant-id target-id)
                   (reject! "target counterparty not found"
                            {:type :counterparty/not-found}))]
    (swap! state/disputes*
           (fn [disputes]
             (into {}
                   (map (fn [[id dispute]]
                          [id (if (and (= tenant-id (:dispute/tenant-id dispute))
                                       (= source-id
                                          (:dispute/counterparty-id dispute)))
                                (assoc dispute :dispute/counterparty-id target-id)
                                dispute)]))
                   disputes)))
    (swap! state/counterparties* dissoc source-id)
    (audit! tenant-id "counterparty.merged" source source target actor)
    target))

(defn- external-ref-match? [counterparty source-system external-ref]
  (= external-ref
     (get (:counterparty/external-refs counterparty) source-system)))

(defn resolve-counterparty [{:keys [tenant-id source-system external-ref
                                    canonical-name]}]
  (or (some #(when (external-ref-match? % source-system external-ref) %)
            (list-by-tenant tenant-id))
      (let [normalized (normalize-name canonical-name)]
        (some #(when (= normalized (:counterparty/normalized-name %)) %)
              (list-by-tenant tenant-id)))))

(ns drw.domain.disputes
  (:require [clojure.string :as str]
            [drw.db.schema :as schema]
            [drw.db.scope :as scope]
            [drw.domain.state :as state])
  (:import [java.time Instant ZoneOffset]
           [java.time.temporal ChronoUnit]
           [java.util UUID]))

(def terminal-statuses #{:resolved :withdrawn})
(def default-sla-minutes 7200)

(defn- now-date []
  (java.util.Date/from (Instant/now)))

(defn- blank? [value]
  (or (nil? value) (and (string? value) (str/blank? value))))

(defn- reject! [message data]
  (throw (ex-info message data)))

(defn- require-create-fields! [attrs]
  (doseq [field [:tenant-id :title :description :category
                 :severity :currency :created-by]]
    (when (blank? (get attrs field))
      (reject! (str (name field) " is required")
               {:type :validation-error :field field}))))

(defn list-by-tenant [tenant-id]
  (vec (scope/filter-by-tenant (vals @state/disputes*)
                               tenant-id
                               :dispute/tenant-id)))

(defn get-by-id [tenant-id dispute-id]
  (some #(when (= dispute-id (:dispute/id %)) %)
        (list-by-tenant tenant-id)))

(defn list-timeline [tenant-id dispute-id]
  (->> (scope/filter-by-tenant (state/timeline) tenant-id :timeline/tenant-id)
       (filter #(= dispute-id (:timeline/dispute-id %)))
       vec))

(defn list-audit-log [tenant-id]
  (->> (scope/filter-by-tenant (state/audit-log) tenant-id :audit/tenant-id)
       (filter #(= :dispute (:audit/entity-kind %)))
       vec))

(defn- year-of [date]
  (.getYear (.atZone (.toInstant date) ZoneOffset/UTC)))

(defn- next-reference! [tenant-id date]
  (let [year (year-of date)
        key [tenant-id year]
        n (swap! state/reference-sequences* update key (fnil inc 0))]
    (format "DIS-%d-%04d" year (get n key))))

(defn- timeline! [tenant-id dispute-id kind body actor occurred-at]
  (state/append-timeline!
   {:timeline/id (UUID/randomUUID)
    :timeline/dispute-id dispute-id
    :timeline/tenant-id tenant-id
    :timeline/kind kind
    :timeline/actor-kind (:actor-kind actor)
    :timeline/actor-id (:actor-id actor)
    :timeline/body body
    :timeline/occurred-at (or occurred-at (now-date))}))

(defn- audit! [tenant-id action entity before after actor]
  (state/append-audit!
   {:tenant-id tenant-id
    :actor-kind (:actor-kind actor)
    :actor-id (:actor-id actor)
    :action action
    :entity-kind :dispute
    :entity-id (:dispute/id entity)
    :before-state before
    :after-state after}))

(defn create-dispute! [attrs actor]
  (require-create-fields! attrs)
  (let [created-at (or (:created-at attrs) (now-date))
        tenant-id (:tenant-id attrs)
        entity {:dispute/id (or (:id attrs) (UUID/randomUUID))
                :dispute/tenant-id tenant-id
                :dispute/reference (next-reference! tenant-id created-at)
                :dispute/counterparty-id (:counterparty-id attrs)
                :dispute/title (:title attrs)
                :dispute/description (:description attrs)
                :dispute/status :open
                :dispute/category (:category attrs)
                :dispute/severity (:severity attrs)
                :dispute/monetary-impact-cents 0
                :dispute/currency (:currency attrs)
                :dispute/sla-due-at nil
                :dispute/assigned-user-id nil
                :dispute/assigned-at nil
                :dispute/workflow-execution-id nil
                :dispute/created-at created-at
                :dispute/created-by (:created-by attrs)
                :dispute/created-by-user-id (:created-by-user-id attrs)
                :dispute/resolution-summary nil
                :dispute/resolved-at nil}]
    (swap! state/disputes* assoc (:dispute/id entity) entity)
    (timeline! tenant-id (:dispute/id entity) :dispute-created
               (:dispute/title entity) actor created-at)
    (audit! tenant-id "dispute.created" entity nil entity actor)
    entity))

(defn- target-minutes [tenant-id category severity policies]
  (or (:sla-policy/target-minutes
       (some #(when (and (= tenant-id (:sla-policy/tenant-id %))
                         (= category (:sla-policy/category %))
                         (= severity (:sla-policy/severity %)))
                %)
             policies))
      default-sla-minutes))

(defn- add-minutes [date minutes]
  (java.util.Date/from (.plus (.toInstant date) minutes ChronoUnit/MINUTES)))

(defn assign! [tenant-id dispute-id attrs actor]
  (let [before (or (get-by-id tenant-id dispute-id)
                   (reject! "dispute not found" {:type :dispute/not-found}))
        assigned-at (or (:assigned-at attrs) (now-date))
        minutes (target-minutes tenant-id
                                (:dispute/category before)
                                (:dispute/severity before)
                                (:sla-policies attrs))
        _ (schema/transition-dispute-status-tx
           (assoc before :db/id dispute-id)
           :assigned)
        after (assoc before
                     :dispute/status :assigned
                     :dispute/assigned-user-id (:user-id attrs)
                     :dispute/assigned-at assigned-at
                     :dispute/sla-due-at (add-minutes assigned-at minutes))]
    (swap! state/disputes* assoc dispute-id after)
    (timeline! tenant-id dispute-id :assigned
               (str (:user-id attrs)) actor assigned-at)
    (audit! tenant-id "dispute.assigned" after before after actor)
    after))

(defn transition! [tenant-id dispute-id attrs actor]
  (let [before (or (get-by-id tenant-id dispute-id)
                   (reject! "dispute not found" {:type :dispute/not-found}))
        to (:to attrs)
        occurred-at (or (:occurred-at attrs) (now-date))
        _ (schema/transition-dispute-status-tx (assoc before :db/id dispute-id) to)
        after (cond-> (assoc before :dispute/status to)
                (= to :resolved) (assoc :dispute/resolved-at occurred-at)
                (:resolution-summary attrs)
                (assoc :dispute/resolution-summary (:resolution-summary attrs))
                (= to :investigating) (assoc :dispute/workflow-execution-id nil))]
    (swap! state/disputes* assoc dispute-id after)
    (timeline! tenant-id dispute-id :status-changed
               (str (name (:dispute/status before)) " -> " (name to))
               actor occurred-at)
    (audit! tenant-id "dispute.status_changed" after before after actor)
    after))

(defn ensure-attachable! [dispute]
  (when (contains? terminal-statuses (:dispute/status dispute))
    (reject! "terminal disputes cannot be changed" {:type :dispute/terminal})))

(defn recalculate-monetary-impact [tenant-id dispute-id]
  (reduce + 0 (map :exception/monetary-impact-cents
                   (scope/filter-by-tenant
                    (filter #(= dispute-id (:exception/dispute-id %))
                            (vals @state/exceptions*))
                    tenant-id
                    :exception/tenant-id))))

(defn update-after-exception-attach! [tenant-id dispute-id exception actor]
  (let [before (or (get-by-id tenant-id dispute-id)
                   (reject! "dispute not found" {:type :dispute/not-found}))]
    (ensure-attachable! before)
    (let [after (assoc before
                       :dispute/monetary-impact-cents
                       (recalculate-monetary-impact tenant-id dispute-id))]
      (swap! state/disputes* assoc dispute-id after)
      (timeline! tenant-id dispute-id :exception-attached
                 (str (:exception/source-system exception) "/"
                      (:exception/source-ref exception))
                 actor nil)
      (audit! tenant-id "dispute.exception_attached" after before after actor)
      after)))

(defn comment! [tenant-id dispute-id attrs actor]
  (let [dispute (or (get-by-id tenant-id dispute-id)
                    (reject! "dispute not found" {:type :dispute/not-found}))]
    (ensure-attachable! dispute)
    (when (blank? (:body attrs))
      (reject! "comment body is required"
               {:type :validation-error :field :body}))
    (timeline! tenant-id dispute-id :commented (:body attrs) actor nil)))

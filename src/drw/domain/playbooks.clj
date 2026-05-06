(ns drw.domain.playbooks
  (:require [clojure.string :as str]
            [drw.db.scope :as scope]
            [drw.domain.state :as state])
  (:import [java.util UUID]))

(defn- blank? [value]
  (str/blank? (str value)))

(defn- reject! [message data]
  (throw (ex-info message data)))

(defn- require-field! [attrs field]
  (when (blank? (get attrs field))
    (reject! (str (name field) " is required")
             {:type :validation-error :field field})))

(defn- require-fields! [attrs]
  (doseq [field [:code :display-name :workflow-engine-workflow-id]]
    (require-field! attrs field)))

(defn list-by-tenant [tenant-id filters]
  (let [items (scope/filter-by-tenant (vals @state/playbooks*)
                                      tenant-id
                                      :playbook/tenant-id)]
    (cond->> items
      (some? (:active? filters))
      (filter #(= (:active? filters) (:playbook/is-active %)))
      true vec)))

(defn get-by-id [tenant-id playbook-id]
  (some #(when (= playbook-id (:playbook/id %)) %)
        (list-by-tenant tenant-id {})))

(defn- duplicate-code? [tenant-id code id]
  (some #(and (= code (:playbook/code %))
              (not= id (:playbook/id %)))
        (list-by-tenant tenant-id {})))

(defn- audit! [tenant-id action entity before after actor]
  (state/append-audit!
   {:tenant-id tenant-id
    :actor-kind (:actor-kind actor)
    :actor-id (:actor-id actor)
    :action action
    :entity-kind :playbook
    :entity-id (:playbook/id entity)
    :before-state before
    :after-state after}))

(defn- normalize [tenant-id attrs before]
  (let [id (or (:id attrs) (:playbook/id before) (UUID/randomUUID))
        active? (if (contains? attrs :is-active)
                  (:is-active attrs)
                  (get before :playbook/is-active true))]
    {:playbook/id id
     :playbook/tenant-id tenant-id
     :playbook/code (or (:code attrs) (:playbook/code before))
     :playbook/display-name (or (:display-name attrs)
                                (:playbook/display-name before))
     :playbook/description (or (:description attrs)
                               (:playbook/description before)
                               "")
     :playbook/workflow-engine-workflow-id
     (or (:workflow-engine-workflow-id attrs)
         (:playbook/workflow-engine-workflow-id before))
     :playbook/required-inputs-schema
     (or (:required-inputs-schema attrs)
         (:playbook/required-inputs-schema before)
         "{}")
     :playbook/is-active active?}))

(defn save! [tenant-id attrs actor]
  (let [before (when (:id attrs) (get-by-id tenant-id (:id attrs)))
        _ (when (and (:id attrs) (nil? before))
            (reject! "playbook not found" {:type :playbook/not-found}))
        entity (normalize tenant-id attrs before)]
    (require-fields! {:code (:playbook/code entity)
                      :display-name (:playbook/display-name entity)
                      :workflow-engine-workflow-id
                      (:playbook/workflow-engine-workflow-id entity)})
    (when (duplicate-code? tenant-id (:playbook/code entity)
                           (:playbook/id entity))
      (reject! "playbook code already exists"
               {:type :playbook/duplicate-code}))
    (swap! state/playbooks* assoc (:playbook/id entity) entity)
    (audit! tenant-id
            (if before "playbook.updated" "playbook.created")
            entity before entity actor)
    entity))

(defn disable! [tenant-id playbook-id actor]
  (let [before (or (get-by-id tenant-id playbook-id)
                   (reject! "playbook not found"
                            {:type :playbook/not-found}))
        after (assoc before :playbook/is-active false)]
    (swap! state/playbooks* assoc playbook-id after)
    (audit! tenant-id "playbook.disabled" after before after actor)
    after))

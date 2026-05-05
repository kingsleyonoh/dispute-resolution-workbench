(ns drw.audit.recorder
  (:require [clojure.string :as str])
  (:import [java.time Instant]
           [java.util UUID]))

(def required-fields
  [:tenant-id :actor-kind :actor-id :action :entity-kind :entity-id])

(defn- json-escape [value]
  (-> value
      str
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")))

(declare encode-json)

(defn- encode-entry [[k v]]
  (str (encode-json (name k)) ":" (encode-json v)))

(defn encode-json [value]
  (cond
    (nil? value) "null"
    (keyword? value) (encode-json (name value))
    (string? value) (str "\"" (json-escape value) "\"")
    (or (number? value) (boolean? value)) (str value)
    (uuid? value) (encode-json (str value))
    (inst? value) (encode-json (str value))
    (map? value) (str "{" (str/join "," (map encode-entry value)) "}")
    (sequential? value) (str "[" (str/join "," (map encode-json value)) "]")
    :else (encode-json (str value))))

(defn- blank-value? [value]
  (or (nil? value)
      (and (string? value) (str/blank? value))))

(defn- require-fields! [event]
  (doseq [field required-fields]
    (when (blank-value? (get event field))
      (throw (ex-info (str (name field) " is required") {:field field}))))
  event)

(defn assert-append-only! [tx-data]
  (doseq [op tx-data]
    (when (and (vector? op) (= :db/retract (first op)))
      (throw (ex-info "audit transaction must be append-only"
                      {:type :audit/retract-forbidden
                       :op op}))))
  tx-data)

(defn audit-tx [event]
  (let [event (require-fields! event)
        row {:audit/id (or (:audit-id event) (UUID/randomUUID))
             :audit/tenant-id (:tenant-id event)
             :audit/actor-kind (:actor-kind event)
             :audit/actor-id (:actor-id event)
             :audit/action (:action event)
             :audit/entity-kind (:entity-kind event)
             :audit/entity-id (:entity-id event)
             :audit/before-state (encode-json (:before-state event))
             :audit/after-state (encode-json (:after-state event))
             :audit/occurred-at (or (:occurred-at event)
                                    (java.util.Date/from (Instant/now)))}]
    (assert-append-only! [row])))

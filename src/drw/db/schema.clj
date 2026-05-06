(ns drw.db.schema
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def dispute-transitions
  {:open #{:assigned :withdrawn}
   :assigned #{:investigating :withdrawn}
   :investigating #{:awaiting_counterparty :awaiting_approval
                    :resolving :withdrawn}
   :awaiting_counterparty #{:investigating :withdrawn}
   :awaiting_approval #{:investigating :resolving :withdrawn}
   :resolving #{:resolved :investigating}})

(def correlation-transitions
  {:pending #{:accepted :rejected :auto-merged}})

(def report-transitions
  {:generating #{:ready :failed}})

(defn- read-resource-edn [path]
  (if-let [resource (io/resource path)]
    (edn/read-string (slurp resource))
    (throw (ex-info "resource not found" {:path path}))))

(defn schema
  []
  (read-resource-edn "datomic/schema.edn"))

(defn attributes
  []
  (remove #(= "drw.tx" (namespace (:db/ident %))) (schema)))

(defn tx-function-specs
  []
  (filter #(= "drw.tx" (namespace (:db/ident %))) (schema)))

(defn roundtrip-summary
  []
  (let [loaded (schema)
        roundtripped (edn/read-string (pr-str loaded))
        attrs (remove #(= "drw.tx" (namespace (:db/ident %))) loaded)
        tx-fns (filter #(= "drw.tx" (namespace (:db/ident %))) loaded)]
    (when-not (= loaded roundtripped)
      (throw (ex-info "schema roundtrip mismatch"
                      {:type :schema/roundtrip-mismatch})))
    {:status :ok
     :entry-count (count loaded)
     :attribute-count (count attrs)
     :tx-function-count (count tx-fns)}))

(defn- legal-transition? [transitions from to]
  (contains? (get transitions from #{}) to))

(defn- reject-transition! [from to]
  (throw (ex-info "illegal-status-transition"
                  {:type :illegal-status-transition
                   :from from
                   :to to})))

(defn transition-status-tx [transitions attr entity to]
  (let [from (get entity attr)]
    (when-not (legal-transition? transitions from to)
      (reject-transition! from to))
    [[:db/add (:db/id entity) attr to]]))

(defn transition-dispute-status-tx [entity to]
  (transition-status-tx dispute-transitions :dispute/status entity to))

(defn transition-correlation-status-tx [entity to]
  (transition-status-tx correlation-transitions :correlation/status entity to))

(defn transition-report-status-tx [entity to]
  (transition-status-tx report-transitions :report/status entity to))

(defn- rejection-type [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo ex
      (:type (ex-data ex)))))

(defn status-transition-summary
  []
  (let [legal {:dispute (transition-dispute-status-tx
                         {:db/id 1 :dispute/status :open}
                         :assigned)
               :correlation (transition-correlation-status-tx
                             {:db/id 2 :correlation/status :pending}
                             :accepted)
               :report (transition-report-status-tx
                        {:db/id 3 :report/status :generating}
                        :ready)}
        rejected {:dispute (rejection-type
                            #(transition-dispute-status-tx
                              {:db/id 1 :dispute/status :resolved}
                              :assigned))
                  :correlation (rejection-type
                                #(transition-correlation-status-tx
                                  {:db/id 2 :correlation/status :accepted}
                                  :rejected))
                  :report (rejection-type
                           #(transition-report-status-tx
                             {:db/id 3 :report/status :ready}
                             :failed))}]
    {:status :ok
     :transition-families (keys legal)
     :legal legal
     :rejected rejected}))

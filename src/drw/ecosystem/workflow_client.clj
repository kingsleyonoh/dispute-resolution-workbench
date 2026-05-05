(ns drw.ecosystem.workflow-client
  (:require [clojure.string :as str]))

(defn- missing-config [cfg]
  (cond-> []
    (str/blank? (:workflow-engine-url cfg)) (conj :workflow-engine-url)
    (str/blank? (:workflow-engine-api-key cfg)) (conj :workflow-engine-api-key)))

(defn- require-config! [cfg]
  (let [missing (missing-config cfg)]
    (when (seq missing)
      (throw (ex-info "Workflow Engine configuration is incomplete"
                      {:type :ecosystem/missing-config
                       :service :workflow-engine
                       :missing missing}))))
  cfg)

(defn- endpoint [cfg workflow-id]
  (str (str/replace (:workflow-engine-url cfg) #"/$" "")
       "/api/workflows/" workflow-id "/execute"))

(defn- snake-key [k]
  (keyword (str/replace (name k) "-" "_")))

(defn- snake-map [m]
  (into {} (map (fn [[k v]] [(snake-key k) v])) m))

(defn trigger-workflow! [cfg workflow-id trigger-data]
  (if-not (:workflow-engine-enabled cfg)
    {:status :disabled
     :sent? false
     :service :workflow-engine
     :workflow-id workflow-id}
    (let [cfg (require-config! cfg)
          data (snake-map trigger-data)]
      (if-let [send-fn (:workflow-engine-send-fn cfg)]
        (send-fn {:endpoint (endpoint cfg workflow-id)
                  :api-key (:workflow-engine-api-key cfg)
                  :trigger-data data})
        {:status :stubbed
         :sent? false
         :service :workflow-engine
         :endpoint (endpoint cfg workflow-id)
         :trigger_data data}))))

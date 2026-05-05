(ns drw.ecosystem.hub-client
  (:require [clojure.string :as str]))

(defn- missing-config [cfg]
  (cond-> []
    (str/blank? (:notification-hub-url cfg)) (conj :notification-hub-url)
    (str/blank? (:notification-hub-api-key cfg)) (conj :notification-hub-api-key)))

(defn- require-config! [cfg]
  (let [missing (missing-config cfg)]
    (when (seq missing)
      (throw (ex-info "Notification Hub configuration is incomplete"
                      {:type :ecosystem/missing-config
                       :service :notification-hub
                       :missing missing}))))
  cfg)

(defn- endpoint [cfg]
  (str (str/replace (:notification-hub-url cfg) #"/$" "") "/api/events"))

(defn- normalize-event [event]
  {:event_type (:event-type event)
   :event_id (:event-id event)
   :payload (:payload event)})

(defn emit-event! [cfg event]
  (if-not (:notification-hub-enabled cfg)
    {:status :disabled
     :sent? false
     :service :notification-hub
     :event-type (:event-type event)}
    (let [cfg (require-config! cfg)
          normalized (normalize-event event)]
      (if-let [send-fn (:notification-hub-send-fn cfg)]
        (send-fn {:endpoint (endpoint cfg)
                  :api-key (:notification-hub-api-key cfg)
                  :event normalized})
        {:status :stubbed
         :sent? false
         :service :notification-hub
         :endpoint (endpoint cfg)
         :event normalized}))))

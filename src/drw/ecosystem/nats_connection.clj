(ns drw.ecosystem.nats-connection
  (:require [clojure.string :as str]))

(defn- blank? [value]
  (or (nil? value) (and (string? value) (str/blank? value))))

(defn- missing-config [cfg]
  (cond-> []
    (blank? (:nats-url cfg)) (conj :nats-url)
    (nil? (:nats-connect-fn cfg)) (conj :nats-connect-fn)))

(defn- require-config! [cfg]
  (let [missing (missing-config cfg)]
    (when (seq missing)
      (throw (ex-info "NATS configuration is incomplete"
                      {:type :nats/missing-config
                       :missing missing}))))
  cfg)

(defn- connect-options [cfg]
  {:url (:nats-url cfg)
   :creds-path (:nats-creds-path cfg)
   :stream-name (get cfg :nats-stream-name "ECOSYSTEM_EVENTS")})

(defn connect! [cfg]
  (if-not (:nats-enabled cfg)
    {:status :disabled
     :enabled? false
     :connection nil}
    (let [cfg (require-config! cfg)
          opts (connect-options cfg)
          client ((:nats-connect-fn cfg) opts)]
      {:status :connected
       :enabled? true
       :connection client
       :stream-name (:stream-name opts)})))

(defn- require-client-fn! [conn key]
  (or (get-in conn [:connection key])
      (throw (ex-info "NATS client function is missing"
                      {:type :nats/missing-client-fn
                       :function key}))))

(defn publish!
  ([conn subject payload] (publish! conn subject payload {}))
  ([conn subject payload opts]
   (if (= :disabled (:status conn))
     {:status :disabled}
     ((require-client-fn! conn :publish-fn) subject payload opts))))

(defn subscribe!
  ([conn subject handler] (subscribe! conn subject handler {}))
  ([conn subject handler opts]
   (if (= :disabled (:status conn))
     {:status :disabled :subject subject}
     ((require-client-fn! conn :subscribe-fn) subject handler opts))))

(defn close! [conn]
  (if (= :disabled (:status conn))
    {:status :disabled}
    ((require-client-fn! conn :close-fn))))

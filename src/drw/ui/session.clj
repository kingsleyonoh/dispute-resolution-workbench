(ns drw.ui.session
  (:require [clojure.string :as str]
            [drw.tenants.store :as store])
  (:import [java.util UUID]))

(def cookie-name "drw_session")
(defonce sessions* (atom {}))

(defn reset-sessions! []
  (reset! sessions* {}))

(defn- cookie-pairs [header]
  (into {}
        (keep (fn [part]
                (let [[k v] (str/split (str/trim part) #"=" 2)]
                  (when-not (str/blank? k)
                    [k (some-> v (str/replace #"^\"|\"$" ""))]))))
        (str/split (or header "") #";")))

(defn cookie-value [request]
  (or (get-in request [:headers "x-drw-session"])
      (get-in request [:headers "X-DRW-Session"])
      (get-in request [:headers :x-drw-session])
      (get-in request [:cookies cookie-name :value])
      (get-in request [:cookies cookie-name "value"])
      (get-in request [:cookies cookie-name])
      (let [header (or (get-in request [:headers "cookie"])
                       (get-in request [:headers "Cookie"])
                       (get-in request [:headers :cookie])
                       (get-in request [:headers :Cookie]))]
        (get (cookie-pairs header) cookie-name))))

(defn tenant-from-request [request]
  (or (some-> request cookie-value (get @sessions*) store/tenant-by-id)
      (some-> (or (get-in request [:headers "x-api-key"])
                  (get-in request [:headers "X-API-Key"])
                  (get-in request [:headers :x-api-key]))
              store/tenant-by-api-key)))

(defn create-session! [tenant-id]
  (let [token (str (UUID/randomUUID))]
    (swap! sessions* assoc token tenant-id)
    token))

(defn clear-session! [request]
  (when-let [token (cookie-value request)]
    (swap! sessions* dissoc token)))

(defn session-cookie [token]
  (str cookie-name "=" token "; Path=/; HttpOnly; SameSite=Lax"))

(def expired-cookie
  (str cookie-name "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"))

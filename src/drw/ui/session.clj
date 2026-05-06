(ns drw.ui.session
  (:require [clojure.string :as str]
            [drw.tenants.store :as store])
  (:import [java.util UUID]))

(def cookie-name "drw_session")
(defonce sessions* (atom {}))
(defonce csrf-tokens* (atom {}))
(def ^:dynamic *csrf-token* nil)

(defn reset-sessions! []
  (reset! sessions* {})
  (reset! csrf-tokens* {}))

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
  (or (some->> request cookie-value (get @sessions*) store/tenant-by-id)
      (some-> (or (get-in request [:headers "x-api-key"])
                  (get-in request [:headers "X-API-Key"])
                  (get-in request [:headers :x-api-key]))
              store/tenant-by-api-key)))

(defn create-session! [tenant-id]
  (let [token (str (UUID/randomUUID))]
    (swap! sessions* assoc token tenant-id)
    (swap! csrf-tokens* assoc token (str (UUID/randomUUID)))
    token))

(defn clear-session! [request]
  (when-let [token (cookie-value request)]
    (swap! sessions* dissoc token)
    (swap! csrf-tokens* dissoc token)))

(defn csrf-token [request]
  (some->> request cookie-value (get @csrf-tokens*)))

(defn csrf-field []
  (when *csrf-token*
    [:input {:type "hidden" :name "_csrf" :value *csrf-token*}]))

(defn cookie-session? [request]
  (boolean (some->> request cookie-value (get @sessions*))))

(defn valid-csrf? [request form]
  (or (not (cookie-session? request))
      (let [stored (csrf-token request)
            submitted (or (:_csrf form)
                          (:_csrf-token form)
                          (get-in request [:headers "x-csrf-token"])
                          (get-in request [:headers "X-CSRF-Token"]))]
        (and (not (str/blank? stored))
             (= stored submitted)))))

(defn- secure-suffix [cfg]
  (when (= "production" (or (:app-env cfg) (System/getenv "APP_ENV")))
    "; Secure"))

(defn session-cookie
  ([token] (session-cookie token {}))
  ([token cfg]
   (str cookie-name "=" token "; Path=/; HttpOnly; SameSite=Lax"
        (secure-suffix cfg))))

(defn expired-cookie
  ([] (expired-cookie {}))
  ([cfg]
   (str cookie-name "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"
        (secure-suffix cfg))))

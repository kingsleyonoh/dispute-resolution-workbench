(ns ^:e2e drw.e2e-api.ui-flow-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.state :as domain-state]
            [drw.http.interceptors.ratelimit :as ratelimit]
            [drw.http.server :as server]
            [drw.tenants.store :as store])
  (:import [java.net HttpURLConnection URI URLEncoder]))

(defn- encode-form [params]
  (str/join "&"
            (map (fn [[k v]]
                   (str (URLEncoder/encode (name k) "UTF-8")
                        "="
                        (URLEncoder/encode (str v) "UTF-8")))
                 params)))

(defn- http-client [] nil)

(def ^:dynamic *client* nil)

(defn- header-map [^HttpURLConnection conn]
  (into {}
        (keep (fn [[k values]]
                (when k [(str/lower-case k) (vec values)])))
        (.getHeaderFields conn)))

(defn- read-body [^HttpURLConnection conn]
  (let [stream (if (< (.getResponseCode conn) 400)
                 (.getInputStream conn)
                 (.getErrorStream conn))]
    (if stream (slurp stream) "")))

(defn- request
  ([method url] (request method url nil nil))
  ([method url body headers]
   (let [conn ^HttpURLConnection (.openConnection (.toURL (URI/create url)))]
     (.setInstanceFollowRedirects conn false)
     (.setRequestMethod conn method)
     (doseq [[k v] (or headers {})]
       (.setRequestProperty conn k v))
     (when body
       (.setDoOutput conn true)
       (with-open [out (.getOutputStream conn)]
         (.write out (.getBytes body "UTF-8"))))
     {:status (.getResponseCode conn)
      :headers (header-map conn)
      :body (read-body conn)})))

(defn- form [url params api-key]
  (request "POST" url (encode-form params)
           (cond-> {"Content-Type" "application/x-www-form-urlencoded"}
             api-key (assoc "X-API-Key" api-key))))

(defn- cookie [response]
  (some-> (first (get-in response [:headers "set-cookie"]))
          (str/split #";")
          first))

(defn- location [response]
  (or (first (get-in response [:headers "location"])) ""))

(defn- json-string [body key]
  (second (re-find (re-pattern (str "\"" key "\"\\s*:\\s*\"([^\"]+)\""))
                   body)))

(defn- start-test-server [port]
  (store/reset-store!)
  (domain-state/reset-store!)
  (ratelimit/reset-limits!)
  (server/start! {:port port
                  :api-key-prefix "drw_live_"
                  :self-registration-enabled true}))

(deftest operator-completes-manual-dispute-flow-through-ui-over-http
  (let [port 31553
        base (str "http://127.0.0.1:" port)
        srv (start-test-server port)]
    (try
      (binding [*client* (http-client)]
        (let [registered (request "POST" (str base "/api/tenants/register")
                                  "{\"name\":\"UI Tenant\"}"
                                  {"Content-Type" "application/json"})
              api-key (json-string (:body registered) "apiKey")
              tenant-id (java.util.UUID/fromString
                         (json-string (:body registered) "id"))
              cp (counterparties/create!
                  {:tenant-id tenant-id
                   :canonical-name "Browser Flow Vendor"
                   :kind :vendor}
                  {:actor-kind :user :actor-id "e2e-ui"})
              blocked (request "GET" (str base "/disputes"))
              login (form (str base "/login") {:api_key api-key} nil)
              session-cookie (cookie login)
              session-header {"X-API-Key" api-key}
              dashboard (request "GET" (str base "/") nil session-header)
              created (form (str base "/disputes")
                            {:title "Browser flow dispute"
                             :description "Created from the UI"
                             :category "billing"
                             :severity "high"
                             :currency "EUR"
                             :counterparty_id (:counterparty/id cp)}
                            api-key)
              detail-url (location created)
              dispute-id (second (re-find #"/disputes/([^/?]+)" detail-url))
              list-page (request "GET" (str base "/disputes") nil session-header)
              assigned (form (str base "/disputes/" dispute-id "/assign")
                             {:user_id "33333333-3333-3333-3333-333333333333"}
                             api-key)
              transitioned (form (str base "/disputes/" dispute-id "/transition")
                                 {:to_status "investigating"}
                                 api-key)
              commented (form (str base "/disputes/" dispute-id "/comments")
                              {:body "Called the vendor from UI."}
                              api-key)
              attached (form (str base "/disputes/" dispute-id "/exceptions")
                             {:source_ref "UI-E2E-MAN-1"
                              :kind "manual"
                              :currency "EUR"
                              :observed_at "2026-05-05T10:00:00Z"
                              :monetary_impact_cents "1234"}
                             api-key)
              detail (request "GET" (str base "/disputes/" dispute-id)
                              nil session-header)
              counterparties-page (request "GET" (str base "/counterparties")
                                           nil session-header)
              counterparty-detail (request
                                   "GET"
                                   (str base "/counterparties/" (:counterparty/id cp))
                                   nil
                                   session-header)]
          (is (= 302 (:status blocked)))
          (is (= 303 (:status login)))
          (is (str/includes? session-cookie "drw_session="))
          (is (= 200 (:status dashboard)))
          (is (str/includes? (:body dashboard) "Operations dashboard"))
          (is (= 303 (:status created)))
          (is (str/includes? (:body list-page) "Browser flow dispute"))
          (is (= 303 (:status assigned)))
          (is (= 303 (:status transitioned)))
          (is (= 303 (:status commented)))
          (is (= 303 (:status attached)))
          (is (str/includes? (:body detail) "investigating"))
          (is (str/includes? (:body detail) "Called the vendor from UI."))
          (is (str/includes? (:body detail) "UI-E2E-MAN-1"))
          (is (str/includes? (:body counterparties-page) "Browser Flow Vendor"))
          (is (str/includes? (:body counterparty-detail) "Counterparty history"))))
      (finally
        (server/stop! srv)))))

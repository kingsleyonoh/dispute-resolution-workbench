(ns ^:e2e drw.e2e-api.ui-ingestion-flow-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
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

(defn- json-string [body key]
  (second (re-find (re-pattern (str "\"" key "\"\\s*:\\s*\"([^\"]+)\""))
                   body)))

(deftest operator-manages-ingestion-settings-through-ui-over-http
  (let [port 31557
        base (str "http://127.0.0.1:" port)
        srv (server/start!
             {:port port
              :api-key-prefix "drw_live_"
              :self-registration-enabled true
              :invoice-recon-enabled true
              :invoice-recon-url "https://invoice.example.invalid"
              :invoice-recon-api-key "example_key"
              :invoice-recon-poll-interval-seconds 600
              :ingestion-http-send-fns
              {:invoice-recon
               (fn [_]
                 {:status 200
                  :body {:discrepancies
                         [{:invoice_id "INV-UI-PULL"
                           :vendor_id "vendor-7"
                           :discrepancy_amount_cents 4500
                           :currency "EUR"
                           :observed_at
                           #inst "2026-05-05T10:00:00.000-00:00"}]
                         :next_cursor "cursor-ui"}})}
              :ingestion-max-attempts 1})]
    (try
      (store/reset-store!)
      (domain-state/reset-store!)
      (ratelimit/reset-limits!)
      (let [registered (request "POST" (str base "/api/tenants/register")
                                "{\"name\":\"Ingestion UI\"}"
                                {"Content-Type" "application/json"})
            api-key (json-string (:body registered) "apiKey")
            settings (request "GET" (str base "/settings/ingestion")
                              nil {"X-API-Key" api-key})
            source-id (second (re-find #"/settings/ingestion/([^/]+)/pull-now"
                                       (:body settings)))
            disabled (form (str base "/settings/ingestion")
                           {:source_system "invoice-recon"
                            :base_url "https://invoice.example.invalid"
                            :poll_interval_seconds "600"}
                           api-key)
            disabled-run (form
                          (str base "/settings/ingestion/" source-id
                               "/pull-now")
                          {} api-key)
            enabled (form (str base "/settings/ingestion")
                          {:source_system "invoice-recon"
                           :is_enabled "true"
                           :base_url "https://invoice.example.invalid"
                           :poll_interval_seconds "600"}
                          api-key)
            pulled (form
                    (str base "/settings/ingestion/" source-id "/pull-now")
                    {} api-key)
            after (request "GET" (str base "/settings/ingestion")
                           nil {"X-API-Key" api-key})]
        (is (= 200 (:status settings)))
        (is (str/includes? (:body settings) "Ingestion settings"))
        (is (= 303 (:status disabled)))
        (is (= 303 (:status disabled-run)))
        (is (= 303 (:status enabled)))
        (is (= 303 (:status pulled)))
        (is (str/includes? (:body after) "INV-UI-PULL"))
        (is (str/includes? (:body after) "succeeded")))
      (finally
        (server/stop! srv)))))

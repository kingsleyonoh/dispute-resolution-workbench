(ns ^:e2e drw.e2e-api.ingestion-endpoints-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.state :as domain-state]
            [drw.http.interceptors.ratelimit :as ratelimit]
            [drw.http.server :as server]
            [drw.tenants.store :as store])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]))

(defn- request
  ([method url] (request method url nil nil))
  ([method url body headers]
   (let [builder (-> (HttpRequest/newBuilder)
                     (.uri (URI/create url)))
         builder (reduce (fn [b [k v]] (.header b k v))
                         builder
                         (or headers {}))
         publisher (if body
                     (HttpRequest$BodyPublishers/ofString body)
                     (HttpRequest$BodyPublishers/noBody))]
     (-> (HttpClient/newHttpClient)
         (.send (-> builder
                    (.method method publisher)
                    .build)
                (HttpResponse$BodyHandlers/ofString))))))

(defn- json-string [body key]
  (second (re-find (re-pattern (str "\"" key "\"\\s*:\\s*\"([^\"]+)\""))
                   body)))

(deftest ingestion-source-endpoints-work-over-real-http
  (let [port 31556
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
                         [{:invoice_id "INV-E2E-PULL"
                           :vendor_id "vendor-7"
                           :discrepancy_amount_cents 4500
                           :currency "EUR"
                           :observed_at
                           #inst "2026-05-05T10:00:00.000-00:00"}]
                         :next_cursor "cursor-e2e"}})}
              :ingestion-max-attempts 1})]
    (try
      (store/reset-store!)
      (domain-state/reset-store!)
      (ratelimit/reset-limits!)
      (let [tenant-a (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Ingestion A\"}"
                              {"Content-Type" "application/json"})
            tenant-b (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Ingestion B\"}"
                              {"Content-Type" "application/json"})
            key-a (json-string (.body tenant-a) "apiKey")
            key-b (json-string (.body tenant-b) "apiKey")
            listed (request "GET" (str base "/api/ingestion-sources")
                            nil {"X-API-Key" key-a})
            source-id (json-string (.body listed) "id")
            disabled (request
                      "POST" (str base "/api/ingestion-sources")
                      "{\"source_system\":\"invoice-recon\",\"is_enabled\":false}"
                      {"X-API-Key" key-a "Content-Type" "application/json"})
            disabled-run (request
                          "POST"
                          (str base "/api/ingestion-sources/" source-id
                               "/pull-now")
                          nil {"X-API-Key" key-a})
            enabled (request
                     "POST" (str base "/api/ingestion-sources")
                     "{\"source_system\":\"invoice-recon\",\"is_enabled\":true}"
                     {"X-API-Key" key-a "Content-Type" "application/json"})
            pulled (request
                    "POST"
                    (str base "/api/ingestion-sources/" source-id "/pull-now")
                    nil {"X-API-Key" key-a})
            cross (request
                   "POST"
                   (str base "/api/ingestion-sources/" source-id "/pull-now")
                   nil {"X-API-Key" key-b})
            runs (request "GET" (str base "/api/ingestion-runs")
                          nil {"X-API-Key" key-a})]
        (is (= 200 (.statusCode listed)))
        (is (str/includes? (.body listed) "\"sourceSystem\":\"invoice-recon\""))
        (is (= 200 (.statusCode disabled)))
        (is (= 201 (.statusCode disabled-run)))
        (is (str/includes? (.body disabled-run) "\"status\":\"disabled\""))
        (is (= 200 (.statusCode enabled)))
        (is (= 201 (.statusCode pulled)))
        (is (str/includes? (.body pulled) "\"status\":\"succeeded\""))
        (is (= 404 (.statusCode cross)))
        (is (= 200 (.statusCode runs)))
        (is (str/includes? (.body runs) "INV-E2E-PULL")))
      (finally
        (server/stop! srv)))))

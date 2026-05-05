(ns ^:e2e drw.e2e-api.workbench-endpoints-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
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

(defn- seed-correlation! [tenant-id]
  (let [cp (counterparties/create!
            {:tenant-id tenant-id
             :canonical-name "HTTP Correlation Vendor"
             :kind :vendor}
            {:actor-kind :user :actor-id "e2e"})
        dispute (disputes/create-dispute!
                 {:tenant-id tenant-id
                  :title "HTTP existing dispute"
                  :description "Existing open item."
                  :category :billing
                  :severity :medium
                  :currency "EUR"
                  :counterparty-id (:counterparty/id cp)
                  :created-by :user
                  :created-at #inst "2026-05-05T09:00:00.000-00:00"}
                 {:actor-kind :user :actor-id "e2e"})
        result (exceptions/ingest!
                {:tenant-id tenant-id
                 :source-system :invoice-recon
                 :source-ref "HTTP-CORR-1"
                 :kind :invoice-discrepancy
                 :currency "EUR"
                 :counterparty-id (:counterparty/id cp)
                 :observed-at #inst "2026-05-05T10:00:00.000-00:00"
                 :monetary-impact-cents 0}
                {:actor-kind :adapter :actor-id "invoice-recon"})]
    {:dispute dispute
     :correlation (first (:correlations result))}))

(defn- start-test-server [port]
  (store/reset-store!)
  (domain-state/reset-store!)
  (ratelimit/reset-limits!)
  (server/start! {:port port
                  :api-key-prefix "drw_live_"
                  :self-registration-enabled true}))

(deftest workbench-endpoints-work-over-real-http-and-enforce-tenant-scope
  (let [port 31552
        base (str "http://127.0.0.1:" port)
        srv (start-test-server port)]
    (try
      (let [tenant-a (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Workbench A\"}"
                              {"Content-Type" "application/json"})
            tenant-b (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Workbench B\"}"
                              {"Content-Type" "application/json"})
            key-a (json-string (.body tenant-a) "apiKey")
            key-b (json-string (.body tenant-b) "apiKey")
            tenant-id-a (java.util.UUID/fromString
                         (json-string (.body tenant-a) "id"))
            cp (counterparties/create!
                {:tenant-id tenant-id-a
                 :canonical-name "HTTP Vendor"
                 :kind :vendor}
                {:actor-kind :user :actor-id "e2e"})
            create-dispute (request
                            "POST" (str base "/api/disputes")
                            "{\"title\":\"HTTP dispute\",\"description\":\"Body\",\"category\":\"billing\",\"severity\":\"high\",\"currency\":\"EUR\"}"
                            {"X-API-Key" key-a "Content-Type" "application/json"})
            dispute-id (json-string (.body create-dispute) "id")
            get-dispute (request "GET" (str base "/api/disputes/" dispute-id)
                                 nil {"X-API-Key" key-a})
            cross-get (request "GET" (str base "/api/disputes/" dispute-id)
                               nil {"X-API-Key" key-b})
            assign (request
                    "PATCH" (str base "/api/disputes/" dispute-id "/assign")
                    "{\"user_id\":\"33333333-3333-3333-3333-333333333333\"}"
                    {"X-API-Key" key-a "Content-Type" "application/json"})
            transition (request
                        "PATCH" (str base "/api/disputes/" dispute-id "/transition")
                        "{\"to_status\":\"investigating\"}"
                        {"X-API-Key" key-a "Content-Type" "application/json"})
            comment (request
                     "POST" (str base "/api/disputes/" dispute-id "/comments")
                     "{\"body\":\"HTTP comment\"}"
                     {"X-API-Key" key-a "Content-Type" "application/json"})
            create-exception (request
                              "POST" (str base "/api/exceptions")
                              "{\"source_ref\":\"HTTP-MAN-1\",\"kind\":\"manual\",\"currency\":\"EUR\",\"observed_at\":\"2026-05-05T10:00:00Z\",\"monetary_impact_cents\":700}"
                              {"X-API-Key" key-a "Content-Type" "application/json"})
            exception-id (json-string (.body create-exception) "id")
            attach (request
                    "POST" (str base "/api/disputes/" dispute-id "/attach-exception")
                    (str "{\"exception_id\":\"" exception-id "\"}")
                    {"X-API-Key" key-a "Content-Type" "application/json"})
            list-counterparties (request "GET" (str base "/api/counterparties")
                                         nil {"X-API-Key" key-a})
            get-counterparty (request "GET"
                                      (str base "/api/counterparties/"
                                           (:counterparty/id cp))
                                      nil {"X-API-Key" key-a})]
        (is (= 201 (.statusCode create-dispute)))
        (is (str/includes? (.body create-dispute) "\"status\":\"open\""))
        (is (= 200 (.statusCode get-dispute)))
        (is (= 404 (.statusCode cross-get)))
        (is (= 200 (.statusCode assign)))
        (is (= 200 (.statusCode transition)))
        (is (= 201 (.statusCode comment)))
        (is (= 201 (.statusCode create-exception)))
        (is (= 200 (.statusCode attach)))
        (is (str/includes? (.body attach) "\"monetaryImpactCents\":700"))
        (is (= 200 (.statusCode list-counterparties)))
        (is (str/includes? (.body list-counterparties) "HTTP Vendor"))
        (is (= 200 (.statusCode get-counterparty))))
      (finally
        (server/stop! srv)))))

(deftest correlations-work-over-real-http-and-enforce-tenant-scope
  (let [port 31554
        base (str "http://127.0.0.1:" port)
        srv (start-test-server port)]
    (try
      (let [tenant-a (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Correlation A\"}"
                              {"Content-Type" "application/json"})
            tenant-b (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Correlation B\"}"
                              {"Content-Type" "application/json"})
            key-a (json-string (.body tenant-a) "apiKey")
            key-b (json-string (.body tenant-b) "apiKey")
            tenant-id-a (java.util.UUID/fromString
                         (json-string (.body tenant-a) "id"))
            seed (seed-correlation! tenant-id-a)
            correlation-id (str (get-in seed [:correlation :correlation/id]))
            listed (request "GET" (str base "/api/correlations?status=pending")
                            nil {"X-API-Key" key-a})
            detail (request "GET" (str base "/api/correlations/" correlation-id)
                            nil {"X-API-Key" key-a})
            cross (request "GET" (str base "/api/correlations/" correlation-id)
                           nil {"X-API-Key" key-b})
            accepted (request "POST"
                              (str base "/api/correlations/"
                                   correlation-id "/accept")
                              nil {"X-API-Key" key-a})
            accepted-again (request "POST"
                                    (str base "/api/correlations/"
                                         correlation-id "/accept")
                                    nil {"X-API-Key" key-a})]
        (is (= 200 (.statusCode listed)))
        (is (str/includes? (.body listed) "HTTP-CORR-1"))
        (is (= 200 (.statusCode detail)))
        (is (= 404 (.statusCode cross)))
        (is (= 200 (.statusCode accepted)))
        (is (str/includes? (.body accepted) "\"status\":\"accepted\""))
        (is (= 422 (.statusCode accepted-again))))
      (finally
        (server/stop! srv)))))

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

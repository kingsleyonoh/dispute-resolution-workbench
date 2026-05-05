(ns ^:e2e drw.e2e-api.hub-exception-ingress-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as domain-state]
            [drw.http.interceptors.ratelimit :as ratelimit]
            [drw.http.server :as server]
            [drw.tenants.store :as store])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(def secret "dummy_hub_ingress_secret")

(defn- hex [bytes]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)))

(defn- sign [body]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (.init mac (SecretKeySpec. (.getBytes secret "UTF-8") "HmacSHA256"))
    (str "sha256=" (hex (.doFinal mac (.getBytes body "UTF-8"))))))

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

(defn- start-test-server [port]
  (store/reset-store!)
  (domain-state/reset-store!)
  (ratelimit/reset-limits!)
  (server/start! {:port port
                  :api-key-prefix "drw_live_"
                  :self-registration-enabled true
                  :hub-ingress-secret secret}))

(def body
  (str "{\"source_ref\":\"HUB-E2E-1\","
       "\"source_system\":\"invoice-recon\","
       "\"kind\":\"invoice-discrepancy\","
       "\"currency\":\"EUR\","
       "\"observed_at\":\"2026-05-05T10:00:00Z\","
       "\"monetary_impact_cents\":3400}"))

(deftest hub-exception-ingress-works-over-real-http-without-api-key-auth
  (let [port 31558
        base (str "http://127.0.0.1:" port)
        srv (start-test-server port)]
    (try
      (let [tenant (request "POST" (str base "/api/tenants/register")
                            "{\"name\":\"Hub Tenant\"}"
                            {"Content-Type" "application/json"})
            tenant-id (java.util.UUID/fromString
                       (json-string (.body tenant) "id"))
            tenant-slug (json-string (.body tenant) "slug")
            valid (request
                   "POST" (str base "/api/exceptions/from-hub")
                   body
                   {"Content-Type" "application/json"
                    "X-Hub-Signature-256" (sign body)
                    "X-Hub-Tenant-Slug" tenant-slug})
            missing-hmac (request
                          "POST" (str base "/api/exceptions/from-hub")
                          body
                          {"Content-Type" "application/json"
                           "X-API-Key" "drw_live_not_a_hub_secret"
                           "X-Hub-Tenant-Slug" tenant-slug})
            stored (exceptions/list-by-tenant
                    tenant-id
                    {:source-system :invoice-recon})]
        (is (= 201 (.statusCode valid)))
        (is (str/includes? (.body valid) "\"sourceRef\":\"HUB-E2E-1\""))
        (is (= 1 (count stored)))
        (is (= 401 (.statusCode missing-hmac)))
        (is (str/includes? (.body missing-hmac) "HUB_SIGNATURE_REQUIRED")))
      (finally
        (server/stop! srv)))))

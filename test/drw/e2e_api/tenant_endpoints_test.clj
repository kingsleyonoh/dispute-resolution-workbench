(ns ^:e2e drw.e2e-api.tenant-endpoints-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
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

(defn- start-test-server [port]
  (store/reset-store!)
  (ratelimit/reset-limits!)
  (server/start! {:port port
                  :api-key-prefix "drw_live_"
                  :self-registration-enabled true}))

(deftest tenant-registration-profile-and-rotation-work-over-real-http
  (let [port 31550
        base (str "http://127.0.0.1:" port)
        srv (start-test-server port)]
    (try
      (let [registered (request "POST" (str base "/api/tenants/register")
                                "{\"name\":\"Tenant Alpha\",\"legal_name\":\"Tenant Alpha LLC\"}"
                                {"Content-Type" "application/json"})
            body (.body registered)
            api-key (json-string body "apiKey")
            tenant-id (json-string body "id")
            profile (request "GET" (str base "/api/tenants/me")
                             nil {"X-API-Key" api-key})
            alias-profile (request "GET" (str base "/tenants/me")
                                   nil {"X-API-Key" api-key})
            rotated (request "POST" (str base "/api/tenants/rotate-key")
                             nil {"X-API-Key" api-key})
            new-api-key (json-string (.body rotated) "apiKey")
            old-key-profile (request "GET" (str base "/api/tenants/me")
                                     nil {"X-API-Key" api-key})
            new-key-profile (request "GET" (str base "/api/tenants/me")
                                     nil {"X-API-Key" new-api-key})]
        (is (= 201 (.statusCode registered)))
        (is (str/starts-with? api-key "drw_live_"))
        (is (str/includes? body "\"name\":\"Tenant Alpha\""))
        (is (not (str/includes? body "api-key-hash")))
        (is (= 200 (.statusCode profile)))
        (is (str/includes? (.body profile) tenant-id))
        (is (= (.body profile) (.body alias-profile)))
        (is (= 200 (.statusCode rotated)))
        (is (not= api-key new-api-key))
        (is (= 401 (.statusCode old-key-profile)))
        (is (= 200 (.statusCode new-key-profile))))
      (finally
        (server/stop! srv)))))

(deftest tenant-auth-boundaries-return-401-403-and-tenant-scoped-profile
  (let [port 31551
        base (str "http://127.0.0.1:" port)
        srv (start-test-server port)]
    (try
      (let [tenant-a (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Tenant A\"}"
                              {"Content-Type" "application/json"})
            tenant-b (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Tenant B\"}"
                              {"Content-Type" "application/json"})
            key-a (json-string (.body tenant-a) "apiKey")
            key-b (json-string (.body tenant-b) "apiKey")
            id-b (json-string (.body tenant-b) "id")
            missing-key (request "GET" (str base "/api/tenants/me"))
            invalid-key (request "GET" (str base "/api/tenants/me")
                                 nil {"X-API-Key" "drw_live_invalid"})
            forced-b (request "GET" (str base "/api/tenants/me?id=" id-b)
                              nil {"X-API-Key" key-a})
            profile-b (request "GET" (str base "/api/tenants/me")
                               nil {"X-API-Key" key-b})]
        (is (= 401 (.statusCode missing-key)))
        (is (str/includes? (.body missing-key) "UNAUTHORIZED"))
        (is (= 401 (.statusCode invalid-key)))
        (is (= 200 (.statusCode forced-b)))
        (is (str/includes? (.body forced-b) "\"name\":\"Tenant A\""))
        (is (not (str/includes? (.body forced-b) id-b)))
        (is (str/includes? (.body profile-b) id-b))
        (let [tenant-b-id (java.util.UUID/fromString id-b)
              disabled (do (store/disable-tenant! tenant-b-id)
                           (request "GET" (str base "/api/tenants/me")
                                    nil {"X-API-Key" key-b}))]
          (is (= 403 (.statusCode disabled)))
          (is (str/includes? (.body disabled) "TENANT_DISABLED"))))
      (finally
        (server/stop! srv)))))

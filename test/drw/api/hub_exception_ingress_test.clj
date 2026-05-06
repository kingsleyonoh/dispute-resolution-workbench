(ns drw.api.hub-exception-ingress-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.api.exceptions :as api-exceptions]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.tenants.store :as store])
  (:import [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (get tenants "acme-gmbh-de"))
(def tenant-b (get tenants "globex-inc-us"))
(def secret "dummy_hub_ingress_secret")
(def timestamp "1778061600")
(def now-ms 1778061600000)

(defn- hex [bytes]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)))

(defn- sign [delivery-id body]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (.init mac (SecretKeySpec. (.getBytes secret "UTF-8") "HmacSHA256"))
    (str "sha256="
         (hex (.doFinal mac (.getBytes
                             (str timestamp "." delivery-id "." body)
                             "UTF-8"))))))

(defn- hub-headers [tenant delivery-id body]
  {"x-hub-signature-256" (sign delivery-id body)
   "x-hub-tenant-slug" (:tenant/slug tenant)
   "x-hub-timestamp" timestamp
   "x-hub-delivery-id" delivery-id})

(defn- reset-state! []
  (store/reset-store!)
  (api-exceptions/reset-hub-deliveries!)
  (state/reset-store!))

(defn- req [body headers]
  {:body body
   :headers headers
   :public-route? true})

(def body
  (str "{\"source_ref\":\"HUB-INV-1\","
       "\"source_system\":\"invoice-recon\","
       "\"kind\":\"invoice-discrepancy\","
       "\"currency\":\"EUR\","
       "\"observed_at\":\"2026-05-05T10:00:00Z\","
       "\"monetary_impact_cents\":1200}"))

(deftest hub-ingress-verifies-hmac-and-ingests-through-domain-pipeline
  (reset-state!)
  (let [handler (api-exceptions/from-hub-handler
                 {:hub-ingress-secret secret :hub-now-ms now-ms})
        response (handler
                  (req body
                       (hub-headers tenant-a "delivery-1" body)))
        stored-a (exceptions/list-by-tenant
                  (:tenant/id tenant-a)
                  {:source-system :invoice-recon})
        stored-b (exceptions/list-by-tenant
                  (:tenant/id tenant-b)
                  {:source-system :invoice-recon})]
    (is (= 201 (:status response)))
    (is (str/includes? (:body response) "\"sourceRef\":\"HUB-INV-1\""))
    (is (str/includes? (:body response) "\"status\":\"dispute-created\""))
    (is (= 1 (count stored-a)))
    (is (= 0 (count stored-b)))
    (is (some? (:exception/dispute-id (first stored-a))))))

(deftest hub-ingress-rejects-auth-tenant-and-payload-failures
  (reset-state!)
  (let [handler (api-exceptions/from-hub-handler
                 {:hub-ingress-secret secret :hub-now-ms now-ms})
        missing-signature (handler
                           (req body (dissoc (hub-headers tenant-a
                                                          "delivery-missing"
                                                          body)
                                             "x-hub-signature-256")))
        invalid-signature (handler
                           (req body
                                {"x-hub-signature-256" "sha256=bad"
                                 "x-hub-tenant-slug"
                                 (:tenant/slug tenant-a)
                                 "x-hub-timestamp" timestamp
                                 "x-hub-delivery-id" "delivery-bad"}))
        missing-tenant (handler
                        (req body (dissoc (hub-headers tenant-a
                                                       "delivery-no-tenant"
                                                       body)
                                          "x-hub-tenant-slug")))
        unknown-tenant (handler
                        (req body
                             (assoc (hub-headers tenant-a
                                                 "delivery-unknown"
                                                 body)
                                    "x-hub-tenant-slug" "missing-tenant")))
        disabled-tenant (do
                          (store/disable-tenant! (:tenant/id tenant-b))
                          (handler
                           (req body
                                (hub-headers tenant-b "delivery-disabled" body))))
        malformed (let [bad-body "{not-json"]
                    (handler
                     (req bad-body
                          (hub-headers tenant-a "delivery-malformed" bad-body))))
        unsupported (let [bad-body (str/replace body "invoice-recon"
                                                "unknown-system")]
                      (handler
                       (req bad-body
                            (hub-headers tenant-a "delivery-system" bad-body))))
        unsupported-kind (let [bad-body (str/replace body
                                                     "invoice-discrepancy"
                                                     "unknown-kind")]
                           (handler
                            (req bad-body
                                 (hub-headers tenant-a "delivery-kind" bad-body))))
        duplicate-first (handler
                         (req body
                              (hub-headers tenant-a "delivery-duplicate" body)))
        duplicate-second (handler
                          (req body
                               (hub-headers tenant-a "delivery-duplicate" body)))
        stale (handler
               (req body
                    {"x-hub-signature-256" (sign "delivery-stale" body)
                     "x-hub-tenant-slug" (:tenant/slug tenant-a)
                     "x-hub-timestamp" "1"
                     "x-hub-delivery-id" "delivery-stale"}))]
    (is (= 401 (:status missing-signature)))
    (is (= 401 (:status invalid-signature)))
    (is (= 400 (:status missing-tenant)))
    (is (= 404 (:status unknown-tenant)))
    (is (= 403 (:status disabled-tenant)))
    (is (= 400 (:status malformed)))
    (is (= 400 (:status unsupported)))
    (is (= 400 (:status unsupported-kind)))
    (is (= 201 (:status duplicate-first)))
    (is (= 200 (:status duplicate-second)))
    (is (= 401 (:status stale)))))

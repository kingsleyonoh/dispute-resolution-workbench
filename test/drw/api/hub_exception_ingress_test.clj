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

(defn- hex [bytes]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)))

(defn- sign [body]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (.init mac (SecretKeySpec. (.getBytes secret "UTF-8") "HmacSHA256"))
    (str "sha256=" (hex (.doFinal mac (.getBytes body "UTF-8"))))))

(defn- reset-state! []
  (store/reset-store!)
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
                 {:hub-ingress-secret secret})
        response (handler
                  (req body
                       {"x-hub-signature-256" (sign body)
                        "x-hub-tenant-slug" (:tenant/slug tenant-a)}))
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
                 {:hub-ingress-secret secret})
        missing-signature (handler
                           (req body {"x-hub-tenant-slug"
                                      (:tenant/slug tenant-a)}))
        invalid-signature (handler
                           (req body
                                {"x-hub-signature-256" "sha256=bad"
                                 "x-hub-tenant-slug"
                                 (:tenant/slug tenant-a)}))
        missing-tenant (handler
                        (req body {"x-hub-signature-256" (sign body)}))
        unknown-tenant (handler
                        (req body
                             {"x-hub-signature-256" (sign body)
                              "x-hub-tenant-slug" "missing-tenant"}))
        disabled-tenant (do
                          (store/disable-tenant! (:tenant/id tenant-b))
                          (handler
                           (req body
                                {"x-hub-signature-256" (sign body)
                                 "x-hub-tenant-slug"
                                 (:tenant/slug tenant-b)})))
        malformed (let [bad-body "{not-json"]
                    (handler
                     (req bad-body
                          {"x-hub-signature-256" (sign bad-body)
                           "x-hub-tenant-slug" (:tenant/slug tenant-a)})))
        unsupported (let [bad-body (str/replace body "invoice-recon"
                                                "unknown-system")]
                      (handler
                       (req bad-body
                            {"x-hub-signature-256" (sign bad-body)
                             "x-hub-tenant-slug" (:tenant/slug tenant-a)})))
        unsupported-kind (let [bad-body (str/replace body
                                                     "invoice-discrepancy"
                                                     "unknown-kind")]
                           (handler
                            (req bad-body
                                 {"x-hub-signature-256" (sign bad-body)
                                  "x-hub-tenant-slug"
                                  (:tenant/slug tenant-a)})))
        duplicate-first (handler
                         (req body
                              {"x-hub-signature-256" (sign body)
                               "x-hub-tenant-slug" (:tenant/slug tenant-a)}))
        duplicate-second (handler
                          (req body
                               {"x-hub-signature-256" (sign body)
                                "x-hub-tenant-slug" (:tenant/slug tenant-a)}))]
    (is (= 401 (:status missing-signature)))
    (is (= 401 (:status invalid-signature)))
    (is (= 400 (:status missing-tenant)))
    (is (= 404 (:status unknown-tenant)))
    (is (= 403 (:status disabled-tenant)))
    (is (= 400 (:status malformed)))
    (is (= 400 (:status unsupported)))
    (is (= 400 (:status unsupported-kind)))
    (is (= 201 (:status duplicate-first)))
    (is (= 409 (:status duplicate-second)))))

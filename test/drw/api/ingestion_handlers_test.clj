(ns drw.api.ingestion-handlers-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.api.ingestion :as api-ingestion]
            [drw.domain.ingestion-sources :as ingestion]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))

(def cfg
  {:invoice-recon-enabled true
   :invoice-recon-url "https://invoice.example.invalid"
   :invoice-recon-api-key "example_key"
   :invoice-recon-poll-interval-seconds 600
   :ingestion-http-send-fns
   {:invoice-recon
    (fn [_]
      {:status 200
       :body {:discrepancies [{:invoice_id "INV-API-PULL"
                               :vendor_id "vendor-7"
                               :discrepancy_amount_cents 4500
                               :currency "EUR"
                               :observed_at
                               #inst "2026-05-05T10:00:00.000-00:00"}]
              :next_cursor "cursor-api"}})}
   :ingestion-max-attempts 1})

(defn- req
  ([tenant-id] (req tenant-id nil nil nil))
  ([tenant-id body path-params query-params]
   {:current-tenant {:tenant-id tenant-id :slug "tenant"}
    :body body
    :path-params (or path-params {})
    :query-params (or query-params {})}))

(defn- body-includes? [response value]
  (str/includes? (str (:body response)) value))

(defn- source-id [tenant-id]
  (str (:ingestion-source/id
        (first (filter #(= :invoice-recon
                           (:ingestion-source/source-system %))
                       (ingestion/list-sources tenant-id cfg))))))

(deftest ingestion-source-api-lists-updates-pulls-and-lists-runs
  (state/reset-store!)
  (let [listed ((api-ingestion/list-sources-handler cfg) (req tenant-a))
        id (source-id tenant-a)
        updated ((api-ingestion/save-source-handler cfg)
                 (req tenant-a
                      "{\"source_system\":\"invoice-recon\",\"is_enabled\":false,\"base_url\":\"https://tenant.example\",\"poll_interval_seconds\":120}"
                      nil nil))
        disabled-run ((api-ingestion/pull-now-handler cfg)
                      (req tenant-a nil {:id id} nil))
        enabled ((api-ingestion/save-source-handler cfg)
                 (req tenant-a
                      "{\"source_system\":\"invoice-recon\",\"is_enabled\":true}"
                      nil nil))
        pull ((api-ingestion/pull-now-handler cfg)
              (req tenant-a nil {:id id} nil))
        runs ((api-ingestion/list-runs-handler cfg)
              (req tenant-a nil nil {:source_system "invoice-recon"}))]
    (is (= 200 (:status listed)))
    (is (body-includes? listed "\"sourceSystem\":\"invoice-recon\""))
    (is (= 200 (:status updated)))
    (is (body-includes? updated "\"isEnabled\":false"))
    (is (= 201 (:status disabled-run)))
    (is (body-includes? disabled-run "\"status\":\"disabled\""))
    (is (= 200 (:status enabled)))
    (is (= 201 (:status pull)))
    (is (body-includes? pull "\"status\":\"succeeded\""))
    (is (= 200 (:status runs)))
    (is (body-includes? runs "\"runs\""))))

(deftest ingestion-api-enforces-tenant-isolation-and-validation
  (state/reset-store!)
  (let [id (source-id tenant-a)
        cross ((api-ingestion/pull-now-handler cfg)
               (req tenant-b nil {:id id} nil))
        bad-source ((api-ingestion/save-source-handler cfg)
                    (req tenant-a
                         "{\"source_system\":\"unknown\"}"
                         nil nil))
        bad-id ((api-ingestion/pull-now-handler cfg)
                (req tenant-a nil {:id "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}
                     nil))]
    (is (= 404 (:status cross)))
    (is (= 400 (:status bad-source)))
    (is (= 404 (:status bad-id)))))

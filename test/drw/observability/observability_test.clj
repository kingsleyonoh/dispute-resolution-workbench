(ns drw.observability.observability-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.ingestion-sources :as ingestion]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.http.handlers :as handlers]
            [drw.observability.logging :as logging]
            [drw.observability.sentry :as sentry]))

(def tenant-id (:tenant/id (get (fixtures/tenants-by-slug) "acme-gmbh-de")))

(def cfg
  {:invoice-recon-enabled true
   :invoice-recon-url "https://invoice.example.invalid"
   :invoice-recon-poll-interval-seconds 600
   :transaction-recon-enabled false
   :contract-lifecycle-enabled false
   :webhook-engine-enabled false})

(defn- invoice-source []
  (first (filter #(= :invoice-recon (:ingestion-source/source-system %))
                 (ingestion/list-sources tenant-id cfg))))

(defn- mark-success! [source instant]
  (swap! state/ingestion-sources*
         assoc-in [(:ingestion-source/id source)
                   :ingestion-source/last-successful-pull-at]
         instant))

(deftest readiness-fails-until-enabled-adapters-are-fresh
  (state/reset-store!)
  (let [now #inst "2026-05-06T12:00:00.000-00:00"
        source (invoice-source)
        not-ready (handlers/ready
                   (assoc cfg :tenant-source [(get (fixtures/tenants-by-slug)
                                                   "acme-gmbh-de")])
                   {}
                   {:now now})]
    (is (= 503 (:status not-ready)))
    (mark-success! source #inst "2026-05-06T11:50:00.000-00:00")
    (is (= 200 (:status (handlers/ready
                         (assoc cfg :tenant-source [(get (fixtures/tenants-by-slug)
                                                         "acme-gmbh-de")])
                         {}
                         {:now now}))))))

(deftest metrics-endpoint-emits-prometheus-text
  (state/reset-store!)
  (let [response (handlers/metrics {:prometheus-enabled true} {})]
    (is (= 200 (:status response)))
    (is (= "text/plain; version=0.0.4"
           (get-in response [:headers "Content-Type"])))
    (is (str/includes? (:body response) "drw_disputes_total"))))

(deftest metrics-endpoint-honors-basic-auth-when-configured
  (let [cfg {:prometheus-enabled true
             :metrics-basic-auth-user "metrics"
             :metrics-basic-auth-pass "secret"}
        authorized {"Authorization" "Basic bWV0cmljczpzZWNyZXQ="}]
    (is (= 401 (:status (handlers/metrics cfg {}))))
    (is (= 200 (:status (handlers/metrics cfg {:headers authorized}))))))

(deftest json-logging-and-sentry-use-injected-sinks
  (let [logs (atom [])
        sent (atom [])
        log-line (logging/log-event!
                  {:axiom-dataset "drw-test"
                   :log-sink-fn #(swap! logs conj %)}
                  :info
                  "operator.action"
                  {:tenant_id "tenant-a"})
        capture (sentry/capture-exception!
                 {:sentry-dsn "https://sentry.example.invalid/1"
                  :sentry-capture-fn #(swap! sent conj %)}
                 (ex-info "boom" {:type :test})
                 {:request_id "req-1"})]
    (is (str/includes? log-line "\"dataset\":\"drw-test\""))
    (is (= [log-line] @logs))
    (is (= :captured (:status capture)))
    (is (= "boom" (-> @sent first :exception ex-message)))))

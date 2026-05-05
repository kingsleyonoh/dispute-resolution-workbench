(ns drw.adapters.fetcher-test
  (:require [clojure.test :refer [deftest is]]
            [drw.adapters.fetcher :as fetcher]))

(def base-cfg
  {:enabled? true
   :tenant-id "tenant-a"
   :source-system :invoice-recon
   :base-url "https://invoice.example.invalid"
   :api-key "example_key"
   :max-attempts 3
   :failure-threshold 2
   :backoff-ms 5})

(deftest disabled-adapter-is-side-effect-free
  (let [calls (atom 0)
        result (fetcher/fetch!
                (assoc base-cfg
                       :enabled? false
                       :http-send-fn (fn [_]
                                       (swap! calls inc)))
                {:method :get
                 :path "/api/discrepancies"
                 :query-params {:since "cursor-1"}})]
    (is (= 0 @calls))
    (is (= {:status :disabled
            :error? false
            :fetched? false
            :tenant-id "tenant-a"
            :source-system :invoice-recon}
           result))))

(deftest fetcher-builds-tenant-scoped-request-and-retries-upstream-failures
  (let [requests (atom [])
        sleeps (atom [])
        response (fetcher/fetch!
                  (assoc base-cfg
                         :sleep-fn #(swap! sleeps conj %)
                         :http-send-fn
                         (fn [request]
                           (swap! requests conj request)
                           (if (= 1 (count @requests))
                             {:status 503 :body "busy"}
                             {:status 200 :body [{:id "disc-1"}]})))
                  {:method :get
                   :path "/api/discrepancies"
                   :query-params {:since "cursor-1" :limit 100}})]
    (is (= :ok (:status response)))
    (is (false? (:error? response)))
    (is (= 2 (:attempts response)))
    (is (= [{:id "disc-1"}] (get-in response [:response :body])))
    (is (= [5] @sleeps))
    (is (= "https://invoice.example.invalid/api/discrepancies?since=cursor-1&limit=100"
           (:url (first @requests))))
    (is (= "example_key" (get-in (first @requests) [:headers "X-API-Key"])))
    (is (= "tenant-a" (:tenant-id response)))))

(deftest fetcher-classifies-timeouts-without-throwing-through-job-boundaries
  (let [result (fetcher/fetch!
                (assoc base-cfg
                       :max-attempts 1
                       :http-send-fn
                       (fn [_]
                         (throw (ex-info "timed out"
                                         {:type :adapter/timeout}))))
                {:method :get
                 :path "/api/discrepancies"})]
    (is (= :failed (:status result)))
    (is (= :timeout (:reason result)))
    (is (= 1 (:attempts result)))
    (is (= "tenant-a" (:tenant-id result)))
    (is (= :invoice-recon (:source-system result)))))

(deftest circuit-breaker-opens-per-tenant-and-source-system
  (let [circuit (fetcher/new-circuit)
        failing-cfg (assoc base-cfg
                           :circuit circuit
                           :max-attempts 1
                           :http-send-fn (fn [_] {:status 503}))
        first-failure (fetcher/fetch! failing-cfg {:method :get :path "/api/discrepancies"})
        second-failure (fetcher/fetch! failing-cfg {:method :get :path "/api/discrepancies"})
        short-circuit (fetcher/fetch! failing-cfg {:method :get :path "/api/discrepancies"})
        isolated-tenant (fetcher/fetch!
                         (assoc failing-cfg
                                :tenant-id "tenant-b"
                                :http-send-fn (fn [_] {:status 200 :body []}))
                         {:method :get :path "/api/discrepancies"})]
    (is (= :failed (:status first-failure)))
    (is (= :failed (:status second-failure)))
    (is (= :circuit-open (:status short-circuit)))
    (is (= 0 (:attempts short-circuit)))
    (is (= :ok (:status isolated-tenant)))))

(deftest enabled-adapter-requires-fetcher-configuration
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"Adapter fetcher configuration is incomplete"
       (fetcher/fetch! (dissoc base-cfg :base-url)
                       {:method :get
                        :path "/api/discrepancies"}))))

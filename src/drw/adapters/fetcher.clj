(ns drw.adapters.fetcher
  (:require [clojure.string :as str])
  (:import [java.net URLEncoder]))

(def default-timeout-ms 5000)
(def default-max-attempts 3)
(def default-failure-threshold 3)

(defn new-circuit []
  (atom {}))

(defonce ^:private default-circuit (new-circuit))

(defn- encode [value]
  (URLEncoder/encode (str value) "UTF-8"))

(defn- query-string [query-params]
  (when (seq query-params)
    (str "?"
         (str/join "&"
                   (map (fn [[k v]]
                          (str (encode (name k)) "=" (encode v)))
                        query-params)))))

(defn- endpoint [base-url path query-params]
  (str (str/replace base-url #"/$" "")
       (if (str/starts-with? path "/") path (str "/" path))
       (query-string query-params)))

(defn- missing-config [cfg]
  (cond-> []
    (nil? (:tenant-id cfg)) (conj :tenant-id)
    (nil? (:source-system cfg)) (conj :source-system)
    (str/blank? (:base-url cfg)) (conj :base-url)
    (str/blank? (:api-key cfg)) (conj :api-key)
    (nil? (:http-send-fn cfg)) (conj :http-send-fn)))

(defn- require-config! [cfg]
  (let [missing (missing-config cfg)]
    (when (seq missing)
      (throw (ex-info "Adapter fetcher configuration is incomplete"
                      {:type :adapter/missing-config
                       :missing missing}))))
  cfg)

(defn- disabled-result [cfg]
  {:status :disabled
   :error? false
   :fetched? false
   :tenant-id (:tenant-id cfg)
   :source-system (:source-system cfg)})

(defn- circuit-key [cfg]
  [(:tenant-id cfg) (:source-system cfg)])

(defn- circuit-open? [circuit key]
  (= :open (:status (get @circuit key))))

(defn- mark-success! [circuit key]
  (swap! circuit dissoc key))

(defn- mark-failure! [circuit key threshold]
  (swap! circuit
         (fn [state]
           (let [failures (inc (get-in state [key :failures] 0))]
             (assoc state key {:status (if (>= failures threshold)
                                         :open
                                         :closed)
                               :failures failures})))))

(defn- success-status? [status]
  (and (integer? status) (<= 200 status 299)))

(defn- retryable-status? [status]
  (or (= 408 status)
      (= 429 status)
      (and (integer? status) (<= 500 status))))

(defn- request-map [cfg request]
  {:method (get request :method :get)
   :url (endpoint (:base-url cfg) (:path request) (:query-params request))
   :headers (merge {"Accept" "application/json"
                    "X-API-Key" (:api-key cfg)}
                   (:headers request))
   :timeout-ms (get cfg :timeout-ms default-timeout-ms)
   :tenant-id (:tenant-id cfg)
   :source-system (:source-system cfg)
   :body (:body request)})

(defn- call-transport [send-fn request]
  (try
    {:response (send-fn request)}
    (catch Exception ex
      {:exception ex})))

(defn- exception-reason [ex]
  (if (= :adapter/timeout (:type (ex-data ex)))
    :timeout
    :upstream-exception))

(defn- failure-result [cfg attempts reason data]
  (merge {:status :failed
          :error? true
          :tenant-id (:tenant-id cfg)
          :source-system (:source-system cfg)
          :attempts attempts
          :reason reason}
         data))

(defn- success-result [cfg attempts response]
  {:status :ok
   :error? false
   :tenant-id (:tenant-id cfg)
   :source-system (:source-system cfg)
   :attempts attempts
   :response response})

(defn- sleep-before-retry [cfg attempt]
  (let [backoff-ms (long (get cfg :backoff-ms 0))]
    (when (pos? backoff-ms)
      ((get cfg :sleep-fn Thread/sleep)
       (* backoff-ms (long (Math/pow 2 (dec attempt))))))))

(defn- final-response [cfg circuit key attempts response]
  (mark-failure! circuit key (get cfg :failure-threshold default-failure-threshold))
  (failure-result cfg
                  attempts
                  :upstream-http-error
                  {:upstream-status (:status response)
                   :response response}))

(defn- attempt-fetch [cfg circuit key request]
  (let [max-attempts (get cfg :max-attempts default-max-attempts)
        send-fn (:http-send-fn cfg)]
    (loop [attempt 1]
      (let [{:keys [response exception]} (call-transport send-fn request)]
        (cond
          exception
          (if (< attempt max-attempts)
            (do (sleep-before-retry cfg attempt)
                (recur (inc attempt)))
            (do (mark-failure! circuit key (:failure-threshold cfg))
                (failure-result cfg attempt (exception-reason exception)
                                {:exception-message (ex-message exception)})))

          (success-status? (:status response))
          (do (mark-success! circuit key)
              (success-result cfg attempt response))

          (and (retryable-status? (:status response)) (< attempt max-attempts))
          (do (sleep-before-retry cfg attempt)
              (recur (inc attempt)))

          :else
          (final-response cfg circuit key attempt response))))))

(defn fetch! [cfg request]
  (if-not (:enabled? cfg)
    (disabled-result cfg)
    (let [cfg (require-config! cfg)
          circuit (get cfg :circuit default-circuit)
          key (circuit-key cfg)]
      (if (circuit-open? circuit key)
        {:status :circuit-open
         :error? true
         :tenant-id (:tenant-id cfg)
         :source-system (:source-system cfg)
         :attempts 0}
        (attempt-fetch cfg circuit key (request-map cfg request))))))

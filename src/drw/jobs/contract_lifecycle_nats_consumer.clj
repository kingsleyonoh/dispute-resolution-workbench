(ns drw.jobs.contract-lifecycle-nats-consumer
  (:require [drw.adapters.contract-lifecycle :as contract]
            [drw.adapters.protocol :as adapter]
            [drw.domain.exceptions :as exceptions]
            [drw.ecosystem.nats-connection :as nats]))

(def subjects
  ["contract.obligation.breached" "contract.conflict.detected"])

(defn- duplicate? [ex]
  (= :exception/duplicate-source-ref (:type (ex-data ex))))

(defn- store-one! [normalized actor ingestion-opts]
  (try
    (exceptions/ingest! normalized actor ingestion-opts)
    :stored
    (catch clojure.lang.ExceptionInfo ex
      (if (duplicate? ex) :skipped :rejected))))

(defn handle-message!
  ([tenant-config message]
   (handle-message! tenant-config message
                    {:actor-kind :adapter
                     :actor-id "contract-lifecycle-nats"}))
  ([tenant-config message actor]
   (try
     (let [normalized (adapter/parse-webhook
                       contract/adapter
                       tenant-config
                       (:payload message)
                       {:subject (:subject message)})
           status (store-one! normalized actor (:ingestion tenant-config {}))]
       {:status status :source-ref (:source-ref normalized)})
     (catch clojure.lang.ExceptionInfo ex
       {:status :rejected
        :error (ex-data ex)}))))

(defn- nats-config [cfg opts]
  (merge {:nats-enabled (and (:contract-lifecycle-enabled cfg)
                             (:nats-enabled cfg))
          :nats-url (:nats-url cfg)
          :nats-creds-path (:nats-creds-path cfg)
          :nats-stream-name (:nats-stream-name cfg)}
         (select-keys opts [:nats-connect-fn])))

(defn- result-counts [results]
  (frequencies (map :status @results)))

(defn- subscribe-subject! [conn tenant-config results actor subject]
  (nats/subscribe!
   conn
   subject
   (fn [message]
     (swap! results conj
            (handle-message! tenant-config
                             (assoc message :subject subject)
                             actor)))
   {:stream-name (:stream-name conn)
    :durable-name "drw-contract-lifecycle"}))

(defn run-once! [cfg opts]
  (let [conn (nats/connect! (nats-config cfg opts))]
    (if (= :disabled (:status conn))
      {:tenant-id (:tenant-id opts)
       :source-system :contract-lifecycle
       :status :disabled
       :subscriptions 0
       :exceptions-stored 0
       :exceptions-skipped 0
       :exceptions-rejected 0}
      (let [results (atom [])
            tenant-config {:tenant-id (:tenant-id opts)}
            actor {:actor-kind :adapter
                   :actor-id "contract-lifecycle-nats"}]
        (doseq [subject subjects]
          (subscribe-subject! conn tenant-config results actor subject))
        (let [counts (result-counts results)]
          {:tenant-id (:tenant-id opts)
           :source-system :contract-lifecycle
           :status :subscribed
           :subscriptions (count subjects)
           :exceptions-stored (get counts :stored 0)
           :exceptions-skipped (get counts :skipped 0)
           :exceptions-rejected (get counts :rejected 0)})))))

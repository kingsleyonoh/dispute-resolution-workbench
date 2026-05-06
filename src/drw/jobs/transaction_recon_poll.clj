(ns drw.jobs.transaction-recon-poll
  (:require [drw.adapters.transaction-recon :as transaction-recon]
            [drw.jobs.adapter-poll :as adapter-poll]))

(def transport-keys
  [:http-send-fn :circuit :max-attempts :failure-threshold :timeout-ms
   :backoff-ms :sleep-fn])

(defn- tenant-config [cfg opts]
  (merge {:enabled? (:transaction-recon-enabled cfg)
          :tenant-id (:tenant-id opts)
          :base-url (:transaction-recon-url cfg)
          :api-key (:transaction-recon-api-key cfg)}
         (select-keys opts transport-keys)))

(defn run-once! [cfg opts]
  (adapter-poll/run-once!
   transaction-recon/adapter
   (tenant-config cfg opts)
   (assoc opts
          :actor {:actor-kind :adapter
                  :actor-id "transaction-recon-poll"})))

(ns drw.jobs.contract-lifecycle-backfill
  (:require [drw.adapters.contract-lifecycle :as contract]
            [drw.jobs.adapter-poll :as adapter-poll]))

(def transport-keys
  [:http-send-fn :circuit :max-attempts :failure-threshold :timeout-ms
   :backoff-ms :sleep-fn])

(defn- tenant-config [cfg opts]
  (merge {:enabled? (:contract-lifecycle-enabled cfg)
          :tenant-id (:tenant-id opts)
          :base-url (:contract-lifecycle-url cfg)
          :api-key (:contract-lifecycle-api-key cfg)}
         (select-keys opts transport-keys)))

(defn run-once! [cfg opts]
  (adapter-poll/run-once!
   contract/adapter
   (tenant-config cfg opts)
   (assoc opts
          :actor {:actor-kind :adapter
                  :actor-id "contract-lifecycle-backfill"})))

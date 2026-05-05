(ns drw.jobs.webhook-engine-dlq-poll
  (:require [drw.adapters.webhook-engine :as webhook-engine]
            [drw.jobs.adapter-poll :as adapter-poll]))

(def transport-keys
  [:http-send-fn :circuit :max-attempts :failure-threshold :timeout-ms
   :backoff-ms :sleep-fn])

(defn- tenant-config [cfg opts]
  (merge {:enabled? (:webhook-engine-enabled cfg)
          :tenant-id (:tenant-id opts)
          :base-url (:webhook-engine-url cfg)
          :api-key (:webhook-engine-api-key cfg)}
         (select-keys opts transport-keys)))

(defn run-once! [cfg opts]
  (adapter-poll/run-once!
   webhook-engine/adapter
   (tenant-config cfg opts)
   (assoc opts
          :actor {:actor-kind :adapter
                  :actor-id "webhook-engine-dlq-poll"})))

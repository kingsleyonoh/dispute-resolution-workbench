(ns drw.jobs.invoice-recon-poll
  (:require [drw.adapters.invoice-recon :as invoice-recon]
            [drw.jobs.adapter-poll :as adapter-poll]))

(def transport-keys
  [:http-send-fn :circuit :max-attempts :failure-threshold :timeout-ms
   :backoff-ms :sleep-fn])

(defn- tenant-config [cfg opts]
  (merge {:enabled? (:invoice-recon-enabled cfg)
          :tenant-id (:tenant-id opts)
          :base-url (:invoice-recon-url cfg)
          :api-key (:invoice-recon-api-key cfg)}
         (select-keys opts transport-keys)))

(defn run-once! [cfg opts]
  (adapter-poll/run-once!
   invoice-recon/adapter
   (tenant-config cfg opts)
   (assoc opts
          :actor {:actor-kind :adapter
                  :actor-id "invoice-recon-poll"})))

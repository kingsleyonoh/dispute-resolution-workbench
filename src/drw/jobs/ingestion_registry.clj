(ns drw.jobs.ingestion-registry
  (:require [drw.jobs.contract-lifecycle-backfill :as contract-poll]
            [drw.jobs.invoice-recon-poll :as invoice-poll]
            [drw.jobs.transaction-recon-poll :as transaction-poll]
            [drw.jobs.webhook-engine-dlq-poll :as webhook-poll]))

(def source-registry
  {:invoice-recon {:run-fn invoice-poll/run-once!}
   :transaction-recon {:run-fn transaction-poll/run-once!}
   :contract-lifecycle {:run-fn contract-poll/run-once!}
   :webhook-engine {:run-fn webhook-poll/run-once!}})

(defn with-source-registry [cfg]
  (assoc cfg :ingestion-source-registry source-registry))

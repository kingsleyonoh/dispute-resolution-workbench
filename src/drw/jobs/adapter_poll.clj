(ns drw.jobs.adapter-poll
  (:require [drw.adapters.protocol :as adapter]
            [drw.domain.exceptions :as exceptions]))

(defn- duplicate? [ex]
  (= :exception/duplicate-source-ref (:type (ex-data ex))))

(defn- store-one! [normalized actor ingestion-opts]
  (try
    (exceptions/ingest! normalized actor ingestion-opts)
    :stored
    (catch clojure.lang.ExceptionInfo ex
      (if (duplicate? ex) :skipped :rejected))))

(defn- store-exceptions! [items actor ingestion-opts]
  (frequencies (map #(store-one! % actor ingestion-opts) items)))

(defn- run-status [poll-result]
  (if (:disabled? poll-result) :disabled :succeeded))

(defn- base-run [poll-result status counts]
  {:tenant-id (:tenant-id poll-result)
   :source-system (:source-system poll-result)
   :status status
   :exceptions-attempted (count (:exceptions poll-result))
   :exceptions-stored (get counts :stored 0)
   :exceptions-skipped (get counts :skipped 0)
   :exceptions-rejected (get counts :rejected 0)
   :cursor (:cursor poll-result)
   :error nil})

(defn run-once! [adapter tenant-config opts]
  (let [poll-result (adapter/poll! adapter tenant-config (:cursor opts))]
    (if (:error? poll-result)
      {:tenant-id (:tenant-id poll-result)
       :source-system (:source-system poll-result)
       :status :failed
       :exceptions-attempted 0
       :exceptions-stored 0
       :exceptions-skipped 0
       :exceptions-rejected 0
       :cursor (:cursor opts)
       :error (:error poll-result)}
      (let [counts (store-exceptions!
                    (:exceptions poll-result)
                    (:actor opts {:actor-kind :adapter
                                  :actor-id (name (:source-system
                                                   poll-result))})
                    (:ingestion opts {}))]
        (base-run poll-result (run-status poll-result) counts)))))

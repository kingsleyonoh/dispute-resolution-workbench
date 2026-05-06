(ns drw.domain.ingestion-sources
  (:require [drw.domain.ingestion-url :as ingestion-url]
            [drw.domain.state :as state])
  (:import [java.time Instant]
           [java.util UUID]))

(def default-source-registry
  {:invoice-recon
   {:display-name "Invoice Reconciliation"
    :enabled-key :invoice-recon-enabled
    :url-key :invoice-recon-url
    :api-key-secret-ref "INVOICE_RECON_API_KEY"
    :interval-key :invoice-recon-poll-interval-seconds
    :default-interval 600}
   :transaction-recon
   {:display-name "Transaction Reconciliation"
    :enabled-key :transaction-recon-enabled
    :url-key :transaction-recon-url
    :api-key-secret-ref "TRANSACTION_RECON_API_KEY"
    :interval-key :transaction-recon-poll-interval-seconds
    :default-interval 900}
   :contract-lifecycle
   {:display-name "Contract Lifecycle"
    :enabled-key :contract-lifecycle-enabled
    :url-key :contract-lifecycle-url
    :api-key-secret-ref "CONTRACT_LIFECYCLE_API_KEY"
    :interval-key :contract-lifecycle-backfill-interval-seconds
    :default-interval 900}
   :webhook-engine
   {:display-name "Webhook Engine"
    :enabled-key :webhook-engine-enabled
    :url-key :webhook-engine-url
    :api-key-secret-ref "WEBHOOK_ENGINE_API_KEY"
    :interval-key :webhook-engine-dlq-poll-interval-seconds
    :default-interval 1800}})

(defn source-registry [cfg]
  (merge-with merge
              default-source-registry
              (:ingestion-source-registry cfg)))

(defn- now-date []
  (java.util.Date/from (Instant/now)))

(defn- reject! [message data]
  (throw (ex-info message data)))

(defn- require-source-system! [registry source-system]
  (when-not (contains? registry source-system)
    (reject! "source-system is invalid"
             {:type :validation-error :field :source-system}))
  source-system)

(defn- existing-source [tenant-id source-system]
  (some #(when (and (= tenant-id (:ingestion-source/tenant-id %))
                    (= source-system (:ingestion-source/source-system %)))
           %)
        (vals @state/ingestion-sources*)))

(defn- source-config [cfg spec]
  {:base-url (get cfg (:url-key spec))
   :api-key-secret-ref (:api-key-secret-ref spec)
   :poll-interval-seconds (get cfg (:interval-key spec)
                               (:default-interval spec))
   :filters {}})

(defn- validate-base-url! [source base-url cfg]
  (let [spec (get (source-registry cfg)
                  (:ingestion-source/source-system source))
        configured-host (ingestion-url/uri-host (get cfg (:url-key spec)))
        requested-host (ingestion-url/uri-host base-url)]
    (when (ingestion-url/private-host? requested-host)
      (reject! "base-url host is not allowed"
               {:type :validation-error :field :base-url}))
    (when (and configured-host (not= configured-host requested-host))
      (reject! "base-url host must match configured adapter host"
               {:type :validation-error :field :base-url}))))

(defn- default-source [tenant-id source-system cfg]
  (let [spec (get (source-registry cfg) source-system)]
    {:ingestion-source/id (UUID/randomUUID)
     :ingestion-source/tenant-id tenant-id
     :ingestion-source/source-system source-system
     :ingestion-source/display-name (:display-name spec)
     :ingestion-source/is-enabled (true? (get cfg (:enabled-key spec)))
     :ingestion-source/config (source-config cfg spec)
     :ingestion-source/cursor nil
     :ingestion-source/last-successful-pull-at nil
     :ingestion-source/last-error nil}))

(defn- ensure-source! [tenant-id source-system cfg]
  (require-source-system! (source-registry cfg) source-system)
  (or (existing-source tenant-id source-system)
      (let [source (default-source tenant-id source-system cfg)]
        (swap! state/ingestion-sources*
               assoc (:ingestion-source/id source) source)
        source)))

(defn list-sources [tenant-id cfg]
  (mapv #(ensure-source! tenant-id % cfg)
        (keys (source-registry cfg))))

(defn get-source
  ([tenant-id source-id] (get-source tenant-id source-id {}))
  ([tenant-id source-id cfg]
   (some #(when (= source-id (:ingestion-source/id %)) %)
         (list-sources tenant-id cfg))))

(defn- apply-settings [source settings cfg]
  (let [{:keys [enabled? base-url poll-interval-seconds filters]} settings]
    (when (some? base-url)
      (validate-base-url! source base-url cfg))
    (cond-> source
      (some? enabled?) (assoc :ingestion-source/is-enabled enabled?)
      (some? base-url) (assoc-in [:ingestion-source/config :base-url] base-url)
      (some? poll-interval-seconds)
      (assoc-in [:ingestion-source/config :poll-interval-seconds]
                poll-interval-seconds)
      (some? filters) (assoc-in [:ingestion-source/config :filters] filters))))

(defn save-settings!
  ([tenant-id source-id settings] (save-settings! tenant-id source-id settings {}))
  ([tenant-id source-id settings cfg]
   (let [source (or (get-source tenant-id source-id cfg)
                    (reject! "ingestion source not found"
                             {:type :ingestion-source/not-found}))
         updated (apply-settings source settings cfg)]
     (swap! state/ingestion-sources*
            assoc (:ingestion-source/id updated) updated)
     updated)))

(defn save-settings-by-system! [tenant-id source-system settings cfg]
  (let [source (ensure-source! tenant-id
                               (require-source-system!
                                (source-registry cfg)
                                source-system)
                               cfg)]
    (save-settings! tenant-id (:ingestion-source/id source) settings cfg)))

(defn- effective-cfg [cfg source]
  (let [spec (get (source-registry cfg)
                  (:ingestion-source/source-system source))]
    (assoc cfg
           (:enabled-key spec) (:ingestion-source/is-enabled source)
           (:url-key spec) (get-in source [:ingestion-source/config
                                           :base-url])
           (:interval-key spec) (get-in source [:ingestion-source/config
                                                :poll-interval-seconds]))))

(def transport-keys
  [:failure-threshold :timeout-ms :backoff-ms :sleep-fn])

(defn- pull-opts [cfg source]
  (let [source-system (:ingestion-source/source-system source)]
    (cond-> {:tenant-id (:ingestion-source/tenant-id source)
             :cursor (:ingestion-source/cursor source)
             :max-attempts (get cfg :ingestion-max-attempts 1)}
      (get-in cfg [:ingestion-http-send-fns source-system])
      (assoc :http-send-fn (get-in cfg [:ingestion-http-send-fns
                                        source-system]))
      true (merge (select-keys cfg transport-keys)))))

(defn- run-record [job-result started finished]
  {:ingestion-run/id (UUID/randomUUID)
   :ingestion-run/tenant-id (:tenant-id job-result)
   :ingestion-run/source-system (:source-system job-result)
   :ingestion-run/status (:status job-result)
   :ingestion-run/exceptions-attempted
   (:exceptions-attempted job-result)
   :ingestion-run/exceptions-stored (:exceptions-stored job-result)
   :ingestion-run/exceptions-skipped (:exceptions-skipped job-result)
   :ingestion-run/exceptions-rejected (:exceptions-rejected job-result)
   :ingestion-run/source-refs (:source-refs job-result)
   :ingestion-run/started-at started
   :ingestion-run/finished-at finished
   :ingestion-run/cursor (:cursor job-result)
   :ingestion-run/error (:error job-result)})

(defn- update-source-after-run [source run]
  (let [source-id (:ingestion-source/id source)]
    (swap! state/ingestion-sources*
           update source-id
           (fn [current]
             (cond-> (assoc current :ingestion-source/cursor
                            (:ingestion-run/cursor run))
               (= :succeeded (:ingestion-run/status run))
               (assoc :ingestion-source/last-successful-pull-at
                      (:ingestion-run/finished-at run)
                      :ingestion-source/last-error nil)
               (= :failed (:ingestion-run/status run))
               (assoc :ingestion-source/last-error
                      (:ingestion-run/error run)))))))

(defn- disabled-result [source]
  {:tenant-id (:ingestion-source/tenant-id source)
   :source-system (:ingestion-source/source-system source)
   :status :disabled
   :exceptions-attempted 0
   :exceptions-stored 0
   :exceptions-skipped 0
   :exceptions-rejected 0
   :source-refs []
   :cursor (:ingestion-source/cursor source)
   :error nil})

(defn- require-run-fn! [cfg source]
  (let [spec (get (source-registry cfg)
                  (:ingestion-source/source-system source))]
    (or (:run-fn spec)
        (reject! "ingestion source runner is not configured"
                 {:type :ingestion-source/runner-not-configured}))))

(defn pull-now! [tenant-id source-id cfg]
  (let [source (or (get-source tenant-id source-id cfg)
                   (reject! "ingestion source not found"
                            {:type :ingestion-source/not-found}))
        started (now-date)
        result (if-not (:ingestion-source/is-enabled source)
                 (disabled-result source)
                 ((require-run-fn! cfg source)
                  (effective-cfg cfg source)
                  (pull-opts cfg source)))
        finished (now-date)
        run (run-record result started finished)]
    (swap! state/ingestion-runs* assoc (:ingestion-run/id run) run)
    (update-source-after-run source run)
    run))

(defn list-runs [tenant-id {:keys [source-system status]}]
  (->> (vals @state/ingestion-runs*)
       (filter #(= tenant-id (:ingestion-run/tenant-id %)))
       (filter #(or (nil? source-system)
                    (= source-system (:ingestion-run/source-system %))))
       (filter #(or (nil? status)
                    (= status (:ingestion-run/status %))))
       (sort-by :ingestion-run/started-at #(compare %2 %1))
       vec))

(ns drw.config
  (:require [clojure.string :as str]))

(def required-env
  ["APP_ENV" "PORT" "DATABASE_URL" "DATOMIC_URI" "REDIS_URL"
   "SESSION_SECRET"])

(defn- parse-port [value]
  (try
    (Integer/parseInt value)
    (catch NumberFormatException ex
      (throw (ex-info "PORT must be an integer" {:value value} ex)))))

(defn- env-true? [env key]
  (= "true" (get env key)))

(defn- parse-env-line [line]
  (let [trimmed (str/trim line)]
    (when-not (or (str/blank? trimmed) (str/starts-with? trimmed "#"))
      (let [[k v] (str/split trimmed #"=" 2)]
        [(str/trim k) (str/trim (or v ""))]))))

(defn read-env-file [path]
  (into {}
        (keep parse-env-line)
        (str/split-lines (slurp path))))

(defn- base-config [env]
  {:app-env (get env "APP_ENV")
   :port (parse-port (get env "PORT"))
   :database-url (get env "DATABASE_URL")
   :database-pool (parse-port (get env "DATABASE_POOL" "10"))
   :datomic-uri (get env "DATOMIC_URI")
   :datomic-storage-dir (get env "DATOMIC_STORAGE_DIR" "storage/datomic-local")
   :datomic-sql-transactor-properties
   (get env "DATOMIC_SQL_TRANSACTOR_PROPERTIES"
        "resources/datomic/sql-transactor.properties")
   :redis-url (get env "REDIS_URL")
   :session-secret (get env "SESSION_SECRET")
   :self-registration-enabled (not= "false" (get env "SELF_REGISTRATION_ENABLED"
                                                 "true"))
   :api-key-prefix (get env "API_KEY_PREFIX" "drw_live_")})

(defn- workflow-config [env]
  {:notification-hub-enabled (= "true" (get env "NOTIFICATION_HUB_ENABLED"))
   :notification-hub-url (get env "NOTIFICATION_HUB_URL")
   :notification-hub-api-key (get env "NOTIFICATION_HUB_API_KEY")
   :hub-ingress-secret (get env "HUB_INGRESS_SECRET")
   :workflow-engine-enabled (= "true" (get env "WORKFLOW_ENGINE_ENABLED"))
   :workflow-engine-url (get env "WORKFLOW_ENGINE_URL")
   :workflow-engine-api-key (get env "WORKFLOW_ENGINE_API_KEY")})

(defn- adapter-config [env]
  {:invoice-recon-enabled (env-true? env "INVOICE_RECON_ENABLED")
   :invoice-recon-url (get env "INVOICE_RECON_URL")
   :invoice-recon-api-key (get env "INVOICE_RECON_API_KEY")
   :invoice-recon-poll-interval-seconds
   (parse-port (get env "INVOICE_RECON_POLL_INTERVAL_SECONDS" "600"))
   :contract-lifecycle-enabled (env-true? env "CONTRACT_LIFECYCLE_ENABLED")
   :contract-lifecycle-url (get env "CONTRACT_LIFECYCLE_URL")
   :contract-lifecycle-api-key (get env "CONTRACT_LIFECYCLE_API_KEY")
   :contract-lifecycle-backfill-interval-seconds
   (parse-port (get env "CONTRACT_LIFECYCLE_BACKFILL_INTERVAL_SECONDS" "900"))
   :nats-enabled (env-true? env "NATS_ENABLED")
   :nats-url (get env "NATS_URL")
   :nats-creds-path (get env "NATS_CREDS_PATH")
   :nats-stream-name (get env "NATS_STREAM_NAME" "ECOSYSTEM_EVENTS")
   :transaction-recon-enabled (env-true? env "TRANSACTION_RECON_ENABLED")
   :transaction-recon-url (get env "TRANSACTION_RECON_URL")
   :transaction-recon-api-key (get env "TRANSACTION_RECON_API_KEY")
   :transaction-recon-poll-interval-seconds
   (parse-port (get env "TRANSACTION_RECON_POLL_INTERVAL_SECONDS" "900"))
   :webhook-engine-enabled (env-true? env "WEBHOOK_ENGINE_ENABLED")
   :webhook-engine-url (get env "WEBHOOK_ENGINE_URL")
   :webhook-engine-api-key (get env "WEBHOOK_ENGINE_API_KEY")
   :webhook-engine-dlq-poll-interval-seconds
   (parse-port (get env "WEBHOOK_ENGINE_DLQ_POLL_INTERVAL_SECONDS" "1800"))})

(defn- normalize-config [env]
  (merge (base-config env)
         (workflow-config env)
         (adapter-config env)))

(defn load-config
  ([] (load-config {:env (System/getenv)}))
  ([{:keys [env env-file]}]
   (let [loaded-env (cond
                      env env
                      env-file (read-env-file env-file)
                      :else (System/getenv))
         missing (filter #(str/blank? (get loaded-env %)) required-env)]
     (when (seq missing)
       (throw (ex-info "Missing required environment variables"
                       {:missing (vec missing)})))
     (normalize-config loaded-env))))

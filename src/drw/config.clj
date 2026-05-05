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

(defn- parse-env-line [line]
  (let [trimmed (str/trim line)]
    (when-not (or (str/blank? trimmed) (str/starts-with? trimmed "#"))
      (let [[k v] (str/split trimmed #"=" 2)]
        [(str/trim k) (str/trim (or v ""))]))))

(defn read-env-file [path]
  (into {}
        (keep parse-env-line)
        (str/split-lines (slurp path))))

(defn- normalize-config [env]
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
   :notification-hub-enabled (= "true" (get env "NOTIFICATION_HUB_ENABLED"))
   :notification-hub-url (get env "NOTIFICATION_HUB_URL")
   :workflow-engine-enabled (= "true" (get env "WORKFLOW_ENGINE_ENABLED"))
   :workflow-engine-url (get env "WORKFLOW_ENGINE_URL")})

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

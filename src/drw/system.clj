(ns drw.system
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [datomic.client.api :as d]
            [next.jdbc :as jdbc]
            [taoensso.carmine :as car])
  (:import [java.net URI]))

(defn- require-config [cfg key env-name]
  (let [value (get cfg key)]
    (when (str/blank? value)
      (throw (ex-info (str env-name " is required") {:env env-name})))
    value))

(defn- local-datomic-parts [uri]
  (when-not (str/starts-with? uri "datomic:local://")
    (throw (ex-info "DATOMIC_URI must use datomic:local" {:uri uri})))
  (let [tail (subs uri (count "datomic:local://"))
        [system db-name] (str/split tail #"/" 2)]
    (when (or (str/blank? system) (str/blank? db-name))
      (throw (ex-info "DATOMIC_URI must include system and database name"
                      {:uri uri})))
    {:system system :db-name db-name}))

(defn datomic-local-client-opts [cfg]
  (let [uri (require-config cfg :datomic-uri "DATOMIC_URI")
        {:keys [system]} (local-datomic-parts uri)]
    {:server-type :datomic-local
     :system system
     :storage-dir (.getAbsolutePath
                   (io/file (or (:datomic-storage-dir cfg)
                                "storage/datomic-local")))}))

(defn- load-properties [path]
  (with-open [reader (io/reader path)]
    (doto (java.util.Properties.)
      (.load reader))))

(defn- database-name [jdbc-url]
  (-> jdbc-url
      (str/replace #"^jdbc:" "")
      URI.
      .getPath
      (str/replace #"^/" "")))

(defn- query-param [jdbc-url key]
  (let [query (some-> jdbc-url (str/replace #"^jdbc:" "") URI. .getQuery)]
    (some (fn [part]
            (let [[k v] (str/split part #"=" 2)]
              (when (= key k) v)))
          (str/split (or query "") #"&"))))

(defn datomic-sql-storage-config [cfg]
  (let [jdbc-url (require-config cfg :database-url "DATABASE_URL")
        props-file (or (:datomic-sql-transactor-properties cfg)
                       "resources/datomic/sql-transactor.properties")
        props (load-properties props-file)
        sql-url (.getProperty props "sql-url")]
    {:properties-file props-file
     :protocol (.getProperty props "protocol")
     :transactor-host (.getProperty props "host")
     :sql-url sql-url
     :jdbc-url jdbc-url
     :database-name (database-name jdbc-url)
     :database-user (or (query-param sql-url "user")
                        (query-param jdbc-url "user"))}))

(defn check-datomic-local! [cfg]
  (let [uri (require-config cfg :datomic-uri "DATOMIC_URI")
        {:keys [system db-name]} (local-datomic-parts uri)
        client-opts (assoc (datomic-local-client-opts cfg) :system system)
        client (d/client client-opts)]
    (d/create-database client {:db-name db-name})
    (d/connect client {:db-name db-name})
    {:status :up :uri uri :system system :db-name db-name}))

(defn check-postgres! [cfg]
  (let [jdbc-url (require-config cfg :database-url "DATABASE_URL")
        ds (jdbc/get-datasource {:jdbcUrl jdbc-url
                                 :user "drw"
                                 :password "drw"})
        row (jdbc/execute-one! ds ["select 1 as result"])]
    {:status :up :result (:result row)}))

(defn- redis-server-spec [redis-url]
  (let [uri (URI. redis-url)]
    {:host (.getHost uri)
     :port (if (= -1 (.getPort uri))
             6379
             (.getPort uri))}))

(defn check-redis! [cfg]
  (let [redis-url (require-config cfg :redis-url "REDIS_URL")
        spec (redis-server-spec redis-url)
        result (car/wcar {:pool {} :spec spec} (car/ping))]
    {:status :up :result result}))

(ns drw.system
  (:require [clojure.string :as str]
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

(defn check-datomic-local! [cfg]
  (let [uri (require-config cfg :datomic-uri "DATOMIC_URI")
        {:keys [system db-name]} (local-datomic-parts uri)
        client-opts {:server-type :datomic-local
                     :system system
                     :storage-dir :mem}
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

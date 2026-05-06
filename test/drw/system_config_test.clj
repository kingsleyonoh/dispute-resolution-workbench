(ns drw.system-config-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [drw.config :as config]
            [drw.system :as system]))

(deftest builds-datomic-local-client-config-from-runtime-config
  (let [cfg (config/load-config {:env-file ".env.example"})
        opts (system/datomic-local-client-opts cfg)]
    (is (= :datomic-local (:server-type opts)))
    (is (= "drw" (:system opts)))
    (is (str/ends-with? (:storage-dir opts) "storage/datomic-local"))))

(deftest rejects-datomic-local-uris-without-database-name
  (testing "dev-local config must name both system and database"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"DATOMIC_URI must include system and database name"
         (system/datomic-local-client-opts
          {:datomic-uri "datomic:local://drw"})))))

(deftest builds-datomic-sql-storage-config-from-runtime-config
  (let [cfg (config/load-config {:env-file ".env.example"})
        sql (system/datomic-sql-storage-config cfg)]
    (is (= "resources/datomic/sql-transactor.properties"
           (:properties-file sql)))
    (is (= "jdbc:postgresql://localhost:5432/datomic_storage"
           (:jdbc-url sql)))
    (is (= "drw" (:database-user sql)))
    (is (= "datomic_storage" (:database-name sql)))))

(deftest rejects-datomic-sql-storage-config-without-jdbc-url
  (testing "SQL storage config fails fast when DATABASE_URL is absent"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"DATABASE_URL is required"
         (system/datomic-sql-storage-config
          {:datomic-sql-transactor-properties
           "resources/datomic/sql-transactor.properties"})))))

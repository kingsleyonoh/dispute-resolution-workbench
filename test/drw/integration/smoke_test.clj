(ns ^:integration drw.integration.smoke-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.config :as config]
            [drw.system :as system]))

(deftest datomic-local-smoke-test
  (let [cfg (config/load-config {:env-file ".env.example"})
        result (system/check-datomic-local! cfg)]
    (is (= :up (:status result)))
    (is (= (:datomic-uri cfg) (:uri result)))))

(deftest datomic-local-rejects-bad-uri
  (testing "Datomic smoke checks fail fast when DATOMIC_URI is malformed"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"DATOMIC_URI must use datomic:local"
         (system/check-datomic-local! {:datomic-uri "datomic:sql://bad"})))))

(deftest postgres-smoke-test
  (let [cfg (config/load-config {:env-file ".env.example"})
        result (system/check-postgres! cfg)]
    (is (= :up (:status result)))
    (is (= 1 (:result result)))))

(deftest postgres-rejects-missing-url
  (testing "Postgres health checks require DATABASE_URL"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"DATABASE_URL is required"
         (system/check-postgres! {})))))

(deftest redis-smoke-test
  (let [cfg (config/load-config {:env-file ".env.example"})
        result (system/check-redis! cfg)]
    (is (= :up (:status result)))
    (is (= "PONG" (:result result)))))

(deftest redis-rejects-missing-url
  (testing "Redis health checks require REDIS_URL"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"REDIS_URL is required"
         (system/check-redis! {})))))

(ns drw.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.config :as config]))

(deftest loads-required-runtime-config-from-env-file
  (let [cfg (config/load-config {:env-file ".env.example"})]
    (is (= "development" (:app-env cfg)))
    (is (= 3049 (:port cfg)))
    (is (= "datomic:local://drw/dev" (:datomic-uri cfg)))
    (is (= "jdbc:postgresql://localhost:5432/datomic_storage"
           (:database-url cfg)))
    (is (= "redis://localhost:6379/0" (:redis-url cfg)))
    (is (string? (:session-secret cfg)))))

(deftest rejects-missing-required-runtime-config
  (testing "missing required env vars fail during startup config load"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Missing required environment variables"
         (config/load-config {:env {}})))))

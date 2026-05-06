(ns drw.setup-first-run-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.config :as config]
            [drw.setup :as setup]))

(defn- pass-check [name payload]
  (fn [_cfg]
    (assoc payload :name name :status :up)))

(defn- injected-checks []
  {:check-datomic-local (pass-check :datomic-local {:db-name "dev"})
   :check-postgres (pass-check :postgres {:result 1})
   :check-redis (pass-check :redis {:result "PONG"})})

(deftest first-run-task-produces-structured-setup-evidence
  (let [cfg (config/load-config {:env-file ".env.example"})
        result (setup/run-first-run! cfg (injected-checks))
        checks-by-name (into {} (map (juxt :name identity)) (:checks result))]
    (is (= :ok (:status result)))
    (is (= [:schema-roundtrip
            :status-transitions
            :tenant-fixtures
            :two-tenant-pdf-render
            :datomic-local
            :datomic-sql-storage
            :postgres
            :redis]
           (map :name (:checks result))))
    (is (pos? (get-in checks-by-name [:schema-roundtrip :attribute-count])))
    (is (pos? (get-in checks-by-name [:schema-roundtrip :tx-function-count])))
    (is (= #{:dispute :correlation :report}
           (set (get-in checks-by-name
                        [:status-transitions :transition-families]))))
    (is (= 2 (get-in checks-by-name [:tenant-fixtures :tenant-count])))
    (is (= 2 (get-in checks-by-name
                     [:two-tenant-pdf-render :tenant-count])))))

(deftest first-run-task-reports-failed-check-without-hiding-context
  (let [cfg (config/load-config {:env-file ".env.example"})
        result (setup/run-first-run!
                cfg
                (assoc (injected-checks)
                       :check-postgres
                       (fn [_]
                         (throw (ex-info "postgres down"
                                         {:type :postgres/down})))))]
    (is (= :failed (:status result)))
    (is (= :postgres (:name (:failed-check result))))
    (is (= :postgres/down (get-in result [:failed-check :type])))))

(deftest first-run-cli-script-delegates-to-setup-alias
  (let [script (slurp "scripts/first-run-setup.ps1")]
    (is (str/includes? script "Set-StrictMode"))
    (is (str/includes? script "clojure -M:setup"))))

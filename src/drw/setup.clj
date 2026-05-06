(ns drw.setup
  (:require [drw.config :as config]
            [drw.db.schema :as schema]
            [drw.domain.reports :as reports]
            [drw.fixtures :as fixtures]
            [drw.system :as system]))

(defn- tenant-fixtures-check []
  (let [tenants (fixtures/load-tenants)]
    {:status :ok
     :tenant-count (count tenants)
     :tenant-slugs (mapv :tenant/slug tenants)}))

(defn- datomic-sql-storage-check [cfg]
  (assoc (system/datomic-sql-storage-config cfg)
         :status :ok))

(defn- default-checks []
  {:check-datomic-local system/check-datomic-local!
   :check-postgres system/check-postgres!
   :check-redis system/check-redis!})

(defn- step-plan [checks]
  [[:schema-roundtrip (fn [_] (schema/roundtrip-summary))]
   [:status-transitions (fn [_] (schema/status-transition-summary))]
   [:tenant-fixtures (fn [_] (tenant-fixtures-check))]
   [:two-tenant-pdf-render
    (fn [_] (reports/two-tenant-pdf-render-check (fixtures/load-tenants)))]
   [:datomic-local (:check-datomic-local checks)]
   [:datomic-sql-storage datomic-sql-storage-check]
   [:postgres (:check-postgres checks)]
   [:redis (:check-redis checks)]])

(defn- run-step [cfg [name f]]
  (try
    (assoc (f cfg) :name name)
    (catch clojure.lang.ExceptionInfo ex
      (merge {:name name
              :status :failed
              :message (ex-message ex)}
             (ex-data ex)))
    (catch Exception ex
      {:name name
       :status :failed
       :message (ex-message ex)})))

(defn run-first-run!
  ([cfg] (run-first-run! cfg {}))
  ([cfg checks]
   (let [all-checks (merge (default-checks) checks)
         results (mapv #(run-step cfg %) (step-plan all-checks))
         failed (first (filter #(= :failed (:status %)) results))]
     (if failed
       {:status :failed
        :failed-check failed
        :checks results}
       {:status :ok
        :checks results}))))

(defn- print-summary [result]
  (doseq [check (:checks result)]
    (println (str "["
                  (if (= :failed (:status check)) "FAIL" "OK")
                  "] "
                  (name (:name check)))))
  (println (str "First-run setup "
                (if (= :ok (:status result)) "passed." "failed."))))

(defn -main [& _args]
  (let [cfg (config/load-config)
        result (run-first-run! cfg)]
    (print-summary result)
    (when-not (= :ok (:status result))
      (System/exit 1))))

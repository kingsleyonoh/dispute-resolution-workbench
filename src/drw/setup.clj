(ns drw.setup
  (:require [drw.config :as config]
            [drw.system :as system]))

(defn -main [& _args]
  (let [cfg (config/load-config)]
    (system/check-datomic-local! cfg)
    (system/datomic-sql-storage-config cfg)
    (println "Setup smoke checks passed.")))

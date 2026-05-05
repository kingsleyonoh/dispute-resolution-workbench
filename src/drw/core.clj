(ns drw.core
  (:gen-class)
  (:require [drw.config :as config]))

(defn -main [& _args]
  (let [cfg (config/load-config)]
    (println "Dispute Resolution Workbench starting on port" (:port cfg))))

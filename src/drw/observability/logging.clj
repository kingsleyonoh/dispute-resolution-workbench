(ns drw.observability.logging
  (:require [drw.api.responses :as responses]))

(defn log-event! [cfg level event fields]
  (let [entry (merge {:level (name level)
                      :event event
                      :dataset (or (:axiom-dataset cfg) "drw-logs")
                      :timestamp (str (java.util.Date.))}
                     fields)
        line (responses/encode entry)]
    (if-let [sink (:log-sink-fn cfg)]
      (sink line)
      (println line))
    line))

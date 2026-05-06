(ns drw.jobs.sla-reaper
  (:require [drw.domain.sla :as sla]))

(defn run-once! [cfg opts]
  (let [checked (count (sla/disputes-with-sla))
        breaches (sla/mark-overdue! cfg opts)]
    {:checked checked
     :breached (count breaches)}))

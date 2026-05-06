(ns drw.jobs.resolution-poller
  (:require [drw.domain.resolution :as resolution]))

(def poller-actor
  {:actor-kind :job
   :actor-id "resolution-poller"})

(defn run-once! [cfg]
  (resolution/poll-active! cfg poller-actor))

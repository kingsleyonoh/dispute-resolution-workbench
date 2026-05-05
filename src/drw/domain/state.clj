(ns drw.domain.state
  (:require [drw.audit.recorder :as recorder]))

(defonce counterparties* (atom {}))
(defonce disputes* (atom {}))
(defonce exceptions* (atom {}))
(defonce correlations* (atom {}))
(defonce timeline* (atom []))
(defonce audit-log* (atom []))
(defonce reference-sequences* (atom {}))
(defonce sla-breaches* (atom #{}))

(defn reset-store! []
  (reset! counterparties* {})
  (reset! disputes* {})
  (reset! exceptions* {})
  (reset! correlations* {})
  (reset! timeline* [])
  (reset! audit-log* [])
  (reset! reference-sequences* {})
  (reset! sla-breaches* #{}))

(defn append-audit! [event]
  (let [tx (recorder/audit-tx event)]
    (swap! audit-log* into tx)
    tx))

(defn audit-log []
  @audit-log*)

(defn append-timeline! [entry]
  (swap! timeline* conj entry)
  entry)

(defn timeline []
  @timeline*)

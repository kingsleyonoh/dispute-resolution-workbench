(ns drw.observability.readiness
  (:require [drw.domain.ingestion-sources :as ingestion]
            [drw.fixtures :as fixtures]))

(defn- tenant-ids [cfg]
  (mapv :tenant/id (or (:tenant-source cfg)
                       (fixtures/load-tenants))))

(defn- enabled? [source]
  (:ingestion-source/is-enabled source))

(defn- interval-ms [source]
  (* 1000 (long (get-in source [:ingestion-source/config
                                :poll-interval-seconds]
                        600))))

(defn- fresh? [now source]
  (let [last-success (:ingestion-source/last-successful-pull-at source)]
    (and last-success
         (<= (- (.getTime now) (.getTime last-success))
             (* 2 (interval-ms source))))))

(defn source-status [now source]
  {:source_system (name (:ingestion-source/source-system source))
   :enabled (enabled? source)
   :ready (or (not (enabled? source)) (fresh? now source))
   :last_successful_pull_at
   (some-> (:ingestion-source/last-successful-pull-at source) str)})

(defn ready-summary [cfg opts]
  (let [now (or (:now opts) (java.util.Date.))
        sources (mapcat #(ingestion/list-sources % cfg) (tenant-ids cfg))
        statuses (mapv #(source-status now %) sources)
        ready? (every? :ready statuses)]
    {:status (if ready? "ready" "not_ready")
     :ready ready?
     :checks {:app true
              :adapters statuses}}))

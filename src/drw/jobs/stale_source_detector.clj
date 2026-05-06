(ns drw.jobs.stale-source-detector
  (:require [drw.domain.ingestion-sources :as ingestion]
            [drw.ecosystem.hub-client :as hub]
            [drw.fixtures :as fixtures])
  (:import [java.util UUID]))

(def stale-after-millis (* 24 60 60 1000))

(defn- tenant-ids [cfg opts]
  (or (:tenant-ids opts)
      (mapv :tenant/id (or (:tenant-source cfg)
                           (fixtures/load-tenants)))))

(defn- stale? [now source]
  (let [last-success (:ingestion-source/last-successful-pull-at source)]
    (and (:ingestion-source/is-enabled source)
         last-success
         (> (- (.getTime now) (.getTime last-success))
            stale-after-millis))))

(defn- payload [source]
  {:tenant_id (str (:ingestion-source/tenant-id source))
   :source_id (str (:ingestion-source/id source))
   :source_system (name (:ingestion-source/source-system source))
   :source_name (:ingestion-source/display-name source)
   :last_successful_pull_at
   (str (:ingestion-source/last-successful-pull-at source))
   :deep_link "/settings/ingestion"})

(defn- emit! [cfg source]
  (hub/emit-event!
   cfg
   {:event-type "dispute.ingestion_source_stale"
    :event-id (str "dispute.ingestion_source_stale-" (UUID/randomUUID))
    :payload (payload source)}))

(defn run-once! [cfg opts]
  (let [now (or (:now opts) (java.util.Date.))
        sources (mapcat #(ingestion/list-sources % cfg)
                        (tenant-ids cfg opts))
        stale (filter #(stale? now %) sources)
        emitted (mapv #(emit! cfg %) stale)]
    {:checked (count sources)
     :stale (count stale)
     :emitted (count emitted)}))

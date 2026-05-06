(ns drw.observability.metrics
  (:require [clojure.string :as str]
            [drw.domain.disputes :as disputes]
            [drw.domain.state :as state]))

(defn- line [name value]
  (str name " " value))

(defn render []
  (str/join
   "\n"
   [(line "# HELP drw_disputes_total Total disputes in memory" "")
    (line "# TYPE drw_disputes_total gauge" "")
    (line "drw_disputes_total" (count @state/disputes*))
    (line "# HELP drw_open_disputes_total Open disputes in memory" "")
    (line "# TYPE drw_open_disputes_total gauge" "")
    (line "drw_open_disputes_total"
          (count (remove #(contains? disputes/terminal-statuses
                                     (:dispute/status %))
                         (vals @state/disputes*))))
    (line "# HELP drw_ingestion_sources_total Ingestion sources in memory" "")
    (line "# TYPE drw_ingestion_sources_total gauge" "")
    (line "drw_ingestion_sources_total" (count @state/ingestion-sources*))
    ""]))

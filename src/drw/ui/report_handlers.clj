(ns drw.ui.report-handlers
  (:require [drw.domain.reports :as reports]
            [drw.fixtures :as fixtures]
            [drw.ui.handlers :as handlers]))

(defn- tenant-source [cfg]
  (or (:tenant-source cfg) (fixtures/load-tenants)))

(defn- pdf-bytes [report]
  (java.nio.file.Files/readAllBytes
   (.toPath (java.io.File. (:report/storage-path report)))))

(defn audit-pdf [cfg]
  (fn [request]
    (handlers/require-tenant
     request
     (fn [tenant]
       (let [tenant-id (:tenant/id tenant)
             dispute-id (handlers/path-id request)
             report (reports/generate-dispute-audit-pdf!
                     cfg
                     (tenant-source cfg)
                     tenant-id
                     dispute-id
                     {:actor-kind :user :actor-id "ui"})]
         (if (= :ready (:report/status report))
           {:status 200
            :headers {"Content-Type" "application/pdf"
                      "Content-Disposition"
                      (str "attachment; filename=\""
                           (:report/id report)
                           ".pdf\"")}
            :body (pdf-bytes report)}
           (handlers/html
            [:main
             [:p "Report generation failed."]
             [:p (:report/error report)]])))))))

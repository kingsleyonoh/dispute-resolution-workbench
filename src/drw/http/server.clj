(ns drw.http.server
  (:require [io.pedestal.http :as http]
            [drw.http.routes :as routes]))

(defn service-map [cfg]
  {::http/routes (routes/routes)
   ::http/type :jetty
   ::http/port (:port cfg)
   ::http/join? false
   ::http/resource-path "/public"})

(defn start! [cfg]
  (-> cfg
      service-map
      http/create-server
      http/start))

(defn stop! [server]
  (http/stop server))

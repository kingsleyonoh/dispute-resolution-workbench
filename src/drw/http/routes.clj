(ns drw.http.routes
  (:require [drw.http.handlers :as handlers]))

(defn routes
  ([] (routes {}))
  ([{:keys [dev-routes]}]
   (when (and (some? dev-routes) (not (boolean? dev-routes)))
     (throw (ex-info "dev routes flag must be boolean"
                     {:dev-routes dev-routes})))
   #{["/" :get handlers/home :route-name :home]
     ["/api/health" :get handlers/health :route-name :health]}))

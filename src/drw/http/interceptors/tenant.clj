(ns drw.http.interceptors.tenant
  (:require [drw.http.interceptors.auth :as auth]
            [drw.http.json :as json]
            [io.pedestal.interceptor :as pedestal]))

(defn interceptor []
  (pedestal/interceptor
   {:name ::tenant
    :enter (fn [context]
             (let [request (:request context)]
               (if (or (:current-tenant request)
                       (:public-route? request)
                       (auth/public-route? request))
                 context
                 (assoc context :response
                        (json/error-response
                         401 "UNAUTHORIZED"
                         "Tenant context is required")))))}))

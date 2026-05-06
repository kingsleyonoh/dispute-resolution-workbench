(ns drw.http.interceptors.audit
  (:require [io.pedestal.interceptor :as pedestal]))

(defn- audit-context [request]
  {:request-id (:request-id request)
   :tenant-id (get-in request [:current-tenant :tenant-id])
   :method (:request-method request)
   :path (:uri request)})

(defn interceptor []
  (pedestal/interceptor
   {:name ::audit
    :enter (fn [context]
             (assoc-in context [:request :audit-context]
                       (audit-context (:request context))))}))

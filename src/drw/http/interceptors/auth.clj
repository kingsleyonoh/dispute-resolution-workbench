(ns drw.http.interceptors.auth
  (:require [clojure.string :as str]
            [drw.http.json :as json]
            [drw.tenants.store :as store]
            [io.pedestal.interceptor :as pedestal]))

(def public-routes
  #{["/api/tenants/register" :post]
    ["/login" :get]
    ["/logout" :post]
    ["/api/exceptions/from-hub" :post]})

(defn public-route? [request]
  (or (contains? public-routes [(:uri request) (:request-method request)])
      (str/starts-with? (:uri request) "/api/health")
      (= "/" (:uri request))))

(defn- header [request k]
  (or (get-in request [:headers k])
      (get-in request [:headers (str/lower-case k)])))

(defn- unauthorized []
  (json/error-response 401 "UNAUTHORIZED" "Valid X-API-Key is required"))

(defn- disabled []
  (json/error-response 403 "TENANT_DISABLED" "Tenant is disabled"))

(defn interceptor [_cfg]
  (pedestal/interceptor
   {:name ::auth
    :enter (fn [context]
             (let [request (:request context)
                   api-key (header request "X-API-Key")]
               (cond
                 (and (str/blank? api-key) (public-route? request))
                 (assoc-in context [:request :public-route?] true)

                 (str/blank? api-key)
                 (assoc context :response (unauthorized))

                 :else
                 (if-let [tenant (store/tenant-by-api-key api-key)]
                   (if (:tenant/is-active tenant)
                     (assoc-in context [:request :current-tenant]
                               {:tenant-id (:tenant/id tenant)
                                :slug (:tenant/slug tenant)
                                :name (:tenant/name tenant)})
                     (assoc context :response (disabled)))
                   (assoc context :response (unauthorized))))))}))

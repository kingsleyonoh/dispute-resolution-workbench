(ns drw.http.routes
  (:require [drw.api.tenants :as tenants]
            [drw.http.handlers :as handlers]
            [drw.http.interceptors.audit :as audit]
            [drw.http.interceptors.auth :as auth]
            [drw.http.interceptors.ratelimit :as ratelimit]
            [drw.http.interceptors.request-id :as request-id]
            [drw.http.interceptors.tenant :as tenant]
            [drw.http.json :as json]))

(defn- api-chain [cfg handler]
  [(request-id/interceptor)
   (json/response-encoder)
   (ratelimit/interceptor)
   (audit/interceptor)
   (auth/interceptor cfg)
   (tenant/interceptor)
   handler])

(defn- page-chain [handler]
  [(request-id/interceptor) handler])

(defn routes
  ([] (routes {}))
  ([{:keys [dev-routes] :as cfg}]
   (when (and (some? dev-routes) (not (boolean? dev-routes)))
     (throw (ex-info "dev routes flag must be boolean"
                     {:dev-routes dev-routes})))
   (let [cfg (merge {:self-registration-enabled true
                     :api-key-prefix "drw_live_"}
                    cfg)]
     #{["/" :get (page-chain handlers/home) :route-name :home]
       ["/api/health" :get (api-chain cfg handlers/health) :route-name :health]
       ["/api/tenants/register" :post
        (api-chain cfg (tenants/register-handler cfg))
        :route-name :tenant-register]
       ["/api/tenants/me" :get
        (api-chain cfg (tenants/profile-handler cfg))
        :route-name :tenant-profile]
       ["/tenants/me" :get
        (api-chain cfg (tenants/profile-handler cfg))
        :route-name :tenant-profile-compat]
       ["/api/tenants/rotate-key" :post
        (api-chain cfg (tenants/rotate-key-handler cfg))
        :route-name :tenant-rotate-key]})))

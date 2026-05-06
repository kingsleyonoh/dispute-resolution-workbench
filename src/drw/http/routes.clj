(ns drw.http.routes
  (:require [drw.api.counterparties :as counterparties]
            [drw.api.correlations :as correlations]
            [drw.api.disputes :as disputes]
            [drw.api.exceptions :as exceptions]
            [drw.api.ingestion :as ingestion]
            [drw.api.playbooks :as playbooks]
            [drw.api.tenants :as tenants]
            [drw.http.handlers :as handlers]
            [drw.http.interceptors.audit :as audit]
            [drw.http.interceptors.auth :as auth]
            [drw.http.interceptors.ratelimit :as ratelimit]
            [drw.http.interceptors.request-id :as request-id]
            [drw.http.interceptors.tenant :as tenant]
            [drw.http.json :as json]
            [drw.ui.handlers :as ui]
            [drw.ui.ingestion-handlers :as ui-ingestion]
            [drw.ui.playbook-handlers :as ui-playbooks]))

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

(defn- ui-routes [cfg]
  #{["/" :get (page-chain ui/home) :route-name :home]
    ["/login" :get (page-chain ui/login) :route-name :login]
    ["/login" :post (page-chain (ui/login-submit cfg))
     :route-name :login-submit]
    ["/logout" :post (page-chain ui/logout) :route-name :logout]
    ["/disputes" :get (page-chain ui/disputes-list)
     :route-name :ui-disputes-list]
    ["/disputes" :post (page-chain ui/disputes-create)
     :route-name :ui-disputes-create]
    ["/disputes/:id" :get (page-chain ui/dispute-detail)
     :route-name :ui-disputes-detail]
    ["/disputes/:id/assign" :post (page-chain ui/assign-dispute)
     :route-name :ui-disputes-assign]
    ["/disputes/:id/transition" :post
     (page-chain ui/transition-dispute)
     :route-name :ui-disputes-transition]
    ["/disputes/:id/comments" :post (page-chain ui/comment-dispute)
     :route-name :ui-disputes-comment]
    ["/disputes/:id/exceptions" :post (page-chain ui/attach-exception)
     :route-name :ui-disputes-attach-exception]
    ["/counterparties" :get (page-chain ui/counterparties-list)
     :route-name :ui-counterparties-list]
    ["/counterparties/:id" :get (page-chain ui/counterparty-detail)
     :route-name :ui-counterparties-detail]})

(defn- ui-review-routes [cfg]
  #{["/correlations" :get (page-chain ui/correlations-list)
     :route-name :ui-correlations-list]
    ["/correlations/:id/accept" :post
     (page-chain ui/accept-correlation)
     :route-name :ui-correlations-accept]
    ["/correlations/:id/reject" :post
     (page-chain ui/reject-correlation)
     :route-name :ui-correlations-reject]
    ["/settings/ingestion" :get
     (page-chain (ui-ingestion/ingestion-settings cfg))
     :route-name :ui-ingestion-settings]
    ["/settings/ingestion" :post
     (page-chain (ui-ingestion/save-ingestion-settings cfg))
     :route-name :ui-ingestion-save]
    ["/settings/ingestion/:id/pull-now" :post
     (page-chain (ui-ingestion/pull-ingestion-now cfg))
     :route-name :ui-ingestion-pull-now]
    ["/settings/playbooks" :get
     (page-chain (ui-playbooks/playbook-settings cfg))
     :route-name :ui-playbooks-settings]
    ["/settings/playbooks" :post
     (page-chain (ui-playbooks/save-playbook cfg))
     :route-name :ui-playbooks-save]
    ["/settings/playbooks/:id/disable" :post
     (page-chain (ui-playbooks/disable-playbook cfg))
     :route-name :ui-playbooks-disable]})

(defn- tenant-routes [cfg]
  #{["/api/health" :get (api-chain cfg handlers/health) :route-name :health]
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
     :route-name :tenant-rotate-key]})

(defn- workbench-routes [cfg]
  #{["/api/disputes" :get
     (api-chain cfg (disputes/list-handler cfg))
     :route-name :disputes-list]
    ["/api/disputes" :post
     (api-chain cfg (disputes/create-handler cfg))
     :route-name :disputes-create]
    ["/api/disputes/:id" :get
     (api-chain cfg (disputes/get-handler cfg))
     :route-name :disputes-get]
    ["/api/disputes/:id/assign" :patch
     (api-chain cfg (disputes/assign-handler cfg))
     :route-name :disputes-assign]
    ["/api/disputes/:id/transition" :patch
     (api-chain cfg (disputes/transition-handler cfg))
     :route-name :disputes-transition]
    ["/api/disputes/:id/comments" :post
     (api-chain cfg (disputes/comment-handler cfg))
     :route-name :disputes-comment]
    ["/api/disputes/:id/attach-exception" :post
     (api-chain cfg (disputes/attach-exception-handler cfg))
     :route-name :disputes-attach-exception]
    ["/api/exceptions" :get
     (api-chain cfg (exceptions/list-handler cfg))
     :route-name :exceptions-list]
    ["/api/exceptions" :post
     (api-chain cfg (exceptions/create-handler cfg))
     :route-name :exceptions-create]
    ["/api/exceptions/from-hub" :post
     (api-chain cfg (exceptions/from-hub-handler cfg))
     :route-name :exceptions-from-hub]})

(defn- ingestion-correlation-routes [cfg]
  #{["/api/correlations" :get
     (api-chain cfg (correlations/list-handler cfg))
     :route-name :correlations-list]
    ["/api/correlations/:id" :get
     (api-chain cfg (correlations/get-handler cfg))
     :route-name :correlations-get]
    ["/api/correlations/:id/accept" :post
     (api-chain cfg (correlations/accept-handler cfg))
     :route-name :correlations-accept]
    ["/api/correlations/:id/reject" :post
     (api-chain cfg (correlations/reject-handler cfg))
     :route-name :correlations-reject]
    ["/api/ingestion-sources" :get
     (api-chain cfg (ingestion/list-sources-handler cfg))
     :route-name :ingestion-sources-list]
    ["/api/ingestion-sources" :post
     (api-chain cfg (ingestion/save-source-handler cfg))
     :route-name :ingestion-sources-save]
    ["/api/ingestion-sources/:id/pull-now" :post
     (api-chain cfg (ingestion/pull-now-handler cfg))
     :route-name :ingestion-sources-pull-now]
    ["/api/ingestion-runs" :get
     (api-chain cfg (ingestion/list-runs-handler cfg))
     :route-name :ingestion-runs-list]
    ["/api/playbooks" :get
     (api-chain cfg (playbooks/list-handler cfg))
     :route-name :playbooks-list]
    ["/api/playbooks" :post
     (api-chain cfg (playbooks/create-handler cfg))
     :route-name :playbooks-create]
    ["/api/playbooks/:id" :put
     (api-chain cfg (playbooks/update-handler cfg))
     :route-name :playbooks-update]
    ["/api/playbooks/:id" :delete
     (api-chain cfg (playbooks/delete-handler cfg))
     :route-name :playbooks-delete]})

(defn- counterparty-routes [cfg]
  #{["/api/counterparties" :get
     (api-chain cfg (counterparties/list-handler cfg))
     :route-name :counterparties-list]
    ["/api/counterparties/:id" :get
     (api-chain cfg (counterparties/get-handler cfg))
     :route-name :counterparties-get]
    ["/api/counterparties/:id/merge" :post
     (api-chain cfg (counterparties/merge-handler cfg))
     :route-name :counterparties-merge]})

(defn routes
  ([] (routes {}))
  ([{:keys [dev-routes] :as cfg}]
   (when (and (some? dev-routes) (not (boolean? dev-routes)))
     (throw (ex-info "dev routes flag must be boolean"
                     {:dev-routes dev-routes})))
   (let [cfg (merge {:self-registration-enabled true
                     :api-key-prefix "drw_live_"}
                    cfg)]
     (set (concat (ui-routes cfg)
                  (ui-review-routes cfg)
                  (tenant-routes cfg)
                  (workbench-routes cfg)
                  (ingestion-correlation-routes cfg)
                  (counterparty-routes cfg))))))

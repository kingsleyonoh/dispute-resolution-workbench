(ns drw.ui.handlers
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.tenants.store :as store]
            [drw.ui.pages :as pages]
            [drw.ui.request :as ui-req]
            [drw.ui.session :as session]))

(defn- html [node]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (h/html node))})

(defn- redirect
  ([location] (redirect location {}))
  ([location headers]
   {:status 303
    :headers (merge {"Location" location} headers)
    :body ""}))

(defn- login-redirect []
  {:status 302
   :headers {"Location" "/login"}
   :body ""})

(defn- current-tenant [request]
  (session/tenant-from-request request))

(defn- tenant-context [tenant]
  {:tenant-id (:tenant/id tenant)
   :tenant-name (:tenant/display-name tenant)})

(defn- actor [tenant]
  {:actor-kind :user
   :actor-id (:tenant/slug tenant)})

(defn- require-tenant [request render]
  (if-let [tenant (current-tenant request)]
    (render tenant)
    (login-redirect)))

(defn login [_request]
  (html (pages/login-page nil)))

(defn login-submit [_cfg]
  (fn [request]
    (let [api-key (:api-key (ui-req/parse-form request))]
      (if-let [tenant (store/tenant-by-api-key api-key)]
        (if (:tenant/is-active tenant)
          (let [token (session/create-session! (:tenant/id tenant))]
            (redirect "/" {"Set-Cookie" (session/session-cookie token)}))
          (html (pages/login-page "Tenant is disabled.")))
        (html (pages/login-page "Valid API key is required."))))))

(defn logout [request]
  (session/clear-session! request)
  (redirect "/login" {"Set-Cookie" session/expired-cookie}))

(defn home [request]
  (if-let [tenant (current-tenant request)]
    (html (pages/dashboard-page (tenant-context tenant)))
    (html (pages/login-page nil))))

(defn disputes-list [request]
  (require-tenant
   request
   (fn [tenant]
     (html (pages/dispute-list-page (:tenant/id tenant)
                                    (:query-params request))))))

(defn- create-dispute-attrs [tenant-id form]
  {:tenant-id tenant-id
   :title (:title form)
   :description (:description form)
   :category (ui-req/keyword-value (:category form))
   :severity (ui-req/keyword-value (:severity form))
   :currency (:currency form)
   :counterparty-id (ui-req/uuid-value (:counterparty-id form))
   :created-by :user})

(defn disputes-create [request]
  (require-tenant
   request
   (fn [tenant]
     (let [form (ui-req/parse-form request)
           dispute (disputes/create-dispute!
                    (create-dispute-attrs (:tenant/id tenant) form)
                    (actor tenant))]
       (redirect (str "/disputes/" (:dispute/id dispute)))))))

(defn- path-id [request]
  (ui-req/uuid-value (get-in request [:path-params :id])))

(defn dispute-detail [request]
  (require-tenant
   request
   (fn [tenant]
     (html (pages/dispute-detail-page (:tenant/id tenant)
                                      (path-id request)
                                      nil)))))

(defn assign-dispute [request]
  (require-tenant
   request
   (fn [tenant]
     (let [form (ui-req/parse-form request)
           id (path-id request)]
       (disputes/assign! (:tenant/id tenant)
                         id
                         {:user-id (ui-req/uuid-value (:user-id form))}
                         (actor tenant))
       (redirect (str "/disputes/" id))))))

(defn transition-dispute [request]
  (require-tenant
   request
   (fn [tenant]
     (let [form (ui-req/parse-form request)
           id (path-id request)]
       (disputes/transition! (:tenant/id tenant)
                             id
                             {:to (ui-req/keyword-value (:to-status form))}
                             (actor tenant))
       (redirect (str "/disputes/" id))))))

(defn comment-dispute [request]
  (require-tenant
   request
   (fn [tenant]
     (let [form (ui-req/parse-form request)
           id (path-id request)]
       (disputes/comment! (:tenant/id tenant)
                          id
                          {:body (:body form)}
                          (actor tenant))
       (redirect (str "/disputes/" id))))))

(defn- observed-at [form]
  (let [value (:observed-at form)
        normalized (if (str/ends-with? (str value) "Z")
                     value
                     (str value ":00Z"))]
    (ui-req/instant-value normalized)))

(defn attach-exception [request]
  (require-tenant
   request
   (fn [tenant]
     (let [form (ui-req/parse-form request)
           id (path-id request)
           exception (exceptions/create-manual!
                      {:tenant-id (:tenant/id tenant)
                       :source-ref (:source-ref form)
                       :kind (ui-req/keyword-value (:kind form))
                       :currency (:currency form)
                       :observed-at (observed-at form)
                       :monetary-impact-cents
                       (ui-req/long-value (:monetary-impact-cents form))}
                      (actor tenant))]
       (exceptions/attach-to-dispute!
        (:tenant/id tenant)
        {:exception-id (:exception/id exception) :dispute-id id}
        (actor tenant))
       (redirect (str "/disputes/" id))))))

(defn counterparties-list [request]
  (require-tenant
   request
   (fn [tenant] (html (pages/counterparties-page (:tenant/id tenant))))))

(defn counterparty-detail [request]
  (require-tenant
   request
   (fn [tenant]
     (html (pages/counterparty-detail-page (:tenant/id tenant)
                                           (path-id request))))))

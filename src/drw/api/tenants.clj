(ns drw.api.tenants
  (:require [clojure.string :as str]
            [drw.http.json :as json]
            [drw.tenants.store :as store]))

(defn- validation-error [field issue]
  (json/error-response 400 "VALIDATION_ERROR" "Request validation failed"
                       [{:path (name field) :issue issue}]))

(defn- forbidden [message]
  (json/error-response 403 "FORBIDDEN" message))

(defn register-handler [cfg]
  (fn [request]
    (if (= false (:self-registration-enabled cfg))
      (forbidden "Self registration is disabled")
      (let [body (json/parse-object (:body request))
            name (get body :name)
            legal-name (or (get body :legal_name) (get body :legalName))]
        (if (str/blank? name)
          (validation-error :name "must be non-empty")
          (let [{:keys [tenant api-key]}
                (store/register-tenant! {:name name
                                         :legal-name legal-name}
                                        cfg)]
            (json/response 201 (store/registration-response tenant api-key))))))))

(defn profile-handler [_cfg]
  (fn [request]
    (let [tenant-id (get-in request [:current-tenant :tenant-id])]
      (if-let [tenant (store/tenant-by-id tenant-id)]
        (json/response 200 (store/tenant-profile tenant))
        (json/error-response 404 "NOT_FOUND" "Tenant not found")))))

(defn rotate-key-handler [cfg]
  (fn [request]
    (let [tenant-id (get-in request [:current-tenant :tenant-id])]
      (if-let [tenant (store/tenant-by-id tenant-id)]
        (let [{:keys [api-key]} (store/rotate-api-key! (:tenant/id tenant) cfg)]
          (json/response 200 {:apiKey api-key}))
        (json/error-response 404 "NOT_FOUND" "Tenant not found")))))

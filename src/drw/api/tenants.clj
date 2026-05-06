(ns drw.api.tenants
  (:require [clojure.string :as str]
            [drw.api.responses :as responses]
            [drw.tenants.store :as store]))

(defn- validation-error [field issue]
  (responses/error-response 400 "VALIDATION_ERROR" "Request validation failed"
                            [{:path (name field) :issue issue}]))

(defn- forbidden [message]
  (responses/error-response 403 "FORBIDDEN" message))

(defn register-handler [cfg]
  (fn [request]
    (if (= false (:self-registration-enabled cfg))
      (forbidden "Self registration is disabled")
      (let [body (responses/parse-object (:body request))
            name (get body :name)
            legal-name (or (get body :legal_name) (get body :legalName))]
        (if (str/blank? name)
          (validation-error :name "must be non-empty")
          (let [{:keys [tenant api-key]}
                (store/register-tenant! {:name name
                                         :legal-name legal-name}
                                        cfg)]
            (responses/response 201
                                (store/registration-response tenant api-key))))))))

(defn profile-handler [_cfg]
  (fn [request]
    (let [tenant-id (get-in request [:current-tenant :tenant-id])]
      (if-let [tenant (store/tenant-by-id tenant-id)]
        (responses/response 200 (store/tenant-profile tenant))
        (responses/error-response 404 "NOT_FOUND" "Tenant not found")))))

(defn rotate-key-handler [cfg]
  (fn [request]
    (let [tenant-id (get-in request [:current-tenant :tenant-id])]
      (if-let [tenant (store/tenant-by-id tenant-id)]
        (let [{:keys [api-key]} (store/rotate-api-key! (:tenant/id tenant) cfg)]
          (responses/response 200 {:apiKey api-key}))
        (responses/error-response 404 "NOT_FOUND" "Tenant not found")))))

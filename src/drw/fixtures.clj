(ns drw.fixtures
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def tenant-identity-fields
  [:tenant/name
   :tenant/legal-name
   :tenant/full-legal-name
   :tenant/display-name
   :tenant/address
   :tenant/registration
   :tenant/contact
   :tenant/wordmark-url
   :tenant/brand-primary-hex
   :tenant/locale
   :tenant/timezone])

(def required-tenant-fields
  (into tenant-identity-fields
        [:tenant/id :tenant/slug :tenant/api-key-hash
         :tenant/api-key-prefix :tenant/is-active :tenant/created-at]))

(defn- read-resource-edn [path]
  (if-let [resource (io/resource path)]
    (edn/read-string (slurp resource))
    (throw (ex-info "resource not found" {:path path}))))

(defn- blank-value? [value]
  (or (nil? value)
      (and (string? value) (str/blank? value))))

(defn validate-tenants! [tenants]
  (doseq [tenant tenants]
    (let [missing (filter #(blank-value? (get tenant %))
                          required-tenant-fields)]
      (when (seq missing)
        (throw (ex-info "tenant fixture missing required identity fields"
                        {:tenant (:tenant/slug tenant)
                         :missing (vec missing)})))))
  tenants)

(defn load-tenants
  []
  (validate-tenants! (read-resource-edn "fixtures/tenants.edn")))

(defn tenants-by-slug
  []
  (into {} (map (juxt :tenant/slug identity)) (load-tenants)))

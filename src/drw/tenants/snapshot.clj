(ns drw.tenants.snapshot
  (:require [clojure.string :as str]
            [drw.db.scope :as scope]
            [drw.fixtures :as fixtures]))

(def tenant-snapshot-fields
  {:tenant/id :id
   :tenant/name :name
   :tenant/slug :slug
   :tenant/legal-name :legal-name
   :tenant/full-legal-name :full-legal-name
   :tenant/display-name :display-name
   :tenant/address :address
   :tenant/registration :registration
   :tenant/contact :contact
   :tenant/wordmark-url :wordmark-url
   :tenant/brand-primary-hex :brand-primary-hex
   :tenant/locale :locale
   :tenant/timezone :timezone})

(defn- require-snapshot-field [tenant source-key target-key]
  (let [value (get tenant source-key)]
    (when (or (nil? value) (and (string? value) (str/blank? value)))
      (throw (ex-info "tenant snapshot missing required field"
                      {:tenant-id (:tenant/id tenant)
                       :field source-key})))
    [target-key value]))

(defn tenant->snapshot [tenant]
  (into {}
        (map (fn [[source-key target-key]]
               (require-snapshot-field tenant source-key target-key)))
        tenant-snapshot-fields))

(defn capture-tenant-snapshot [tenant-source tenant-id]
  (tenant->snapshot
   (scope/entity-by-tenant-id tenant-source tenant-id :tenant/id)))

(defn tenant-identity-literals [tenant]
  (->> fixtures/tenant-identity-fields
       (keep tenant)
       (filter string?)
       (remove str/blank?)))

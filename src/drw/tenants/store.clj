(ns drw.tenants.store
  (:require [clojure.string :as str]
            [drw.audit.recorder :as recorder]
            [drw.fixtures :as fixtures])
  (:import [java.security MessageDigest SecureRandom]
           [java.time Instant]
           [java.util UUID]))

(defonce tenants* (atom {}))
(defonce prefixes* (atom {}))
(defonce audit-log* (atom []))
(def secure-random (SecureRandom.))

(defn- bytes->hex [bytes]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)))

(defn sha256-hex [value]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes value "UTF-8"))]
    (bytes->hex digest)))

(defn- api-key-hash [api-key]
  (str "sha256:" (sha256-hex api-key)))

(defn- constant-time= [a b]
  (MessageDigest/isEqual (.getBytes (or a "") "UTF-8")
                         (.getBytes (or b "") "UTF-8")))

(def dummy-hash (api-key-hash "drw_dummy_invalid_api_key"))

(defn- random-hex [byte-count]
  (let [bytes (byte-array byte-count)]
    (.nextBytes secure-random bytes)
    (bytes->hex bytes)))

(defn generate-api-key [{:keys [api-key-prefix]}]
  (str (or api-key-prefix "drw_live_") (random-hex 24)))

(defn api-key-prefix [api-key]
  (subs api-key 0 (min 12 (count api-key))))

(defn- slugify [name]
  (let [slug (-> name
                 str/lower-case
                 (str/replace #"[^a-z0-9]+" "-")
                 (str/replace #"(^-|-$)" ""))]
    (if (str/blank? slug) "tenant" slug)))

(defn- unique-slug [base tenants]
  (let [existing (set (map :tenant/slug (vals tenants)))]
    (loop [candidate base
           n 2]
      (if (contains? existing candidate)
        (recur (str base "-" n) (inc n))
        candidate))))

(defn reset-store! []
  (let [tenants (into {} (map (juxt :tenant/id identity)) (fixtures/load-tenants))
        prefixes (into {} (keep (fn [[id tenant]]
                                  (when-let [prefix (:tenant/api-key-prefix tenant)]
                                    [prefix id])))
                       tenants)]
    (clojure.core/reset! tenants* tenants)
    (clojure.core/reset! prefixes* prefixes)
    (clojure.core/reset! audit-log* [])))

(defn tenant-profile [tenant]
  {:id (str (:tenant/id tenant))
   :name (:tenant/name tenant)
   :slug (:tenant/slug tenant)
   :legalName (:tenant/legal-name tenant)
   :displayName (:tenant/display-name tenant)
   :isActive (:tenant/is-active tenant)})

(defn registration-response [tenant api-key]
  {:id (str (:tenant/id tenant))
   :name (:tenant/name tenant)
   :slug (:tenant/slug tenant)
   :apiKey api-key})

(defn record-audit! [event]
  (let [tx (recorder/audit-tx event)]
    (swap! audit-log* into tx)
    tx))

(defn register-tenant! [{:keys [name legal-name]} cfg]
  (when (str/blank? name)
    (throw (ex-info "tenant name is required"
                    {:type :validation-error
                     :field :name})))
  (let [api-key (generate-api-key cfg)
        prefix (api-key-prefix api-key)
        id (UUID/randomUUID)
        now (java.util.Date/from (Instant/now))
        tenant {:tenant/id id
                :tenant/name name
                :tenant/slug (unique-slug (slugify name) @tenants*)
                :tenant/api-key-hash (api-key-hash api-key)
                :tenant/api-key-prefix prefix
                :tenant/legal-name (or legal-name name)
                :tenant/display-name name
                :tenant/is-active true
                :tenant/created-at now}]
    (swap! tenants* assoc id tenant)
    (swap! prefixes* assoc prefix id)
    (record-audit! {:tenant-id id
                    :actor-kind :system
                    :actor-id "self-registration"
                    :action :tenant.registered
                    :entity-kind :tenant
                    :entity-id id
                    :after-state (tenant-profile tenant)})
    {:tenant tenant :api-key api-key}))

(defn tenant-by-id [tenant-id]
  (get @tenants* tenant-id))

(defn tenant-by-api-key [api-key]
  (let [prefix (when-not (str/blank? api-key) (api-key-prefix api-key))
        tenant-id (get @prefixes* prefix)
        tenant (get @tenants* tenant-id)
        stored-hash (or (:tenant/api-key-hash tenant) dummy-hash)
        presented-hash (if (str/blank? api-key) dummy-hash (api-key-hash api-key))]
    (when (and tenant (constant-time= stored-hash presented-hash))
      tenant)))

(defn disable-tenant! [tenant-id]
  (swap! tenants* update tenant-id assoc :tenant/is-active false))

(defn rotate-api-key! [tenant-id cfg]
  (let [api-key (generate-api-key cfg)
        prefix (api-key-prefix api-key)
        tenant-before (tenant-by-id tenant-id)
        tenant-after (assoc tenant-before
                            :tenant/api-key-hash (api-key-hash api-key)
                            :tenant/api-key-prefix prefix)]
    (swap! tenants* assoc tenant-id tenant-after)
    (swap! prefixes*
           (fn [prefixes]
             (-> (into {} (remove (fn [[_ id]] (= id tenant-id))) prefixes)
                 (assoc prefix tenant-id))))
    (record-audit! {:tenant-id tenant-id
                    :actor-kind :api-key
                    :actor-id (:tenant/api-key-prefix tenant-before)
                    :action :tenant.api-key-rotated
                    :entity-kind :tenant
                    :entity-id tenant-id
                    :before-state {:api-key-prefix (:tenant/api-key-prefix tenant-before)}
                    :after-state {:api-key-prefix prefix}})
    {:tenant tenant-after :api-key api-key}))

(reset-store!)

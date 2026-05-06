(ns drw.domain.exception-validation
  (:require [clojure.string :as str]))

(def valid-source-systems
  #{:invoice-recon :contract-lifecycle :transaction-recon :webhook-engine :manual})

(def valid-kinds
  #{:invoice-discrepancy :contract-breach :contract-conflict
    :payment-mismatch :delivery-failure :manual})

(def default-ingestion-config
  {:auto-merge-enabled false})

(defn- blank? [value]
  (or (nil? value) (and (string? value) (str/blank? value))))

(defn- reject! [message data]
  (throw (ex-info message data)))

(defn require-create-fields! [attrs]
  (doseq [field [:tenant-id :source-ref :kind :currency :observed-at]]
    (when (blank? (get attrs field))
      (reject! (str (name field) " is required")
               {:type :validation-error :field field})))
  (when-not (contains? valid-source-systems
                       (or (:source-system attrs) :manual))
    (reject! "source-system is invalid"
             {:type :validation-error :field :source-system}))
  (when-not (contains? valid-kinds (:kind attrs))
    (reject! "kind is invalid"
             {:type :validation-error :field :kind})))

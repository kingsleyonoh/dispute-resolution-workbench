(ns drw.api.common
  (:require [clojure.string :as str]
            [drw.api.responses :as responses])
  (:import [java.time Instant]
           [java.util Date UUID]))

(defn current-tenant-id [request]
  (get-in request [:current-tenant :tenant-id]))

(defn actor [request]
  {:actor-kind :api-key
   :actor-id (or (get-in request [:current-tenant :slug]) "api")})

(defn- parse-scalar [value]
  (cond
    (nil? value) nil
    (str/starts-with? value "\"") (subs value 1 (dec (count value)))
    (= value "true") true
    (= value "false") false
    (= value "null") nil
    (re-matches #"-?\d+" value) (Long/parseLong value)
    :else value))

(defn parse-body [request]
  (let [body (:body request)]
    (cond
      (map? body) body
      (nil? body) {}
      :else
      (let [text (if (string? body) body (slurp body))]
        (into {}
              (map (fn [[_ k v]] [(keyword k) (parse-scalar v)]))
              (re-seq #"\"([^\"]+)\"\s*:\s*(\"[^\"]*\"|-?\d+|true|false|null)"
                      text))))))

(defn value [m & ks]
  (when-let [k (first (filter #(contains? m %) ks))]
    (get m k)))

(defn uuid-value [value]
  (cond
    (uuid? value) value
    (str/blank? (str value)) nil
    :else (UUID/fromString (str value))))

(defn instant-value [value]
  (cond
    (inst? value) value
    (str/blank? (str value)) nil
    :else (Date/from (Instant/parse (str value)))))

(defn keyword-value [value]
  (when-not (str/blank? (str value))
    (keyword (str/replace (str value) "_" "-"))))

(defn ok [body]
  (responses/response 200 body))

(defn created [body]
  (responses/response 201 body))

(defn not-found [message]
  (responses/error-response 404 "NOT_FOUND" message))

(defn validation-error [field issue]
  (responses/error-response 400 "VALIDATION_ERROR" "Request validation failed"
                            [{:path (name field) :issue issue}]))

(defn conflict [message]
  (responses/error-response 409 "CONFLICT" message))

(defn illegal-transition [message]
  (responses/error-response 422 "ILLEGAL_STATUS_TRANSITION" message))

(defn unprocessable [message]
  (responses/error-response 422 "VALIDATION_ERROR" message))

(defn handle-domain-error [e]
  (let [{:keys [type field]} (ex-data e)
        message (ex-message e)]
    (case type
      :validation-error (validation-error (or field :request) message)
      :dispute/not-found (not-found "Dispute not found")
      :exception/not-found (not-found "Exception not found")
      :correlation/not-found (not-found "Correlation not found")
      :counterparty/not-found (not-found "Counterparty not found")
      :ingestion-source/not-found (not-found "Ingestion source not found")
      :exception/duplicate-source-ref (conflict message)
      :counterparty/duplicate-normalized-name (conflict message)
      :illegal-status-transition (illegal-transition message)
      :dispute/terminal (unprocessable message)
      (responses/error-response 500 "INTERNAL_ERROR"
                                "Unexpected domain error"))))

(defmacro with-domain-errors [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (handle-domain-error e#))))

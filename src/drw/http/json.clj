(ns drw.http.json
  (:require [clojure.string :as str]
            [io.pedestal.interceptor :as pedestal]))

(defn- escape-json [value]
  (-> value
      str
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")))

(declare encode)

(defn- encode-key [k]
  (cond
    (keyword? k) (name k)
    :else (str k)))

(defn- encode-entry [[k v]]
  (str (encode (encode-key k)) ":" (encode v)))

(defn encode [value]
  (cond
    (nil? value) "null"
    (keyword? value) (encode (name value))
    (string? value) (str "\"" (escape-json value) "\"")
    (uuid? value) (encode (str value))
    (inst? value) (encode (str value))
    (or (number? value) (boolean? value)) (str value)
    (map? value) (str "{" (str/join "," (map encode-entry value)) "}")
    (sequential? value) (str "[" (str/join "," (map encode value)) "]")
    :else (encode (str value))))

(def json-content-type "application/json; charset=utf-8")

(defn response
  ([status body] (response status body {}))
  ([status body headers]
   {:status status
    :headers (merge {"Content-Type" json-content-type} headers)
    :body (encode body)}))

(defn error-response
  ([status code message] (error-response status code message nil))
  ([status code message details]
   {:status status
    :headers {"Content-Type" json-content-type}
    :body {:error (cond-> {:code code
                           :message message}
                    (some? details) (assoc :details details))}}))

(defn- json-body? [body]
  (or (map? body) (sequential? body)))

(defn response-encoder []
  (pedestal/interceptor
   {:name ::response-encoder
    :leave (fn [context]
             (if (json-body? (get-in context [:response :body]))
               (update-in context [:response :body] encode)
               context))}))

(defn parse-object [body]
  (let [text (cond
               (nil? body) ""
               (string? body) body
               :else (slurp body))]
    (into {}
          (map (fn [[_ k v]]
                 [(keyword k) v]))
          (re-seq #"\"([^\"]+)\"\s*:\s*\"([^\"]*)\"" text))))

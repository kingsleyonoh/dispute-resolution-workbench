(ns drw.ui.request
  (:require [clojure.string :as str])
  (:import [java.net URLDecoder]
           [java.time Instant]
           [java.util Date UUID]))

(defn- decode [value]
  (URLDecoder/decode (or value "") "UTF-8"))

(defn parse-form [request]
  (let [text (cond
               (nil? (:body request)) ""
               (string? (:body request)) (:body request)
               :else (slurp (:body request)))]
    (into {}
          (map (fn [part]
                 (let [[k v] (str/split part #"=" 2)]
                   [(keyword (str/replace (decode k) "_" "-"))
                    (decode v)])))
          (remove str/blank? (str/split text #"&")))))

(defn uuid-value [value]
  (when-not (str/blank? (str value))
    (UUID/fromString (str value))))

(defn keyword-value [value]
  (when-not (str/blank? (str value))
    (keyword (str value))))

(defn long-value [value]
  (if (str/blank? (str value))
    0
    (Long/parseLong (str value))))

(defn instant-value [value]
  (when-not (str/blank? (str value))
    (Date/from (Instant/parse (str value)))))

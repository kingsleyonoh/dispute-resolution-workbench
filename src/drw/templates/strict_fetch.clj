(ns drw.templates.strict-fetch
  (:require [clojure.string :as str]))

(defn- token-name [path]
  (str/join "." (map name path)))

(defn fetch [context path]
  (loop [value context
         remaining (seq path)
         consumed []]
    (if-not remaining
      value
      (let [segment (first remaining)
            next-consumed (conj consumed segment)]
        (if (and (map? value) (contains? value segment))
          (recur (get value segment) (next remaining) next-consumed)
          (throw (ex-info "Missing template token"
                          {:type :strict-undefined
                           :token (token-name next-consumed)})))))))

(defn missing-value-fn
  ([path] (missing-value-fn path nil))
  ([path _context-map]
   (throw (ex-info "Missing template token"
                   {:type :strict-undefined
                    :token (str path)}))))

(def selmer-options
  {:missing-value-fn missing-value-fn})

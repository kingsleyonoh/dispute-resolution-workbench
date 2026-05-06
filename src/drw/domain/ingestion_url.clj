(ns drw.domain.ingestion-url
  (:import [java.net URI]))

(defn- reject! [message]
  (throw (ex-info message {:type :validation-error :field :base-url})))

(defn uri-host [value]
  (when value
    (let [uri (URI. value)]
      (when-not (= "https" (.getScheme uri))
        (reject! "base-url must use https"))
      (when-not (.getHost uri)
        (reject! "base-url host is required"))
      (.toLowerCase (.getHost uri)))))

(defn private-host? [host]
  (or (= "localhost" host)
      (= "::1" host)
      (re-matches #"127\..*" host)
      (re-matches #"10\..*" host)
      (re-matches #"192\.168\..*" host)
      (re-matches #"172\.(1[6-9]|2\d|3[0-1])\..*" host)
      (re-matches #"169\.254\..*" host)
      (re-matches #"(?i).*\.local" host)))

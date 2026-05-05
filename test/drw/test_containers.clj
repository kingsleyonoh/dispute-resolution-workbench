(ns drw.test-containers
  (:require [clj-test-containers.core :as containers]
            [clojure.edn :as edn])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]))

(def postgres-image "postgres:16-alpine")
(def redis-image "redis:7-alpine")
(def nginx-image "nginx:1.27-alpine")

(def upstream-resource "drw/integration/upstream_stub/api/discrepancies")
(def upstream-container-path "/usr/share/nginx/html/api/discrepancies")

(defn start-upstream-http-stub! []
  (-> (containers/create {:image-name nginx-image
                          :exposed-ports [80]
                          :wait-for {:wait-strategy :port
                                     :startup-timeout 30}})
      (containers/copy-file-to-container! {:path upstream-resource
                                           :container-path upstream-container-path
                                           :type :classpath-resource})
      containers/start!
      (#(assoc % :base-url (str "http://" (:host %) ":"
                                (get-in % [:mapped-ports 80]))))))

(defn call-with-upstream-http-stub [f]
  (let [container (start-upstream-http-stub!)]
    (try
      (f container)
      (finally
        (containers/stop! container)))))

(defmacro with-upstream-http-stub [[binding] & body]
  `(call-with-upstream-http-stub
    (fn [~binding]
      ~@body)))

(defn- path-and-query [^URI uri]
  (cond-> (.getRawPath uri)
    (.getRawQuery uri) (str "?" (.getRawQuery uri))))

(defn- http-request [url headers]
  (let [builder (HttpRequest/newBuilder (URI/create url))]
    (doseq [[k v] headers]
      (.header builder k v))
    (-> builder .GET .build)))

(defn recording-edn-http-send-fn [requests]
  (let [client (HttpClient/newHttpClient)]
    (fn [{:keys [url headers] :as request}]
      (let [uri (URI/create url)
            response (.send client
                            (http-request url headers)
                            (HttpResponse$BodyHandlers/ofString))]
        (swap! requests conj (assoc request :path-and-query
                                    (path-and-query uri)))
        {:status (.statusCode response)
         :body (edn/read-string (.body response))}))))

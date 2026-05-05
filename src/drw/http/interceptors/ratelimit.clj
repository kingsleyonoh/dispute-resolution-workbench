(ns drw.http.interceptors.ratelimit
  (:require [clojure.string :as str]
            [drw.http.json :as json]
            [io.pedestal.interceptor :as pedestal]))

(defonce buckets* (atom {}))

(defn reset-limits! []
  (clojure.core/reset! buckets* {}))

(defn- route-limit [request]
  (let [uri (:uri request)
        method (:request-method request)]
    (cond
      (and (= :post method) (= "/api/tenants/register" uri))
      {:limit 5 :window-ms 60000 :scope :ip}

      (and (= :post method) (= "/api/tenants/rotate-key" uri))
      {:limit 5 :window-ms 3600000 :scope :api-key}

      (str/starts-with? uri "/api/health")
      {:limit 600 :window-ms 60000 :scope :ip}

      :else
      {:limit 60 :window-ms 60000 :scope :api-key})))

(defn- header [request k]
  (or (get-in request [:headers k])
      (get-in request [:headers (str/lower-case k)])))

(defn- default-bucket [request {:keys [scope]}]
  (str (:request-method request) ":" (:uri request) ":"
       (case scope
         :api-key (or (header request "X-API-Key") (:remote-addr request) "unknown")
         :ip (or (:remote-addr request) "unknown"))))

(defn- reserve! [bucket limit window-ms now]
  (let [result (atom nil)]
    (swap! buckets*
           (fn [buckets]
             (let [{:keys [window-start count]}
                   (get buckets bucket {:window-start now :count 0})
                   expired? (>= (- now window-start) window-ms)
                   current (if expired?
                             {:window-start now :count 0}
                             {:window-start window-start :count count})]
               (if (>= (:count current) limit)
                 (do (clojure.core/reset! result {:allowed? false
                                                  :remaining 0})
                     buckets)
                 (let [next-count (inc (:count current))]
                   (clojure.core/reset! result {:allowed? true
                                                :remaining (- limit next-count)})
                   (assoc buckets bucket
                          (assoc current :count next-count)))))))
    @result))

(defn interceptor
  ([] (interceptor {}))
  ([{:keys [limit window-ms bucket-fn]}]
   (pedestal/interceptor
    {:name ::rate-limit
     :enter (fn [context]
              (let [request (:request context)
                    route (route-limit request)
                    limit (or limit (:limit route))
                    window-ms (or window-ms (:window-ms route))
                    bucket ((or bucket-fn default-bucket) request route)
                    result (reserve! bucket limit window-ms
                                     (System/currentTimeMillis))]
                (if (:allowed? result)
                  (assoc-in context [:request :rate-limit] result)
                  (assoc context :response
                         (json/error-response
                          429 "RATE_LIMITED"
                          "Rate limit exceeded")))))})))

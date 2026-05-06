(ns drw.http.interceptors.request-id
  (:require [clojure.string :as str]
            [io.pedestal.interceptor :as pedestal])
  (:import [java.util UUID]))

(defn- header [request k]
  (or (get-in request [:headers k])
      (get-in request [:headers (str/lower-case k)])))

(defn interceptor []
  (pedestal/interceptor
   {:name ::request-id
    :enter (fn [context]
             (let [request-id (or (header (:request context) "X-Request-Id")
                                  (str (UUID/randomUUID)))]
               (assoc-in context [:request :request-id] request-id)))
    :leave (fn [context]
             (let [request-id (get-in context [:request :request-id])]
               (cond-> context
                 request-id
                 (assoc-in [:response :headers "X-Request-Id"] request-id))))}))

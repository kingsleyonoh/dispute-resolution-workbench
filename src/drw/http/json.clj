(ns drw.http.json
  (:require [drw.api.responses :as responses]
            [io.pedestal.interceptor :as pedestal]))

(def response responses/response)
(def error-response responses/error-response)
(def parse-object responses/parse-object)

(defn- json-body? [body]
  (or (map? body) (sequential? body)))

(defn response-encoder []
  (pedestal/interceptor
   {:name ::response-encoder
    :leave (fn [context]
             (if (json-body? (get-in context [:response :body]))
               (update-in context [:response :body] responses/encode)
               context))}))

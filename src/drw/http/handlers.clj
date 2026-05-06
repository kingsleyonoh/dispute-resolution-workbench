(ns drw.http.handlers
  (:import (java.util Base64))
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [drw.api.responses :as responses]
            [drw.observability.metrics :as metrics]
            [drw.observability.readiness :as readiness]
            [drw.ui.pages :as pages]))

(defn health [_request]
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body "{\"status\":\"up\"}"})

(defn ready
  ([cfg request] (ready cfg request {}))
  ([cfg _request opts]
   (let [summary (readiness/ready-summary cfg opts)]
     {:status (if (:ready summary) 200 503)
      :headers {"Content-Type" "application/json; charset=utf-8"}
      :body (responses/encode summary)})))

(defn- header [request k]
  (or (get-in request [:headers k])
      (get-in request [:headers (str/lower-case k)])))

(defn- configured-basic-auth [cfg]
  (let [user (:metrics-basic-auth-user cfg)
        pass (:metrics-basic-auth-pass cfg)]
    (when (and (not (str/blank? user)) (not (str/blank? pass)))
      (str user ":" pass))))

(defn- supplied-basic-auth [request]
  (when-let [auth (header request "Authorization")]
    (when (str/starts-with? auth "Basic ")
      (try
        (String. (.decode (Base64/getDecoder) (subs auth 6)) "UTF-8")
        (catch IllegalArgumentException _ nil)))))

(defn metrics [cfg request]
  (cond
    (false? (:prometheus-enabled cfg))
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "metrics disabled"}

    (and (configured-basic-auth cfg)
         (not= (configured-basic-auth cfg) (supplied-basic-auth request)))
    {:status 401
     :headers {"Content-Type" "text/plain"
               "WWW-Authenticate" "Basic realm=\"metrics\""}
     :body "metrics authentication required"}

    :else
    {:status 200
     :headers {"Content-Type" "text/plain; version=0.0.4"}
     :body (metrics/render)}))

(defn home [_request]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (h/html (pages/login-page nil)))})

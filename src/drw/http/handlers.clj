(ns drw.http.handlers
  (:require [hiccup2.core :as h]
            [drw.ui.pages :as pages]))

(defn health [_request]
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body "{\"status\":\"up\"}"})

(defn home [_request]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (h/html (pages/login-page nil)))})

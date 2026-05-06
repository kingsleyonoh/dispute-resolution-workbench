(ns ^:e2e drw.e2e-api.health-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.http.server :as server])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]))

(defn- get-url [url]
  (let [client (HttpClient/newHttpClient)
        request (-> (HttpRequest/newBuilder)
                    (.uri (URI/create url))
                    .GET
                    .build)]
    (.send client request (HttpResponse$BodyHandlers/ofString))))

(deftest health-and-home-routes-serve-over-real-http
  (let [port 31549
        srv (server/start! {:port port})]
    (try
      (let [health (get-url (str "http://127.0.0.1:" port "/api/health"))
            home (get-url (str "http://127.0.0.1:" port "/"))]
        (is (= 200 (.statusCode health)))
        (is (= "{\"status\":\"up\"}" (.body health)))
        (is (= 200 (.statusCode home)))
        (is (str/includes? (.body home) "Dispute Resolution Workbench"))
        (is (str/includes? (.body home) "htmx.org@2.0.4")))
      (finally
        (server/stop! srv)))))

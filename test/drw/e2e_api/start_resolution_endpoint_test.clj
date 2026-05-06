(ns ^:e2e drw.e2e-api.start-resolution-endpoint-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.state :as domain-state]
            [drw.http.interceptors.ratelimit :as ratelimit]
            [drw.http.server :as server]
            [drw.tenants.store :as store])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]))

(defn- request
  ([method url] (request method url nil nil))
  ([method url body headers]
   (let [builder (-> (HttpRequest/newBuilder)
                     (.uri (URI/create url)))
         builder (reduce (fn [b [k v]] (.header b k v))
                         builder
                         (or headers {}))
         publisher (if body
                     (HttpRequest$BodyPublishers/ofString body)
                     (HttpRequest$BodyPublishers/noBody))]
     (-> (HttpClient/newHttpClient)
         (.send (-> builder (.method method publisher) .build)
                (HttpResponse$BodyHandlers/ofString))))))

(defn- json-string [body key]
  (second (re-find (re-pattern (str "\"" key "\"\\s*:\\s*\"([^\"]+)\""))
                   body)))

(deftest start-resolution-endpoint-works-over-real-http
  (let [port 31560
        base (str "http://127.0.0.1:" port)
        sent (atom nil)
        srv (server/start!
             {:port port
              :api-key-prefix "drw_live_"
              :self-registration-enabled true
              :workflow-engine-enabled true
              :workflow-engine-url "https://workflows.example.invalid"
              :workflow-engine-api-key "example_key"
              :workflow-engine-send-fn
              (fn [req]
                (reset! sent req)
                {:status :started :execution-id "exec-e2e"})})]
    (try
      (store/reset-store!)
      (domain-state/reset-store!)
      (ratelimit/reset-limits!)
      (let [registered (request "POST" (str base "/api/tenants/register")
                                "{\"name\":\"Resolution API\"}"
                                {"Content-Type" "application/json"})
            api-key (json-string (.body registered) "apiKey")
            auth {"X-API-Key" api-key "Content-Type" "application/json"}
            playbook (request "POST" (str base "/api/playbooks")
                              "{\"code\":\"credit-note-and-refund\",\"display_name\":\"Credit note and refund\",\"workflow_engine_workflow_id\":\"wf-credit\"}"
                              auth)
            playbook-id (json-string (.body playbook) "id")
            created (request "POST" (str base "/api/disputes")
                             "{\"title\":\"Resolution target\",\"description\":\"Needs workflow\",\"category\":\"billing\",\"severity\":\"high\",\"currency\":\"EUR\"}"
                             auth)
            dispute-id (json-string (.body created) "id")
            assigned (request "PATCH" (str base "/api/disputes/" dispute-id
                                           "/assign")
                              "{\"user_id\":\"33333333-3333-3333-3333-333333333333\"}"
                              auth)
            investigating (request "PATCH" (str base "/api/disputes/"
                                                dispute-id "/transition")
                                   "{\"to_status\":\"investigating\"}" auth)
            started (request "POST" (str base "/api/disputes/" dispute-id
                                         "/start-resolution")
                             (str "{\"playbook_id\":\"" playbook-id "\"}") auth)
            detail (request "GET" (str base "/api/disputes/" dispute-id)
                            nil {"X-API-Key" api-key})]
        (is (= 201 (.statusCode started)))
        (is (= 200 (.statusCode assigned)))
        (is (= 200 (.statusCode investigating)))
        (is (str/includes? (.body started) "\"executionId\":\"exec-e2e\""))
        (is (str/includes? (.body detail) "\"status\":\"resolving\""))
        (is (= "wf-credit" (:workflow-id @sent))))
      (finally
        (server/stop! srv)))))

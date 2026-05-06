(ns ^:e2e drw.e2e-api.playbooks-endpoints-test
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

(def create-body
  "{\"code\":\"credit-note-and-refund\",\"display_name\":\"Credit note and refund\",\"description\":\"Issue credit note\",\"workflow_engine_workflow_id\":\"wf-credit\",\"required_inputs_schema\":\"{}\",\"is_active\":true}")

(deftest playbook-endpoints-work-over-real-http
  (let [port 31558
        base (str "http://127.0.0.1:" port)
        srv (server/start! {:port port
                            :api-key-prefix "drw_live_"
                            :self-registration-enabled true})]
    (try
      (store/reset-store!)
      (domain-state/reset-store!)
      (ratelimit/reset-limits!)
      (let [tenant-a (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Playbook A\"}"
                              {"Content-Type" "application/json"})
            tenant-b (request "POST" (str base "/api/tenants/register")
                              "{\"name\":\"Playbook B\"}"
                              {"Content-Type" "application/json"})
            key-a (json-string (.body tenant-a) "apiKey")
            key-b (json-string (.body tenant-b) "apiKey")
            created (request "POST" (str base "/api/playbooks")
                             create-body
                             {"X-API-Key" key-a
                              "Content-Type" "application/json"})
            id (json-string (.body created) "id")
            listed (request "GET" (str base "/api/playbooks")
                            nil {"X-API-Key" key-a})
            cross-list (request "GET" (str base "/api/playbooks")
                                nil {"X-API-Key" key-b})
            updated (request "PUT" (str base "/api/playbooks/" id)
                             "{\"display_name\":\"Refund package\"}"
                             {"X-API-Key" key-a
                              "Content-Type" "application/json"})
            disabled (request "DELETE" (str base "/api/playbooks/" id)
                              nil {"X-API-Key" key-a})
            cross-disable (request "DELETE" (str base "/api/playbooks/" id)
                                   nil {"X-API-Key" key-b})]
        (is (= 201 (.statusCode created)))
        (is (= 200 (.statusCode listed)))
        (is (str/includes? (.body listed) "Credit note and refund"))
        (is (= 200 (.statusCode cross-list)))
        (is (not (str/includes? (.body cross-list) "Credit note and refund")))
        (is (= 200 (.statusCode updated)))
        (is (str/includes? (.body updated) "Refund package"))
        (is (= 200 (.statusCode disabled)))
        (is (str/includes? (.body disabled) "\"isActive\":false"))
        (is (= 404 (.statusCode cross-disable))))
      (finally
        (server/stop! srv)))))

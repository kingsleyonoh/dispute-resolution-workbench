(ns ^:e2e drw.e2e-api.ui-start-resolution-flow-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.domain.state :as domain-state]
            [drw.http.interceptors.ratelimit :as ratelimit]
            [drw.http.server :as server]
            [drw.tenants.store :as store])
  (:import [java.net HttpURLConnection URI URLEncoder]))

(defn- encode-form [params]
  (str/join "&"
            (map (fn [[k v]]
                   (str (URLEncoder/encode (name k) "UTF-8")
                        "="
                        (URLEncoder/encode (str v) "UTF-8")))
                 params)))

(defn- read-body [^HttpURLConnection conn]
  (let [stream (if (< (.getResponseCode conn) 400)
                 (.getInputStream conn)
                 (.getErrorStream conn))]
    (if stream (slurp stream) "")))

(defn- request
  ([method url] (request method url nil nil))
  ([method url body headers]
   (let [conn ^HttpURLConnection (.openConnection (.toURL (URI/create url)))]
     (.setInstanceFollowRedirects conn false)
     (.setRequestMethod conn method)
     (doseq [[k v] (or headers {})]
       (.setRequestProperty conn k v))
     (when body
       (.setDoOutput conn true)
       (with-open [out (.getOutputStream conn)]
         (.write out (.getBytes body "UTF-8"))))
     {:status (.getResponseCode conn)
      :body (read-body conn)})))

(defn- form [url params api-key]
  (request "POST" url (encode-form params)
           (cond-> {"Content-Type" "application/x-www-form-urlencoded"}
             api-key (assoc "X-API-Key" api-key))))

(defn- json-string [body key]
  (second (re-find (re-pattern (str "\"" key "\"\\s*:\\s*\"([^\"]+)\""))
                   body)))

(deftest operator-starts-resolution-through-ui-over-http
  (let [port 31561
        base (str "http://127.0.0.1:" port)
        srv (server/start!
             {:port port
              :api-key-prefix "drw_live_"
              :self-registration-enabled true
              :workflow-engine-enabled true
              :workflow-engine-url "https://workflows.example.invalid"
              :workflow-engine-api-key "example_key"
              :workflow-engine-send-fn
              (fn [_] {:status :started :execution-id "exec-ui"})})]
    (try
      (store/reset-store!)
      (domain-state/reset-store!)
      (ratelimit/reset-limits!)
      (let [registered (request "POST" (str base "/api/tenants/register")
                                "{\"name\":\"Resolution UI\"}"
                                {"Content-Type" "application/json"})
            api-key (json-string (:body registered) "apiKey")
            auth {"X-API-Key" api-key "Content-Type" "application/json"}
            playbook (request "POST" (str base "/api/playbooks")
                              "{\"code\":\"credit-note-and-refund\",\"display_name\":\"Credit note and refund\",\"workflow_engine_workflow_id\":\"wf-credit\"}"
                              auth)
            playbook-id (json-string (:body playbook) "id")
            created (request "POST" (str base "/api/disputes")
                             "{\"title\":\"Resolution target\",\"description\":\"Needs workflow\",\"category\":\"billing\",\"severity\":\"high\",\"currency\":\"EUR\"}"
                             auth)
            dispute-id (json-string (:body created) "id")
            _assigned (form (str base "/disputes/" dispute-id "/assign")
                            {:user_id "33333333-3333-3333-3333-333333333333"}
                            api-key)
            _investigating (form (str base "/disputes/" dispute-id
                                      "/transition")
                                 {:to_status "investigating"}
                                 api-key)
            detail (request "GET" (str base "/disputes/" dispute-id)
                            nil {"X-API-Key" api-key})
            started (form (str base "/disputes/" dispute-id
                               "/start-resolution")
                          {:playbook_id playbook-id
                           :inputs_json "{}"}
                          api-key)
            after (request "GET" (str base "/disputes/" dispute-id)
                           nil {"X-API-Key" api-key})]
        (is (= 200 (:status detail)))
        (is (str/includes? (:body detail) "Start resolution"))
        (is (= 303 (:status started)))
        (is (str/includes? (:body after) "resolving"))
        (is (str/includes? (:body after) "exec-ui")))
      (finally
        (server/stop! srv)))))

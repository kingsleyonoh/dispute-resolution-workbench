(ns ^:e2e drw.e2e-api.ui-playbooks-flow-test
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

(deftest operator-manages-playbooks-through-ui-over-http
  (let [port 31559
        base (str "http://127.0.0.1:" port)
        srv (server/start! {:port port
                            :api-key-prefix "drw_live_"
                            :self-registration-enabled true})]
    (try
      (store/reset-store!)
      (domain-state/reset-store!)
      (ratelimit/reset-limits!)
      (let [registered (request "POST" (str base "/api/tenants/register")
                                "{\"name\":\"Playbook UI\"}"
                                {"Content-Type" "application/json"})
            api-key (json-string (:body registered) "apiKey")
            settings (request "GET" (str base "/settings/playbooks")
                              nil {"X-API-Key" api-key})
            created (form (str base "/settings/playbooks")
                          {:code "credit-note-and-refund"
                           :display_name "Credit note and refund"
                           :description "Issue credit note"
                           :workflow_engine_workflow_id "wf-credit"
                           :required_inputs_schema "{}"
                           :is_active "true"}
                          api-key)
            after-create (request "GET" (str base "/settings/playbooks")
                                  nil {"X-API-Key" api-key})
            playbook-id (second (re-find #"/settings/playbooks/([^/]+)/disable"
                                         (:body after-create)))
            disabled (form (str base "/settings/playbooks/" playbook-id
                                "/disable")
                           {} api-key)
            after-disable (request "GET" (str base "/settings/playbooks")
                                   nil {"X-API-Key" api-key})]
        (is (= 200 (:status settings)))
        (is (str/includes? (:body settings) "Playbook settings"))
        (is (= 303 (:status created)))
        (is (str/includes? (:body after-create) "Credit note and refund"))
        (is (= 303 (:status disabled)))
        (is (str/includes? (:body after-disable) "inactive")))
      (finally
        (server/stop! srv)))))

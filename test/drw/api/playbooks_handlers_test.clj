(ns drw.api.playbooks-handlers-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.api.playbooks :as api-playbooks]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))

(defn- req
  ([tenant-id] (req tenant-id nil nil nil))
  ([tenant-id body path-params query-params]
   {:current-tenant {:tenant-id tenant-id :slug "tenant"}
    :body body
    :path-params (or path-params {})
    :query-params (or query-params {})}))

(defn- body-includes? [response value]
  (str/includes? (str (:body response)) value))

(def create-body
  "{\"code\":\"credit-note-and-refund\",\"display_name\":\"Credit note and refund\",\"description\":\"Issue credit note\",\"workflow_engine_workflow_id\":\"wf-credit\",\"required_inputs_schema\":\"{}\",\"is_active\":true}")

(deftest playbook-api-cruds-tenant-scoped-playbooks
  (state/reset-store!)
  (let [created ((api-playbooks/create-handler {})
                 (req tenant-a create-body nil nil))
        id (second (re-find #"\"id\":\"([^\"]+)\"" (:body created)))
        listed ((api-playbooks/list-handler {}) (req tenant-a))
        cross-list ((api-playbooks/list-handler {}) (req tenant-b))
        updated ((api-playbooks/update-handler {})
                 (req tenant-a
                      "{\"display_name\":\"Refund package\",\"is_active\":true}"
                      {:id id} nil))
        disabled ((api-playbooks/delete-handler {})
                  (req tenant-a nil {:id id} nil))
        cross-disable ((api-playbooks/delete-handler {})
                       (req tenant-b nil {:id id} nil))]
    (is (= 201 (:status created)))
    (is (body-includes? created "\"code\":\"credit-note-and-refund\""))
    (is (= 200 (:status listed)))
    (is (body-includes? listed "Credit note and refund"))
    (is (= 200 (:status cross-list)))
    (is (not (body-includes? cross-list "Credit note and refund")))
    (is (= 200 (:status updated)))
    (is (body-includes? updated "Refund package"))
    (is (= 200 (:status disabled)))
    (is (body-includes? disabled "\"isActive\":false"))
    (is (= 404 (:status cross-disable)))))

(deftest playbook-api-rejects-invalid-and-duplicate-payloads
  (state/reset-store!)
  (let [created ((api-playbooks/create-handler {})
                 (req tenant-a create-body nil nil))
        duplicate ((api-playbooks/create-handler {})
                   (req tenant-a create-body nil nil))
        invalid ((api-playbooks/create-handler {})
                 (req tenant-a "{\"display_name\":\"No code\"}" nil nil))]
    (is (= 201 (:status created)))
    (is (= 409 (:status duplicate)))
    (is (= 400 (:status invalid)))))

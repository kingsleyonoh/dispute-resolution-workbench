(ns drw.api.audit-pdf-handlers-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.api.disputes :as api-disputes]
            [drw.domain.disputes :as disputes]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))
(def actor {:actor-kind :user :actor-id "audit-api-test"})

(defn- req [tenant-id id]
  {:current-tenant {:tenant-id tenant-id :slug "tenant"}
   :path-params {:id (str id)}
   :query-params {}})

(defn- dispute! [tenant-id]
  (disputes/create-dispute!
   {:tenant-id tenant-id
    :title "Audit PDF target"
    :description "Download packet."
    :category :billing
    :severity :medium
    :currency "EUR"
    :created-by :user}
   actor))

(deftest audit-pdf-api-generates-and-streams-tenant-scoped-pdf
  (state/reset-store!)
  (let [dispute (dispute! tenant-a)
        cfg {:report-storage-dir "target/test-audit-api"
             :pdf-render-fn (fn [{:keys [html]}]
                              (.getBytes (str "PDF:" html) "UTF-8"))}
        ok ((api-disputes/audit-pdf-handler cfg)
            (req tenant-a (:dispute/id dispute)))
        cross ((api-disputes/audit-pdf-handler cfg)
               (req tenant-b (:dispute/id dispute)))]
    (is (= 200 (:status ok)))
    (is (= "application/pdf" (get-in ok [:headers "Content-Type"])))
    (is (str/starts-with? (String. (:body ok) "UTF-8") "PDF:"))
    (is (= 404 (:status cross)))))

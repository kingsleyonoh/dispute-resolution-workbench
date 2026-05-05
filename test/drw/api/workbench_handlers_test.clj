(ns drw.api.workbench-handlers-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [drw.api.counterparties :as api-counterparties]
            [drw.api.disputes :as api-disputes]
            [drw.api.exceptions :as api-exceptions]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))
(def now "2026-05-05T10:00:00Z")

(defn- reset-domain! []
  (state/reset-store!))

(defn- req
  ([tenant-id] (req tenant-id nil nil nil))
  ([tenant-id body path-params query-params]
   {:current-tenant {:tenant-id tenant-id}
    :body body
    :path-params (or path-params {})
    :query-params (or query-params {})}))

(defn- body-includes? [response value]
  (str/includes? (str (:body response)) value))

(deftest dispute-api-creates-lists-reads-and-rejects-cross-tenant-access
  (reset-domain!)
  (let [create ((api-disputes/create-handler {})
                (req tenant-a
                     (str "{\"title\":\"Invoice mismatch\","
                          "\"description\":\"Totals differ\","
                          "\"category\":\"billing\","
                          "\"severity\":\"high\","
                          "\"currency\":\"EUR\"}")
                     nil nil))
        id (second (re-find #"\"id\":\"([^\"]+)\"" (:body create)))
        list-a ((api-disputes/list-handler {}) (req tenant-a))
        get-a ((api-disputes/get-handler {}) (req tenant-a nil {:id id} nil))
        get-b ((api-disputes/get-handler {}) (req tenant-b nil {:id id} nil))
        invalid ((api-disputes/create-handler {})
                 (req tenant-a "{\"description\":\"missing title\"}" nil nil))]
    (is (= 201 (:status create)))
    (is (body-includes? create "\"status\":\"open\""))
    (is (= 200 (:status list-a)))
    (is (body-includes? list-a "Invoice mismatch"))
    (is (= 200 (:status get-a)))
    (is (= 404 (:status get-b)))
    (is (= 400 (:status invalid)))))

(deftest dispute-action-api-enforces-status-and-attach-rules
  (reset-domain!)
  (let [dispute (disputes/create-dispute!
                 {:tenant-id tenant-a
                  :title "Action target"
                  :description "Needs owner."
                  :category :billing
                  :severity :medium
                  :currency "EUR"
                  :created-by :user}
                 {:actor-kind :user :actor-id "seed"})
        exception (exceptions/create-manual!
                   {:tenant-id tenant-a
                    :source-ref "MAN-ACTION"
                    :kind :manual
                    :currency "EUR"
                    :observed-at #inst "2026-05-05T10:00:00.000-00:00"
                    :monetary-impact-cents 1500}
                   {:actor-kind :user :actor-id "seed"})
        id (str (:dispute/id dispute))
        exception-id (str (:exception/id exception))
        assigned ((api-disputes/assign-handler {})
                  (req tenant-a "{\"user_id\":\"33333333-3333-3333-3333-333333333333\"}"
                       {:id id} nil))
        investigating ((api-disputes/transition-handler {})
                       (req tenant-a "{\"to_status\":\"investigating\"}" {:id id} nil))
        comment ((api-disputes/comment-handler {})
                 (req tenant-a "{\"body\":\"Called the vendor.\"}" {:id id} nil))
        attach ((api-disputes/attach-exception-handler {})
                (req tenant-a (str "{\"exception_id\":\"" exception-id "\"}")
                     {:id id} nil))
        illegal ((api-disputes/transition-handler {})
                 (req tenant-a "{\"to_status\":\"assigned\"}" {:id id} nil))]
    (is (= 200 (:status assigned)))
    (is (= 200 (:status investigating)))
    (is (= 201 (:status comment)))
    (is (= 200 (:status attach)))
    (is (body-includes? attach "\"monetaryImpactCents\":1500"))
    (is (= 422 (:status illegal)))))

(deftest exception-api-creates-lists-and-rejects-duplicates
  (reset-domain!)
  (let [body (str "{\"source_ref\":\"MAN-API-1\","
                  "\"kind\":\"manual\","
                  "\"currency\":\"EUR\","
                  "\"observed_at\":\"" now "\","
                  "\"monetary_impact_cents\":1200}")
        created ((api-exceptions/create-handler {}) (req tenant-a body nil nil))
        duplicate ((api-exceptions/create-handler {}) (req tenant-a body nil nil))
        listed ((api-exceptions/list-handler {})
                (req tenant-a nil nil {:source_system "manual"}))
        invalid ((api-exceptions/create-handler {})
                 (req tenant-a "{\"kind\":\"manual\"}" nil nil))]
    (is (= 201 (:status created)))
    (is (body-includes? created "\"sourceRef\":\"MAN-API-1\""))
    (is (= 409 (:status duplicate)))
    (is (= 200 (:status listed)))
    (is (body-includes? listed "MAN-API-1"))
    (is (= 400 (:status invalid)))))

(deftest counterparty-api-lists-gets-merges-and-hides-other-tenants
  (reset-domain!)
  (let [target (counterparties/create!
                {:tenant-id tenant-a :canonical-name "Target Vendor" :kind :vendor}
                {:actor-kind :user :actor-id "seed"})
        source (counterparties/create!
                {:tenant-id tenant-a :canonical-name "Source Vendor" :kind :vendor}
                {:actor-kind :user :actor-id "seed"})
        other (counterparties/create!
               {:tenant-id tenant-b :canonical-name "Other Vendor" :kind :vendor}
               {:actor-kind :user :actor-id "seed"})
        dispute (disputes/create-dispute!
                 {:tenant-id tenant-a
                  :title "Merge dispute"
                  :description "References source."
                  :category :billing
                  :severity :medium
                  :currency "EUR"
                  :counterparty-id (:counterparty/id source)
                  :created-by :user}
                 {:actor-kind :user :actor-id "seed"})
        list-a ((api-counterparties/list-handler {}) (req tenant-a))
        get-a ((api-counterparties/get-handler {})
               (req tenant-a nil {:id (str (:counterparty/id target))} nil))
        get-cross ((api-counterparties/get-handler {})
                   (req tenant-a nil {:id (str (:counterparty/id other))} nil))
        merged ((api-counterparties/merge-handler {})
                (req tenant-a
                     (str "{\"merge_into_id\":\"" (:counterparty/id target) "\"}")
                     {:id (str (:counterparty/id source))} nil))]
    (is (= 200 (:status list-a)))
    (is (body-includes? list-a "Target Vendor"))
    (is (= 200 (:status get-a)))
    (is (= 404 (:status get-cross)))
    (is (= 200 (:status merged)))
    (is (= (:counterparty/id target)
           (:dispute/counterparty-id
            (disputes/get-by-id tenant-a (:dispute/id dispute)))))
    (is (nil? (counterparties/get-by-id tenant-a (:counterparty/id source))))))

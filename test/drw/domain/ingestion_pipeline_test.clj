(ns drw.domain.ingestion-pipeline-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))
(def actor {:actor-kind :adapter :actor-id "ingestion-test"})
(def now #inst "2026-05-05T10:00:00.000-00:00")

(defn- reset-domain! []
  (state/reset-store!))

(defn- ex-type [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (:type (ex-data e)))))

(defn- vendor! [tenant-id name]
  (counterparties/create!
   {:tenant-id tenant-id
    :canonical-name name
    :kind :vendor
    :external-refs {:invoice-recon (str name "-external")}}
   actor))

(defn- invoice-exception [tenant-id source-ref counterparty-id]
  {:tenant-id tenant-id
   :source-system :invoice-recon
   :source-ref source-ref
   :entity-id "invoice-100"
   :counterparty-id counterparty-id
   :kind :invoice-discrepancy
   :raw-payload {:source_ref source-ref}
   :monetary-impact-cents 5000
   :currency "EUR"
   :observed-at now})

(defn- dispute! [tenant-id counterparty-id]
  (disputes/create-dispute!
   {:tenant-id tenant-id
    :title "Existing invoice dispute"
    :description "Existing dispute for related invoice."
    :category :billing
    :severity :medium
    :currency "EUR"
    :counterparty-id counterparty-id
    :created-by :adapter
    :created-at now}
   actor))

(defn- attached-source! [tenant-id dispute-id counterparty-id]
  (let [exception (exceptions/create-manual!
                   (assoc (invoice-exception tenant-id
                                             "INV-ATTACHED"
                                             counterparty-id)
                          :entity-id "invoice-100")
                   actor)]
    (exceptions/attach-to-dispute!
     tenant-id
     {:exception-id (:exception/id exception)
      :dispute-id dispute-id}
     actor)))

(deftest unmatched-ingestion-creates-tenant-scoped-dispute-and-attaches-source
  (reset-domain!)
  (let [vendor (vendor! tenant-a "Unmatched Vendor")
        result (exceptions/ingest!
                (invoice-exception tenant-a "INV-UNMATCHED"
                                   (:counterparty/id vendor))
                actor)
        exception (:exception result)
        dispute (:dispute result)]
    (is (= :dispute-created (:status result)))
    (is (= (:dispute/id dispute) (:exception/dispute-id exception)))
    (is (= tenant-a (:exception/tenant-id exception)))
    (is (= 1 (count (exceptions/list-by-tenant tenant-a))))
    (is (= 1 (count (disputes/list-by-tenant tenant-a))))
    (is (empty? (exceptions/list-by-tenant tenant-b)))
    (is (= [:dispute-created :exception-attached]
           (map :timeline/kind
                (disputes/list-timeline tenant-a (:dispute/id dispute)))))
    (is (= ["exception.created"
            "dispute.created"
            "exception.attached"
            "dispute.exception_attached"]
           (->> (state/audit-log)
                (remove #(= :counterparty (:audit/entity-kind %)))
                (map :audit/action))))))

(deftest duplicate-source-refs-reject-before-correlation-side-effects
  (reset-domain!)
  (let [vendor (vendor! tenant-a "Duplicate Vendor")
        attrs (invoice-exception tenant-a "INV-DUP" (:counterparty/id vendor))]
    (exceptions/ingest! attrs actor)
    (is (= :exception/duplicate-source-ref
           (ex-type #(exceptions/ingest! attrs actor))))
    (is (= 1 (count (exceptions/list-by-tenant tenant-a))))
    (is (= 1 (count (disputes/list-by-tenant tenant-a))))
    (is (empty? (exceptions/list-correlations tenant-a)))))

(deftest correlated-ingestion-creates-pending-candidate-without-new-dispute
  (reset-domain!)
  (let [vendor (vendor! tenant-a "Review Vendor")
        existing (dispute! tenant-a (:counterparty/id vendor))
        result (exceptions/ingest!
                (dissoc (invoice-exception tenant-a "INV-REVIEW"
                                           (:counterparty/id vendor))
                        :entity-id)
                actor)]
    (is (= :correlation-pending (:status result)))
    (is (= (:dispute/id existing)
           (-> result :correlations first :correlation/target-dispute-id)))
    (is (= :pending (-> result :correlations first :correlation/status)))
    (is (nil? (:exception/dispute-id (:exception result))))
    (is (= 1 (count (disputes/list-by-tenant tenant-a))))
    (is (= 1 (count (exceptions/list-correlations tenant-a))))))

(deftest high-confidence-candidates-auto-merge-only-when-enabled
  (reset-domain!)
  (let [vendor (vendor! tenant-a "High Confidence Vendor")
        existing (dispute! tenant-a (:counterparty/id vendor))]
    (attached-source! tenant-a (:dispute/id existing) (:counterparty/id vendor))
    (let [default-result (exceptions/ingest!
                          (invoice-exception tenant-a "INV-HIGH-1"
                                             (:counterparty/id vendor))
                          actor)]
      (is (= :correlation-pending (:status default-result)))
      (is (= :pending (-> default-result :correlations first
                          :correlation/status)))
      (is (nil? (:exception/dispute-id (:exception default-result)))))
    (let [merged (exceptions/ingest!
                  (invoice-exception tenant-a "INV-HIGH-2"
                                     (:counterparty/id vendor))
                  actor
                  {:auto-merge-enabled true})]
      (is (= :auto-merged (:status merged)))
      (is (= :auto-merged (-> merged :correlation :correlation/status)))
      (is (= (:dispute/id existing)
             (:exception/dispute-id (:exception merged)))))))

(deftest ingestion-never-correlates-across-tenants
  (reset-domain!)
  (let [other-vendor (vendor! tenant-b "Shared Name Vendor")]
    (dispute! tenant-b (:counterparty/id other-vendor))
    (let [result (exceptions/ingest!
                  {:tenant-id tenant-a
                   :source-system :invoice-recon
                   :source-ref "INV-TENANT-A"
                   :counterparty-name "Shared Name Vendor"
                   :kind :invoice-discrepancy
                   :raw-payload {}
                   :monetary-impact-cents 5000
                   :currency "EUR"
                   :observed-at now}
                  actor)]
      (is (= :dispute-created (:status result)))
      (is (= 1 (count (disputes/list-by-tenant tenant-a))))
      (is (= 1 (count (disputes/list-by-tenant tenant-b))))
      (is (empty? (exceptions/list-correlations tenant-a))))))

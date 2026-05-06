(ns drw.domain.correlator-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.correlator :as correlator]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))
(def cp-a #uuid "11111111-1111-1111-1111-111111111111")
(def cp-b #uuid "22222222-2222-2222-2222-222222222222")

(def observed-at #inst "2026-05-05T10:00:00.000-00:00")
(def created-at #inst "2026-05-04T10:00:00.000-00:00")

(def base-exception
  {:exception/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
   :exception/tenant-id tenant-a
   :exception/source-system :invoice-recon
   :exception/source-ref "INV-100"
   :exception/entity-id "invoice-100"
   :exception/counterparty-id cp-a
   :exception/kind :invoice-discrepancy
   :exception/monetary-impact-cents 10M
   :exception/currency "EUR"
   :exception/observed-at observed-at})

(def base-dispute
  {:dispute/id #uuid "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
   :dispute/tenant-id tenant-a
   :dispute/counterparty-id cp-a
   :dispute/status :assigned
   :dispute/category :billing
   :dispute/monetary-impact-cents 10M
   :dispute/currency "EUR"
   :dispute/created-at created-at})

(def base-attached
  [{:exception/id #uuid "cccccccc-cccc-cccc-cccc-cccccccccccc"
    :exception/tenant-id tenant-a
    :exception/dispute-id (:dispute/id base-dispute)
    :exception/source-system :invoice-recon
    :exception/source-ref "INV-100"
    :exception/entity-id "invoice-100"
    :exception/kind :invoice-discrepancy}])

(deftest scorer-uses-all-strong-signals-and-explains-deterministically
  (let [candidate (correlator/score-candidate
                   tenant-a
                   base-exception
                   base-dispute
                   base-attached)]
    (is (= (:dispute/id base-dispute) (:dispute-id candidate)))
    (is (= 1.0 (:score candidate)))
    (is (= :auto-merge (:band candidate)))
    (is (= "source-ref matched attached exception; entity-id matched attached exception; counterparty matched; currency matched; amount within 10%; observed within 72h; category matched"
           (:rationale candidate)))
    (is (= [{:signal :source-ref :status :match :points 0.15}
            {:signal :entity-id :status :match :points 0.15}
            {:signal :counterparty :status :match :points 0.25}
            {:signal :currency :status :match :points 0.1}
            {:signal :amount :status :match :points 0.15}
            {:signal :date :status :match :points 0.1}
            {:signal :category :status :match :points 0.1}]
           (:explanation candidate)))))

(deftest scoring-produces-review-band-from-business-signals-without-source-ids
  (let [exception (dissoc base-exception :exception/source-ref
                          :exception/entity-id)
        candidate (correlator/score-candidate
                   tenant-a exception base-dispute base-attached)]
    (is (= 0.7 (:score candidate)))
    (is (= :review (:band candidate)))
    (is (= [:missing :missing :match :match :match :match :match]
           (map :status (:explanation candidate))))))

(deftest weak-or-missing-signals-do-not-propose-correlations
  (let [weak-exception (-> base-exception
                           (dissoc :exception/source-ref
                                   :exception/entity-id)
                           (assoc :exception/currency "USD"
                                  :exception/monetary-impact-cents 99M
                                  :exception/observed-at
                                  #inst "2026-01-01T10:00:00.000-00:00"
                                  :exception/kind :payment-mismatch))
        missing-counterparty (dissoc base-exception :exception/counterparty-id)]
    (is (= :weak (:band (correlator/score-candidate
                         tenant-a weak-exception base-dispute base-attached))))
    (is (empty? (correlator/score-candidates
                 tenant-a weak-exception [base-dispute] base-attached)))
    (is (empty? (correlator/score-candidates
                 tenant-a missing-counterparty [base-dispute] base-attached)))))

(deftest scoring-is-tenant-isolated-and-ignores-cross-tenant-collisions
  (let [other-dispute (assoc base-dispute
                             :dispute/id #uuid "dddddddd-dddd-dddd-dddd-dddddddddddd"
                             :dispute/tenant-id tenant-b)
        other-attached [(assoc (first base-attached)
                               :exception/tenant-id tenant-b
                               :exception/dispute-id
                               (:dispute/id other-dispute))]]
    (is (empty? (correlator/score-candidates
                 tenant-a base-exception [other-dispute] other-attached)))
    (is (= :no-match
           (:band (correlator/score-candidate
                   tenant-a base-exception other-dispute other-attached))))))

(deftest candidate-list-is-sorted-and-keeps-top-ties-for-operator-review
  (let [tie-dispute (assoc base-dispute
                           :dispute/id #uuid "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")
        candidates (correlator/score-candidates
                    tenant-a
                    (dissoc base-exception :exception/source-ref
                            :exception/entity-id)
                    [tie-dispute base-dispute]
                    base-attached)]
    (is (= [(:dispute/id tie-dispute) (:dispute/id base-dispute)]
           (map :dispute-id candidates)))
    (is (= [0.7 0.7] (map :score candidates)))
    (is (every? #(= :review (:band %)) candidates))))

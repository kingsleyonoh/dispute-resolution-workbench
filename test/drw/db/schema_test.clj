(ns drw.db.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.db.schema :as schema]))

(deftest section-four-schema-declares-required-tenant-and-audit-attributes
  (let [attrs (set (map :db/ident (schema/attributes)))]
    (is (contains? attrs :tenant/legal-name))
    (is (contains? attrs :tenant/brand-primary-hex))
    (is (contains? attrs :dispute/tenant-id))
    (is (contains? attrs :exception/tenant-id))
    (is (contains? attrs :timeline/tenant-id))
    (is (contains? attrs :audit/after-state))
    (is (contains? attrs :report/tenant-snapshot))))

(deftest section-four-status-transitions-are-validated-before-tx
  (testing "legal dispute transitions produce tx data"
    (is (= [[:db/add 42 :dispute/status :assigned]]
           (schema/transition-dispute-status-tx
            {:db/id 42 :dispute/status :open}
            :assigned))))
  (testing "terminal and same-state dispute transitions are rejected"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"illegal-status-transition"
         (schema/transition-dispute-status-tx
          {:db/id 42 :dispute/status :resolved}
          :assigned)))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"illegal-status-transition"
         (schema/transition-dispute-status-tx
          {:db/id 42 :dispute/status :assigned}
          :assigned)))))

(deftest report-and-correlation-status-transitions-are-terminal-aware
  (testing "report artifacts only leave generating"
    (is (= [[:db/add 99 :report/status :ready]]
           (schema/transition-report-status-tx
            {:db/id 99 :report/status :generating}
            :ready)))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"illegal-status-transition"
         (schema/transition-report-status-tx
          {:db/id 99 :report/status :ready}
          :failed))))
  (testing "correlation candidates only leave pending"
    (is (= [[:db/add 7 :correlation/status :accepted]]
           (schema/transition-correlation-status-tx
            {:db/id 7 :correlation/status :pending}
            :accepted)))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"illegal-status-transition"
         (schema/transition-correlation-status-tx
          {:db/id 7 :correlation/status :accepted}
          :rejected)))))

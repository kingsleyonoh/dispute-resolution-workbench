(ns drw.audit.recorder-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.audit.recorder :as recorder]))

(deftest audit-recorder-builds-insert-only-audit-transactions
  (let [tenant-id #uuid "11111111-1111-1111-1111-111111111111"
        entity-id #uuid "22222222-2222-2222-2222-222222222222"
        tx (recorder/audit-tx
            {:tenant-id tenant-id
             :actor-kind :user
             :actor-id "user-1"
             :action "dispute.status_changed"
             :entity-kind :dispute
             :entity-id entity-id
             :before-state {:status :open}
             :after-state {:status :assigned}
             :occurred-at #inst "2026-05-05T10:00:00.000-00:00"})]
    (is (= 1 (count tx)))
    (is (uuid? (:audit/id (first tx))))
    (is (= tenant-id (:audit/tenant-id (first tx))))
    (is (= "{\"status\":\"assigned\"}"
           (:audit/after-state (first tx))))))

(deftest audit-recorder-rejects-retractions-and-missing-tenant-scope
  (testing "audit txs never include retract operations"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"audit transaction must be append-only"
         (recorder/assert-append-only!
          [[:db/retract 1 :audit/action "x"]]))))
  (testing "tenant id is required because audit rows are tenant scoped"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"tenant-id is required"
         (recorder/audit-tx
          {:actor-kind :system
           :actor-id "system"
           :action "dispute.created"
           :entity-kind :dispute
           :entity-id #uuid "22222222-2222-2222-2222-222222222222"
           :before-state nil
           :after-state {:status :open}})))))

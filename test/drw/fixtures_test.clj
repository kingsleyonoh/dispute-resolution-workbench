(ns drw.fixtures-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.fixtures :as fixtures]))

(deftest fixture-loader-uses-two-distinct-tenant-identities
  (let [tenants (fixtures/load-tenants)
        by-slug (fixtures/tenants-by-slug)]
    (is (= #{"acme-gmbh-de" "globex-inc-us"}
           (set (map :tenant/slug tenants))))
    (is (uuid? (:tenant/id (get by-slug "acme-gmbh-de"))))
    (is (uuid? (:tenant/id (get by-slug "globex-inc-us"))))
    (doseq [field fixtures/tenant-identity-fields]
      (is (not= (get (get by-slug "acme-gmbh-de") field)
                (get (get by-slug "globex-inc-us") field))
          (str field " must differ across tenant fixtures")))))

(deftest tenant-fixture-loader-rejects-missing-identity-fields
  (testing "all Section 4.T fields are mandatory in tenant fixtures"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"tenant fixture missing required identity fields"
         (fixtures/validate-tenants!
          [(dissoc (first (fixtures/load-tenants))
                   :tenant/legal-name)])))))

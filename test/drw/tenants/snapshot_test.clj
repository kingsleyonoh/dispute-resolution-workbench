(ns drw.tenants.snapshot-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [drw.fixtures :as fixtures]
            [drw.tenants.snapshot :as snapshot]))

(defn- snapshot-for [slug]
  (snapshot/capture-tenant-snapshot
   (vals (fixtures/tenants-by-slug))
   (:tenant/id (get (fixtures/tenants-by-slug) slug))))

(deftest captures-all-template-identity-fields-from-tenant-data
  (let [snap (snapshot-for "acme-gmbh-de")]
    (is (= "acme-gmbh-de" (:slug snap)))
    (is (= "Acme GmbH" (:legal-name snap)))
    (is (= "Europe/Berlin" (:timezone snap)))
    (is (str/includes? (:address snap) "Berlin"))
    (is (str/includes? (:contact snap) "ops-acme"))))

(deftest tenant-snapshot-excludes-other-tenant-literals
  (let [snap (snapshot-for "acme-gmbh-de")
        rendered (pr-str snap)]
    (doseq [literal (snapshot/tenant-identity-literals
                     (get (fixtures/tenants-by-slug) "globex-inc-us"))]
      (is (not (str/includes? rendered literal))
          (str "TENANT_IDENTITY_LEAK: " literal)))))

(deftest tenant-snapshot-rejects-cross-tenant-misses
  (testing "unknown tenant ids fail as not found instead of falling back"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"tenant not found"
         (snapshot/capture-tenant-snapshot
          (vals (fixtures/tenants-by-slug))
          #uuid "00000000-0000-0000-0000-000000000000")))))

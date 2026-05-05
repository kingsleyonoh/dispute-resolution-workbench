(ns drw.http.routes-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.http.routes :as routes]))

(deftest route-table-exposes-setup-health-and-root-page
  (let [routes-by-name (into {}
                             (map (fn [[path method _handler _ name]]
                                    [name {:path path :method method}]))
                             (routes/routes))]
    (is (= {:path "/api/health" :method :get} (:health routes-by-name)))
    (is (= {:path "/" :method :get} (:home routes-by-name)))
    (is (= {:path "/login" :method :get} (:login routes-by-name)))
    (is (= {:path "/login" :method :post} (:login-submit routes-by-name)))
    (is (= {:path "/disputes" :method :get} (:ui-disputes-list routes-by-name)))
    (is (= {:path "/disputes" :method :post}
           (:ui-disputes-create routes-by-name)))
    (is (= {:path "/disputes/:id" :method :get}
           (:ui-disputes-detail routes-by-name)))
    (is (= {:path "/counterparties" :method :get}
           (:ui-counterparties-list routes-by-name)))
    (is (= {:path "/api/tenants/register" :method :post}
           (:tenant-register routes-by-name)))
    (is (= {:path "/api/tenants/me" :method :get}
           (:tenant-profile routes-by-name)))
    (is (= {:path "/tenants/me" :method :get}
           (:tenant-profile-compat routes-by-name)))
    (is (= {:path "/api/tenants/rotate-key" :method :post}
           (:tenant-rotate-key routes-by-name)))
    (is (= {:path "/api/disputes" :method :get}
           (:disputes-list routes-by-name)))
    (is (= {:path "/api/disputes" :method :post}
           (:disputes-create routes-by-name)))
    (is (= {:path "/api/disputes/:id" :method :get}
           (:disputes-get routes-by-name)))
    (is (= {:path "/api/exceptions" :method :post}
           (:exceptions-create routes-by-name)))
    (is (= {:path "/api/counterparties" :method :get}
           (:counterparties-list routes-by-name)))))

(deftest rejects-non-boolean-dev-route-toggle
  (testing "route construction rejects malformed setup options"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"dev routes flag must be boolean"
         (routes/routes {:dev-routes "yes"})))))

(ns drw.http.routes-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.http.routes :as routes]))

(deftest route-table-exposes-setup-health-and-root-page
  (let [routes-by-name (into {}
                             (map (fn [[path method _handler _ name]]
                                    [name {:path path :method method}]))
                             (routes/routes))]
    (is (= {:path "/api/health" :method :get} (:health routes-by-name)))
    (is (= {:path "/api/health/ready" :method :get}
           (:health-ready routes-by-name)))
    (is (= {:path "/metrics" :method :get} (:metrics routes-by-name)))
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
    (is (= {:path "/correlations" :method :get}
           (:ui-correlations-list routes-by-name)))
    (is (= {:path "/settings/ingestion" :method :get}
           (:ui-ingestion-settings routes-by-name)))
    (is (= {:path "/settings/ingestion" :method :post}
           (:ui-ingestion-save routes-by-name)))
    (is (= {:path "/settings/ingestion/:id/pull-now" :method :post}
           (:ui-ingestion-pull-now routes-by-name)))
    (is (= {:path "/settings/playbooks" :method :get}
           (:ui-playbooks-settings routes-by-name)))
    (is (= {:path "/settings/playbooks" :method :post}
           (:ui-playbooks-save routes-by-name)))
    (is (= {:path "/settings/playbooks/:id/disable" :method :post}
           (:ui-playbooks-disable routes-by-name)))
    (is (= {:path "/disputes/:id/start-resolution" :method :post}
           (:ui-disputes-start-resolution routes-by-name)))
    (is (= {:path "/disputes/:id/audit-pdf" :method :get}
           (:ui-disputes-audit-pdf routes-by-name)))
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
    (is (= {:path "/api/disputes/:id/start-resolution" :method :post}
           (:disputes-start-resolution routes-by-name)))
    (is (= {:path "/api/disputes/:id/audit-pdf" :method :get}
           (:disputes-audit-pdf routes-by-name)))
    (is (= {:path "/api/exceptions" :method :post}
           (:exceptions-create routes-by-name)))
    (is (= {:path "/api/exceptions/from-hub" :method :post}
           (:exceptions-from-hub routes-by-name)))
    (is (= {:path "/api/correlations" :method :get}
           (:correlations-list routes-by-name)))
    (is (= {:path "/api/correlations/:id/accept" :method :post}
           (:correlations-accept routes-by-name)))
    (is (= {:path "/api/ingestion-sources" :method :get}
           (:ingestion-sources-list routes-by-name)))
    (is (= {:path "/api/ingestion-sources" :method :post}
           (:ingestion-sources-save routes-by-name)))
    (is (= {:path "/api/ingestion-sources/:id/pull-now" :method :post}
           (:ingestion-sources-pull-now routes-by-name)))
    (is (= {:path "/api/ingestion-runs" :method :get}
           (:ingestion-runs-list routes-by-name)))
    (is (= {:path "/api/playbooks" :method :get}
           (:playbooks-list routes-by-name)))
    (is (= {:path "/api/playbooks" :method :post}
           (:playbooks-create routes-by-name)))
    (is (= {:path "/api/playbooks/:id" :method :put}
           (:playbooks-update routes-by-name)))
    (is (= {:path "/api/playbooks/:id" :method :delete}
           (:playbooks-delete routes-by-name)))
    (is (= {:path "/api/counterparties" :method :get}
           (:counterparties-list routes-by-name)))))

(deftest rejects-non-boolean-dev-route-toggle
  (testing "route construction rejects malformed setup options"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"dev routes flag must be boolean"
         (routes/routes {:dev-routes "yes"})))))

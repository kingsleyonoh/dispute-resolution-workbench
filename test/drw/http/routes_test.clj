(ns drw.http.routes-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.http.routes :as routes]))

(deftest route-table-exposes-setup-health-and-root-page
  (let [routes-by-name (into {}
                             (map (fn [[path method _handler _ name]]
                                    [name {:path path :method method}]))
                             (routes/routes))]
    (is (= {:path "/api/health" :method :get} (:health routes-by-name)))
    (is (= {:path "/" :method :get} (:home routes-by-name)))))

(deftest rejects-non-boolean-dev-route-toggle
  (testing "route construction rejects malformed setup options"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"dev routes flag must be boolean"
         (routes/routes {:dev-routes "yes"})))))

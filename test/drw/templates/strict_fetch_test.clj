(ns drw.templates.strict-fetch-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.templates.strict-fetch :as strict]))

(deftest strict-template-fetch-resolves-present-token-paths
  (let [ctx {:tenant {:legal-name "Acme GmbH"
                      :contact {:ops-email "ops@example.invalid"}}}]
    (is (= "Acme GmbH" (strict/fetch ctx [:tenant :legal-name])))
    (is (= "ops@example.invalid"
           (strict/fetch ctx [:tenant :contact :ops-email])))))

(deftest strict-template-fetch-throws-on-undefined-token-paths
  (testing "missing nested tokens fail instead of rendering blank strings"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Missing template token"
         (strict/fetch {:tenant {:legal-name "Acme GmbH"}}
                       [:tenant :address])))))

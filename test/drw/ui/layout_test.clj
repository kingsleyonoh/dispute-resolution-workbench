(ns drw.ui.layout-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [hiccup2.core :as h]
            [drw.ui.layout :as layout]))

(deftest renders-htmx-tailwind-application-shell
  (let [html (str (h/html (layout/app-shell
                           {:title "Workbench"}
                           [:main {:id "content"} "Ready"])))]
    (is (str/includes? html "Dispute Resolution Workbench"))
    (is (str/includes? html "https://unpkg.com/htmx.org@2.0.4"))
    (is (str/includes? html "/assets/app.css"))
    (is (str/includes? html "id=\"content\""))))

(deftest rejects-layouts-without-page-title
  (testing "the shared layout requires a title for accessible document chrome"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"page title is required"
         (layout/app-shell {} [:main "Ready"])))))

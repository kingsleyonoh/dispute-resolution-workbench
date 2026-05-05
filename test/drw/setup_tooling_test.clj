(ns drw.setup-tooling-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(deftest docker-compose-defines-runnable-app-service
  (let [compose (slurp "docker-compose.yml")]
    (is (str/includes? compose "app:"))
    (is (str/includes? compose "command: [\"clojure\", \"-M:dev\"]"))
    (is (str/includes? compose "condition: service_healthy"))
    (is (str/includes? compose "\"3049:3049\""))))

(deftest tailwind-cli-skeleton-is-wired-without-frontend-framework
  (let [package-json (slurp "package.json")
        tailwind-config (slurp "tailwind.config.js")
        input-css (slurp "resources/assets/styles/app.css")]
    (is (str/includes? package-json "\"build:css\""))
    (is (str/includes? package-json "tailwindcss"))
    (is (str/includes? tailwind-config "src/**/*.clj"))
    (is (str/includes? input-css "@tailwind base"))))

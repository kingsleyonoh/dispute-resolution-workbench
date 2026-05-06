(ns drw.ui.csrf
  (:require [drw.ui.request :as ui-req]
            [drw.ui.session :as session]))

(defn error-response []
  {:status 403
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body "CSRF token is invalid"})

(defn require-valid! [request form]
  (when-not (session/valid-csrf? request form)
    (throw (ex-info "CSRF token is invalid" {:type :csrf/invalid}))))

(defn with-form [request handle]
  (let [form (ui-req/parse-form request)]
    (try
      (require-valid! request form)
      (handle form)
      (catch clojure.lang.ExceptionInfo ex
        (if (= :csrf/invalid (:type (ex-data ex)))
          (error-response)
          (throw ex))))))

(ns drw.ui.session-security-test
  (:require [clojure.test :refer [deftest is]]
            [drw.ui.session :as session]))

(deftest csrf-validation-applies-to-cookie-sessions
  (session/reset-sessions!)
  (let [tenant-id #uuid "11111111-1111-1111-1111-111111111111"
        token (session/create-session! tenant-id)
        request {:headers {"cookie" (str session/cookie-name "=" token)}}
        csrf (session/csrf-token request)]
    (is (false? (session/valid-csrf? request {})))
    (is (true? (session/valid-csrf? request {:_csrf csrf})))
    (is (true? (session/valid-csrf? {:headers {"X-API-Key" "drw_live_key"}}
                                    {})))))

(deftest production-session-cookies-are-secure
  (is (not (re-find #"Secure" (session/session-cookie "abc" {:app-env "dev"}))))
  (is (re-find #"Secure" (session/session-cookie "abc" {:app-env "production"})))
  (is (re-find #"Secure" (session/expired-cookie {:app-env "production"}))))

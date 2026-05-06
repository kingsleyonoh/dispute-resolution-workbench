(ns drw.http.interceptors-test
  (:require [clojure.test :refer [deftest is testing]]
            [drw.http.interceptors.auth :as auth]
            [drw.http.interceptors.ratelimit :as ratelimit]
            [drw.tenants.store :as store]))

(deftest auth-interceptor-resolves-api-key-to-current-tenant
  (store/reset-store!)
  (let [{:keys [tenant api-key]} (store/register-tenant!
                                  {:name "Tenant Alpha"
                                   :legal-name "Tenant Alpha LLC"}
                                  {:api-key-prefix "drw_live_"})
        context {:request {:uri "/api/tenants/me"
                           :request-method :get
                           :headers {"x-api-key" api-key}}}
        entered ((:enter (auth/interceptor {})) context)]
    (is (= (:tenant/id tenant)
           (get-in entered [:request :current-tenant :tenant-id])))
    (is (= (:tenant/slug tenant)
           (get-in entered [:request :current-tenant :slug])))))

(deftest auth-interceptor-rejects-missing-invalid-and-disabled-keys
  (store/reset-store!)
  (let [{:keys [api-key]} (store/register-tenant!
                           {:name "Tenant Beta"
                            :legal-name "Tenant Beta LLC"}
                           {:api-key-prefix "drw_live_"})
        tenant-id (:tenant/id (store/tenant-by-api-key api-key))
        protected {:request {:uri "/api/tenants/me"
                             :request-method :get
                             :headers {}}}]
    (testing "missing key returns a 401 response"
      (is (= 401 (get-in ((:enter (auth/interceptor {})) protected)
                         [:response :status]))))
    (testing "invalid key returns a 401 response"
      (is (= 401 (get-in ((:enter (auth/interceptor {}))
                          (assoc-in protected [:request :headers "x-api-key"]
                                    "drw_live_bad_key"))
                         [:response :status]))))
    (testing "disabled tenant returns 403"
      (store/disable-tenant! tenant-id)
      (is (= 403 (get-in ((:enter (auth/interceptor {}))
                          (assoc-in protected [:request :headers "x-api-key"]
                                    api-key))
                         [:response :status]))))))

(deftest public-route-allows-missing-api-key
  (let [context {:request {:uri "/api/tenants/register"
                           :request-method :post
                           :headers {}}}
        entered ((:enter (auth/interceptor {})) context)]
    (is (nil? (:response entered)))
    (is (= true (get-in entered [:request :public-route?])))))

(deftest rate-limit-interceptor-blocks-after-configured-budget
  (ratelimit/reset-limits!)
  (let [interceptor (ratelimit/interceptor
                     {:limit 1
                      :window-ms 60000
                      :bucket-fn (constantly "tenant:key")})
        context {:request {:uri "/api/tenants/me"
                           :remote-addr "127.0.0.1"
                           :headers {"x-api-key" "drw_live_test"}}}]
    (is (nil? (:response ((:enter interceptor) context))))
    (let [blocked ((:enter interceptor) context)]
      (is (= 429 (get-in blocked [:response :status])))
      (is (= "RATE_LIMITED" (get-in blocked [:response :body :error :code]))))))

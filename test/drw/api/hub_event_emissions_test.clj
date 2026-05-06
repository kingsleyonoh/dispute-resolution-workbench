(ns drw.api.hub-event-emissions-test
  (:require [clojure.test :refer [deftest is]]
            [drw.api.disputes :as api-disputes]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenant-id (:tenant/id (get (fixtures/tenants-by-slug) "acme-gmbh-de")))

(defn- req [body path-params]
  {:current-tenant {:tenant-id tenant-id :slug "acme-gmbh-de"}
   :body body
   :path-params (or path-params {})
   :query-params {}})

(deftest dispute-api-emits-hub-events-for-section-7b-mutations
  (state/reset-store!)
  (let [sent (atom [])
        cfg {:notification-hub-enabled true
             :notification-hub-url "https://notify.example.invalid"
             :notification-hub-api-key "example_key"
             :notification-hub-send-fn #(swap! sent conj %)}
        created ((api-disputes/create-handler cfg)
                 (req "{\"title\":\"Hub target\",\"description\":\"Needs owner\",\"category\":\"billing\",\"severity\":\"high\",\"currency\":\"EUR\"}"
                      nil))
        id (second (re-find #"\"id\":\"([^\"]+)\"" (:body created)))
        _assigned ((api-disputes/assign-handler cfg)
                   (req "{\"user_id\":\"33333333-3333-3333-3333-333333333333\"}"
                        {:id id}))
        _status ((api-disputes/transition-handler cfg)
                 (req "{\"to_status\":\"investigating\"}" {:id id}))]
    (is (= ["dispute.created" "dispute.assigned" "dispute.status_changed"]
           (map #(get-in % [:event :event_type]) @sent)))
    (is (= id (get-in (first @sent) [:event :payload :dispute_id])))))

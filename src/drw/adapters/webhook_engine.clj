(ns drw.adapters.webhook-engine
  (:require [drw.adapters.fetcher :as fetcher]
            [drw.adapters.protocol :as adapter]))

(def source :webhook-engine)
(def poll-path "/api/dead-letters")
(def default-limit 100)

(defn- value [payload & keys]
  (some (fn [k]
          (or (get payload k)
              (get payload (name k))))
        keys))

(defn- normalize-dead-letter [tenant-id payload]
  (cond-> {:tenant-id tenant-id
           :source-system source
           :source-ref (value payload :dead_letter_id :dead-letter-id
                              :delivery_id :delivery-id :event_id :event-id
                              :id)
           :source-url (value payload :source_url :source-url)
           :kind :delivery-failure
           :raw-payload payload
           :counterparty-external-ref (value payload :counterparty_id
                                             :counterparty-id)
           :monetary-impact-cents (or (value payload :monetary_impact_cents
                                             :monetary-impact-cents
                                             :amount_cents :amount-cents)
                                      0)
           :currency (value payload :currency)
           :observed-at (value payload :observed_at :observed-at
                               :last_failed_at :last-failed-at
                               :failed_at :failed-at
                               :occurred_at :occurred-at)}
    (value payload :counterparty_name :counterparty-name)
    (assoc :counterparty-name
           (value payload :counterparty_name :counterparty-name))))

(defn- query-params [cursor]
  (if cursor
    {:since cursor :limit default-limit}
    {:limit default-limit}))

(defn- body-dead-letters [body]
  (or (:dead_letters body)
      (get body "dead_letters")
      (:dead-letters body)
      (get body "dead-letters")
      (:items body)
      (get body "items")
      []))

(defn- next-cursor [body cursor]
  (or (:next_cursor body)
      (get body "next_cursor")
      (:next-cursor body)
      cursor))

(defn- fetch-config [tenant-config]
  (assoc tenant-config :source-system source))

(defn- failed [this tenant-id cursor fetch-result]
  (adapter/poll-error
   this
   tenant-id
   {:reason (:reason fetch-result)
    :status (:status fetch-result)
    :upstream-status (:upstream-status fetch-result)
    :attempts (:attempts fetch-result)
    :cursor cursor}))

(defrecord WebhookEngineAdapter []
  adapter/ExceptionAdapter
  (source-system [_] source)
  (poll! [this tenant-config cursor]
    (let [tenant-id (:tenant-id tenant-config)
          fetch-result (fetcher/fetch!
                        (fetch-config tenant-config)
                        {:method :get
                         :path poll-path
                         :query-params (query-params cursor)})]
      (case (:status fetch-result)
        :disabled (assoc (adapter/poll-result this tenant-id [] cursor)
                         :disabled? true)
        :ok (let [body (get-in fetch-result [:response :body])
                  exceptions (map #(normalize-dead-letter tenant-id %)
                                  (body-dead-letters body))]
              (adapter/poll-result this tenant-id exceptions
                                   (next-cursor body cursor)))
        (failed this tenant-id cursor fetch-result))))
  (parse-webhook [_ _ _ _]
    (throw (ex-info "webhook engine inbound events are not wired yet"
                    {:type :adapter/webhook-unsupported
                     :source-system source}))))

(def adapter (->WebhookEngineAdapter))

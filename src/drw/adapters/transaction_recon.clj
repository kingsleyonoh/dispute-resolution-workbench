(ns drw.adapters.transaction-recon
  (:require [drw.adapters.fetcher :as fetcher]
            [drw.adapters.protocol :as adapter]))

(def source :transaction-recon)
(def poll-path "/api/v1/discrepancies")
(def default-limit 100)

(defn- value [payload & keys]
  (some (fn [k]
          (or (get payload k)
              (get payload (name k))))
        keys))

(defn- normalize-discrepancy [tenant-id payload]
  {:tenant-id tenant-id
   :source-system source
   :source-ref (value payload :discrepancy_id :discrepancy-id)
   :source-url (value payload :source_url :source-url)
   :kind :payment-mismatch
   :raw-payload payload
   :counterparty-name (value payload :counterparty_name :counterparty-name)
   :monetary-impact-cents (value payload :amount_cents :amount-cents)
   :currency (value payload :currency)
   :observed-at (value payload :observed_at :observed-at :detected_at
                       :detected-at)})

(defn- query-params [cursor]
  (if cursor
    {:since cursor :limit default-limit}
    {:limit default-limit}))

(defn- body-discrepancies [body]
  (or (:discrepancies body)
      (get body "discrepancies")
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

(defrecord TransactionReconAdapter []
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
                  exceptions (map #(normalize-discrepancy tenant-id %)
                                  (body-discrepancies body))]
              (adapter/poll-result this tenant-id exceptions
                                   (next-cursor body cursor)))
        (failed this tenant-id cursor fetch-result))))
  (parse-webhook [_ _ _ _]
    (throw (ex-info "transaction reconciliation webhooks are not wired yet"
                    {:type :adapter/webhook-unsupported
                     :source-system source}))))

(def adapter (->TransactionReconAdapter))

(ns drw.adapters.invoice-recon
  (:require [drw.adapters.fetcher :as fetcher]
            [drw.adapters.protocol :as adapter]))

(def source :invoice-recon)
(def poll-path "/api/discrepancies")
(def default-limit 100)

(defn- value [payload & keys]
  (some (fn [k]
          (or (get payload k)
              (get payload (name k))))
        keys))

(defn- normalize-discrepancy [tenant-id payload]
  {:tenant-id tenant-id
   :source-system source
   :source-ref (value payload :invoice_id :invoice-id)
   :source-url (value payload :source_url :source-url)
   :kind :invoice-discrepancy
   :raw-payload payload
   :counterparty-external-ref (value payload :vendor_id :vendor-id)
   :monetary-impact-cents (value payload :discrepancy_amount_cents
                                 :discrepancy-amount-cents)
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

(defrecord InvoiceReconAdapter []
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
    (throw (ex-info "invoice reconciliation webhooks are not wired yet"
                    {:type :adapter/webhook-unsupported
                     :source-system source}))))

(def adapter (->InvoiceReconAdapter))

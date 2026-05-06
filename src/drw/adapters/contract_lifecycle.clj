(ns drw.adapters.contract-lifecycle
  (:require [clojure.string :as str]
            [drw.adapters.fetcher :as fetcher]
            [drw.adapters.protocol :as adapter]))

(def source :contract-lifecycle)
(def poll-path "/api/obligations")
(def default-limit 100)

(defn- value [payload & keys]
  (some (fn [k]
          (or (get payload k)
              (get payload (name k))))
        keys))

(defn- subject-kind [headers]
  (case (:subject headers)
    "contract.conflict.detected" :contract-conflict
    "contract.obligation.breached" :contract-breach
    nil))

(defn- payload-kind [payload]
  (let [kind (some-> (value payload :kind) str str/lower-case)]
    (cond
      (or (= "contract-conflict" kind)
          (= "conflict" kind)
          (= "conflict_detected" kind)) :contract-conflict
      :else :contract-breach)))

(defn- normalize-contract-exception
  ([tenant-id payload] (normalize-contract-exception tenant-id payload {}))
  ([tenant-id payload headers]
   {:tenant-id tenant-id
    :source-system source
    :source-ref (value payload :obligation_id :obligation-id
                       :conflict_id :conflict-id :event_id :event-id :id)
    :source-url (value payload :source_url :source-url)
    :kind (or (subject-kind headers) (payload-kind payload))
    :raw-payload payload
    :counterparty-external-ref (value payload :counterparty_id
                                      :counterparty-id)
    :monetary-impact-cents (value payload :financial_exposure_cents
                                  :financial-exposure-cents
                                  :monetary_impact_cents
                                  :monetary-impact-cents)
    :currency (value payload :currency)
    :observed-at (value payload :observed_at :observed-at :occurred_at
                        :occurred-at :detected_at :detected-at)}))

(defn- query-params [cursor]
  (if cursor
    {:status "breached,overdue" :since cursor :limit default-limit}
    {:status "breached,overdue" :limit default-limit}))

(defn- body-obligations [body]
  (or (:obligations body)
      (get body "obligations")
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

(defn- tenant-mismatch? [tenant-config payload]
  (let [payload-tenant (value payload :tenant_id :tenant-id)]
    (and payload-tenant
         (not= (str payload-tenant) (str (:tenant-id tenant-config))))))

(defrecord ContractLifecycleAdapter []
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
                  exceptions (map #(normalize-contract-exception tenant-id %)
                                  (body-obligations body))]
              (adapter/poll-result this tenant-id exceptions
                                   (next-cursor body cursor)))
        (failed this tenant-id cursor fetch-result))))
  (parse-webhook [_ tenant-config payload headers]
    (when (tenant-mismatch? tenant-config payload)
      (throw (ex-info "contract event tenant mismatch"
                      {:type :adapter/tenant-mismatch
                       :tenant-id (:tenant-id tenant-config)})))
    (normalize-contract-exception (:tenant-id tenant-config) payload headers)))

(def adapter (->ContractLifecycleAdapter))

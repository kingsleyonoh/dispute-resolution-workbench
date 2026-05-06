(ns drw.api.exceptions
  (:require [clojure.string :as str]
            [drw.api.common :as api]
            [drw.api.responses :as responses]
            [drw.api.serializers :as serializers]
            [drw.domain.exceptions :as exceptions]
            [drw.security.hmac :as hmac]
            [drw.tenants.store :as tenants]))

(def replay-window-seconds 300)
(defonce hub-deliveries* (atom #{}))

(defn reset-hub-deliveries! []
  (reset! hub-deliveries* #{}))

(defn- create-attrs [tenant-id body]
  {:tenant-id tenant-id
   :dispute-id (some-> (api/value body :dispute_id :disputeId) api/uuid-value)
   :source-system (or (api/keyword-value (api/value body :source_system
                                                    :sourceSystem))
                      :manual)
   :source-ref (api/value body :source_ref :sourceRef)
   :source-url (api/value body :source_url :sourceUrl)
   :kind (api/keyword-value (api/value body :kind))
   :raw-payload (api/value body :raw_payload :rawPayload)
   :monetary-impact-cents (or (api/value body :monetary_impact_cents
                                         :monetaryImpactCents)
                              0)
   :currency (api/value body :currency)
   :observed-at (some-> (api/value body :observed_at :observedAt)
                        api/instant-value)})

(defn list-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)
          query (:query-params request)
          filters {:source-system (api/keyword-value
                                   (api/value query :source_system
                                              :sourceSystem))
                   :dispute-id (some-> (api/value query :dispute_id
                                                  :disputeId)
                                       api/uuid-value)}
          exceptions (exceptions/list-by-tenant tenant-id filters)]
      (api/ok {:exceptions (mapv serializers/exception exceptions)
               :nextCursor nil}))))

(defn create-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            exception (exceptions/create-manual!
                       (create-attrs tenant-id body)
                       (api/actor request))]
        (api/created {:exception (serializers/exception exception)
                      :disputeCreated false})))))

(defn- header [request k]
  (or (get-in request [:headers k])
      (get-in request [:headers (str/lower-case k)])))

(defn- raw-body [request]
  (let [body (:body request)]
    (cond
      (nil? body) ""
      (string? body) body
      :else (slurp body))))

(defn- hmac-error [code message]
  (responses/error-response 401 code message))

(defn- tenant-error [status code message]
  (responses/error-response status code message))

(defn- now-epoch-seconds [cfg]
  (quot (or (:hub-now-ms cfg) (System/currentTimeMillis)) 1000))

(defn- long-value [value]
  (try
    (Long/parseLong (str value))
    (catch NumberFormatException _ nil)))

(defn- validate-hub-replay-headers [request cfg]
  (let [timestamp (header request "X-Hub-Timestamp")
        delivery-id (header request "X-Hub-Delivery-Id")
        epoch (long-value timestamp)
        skew (when epoch (Math/abs (- (now-epoch-seconds cfg) epoch)))]
    (cond
      (str/blank? timestamp)
      {:error (tenant-error 400 "HUB_TIMESTAMP_REQUIRED"
                            "X-Hub-Timestamp is required")}
      (str/blank? delivery-id)
      {:error (tenant-error 400 "HUB_DELIVERY_ID_REQUIRED"
                            "X-Hub-Delivery-Id is required")}
      (or (nil? epoch) (> skew replay-window-seconds))
      {:error (tenant-error 401 "HUB_TIMESTAMP_INVALID"
                            "X-Hub-Timestamp is outside the replay window")}
      :else
      {:timestamp timestamp :delivery-id delivery-id})))

(defn- delivery-key [tenant-id delivery-id]
  [tenant-id delivery-id])

(defn- duplicate-delivery? [tenant-id delivery-id]
  (contains? @hub-deliveries* (delivery-key tenant-id delivery-id)))

(defn- remember-delivery! [tenant-id delivery-id]
  (swap! hub-deliveries* conj (delivery-key tenant-id delivery-id)))

(defn- duplicate-delivery-response [delivery-id]
  (api/ok {:status "duplicate" :deliveryId delivery-id}))

(defn- resolve-hub-tenant [request]
  (let [slug (header request "X-Hub-Tenant-Slug")]
    (cond
      (str/blank? slug)
      {:error (tenant-error 400 "TENANT_REQUIRED"
                            "X-Hub-Tenant-Slug is required")}
      :else
      (if-let [tenant (tenants/tenant-by-slug slug)]
        (if (:tenant/is-active tenant)
          {:tenant tenant}
          {:error (tenant-error 403 "TENANT_DISABLED" "Tenant is disabled")})
        {:error (tenant-error 404 "TENANT_NOT_FOUND" "Tenant not found")}))))

(defn- parse-hub-body [text]
  (let [body (api/parse-body {:body text})]
    (if (and (seq (str/trim text)) (empty? body))
      {:error (api/validation-error :body "Malformed JSON payload")}
      {:body body})))

(defn- hub-attrs [tenant-id body]
  (assoc (create-attrs tenant-id body)
         :source-system (api/keyword-value (api/value body :source_system
                                                      :sourceSystem))))

(defn- ingest-response [result]
  (api/created
   (cond-> {:status (name (:status result))
            :exception (serializers/exception (:exception result))}
     (:dispute result)
     (assoc :dispute (serializers/dispute (:dispute result)))
     (:correlation result)
     (assoc :correlation (serializers/correlation (:correlation result)))
     (seq (:correlations result))
     (assoc :correlations (mapv serializers/correlation
                                (:correlations result))))))

(defn from-hub-handler [cfg]
  (fn [request]
    (let [body-text (raw-body request)
          signature (header request "X-Hub-Signature-256")
          {timestamp :timestamp delivery-id :delivery-id replay-response :error}
          (validate-hub-replay-headers request cfg)
          secret (:hub-ingress-secret cfg)]
      (cond
        replay-response replay-response
        (str/blank? signature)
        (hmac-error "HUB_SIGNATURE_REQUIRED"
                    "X-Hub-Signature-256 is required")
        (not (hmac/valid-signature?
              secret
              (hmac/signed-message timestamp delivery-id body-text)
              signature))
        (hmac-error "HUB_SIGNATURE_INVALID"
                    "X-Hub-Signature-256 is invalid")
        :else (let [{tenant :tenant tenant-response :error}
                    (resolve-hub-tenant request)
                    {payload :body payload-response :error}
                    (parse-hub-body body-text)]
                (or tenant-response
                    payload-response
                    (when (duplicate-delivery? (:tenant/id tenant) delivery-id)
                      (duplicate-delivery-response delivery-id))
                    (api/with-domain-errors
                      (let [response (ingest-response
                                      (exceptions/ingest!
                                       (hub-attrs (:tenant/id tenant) payload)
                                       {:actor-kind :hub
                                        :actor-id "notification-hub"}))]
                        (remember-delivery! (:tenant/id tenant) delivery-id)
                        response))))))))

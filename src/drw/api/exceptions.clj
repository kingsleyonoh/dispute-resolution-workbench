(ns drw.api.exceptions
  (:require [clojure.string :as str]
            [drw.api.common :as api]
            [drw.api.responses :as responses]
            [drw.api.serializers :as serializers]
            [drw.domain.exceptions :as exceptions]
            [drw.security.hmac :as hmac]
            [drw.tenants.store :as tenants]))

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
          secret (:hub-ingress-secret cfg)]
      (cond
        (str/blank? signature)
        (hmac-error "HUB_SIGNATURE_REQUIRED"
                    "X-Hub-Signature-256 is required")
        (not (hmac/valid-signature? secret body-text signature))
        (hmac-error "HUB_SIGNATURE_INVALID"
                    "X-Hub-Signature-256 is invalid")
        :else
        (let [{tenant :tenant tenant-response :error} (resolve-hub-tenant request)
              {payload :body payload-response :error} (parse-hub-body body-text)]
          (or tenant-response
              payload-response
              (api/with-domain-errors
                (ingest-response
                 (exceptions/ingest!
                  (hub-attrs (:tenant/id tenant) payload)
                  {:actor-kind :hub
                   :actor-id "notification-hub"})))))))))

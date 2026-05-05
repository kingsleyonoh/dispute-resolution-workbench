(ns drw.api.serializers)

(defn- str-or-nil [value]
  (when value (str value)))

(defn- name-or-nil [value]
  (when value (name value)))

(defn dispute [entity]
  {:id (str (:dispute/id entity))
   :tenantId (str (:dispute/tenant-id entity))
   :reference (:dispute/reference entity)
   :counterpartyId (str-or-nil (:dispute/counterparty-id entity))
   :title (:dispute/title entity)
   :description (:dispute/description entity)
   :status (name-or-nil (:dispute/status entity))
   :category (name-or-nil (:dispute/category entity))
   :severity (name-or-nil (:dispute/severity entity))
   :monetaryImpactCents (:dispute/monetary-impact-cents entity)
   :currency (:dispute/currency entity)
   :slaDueAt (str-or-nil (:dispute/sla-due-at entity))
   :assignedUserId (str-or-nil (:dispute/assigned-user-id entity))
   :assignedAt (str-or-nil (:dispute/assigned-at entity))
   :createdAt (str-or-nil (:dispute/created-at entity))
   :createdBy (name-or-nil (:dispute/created-by entity))
   :resolvedAt (str-or-nil (:dispute/resolved-at entity))})

(defn exception [entity]
  {:id (str (:exception/id entity))
   :tenantId (str (:exception/tenant-id entity))
   :disputeId (str-or-nil (:exception/dispute-id entity))
   :sourceSystem (name-or-nil (:exception/source-system entity))
   :sourceRef (:exception/source-ref entity)
   :sourceUrl (:exception/source-url entity)
   :kind (name-or-nil (:exception/kind entity))
   :rawPayload (:exception/raw-payload entity)
   :monetaryImpactCents (:exception/monetary-impact-cents entity)
   :currency (:exception/currency entity)
   :observedAt (str-or-nil (:exception/observed-at entity))
   :ingestedAt (str-or-nil (:exception/ingested-at entity))})

(defn timeline-entry [entity]
  {:id (str (:timeline/id entity))
   :disputeId (str (:timeline/dispute-id entity))
   :tenantId (str (:timeline/tenant-id entity))
   :kind (name-or-nil (:timeline/kind entity))
   :actorKind (name-or-nil (:timeline/actor-kind entity))
   :actorId (:timeline/actor-id entity)
   :body (:timeline/body entity)
   :occurredAt (str-or-nil (:timeline/occurred-at entity))})

(defn counterparty [entity]
  {:id (str (:counterparty/id entity))
   :tenantId (str (:counterparty/tenant-id entity))
   :canonicalName (:counterparty/canonical-name entity)
   :normalizedName (:counterparty/normalized-name entity)
   :kind (name-or-nil (:counterparty/kind entity))
   :taxId (:counterparty/tax-id entity)
   :countryCode (:counterparty/country-code entity)
   :externalRefs (:counterparty/external-refs entity)
   :createdAt (str-or-nil (:counterparty/created-at entity))})

(defn correlation [entity]
  {:id (str (:correlation/id entity))
   :tenantId (str (:correlation/tenant-id entity))
   :newExceptionId (str (:correlation/new-exception-id entity))
   :targetDisputeId (str (:correlation/target-dispute-id entity))
   :score (:correlation/score entity)
   :rationale (:correlation/rationale entity)
   :status (name-or-nil (:correlation/status entity))
   :decidedByUserId (str-or-nil (:correlation/decided-by-user-id entity))
   :decidedAt (str-or-nil (:correlation/decided-at entity))
   :createdAt (str-or-nil (:correlation/created-at entity))})

(defn correlation-detail [hydrated]
  (let [candidate (:correlation hydrated)
        exception-entity (:exception hydrated)
        dispute-entity (:target-dispute hydrated)]
    (cond-> {:correlation (correlation candidate)}
      exception-entity (assoc :exception (exception exception-entity))
      dispute-entity (assoc :targetDispute (dispute dispute-entity)))))

(defn ingestion-source [entity]
  {:id (str (:ingestion-source/id entity))
   :tenantId (str (:ingestion-source/tenant-id entity))
   :sourceSystem (name-or-nil (:ingestion-source/source-system entity))
   :displayName (:ingestion-source/display-name entity)
   :isEnabled (:ingestion-source/is-enabled entity)
   :config {:baseUrl (get-in entity [:ingestion-source/config :base-url])
            :apiKeySecretRef (get-in entity [:ingestion-source/config
                                             :api-key-secret-ref])
            :pollIntervalSeconds
            (get-in entity [:ingestion-source/config
                            :poll-interval-seconds])
            :filters (get-in entity [:ingestion-source/config :filters])}
   :cursor (:ingestion-source/cursor entity)
   :lastSuccessfulPullAt
   (str-or-nil (:ingestion-source/last-successful-pull-at entity))
   :lastError (:ingestion-source/last-error entity)})

(defn ingestion-run [entity]
  {:id (str (:ingestion-run/id entity))
   :tenantId (str (:ingestion-run/tenant-id entity))
   :sourceSystem (name-or-nil (:ingestion-run/source-system entity))
   :status (name-or-nil (:ingestion-run/status entity))
   :exceptionsAttempted (:ingestion-run/exceptions-attempted entity)
   :exceptionsStored (:ingestion-run/exceptions-stored entity)
   :exceptionsSkipped (:ingestion-run/exceptions-skipped entity)
   :exceptionsRejected (:ingestion-run/exceptions-rejected entity)
   :sourceRefs (:ingestion-run/source-refs entity)
   :startedAt (str-or-nil (:ingestion-run/started-at entity))
   :finishedAt (str-or-nil (:ingestion-run/finished-at entity))
   :cursor (:ingestion-run/cursor entity)
   :error (:ingestion-run/error entity)})

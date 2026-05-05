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

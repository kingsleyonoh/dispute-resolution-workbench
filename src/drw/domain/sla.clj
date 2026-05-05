(ns drw.domain.sla
  (:require [drw.audit.recorder :as recorder]
            [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.domain.state :as state]
            [drw.ecosystem.hub-client :as hub])
  (:import [java.util UUID]))

(defn- overdue? [now dispute]
  (let [due-at (:dispute/sla-due-at dispute)]
    (and due-at
         (.before due-at now)
         (not (contains? disputes/terminal-statuses
                         (:dispute/status dispute))))))

(defn disputes-with-sla []
  (vec (filter :dispute/sla-due-at (vals @state/disputes*))))

(defn overdue-disputes [now]
  (vec (filter #(overdue? now %) (disputes-with-sla))))

(defn- breach-key [dispute]
  [(:dispute/id dispute) (:dispute/sla-due-at dispute)])

(defn- claim-breach! [dispute]
  (let [k (breach-key dispute)
        claimed (atom false)]
    (swap! state/sla-breaches*
           (fn [breaches]
             (if (contains? breaches k)
               breaches
               (do (reset! claimed true) (conj breaches k)))))
    @claimed))

(defn- counterparty-name [dispute]
  (when-let [id (:dispute/counterparty-id dispute)]
    (:counterparty/canonical-name
     (counterparties/get-by-id (:dispute/tenant-id dispute) id))))

(defn- event-payload [dispute]
  {:tenant_id (str (:dispute/tenant-id dispute))
   :dispute_id (str (:dispute/id dispute))
   :reference (:dispute/reference dispute)
   :counterparty_name (counterparty-name dispute)
   :monetary_impact_cents (:dispute/monetary-impact-cents dispute)
   :severity (name (:dispute/severity dispute))
   :category (name (:dispute/category dispute))
   :sla_due_at (str (:dispute/sla-due-at dispute))
   :deep_link (str "/disputes/" (:dispute/id dispute))})

(defn- append-breach! [cfg now actor dispute]
  (let [tenant-id (:dispute/tenant-id dispute)
        dispute-id (:dispute/id dispute)
        payload (event-payload dispute)
        timeline (state/append-timeline!
                  {:timeline/id (UUID/randomUUID)
                   :timeline/dispute-id dispute-id
                   :timeline/tenant-id tenant-id
                   :timeline/kind :sla-breached
                   :timeline/actor-kind (:actor-kind actor)
                   :timeline/actor-id (:actor-id actor)
                   :timeline/body (recorder/encode-json payload)
                   :timeline/occurred-at now})
        audit (state/append-audit!
               {:tenant-id tenant-id
                :actor-kind (:actor-kind actor)
                :actor-id (:actor-id actor)
                :action "dispute.sla_breached"
                :entity-kind :dispute
                :entity-id dispute-id
                :before-state dispute
                :after-state (assoc dispute :sla-breached-at now)})
        hub-result (hub/emit-event!
                    cfg
                    {:event-type "dispute.sla_breached"
                     :event-id (str (UUID/randomUUID))
                     :payload payload})]
    {:dispute dispute
     :timeline timeline
     :audit audit
     :hub hub-result}))

(defn mark-overdue! [cfg {:keys [now actor]}]
  (let [now (or now (java.util.Date.))
        actor (or actor {:actor-kind :system :actor-id "sla-reaper"})]
    (->> (overdue-disputes now)
         (keep #(when (claim-breach! %)
                  (append-breach! cfg now actor %)))
         vec)))

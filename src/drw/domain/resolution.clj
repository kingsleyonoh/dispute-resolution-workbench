(ns drw.domain.resolution
  (:require [clojure.string :as str]
            [drw.domain.disputes :as disputes]
            [drw.domain.hub-events :as hub-events]
            [drw.domain.state :as state]
            [drw.ecosystem.workflow-client :as workflow])
  (:import [java.util UUID]))

(defn- reject! [message data]
  (throw (ex-info message data)))

(defn- require-playbook! [tenant-id playbook]
  (cond
    (nil? playbook)
    (reject! "playbook is required" {:type :playbook/not-found})
    (not= tenant-id (:playbook/tenant-id playbook))
    (reject! "playbook not found" {:type :playbook/not-found})
    (not (:playbook/is-active playbook))
    (reject! "playbook is inactive" {:type :playbook/inactive})
    (str/blank? (:playbook/workflow-engine-workflow-id playbook))
    (reject! "playbook workflow id is required"
             {:type :validation-error :field :workflow-engine-workflow-id})
    :else playbook))

(defn- workflow-disabled? [result]
  (= :disabled (:status result)))

(defn- execution-id [result]
  (or (:execution-id result)
      (:id result)
      (str (UUID/randomUUID))))

(defn- trigger-data [dispute playbook inputs]
  {:dispute-id (:dispute/id dispute)
   :dispute-reference (:dispute/reference dispute)
   :counterparty-id (:dispute/counterparty-id dispute)
   :monetary-impact-cents (:dispute/monetary-impact-cents dispute)
   :currency (:dispute/currency dispute)
   :playbook-code (:playbook/code playbook)
   :playbook-inputs inputs})

(defn start-resolution! [tenant-id dispute-id playbook inputs cfg actor]
  (let [dispute (or (disputes/get-by-id tenant-id dispute-id)
                    (reject! "dispute not found" {:type :dispute/not-found}))
        playbook (require-playbook! tenant-id playbook)
        workflow-id (:playbook/workflow-engine-workflow-id playbook)
        _ (disputes/ensure-attachable! dispute)
        _ (when (= :resolving (:dispute/status dispute))
            (reject! "resolution already running"
                     {:type :resolution/already-running}))
        result (workflow/trigger-workflow!
                cfg workflow-id (trigger-data dispute playbook inputs))]
    (when (workflow-disabled? result)
      (reject! "Workflow Engine is disabled"
               {:type :resolution/workflow-disabled}))
    (let [execution-id (execution-id result)]
      (disputes/mark-workflow-started! tenant-id dispute-id execution-id actor)
      {:status :started
       :workflow-id workflow-id
       :execution-id execution-id
       :workflow-result result})))

(defn- active-disputes []
  (filter #(and (= :resolving (:dispute/status %))
                (seq (:dispute/workflow-execution-id %)))
          (vals @state/disputes*)))

(defn- terminal-result? [result]
  (contains? #{:succeeded :completed :failed} (:status result)))

(defn- timeline! [dispute kind body actor]
  (state/append-timeline!
   {:timeline/id (UUID/randomUUID)
    :timeline/dispute-id (:dispute/id dispute)
    :timeline/tenant-id (:dispute/tenant-id dispute)
    :timeline/kind kind
    :timeline/actor-kind (:actor-kind actor)
    :timeline/actor-id (:actor-id actor)
    :timeline/body body
    :timeline/occurred-at (java.util.Date.)}))

(defn- apply-result! [cfg dispute result actor]
  (let [tenant-id (:dispute/tenant-id dispute)
        dispute-id (:dispute/id dispute)
        summary (or (:summary result) (:resolution-summary result) "")]
    (case (:status result)
      (:succeeded :completed)
      (do
        (let [resolved (disputes/transition!
                        tenant-id dispute-id
                        {:to :resolved :resolution-summary summary}
                        actor)]
          (hub-events/emit-dispute! cfg "dispute.resolved" resolved))
        (timeline! dispute :workflow-completed summary actor))
      :failed
      (do
        (let [failed (disputes/transition!
                      tenant-id dispute-id
                      {:to :investigating
                       :resolution-summary summary}
                      actor)]
          (hub-events/emit-dispute! cfg "dispute.workflow_failed" failed))
        (timeline! dispute :workflow-failed summary actor)))))

(defn poll-active! [cfg actor]
  (->> (active-disputes)
       (mapv (fn [dispute]
               (let [execution-id (:dispute/workflow-execution-id dispute)
                     result (workflow/execution-status! cfg execution-id)]
                 (when (terminal-result? result)
                   (apply-result! cfg dispute result actor))
                 {:execution-id execution-id
                  :status (:status result)
                  :dispute-id (:dispute/id dispute)})))))

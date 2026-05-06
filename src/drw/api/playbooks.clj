(ns drw.api.playbooks
  (:require [drw.api.common :as api]
            [drw.api.serializers :as serializers]
            [drw.domain.playbooks :as playbooks]))

(defn- playbook-id [request]
  (api/uuid-value (get-in request [:path-params :id])))

(defn- bool-value [value default]
  (if (nil? value) default (boolean value)))

(defn- attrs [body current-id]
  (cond-> {:code (api/value body :code)
           :display-name (api/value body :display_name :displayName)
           :description (api/value body :description)
           :workflow-engine-workflow-id
           (api/value body :workflow_engine_workflow_id
                      :workflowEngineWorkflowId)
           :required-inputs-schema
           (api/value body :required_inputs_schema :requiredInputsSchema)}
    current-id (assoc :id current-id)
    (or (contains? body :is_active) (contains? body :isActive))
    (assoc :is-active (bool-value (api/value body :is_active :isActive) true))))

(defn list-handler [_cfg]
  (fn [request]
    (let [tenant-id (api/current-tenant-id request)]
      (api/ok {:playbooks (mapv serializers/playbook
                                (playbooks/list-by-tenant tenant-id {}))
               :nextCursor nil}))))

(defn create-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            created (playbooks/save! tenant-id (attrs body nil)
                                     (api/actor request))]
        (api/created {:playbook (serializers/playbook created)})))))

(defn update-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            body (api/parse-body request)
            updated (playbooks/save! tenant-id
                                     (attrs body (playbook-id request))
                                     (api/actor request))]
        (api/ok {:playbook (serializers/playbook updated)})))))

(defn delete-handler [_cfg]
  (fn [request]
    (api/with-domain-errors
      (let [tenant-id (api/current-tenant-id request)
            disabled (playbooks/disable! tenant-id (playbook-id request)
                                         (api/actor request))]
        (api/ok {:playbook (serializers/playbook disabled)})))))

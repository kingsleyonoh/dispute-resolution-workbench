(ns drw.ui.playbook-handlers
  (:require [drw.domain.playbooks :as playbooks]
            [drw.ui.csrf :as csrf]
            [drw.ui.handlers :as handlers]
            [drw.ui.playbooks :as playbooks-page]
            [drw.ui.request :as ui-req]))

(defn playbook-settings [_cfg]
  (fn [request]
    (handlers/require-tenant
     request
     (fn [tenant]
       (handlers/html
        (playbooks-page/settings-page (:tenant/id tenant)))))))

(defn- playbook-attrs [form]
  (cond-> {:code (:code form)
           :display-name (:display-name form)
           :description (:description form)
           :workflow-engine-workflow-id
           (:workflow-engine-workflow-id form)
           :required-inputs-schema (:required-inputs-schema form)
           :is-active (= "true" (:is-active form))}
    (:id form) (assoc :id (ui-req/uuid-value (:id form)))))

(defn save-playbook [_cfg]
  (fn [request]
    (handlers/require-tenant
     request
     (fn [tenant]
       (csrf/with-form
         request
         (fn [form]
           (playbooks/save! (:tenant/id tenant)
                            (playbook-attrs form)
                            {:actor-kind :user :actor-id "ui"})
           (handlers/redirect "/settings/playbooks")))))))

(defn disable-playbook [_cfg]
  (fn [request]
    (handlers/require-tenant
     request
     (fn [tenant]
       (csrf/with-form
         request
         (fn [_form]
           (playbooks/disable! (:tenant/id tenant)
                               (handlers/path-id request)
                               {:actor-kind :user :actor-id "ui"})
           (handlers/redirect "/settings/playbooks")))))))

(ns drw.ui.resolution-handlers
  (:require [drw.domain.playbooks :as playbooks]
            [drw.domain.resolution :as resolution]
            [drw.ui.csrf :as csrf]
            [drw.ui.handlers :as handlers]
            [drw.ui.request :as ui-req]))

(defn- inputs [form]
  (if-let [raw (:inputs-json form)]
    {:raw raw}
    {}))

(defn start-resolution [cfg]
  (fn [request]
    (handlers/require-tenant
     request
     (fn [tenant]
       (csrf/with-form
         request
         (fn [form]
           (let [tenant-id (:tenant/id tenant)
                 dispute-id (handlers/path-id request)
                 playbook (playbooks/get-by-id
                           tenant-id
                           (ui-req/uuid-value (:playbook-id form)))]
             (resolution/start-resolution!
              tenant-id
              dispute-id
              playbook
              (inputs form)
              cfg
              {:actor-kind :user :actor-id "ui"})
             (handlers/redirect (str "/disputes/" dispute-id)))))))))

(ns drw.ui.playbooks
  (:require [drw.domain.playbooks :as playbooks]
            [drw.ui.page-shell :as page-shell]
            [drw.ui.session :as session]))

(defn- checked? [active?]
  (when active? "checked"))

(defn- playbook-form [playbook]
  (let [id (:playbook/id playbook)
        action "/settings/playbooks"]
    [:form {:method "post" :action action
            :class "rounded border border-slate-200 bg-white p-4"}
     (session/csrf-field)
     (when id [:input {:type "hidden" :name "id" :value id}])
     [:div {:class "grid gap-3 md:grid-cols-2"}
      [:label {:class "text-sm"}
       "Code"
       [:input {:name "code" :value (:playbook/code playbook)
                :required true
                :class "mt-1 w-full rounded border px-3 py-2"}]]
      [:label {:class "text-sm"}
       "Display name"
       [:input {:name "display_name"
                :value (:playbook/display-name playbook)
                :required true
                :class "mt-1 w-full rounded border px-3 py-2"}]]
      [:label {:class "text-sm md:col-span-2"}
       "Description"
       [:textarea {:name "description"
                   :class "mt-1 w-full rounded border px-3 py-2"}
        (:playbook/description playbook)]]
      [:label {:class "text-sm"}
       "Workflow ID"
       [:input {:name "workflow_engine_workflow_id"
                :value (:playbook/workflow-engine-workflow-id playbook)
                :required true
                :class "mt-1 w-full rounded border px-3 py-2"}]]
      [:label {:class "text-sm"}
       "Inputs schema"
       [:input {:name "required_inputs_schema"
                :value (or (:playbook/required-inputs-schema playbook) "{}")
                :class "mt-1 w-full rounded border px-3 py-2"}]]]
     [:div {:class "mt-4 flex items-center gap-3"}
      [:label {:class "flex items-center gap-2 text-sm"}
       [:input {:type "checkbox" :name "is_active" :value "true"
                :checked (checked? (:playbook/is-active playbook))}]
       "Active"]
      [:button {:class "rounded bg-slate-950 px-4 py-2 text-sm text-white"}
       (if id "Save" "Add")]
      (when id
        [:button {:formmethod "post"
                  :formaction (str "/settings/playbooks/" id "/disable")
                  :class "rounded border px-4 py-2 text-sm"}
         "Disable"])]]))

(defn- playbook-card [playbook]
  [:section {:class "rounded border border-slate-200 bg-white p-5"}
   [:div {:class "mb-4 flex items-center justify-between gap-3"}
    [:div
     [:h2 {:class "font-semibold"} (:playbook/display-name playbook)]
     [:p {:class "text-xs text-slate-500"} (:playbook/code playbook)]]
    [:span {:class "text-xs uppercase text-slate-500"}
     (if (:playbook/is-active playbook) "active" "inactive")]]
   (playbook-form playbook)])

(defn settings-page [tenant-id]
  (let [items (playbooks/list-by-tenant tenant-id {})]
    (page-shell/shell
     "Playbook settings"
     [:section {:class "rounded border border-slate-200 bg-white p-5"}
      [:h2 {:class "font-semibold"} "Add playbook"]
      [:div {:class "mt-4"}
       (playbook-form {:playbook/is-active true})]]
     (if (seq items)
       (into [:section {:class "grid gap-4"}]
             (map playbook-card items))
       [:section {:class "rounded border border-slate-200 bg-white p-5"}
        [:p {:class "text-sm text-slate-500"} "No playbooks configured."]]))))

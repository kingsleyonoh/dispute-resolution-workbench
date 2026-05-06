(ns drw.ui.resolution
  (:require [drw.domain.playbooks :as playbooks]
            [drw.ui.session :as session]))

(defn start-section [tenant-id dispute-id]
  (let [items (playbooks/list-by-tenant tenant-id {:active? true})]
    [:form {:method "post"
            :action (str "/disputes/" dispute-id "/start-resolution")
            :class "rounded border bg-white p-4"}
     (session/csrf-field)
     [:h2 {:class "font-semibold"} "Start resolution"]
     (if (seq items)
       [:div {:class "mt-3 grid gap-3 md:grid-cols-2"}
        (into [:select {:name "playbook_id"
                        :class "rounded border px-3 py-2"}]
              (map (fn [playbook]
                     [:option {:value (:playbook/id playbook)}
                      (:playbook/display-name playbook)])
                   items))
        [:input {:name "inputs_json" :value "{}"
                 :class "rounded border px-3 py-2"}]
        [:button {:class "rounded bg-slate-950 px-4 py-2 text-white md:w-fit"}
         "Start"]]
       [:p {:class "mt-3 text-sm text-slate-500"}
        "No active playbooks configured."])]))

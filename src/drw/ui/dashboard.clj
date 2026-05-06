(ns drw.ui.dashboard
  (:require [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.ui.page-shell :as page-shell]))

(def preview-limit 50)

(defn- metric [label value]
  [:section {:class "rounded border border-slate-200 bg-white p-4"}
   [:p {:class "text-xs font-medium uppercase text-slate-500"} label]
   [:p {:class "mt-2 text-2xl font-semibold"} value]])

(defn- open? [dispute]
  (not (contains? disputes/terminal-statuses (:dispute/status dispute))))

(defn- summary [items]
  (reduce (fn [acc dispute]
            (cond-> acc
              (open? dispute) (update :open-count inc)
              (:dispute/assigned-user-id dispute) (update :assigned-count inc)
              (and (open? dispute)
                   (< (count (:preview acc)) preview-limit))
              (update :preview conj dispute)))
          {:open-count 0
           :assigned-count 0
           :preview []}
          items))

(defn- dispute-row [dispute]
  [:tr {:class "border-t border-slate-100"}
   [:td {:class "py-3"}
    [:a {:href (str "/disputes/" (:dispute/id dispute))
         :class "font-medium text-slate-900"}
     (:dispute/title dispute)]
    [:p {:class "text-xs text-slate-500"} (:dispute/reference dispute)]]
   [:td {:class "py-3"} (page-shell/status-label (:dispute/status dispute))]
   [:td {:class "py-3"} (name (:dispute/severity dispute))]
   [:td {:class "py-3 text-right"}
    (page-shell/money (:dispute/monetary-impact-cents dispute)
                      (:dispute/currency dispute))]])

(defn- dispute-table [items]
  [:table {:class "w-full text-left text-sm"}
   [:thead {:class "text-xs uppercase text-slate-500"}
    [:tr
     [:th {:class "pb-2"} "Dispute"]
     [:th {:class "pb-2"} "Status"]
     [:th {:class "pb-2"} "Severity"]
     [:th {:class "pb-2 text-right"} "Impact"]]]
   (into [:tbody] (map dispute-row items))])

(defn dashboard-page [{:keys [tenant-id tenant-name]}]
  (let [{:keys [open-count assigned-count preview]}
        (summary (disputes/list-by-tenant tenant-id))]
    (page-shell/shell "Operations dashboard"
                      [:p {:class "text-sm text-slate-600"} tenant-name]
                      [:div {:class "grid gap-4 md:grid-cols-3"}
                       (metric "Open disputes" open-count)
                       (metric "Assigned" assigned-count)
                       (metric "Counterparties"
                               (count (counterparties/list-by-tenant tenant-id)))]
                      [:section {:class "rounded border border-slate-200 bg-white p-5"}
                       [:div {:class "mb-4 flex items-center justify-between"}
                        [:h2 {:class "text-lg font-semibold"} "My open disputes"]
                        [:a {:href "/disputes" :class "text-sm font-medium"} "View all"]]
                       (if (seq preview)
                         (dispute-table preview)
                         [:p {:class "text-sm text-slate-500"} "No disputes yet."])])))

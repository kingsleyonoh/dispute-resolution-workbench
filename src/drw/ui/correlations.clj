(ns drw.ui.correlations
  (:require [drw.domain.correlations :as correlations]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.ui.session :as session]))

(defn- status-label [status]
  (-> status name (.replace "-" " ") (.replace "_" " ")))

(defn- money [cents currency]
  (format "%s %.2f" (or currency "") (/ (or cents 0) 100.0)))

(defn- decision-form [id action label style]
  [:form {:method "post" :action (str "/correlations/" id "/" action)}
   (session/csrf-field)
   [:button {:class style} label]])

(defn- candidate-row [tenant-id candidate]
  (let [id (:correlation/id candidate)
        exception (exceptions/get-by-id
                   tenant-id
                   (:correlation/new-exception-id candidate))
        dispute (disputes/get-by-id
                 tenant-id
                 (:correlation/target-dispute-id candidate))]
    [:tr {:class "border-t border-slate-100"}
     [:td {:class "py-3"}
      [:p {:class "font-medium text-slate-900"} (:exception/source-ref exception)]
      [:p {:class "text-xs text-slate-500"}
       (str (status-label (:exception/source-system exception))
            " / " (status-label (:exception/kind exception)))]]
     [:td {:class "py-3"}
      [:a {:href (str "/disputes/" (:dispute/id dispute))
           :class "font-medium text-slate-900"}
       (:dispute/title dispute)]
      [:p {:class "text-xs text-slate-500"}
       (money (:dispute/monetary-impact-cents dispute)
              (:dispute/currency dispute))]]
     [:td {:class "py-3"} (:correlation/rationale candidate)]
     [:td {:class "py-3 text-right"} (format "%.2f" (:correlation/score candidate))]
     [:td {:class "py-3"}
      [:div {:class "flex justify-end gap-2"}
       (decision-form id "accept" "Accept"
                      "rounded bg-slate-950 px-3 py-1.5 text-sm text-white")
       (decision-form id "reject" "Reject"
                      "rounded border border-slate-300 px-3 py-1.5 text-sm")]]]))

(defn queue-section [tenant-id]
  (let [items (correlations/list-by-tenant tenant-id {:status :pending})]
    [:section {:class "rounded border border-slate-200 bg-white p-5"}
     (if (seq items)
       [:table {:class "w-full text-left text-sm"}
        [:thead {:class "text-xs uppercase text-slate-500"}
         [:tr
          [:th {:class "pb-2"} "Exception"]
          [:th {:class "pb-2"} "Target dispute"]
          [:th {:class "pb-2"} "Rationale"]
          [:th {:class "pb-2 text-right"} "Score"]
          [:th {:class "pb-2 text-right"} "Decision"]]]
        (into [:tbody] (map #(candidate-row tenant-id %) items))]
       [:p {:class "text-sm text-slate-500"} "No pending correlations."])]))

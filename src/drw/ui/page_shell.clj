(ns drw.ui.page-shell
  (:require [drw.ui.layout :as layout]
            [drw.ui.session :as session]))

(def statuses
  [:assigned :investigating :awaiting_counterparty
   :awaiting_approval :resolving :resolved :withdrawn])

(defn page [title & body]
  (apply layout/app-shell {:title title} body))

(defn nav []
  [:nav {:class "flex items-center gap-4 text-sm"}
   [:a {:href "/" :class "font-medium text-slate-700"} "Dashboard"]
   [:a {:href "/disputes" :class "font-medium text-slate-700"} "Disputes"]
   [:a {:href "/counterparties" :class "font-medium text-slate-700"}
    "Counterparties"]
   [:a {:href "/correlations" :class "font-medium text-slate-700"}
    "Correlations"]
   [:a {:href "/settings/ingestion" :class "font-medium text-slate-700"}
    "Ingestion"]
   [:form {:method "post" :action "/logout"}
    (session/csrf-field)
    [:button {:class "text-slate-500"} "Sign out"]]])

(defn shell [title & body]
  (page title
        [:div {:class "mb-6 flex items-center justify-between border-b pb-4"}
         [:div
          [:p {:class "text-xs font-semibold uppercase text-slate-500"}
           "Operations console"]
          [:h1 {:class "text-2xl font-semibold"} title]]
         (nav)]
        (into [:main {:id "content" :class "space-y-6"}] body)))

(defn status-label [status]
  (-> status name (.replace "-" " ") (.replace "_" " ")))

(defn money [cents currency]
  (format "%s %.2f" (or currency "") (/ (or cents 0) 100.0)))

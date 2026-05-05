(ns drw.ui.pages
  (:require [drw.ui.layout :as layout]))

(defn home []
  (layout/app-shell
   {:title "Workbench"}
   [:main {:id "content" :class "space-y-4"}
    [:section {:class "rounded border border-slate-200 bg-white p-5"}
     [:h1 {:class "text-xl font-semibold"} "Workbench"]
     [:p {:class "mt-2 text-sm text-slate-600"}
      "HTMX console skeleton is ready."]]]))

(ns drw.ui.layout
  (:require [clojure.string :as str]))

(def htmx-src "https://unpkg.com/htmx.org@2.0.4")

(defn- require-title [title]
  (when (str/blank? title)
    (throw (ex-info "page title is required" {})))
  title)

(defn app-shell [{:keys [title]} & body]
  (let [page-title (require-title title)]
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (str page-title " | Dispute Resolution Workbench")]
      [:link {:rel "stylesheet" :href "/assets/app.css"}]
      [:script {:src htmx-src :defer true}]]
     [:body {:class "bg-slate-50 text-slate-950 antialiased"}
      [:header {:class "border-b border-slate-200 bg-white"}
       [:div {:class "mx-auto flex max-w-7xl items-center justify-between px-6 py-4"}
        [:a {:href "/" :class "text-sm font-semibold tracking-wide"}
         "Dispute Resolution Workbench"]
        [:span {:class "text-xs text-slate-500"} "Setup"]]]
      (into [:div {:class "mx-auto max-w-7xl px-6 py-8"}] body)]]))

(ns drw.ui.ingestion
  (:require [clojure.string :as str]
            [drw.domain.ingestion-sources :as ingestion]
            [drw.jobs.ingestion-registry :as registry]
            [drw.ui.session :as session]))

(defn- source-title [source]
  (or (:ingestion-source/display-name source)
      (-> source :ingestion-source/source-system name
          (str/replace "-" " "))))

(defn- checked? [enabled?]
  (when enabled? "checked"))

(defn- source-form [source]
  (let [source-system (:ingestion-source/source-system source)
        config (:ingestion-source/config source)]
    [:form {:method "post" :action "/settings/ingestion"
            :class "rounded border border-slate-200 bg-white p-4"}
     (session/csrf-field)
     [:div {:class "flex items-center justify-between gap-4"}
      [:div
       [:h2 {:class "font-semibold"} (source-title source)]
       [:p {:class "text-xs text-slate-500"} (name source-system)]]
      [:label {:class "flex items-center gap-2 text-sm"}
       [:input {:type "checkbox" :name "is_enabled" :value "true"
                :checked (checked? (:ingestion-source/is-enabled source))}]
       "Enabled"]]
     [:input {:type "hidden" :name "source_system" :value (name source-system)}]
     [:div {:class "mt-4 grid gap-3 md:grid-cols-2"}
      [:label {:class "text-sm"}
       "Base URL"
       [:input {:name "base_url" :value (:base-url config)
                :class "mt-1 w-full rounded border px-3 py-2"}]]
      [:label {:class "text-sm"}
       "Poll interval"
       [:input {:name "poll_interval_seconds" :type "number"
                :value (:poll-interval-seconds config)
                :class "mt-1 w-full rounded border px-3 py-2"}]]]
     [:div {:class "mt-4 flex items-center gap-3"}
      [:button {:class "rounded bg-slate-950 px-4 py-2 text-sm text-white"}
       "Save"]
      [:button {:formmethod "post"
                :formaction (str "/settings/ingestion/"
                                 (:ingestion-source/id source)
                                 "/pull-now")
                :class "rounded border px-4 py-2 text-sm"}
       "Pull now"]]]))

(defn- run-row [run]
  [:tr {:class "border-t border-slate-100"}
   [:td {:class "py-3"} (name (:ingestion-run/source-system run))]
   [:td {:class "py-3"} (name (:ingestion-run/status run))]
   [:td {:class "py-3 text-right"} (:ingestion-run/exceptions-attempted run)]
   [:td {:class "py-3 text-right"} (:ingestion-run/exceptions-stored run)]
   [:td {:class "py-3"} (str/join ", " (:ingestion-run/source-refs run))]
   [:td {:class "py-3"} (or (:ingestion-run/cursor run) "")]])

(defn- runs-table [runs]
  [:section {:class "rounded border border-slate-200 bg-white p-5"}
   [:h2 {:class "font-semibold"} "Recent runs"]
   (if (seq runs)
     [:table {:class "mt-3 w-full text-left text-sm"}
      [:thead {:class "text-xs uppercase text-slate-500"}
       [:tr
        [:th {:class "pb-2"} "Source"]
        [:th {:class "pb-2"} "Status"]
        [:th {:class "pb-2 text-right"} "Attempted"]
        [:th {:class "pb-2 text-right"} "Stored"]
        [:th {:class "pb-2"} "Refs"]
        [:th {:class "pb-2"} "Cursor"]]]
      (into [:tbody] (map run-row runs))]
     [:p {:class "mt-3 text-sm text-slate-500"} "No ingestion runs yet."])])

(defn settings-section [tenant-id cfg]
  (let [cfg (registry/with-source-registry cfg)
        sources (ingestion/list-sources tenant-id cfg)
        runs (ingestion/list-runs tenant-id {})]
    [:div {:class "space-y-6"}
     [:section {:class "grid gap-4 md:grid-cols-2"}
      (map source-form sources)]
     (runs-table runs)]))

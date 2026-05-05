(ns drw.ui.pages
  (:require [drw.domain.counterparties :as counterparties]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.ui.layout :as layout]))

(def statuses
  [:assigned :investigating :awaiting_counterparty
   :awaiting_approval :resolving :resolved :withdrawn])

(defn- page [title & body]
  (apply layout/app-shell {:title title} body))

(defn- nav []
  [:nav {:class "flex items-center gap-4 text-sm"}
   [:a {:href "/" :class "font-medium text-slate-700"} "Dashboard"]
   [:a {:href "/disputes" :class "font-medium text-slate-700"} "Disputes"]
   [:a {:href "/counterparties" :class "font-medium text-slate-700"}
    "Counterparties"]
   [:form {:method "post" :action "/logout"}
    [:button {:class "text-slate-500"} "Sign out"]]])

(defn- shell [title & body]
  (page title
        [:div {:class "mb-6 flex items-center justify-between border-b pb-4"}
         [:div
          [:p {:class "text-xs font-semibold uppercase text-slate-500"}
           "Operations console"]
          [:h1 {:class "text-2xl font-semibold"} title]]
         (nav)]
        (into [:main {:id "content" :class "space-y-6"}] body)))

(defn- status-label [status]
  (-> status name (.replace "-" " ") (.replace "_" " ")))

(defn- money [cents currency]
  (format "%s %.2f" (or currency "") (/ (or cents 0) 100.0)))

(defn login-page [error]
  (page "Login"
        [:main {:id "content" :class "mx-auto max-w-md py-12"}
         [:section {:class "rounded border border-slate-200 bg-white p-6"}
          [:h1 {:class "text-xl font-semibold"} "Sign in"]
          [:p {:class "mt-2 text-sm text-slate-600"}
           "Use a tenant API key to open the operations console."]
          (when error
            [:p {:class "mt-4 rounded bg-red-50 p-3 text-sm text-red-700"} error])
          [:form {:method "post" :action "/login" :class "mt-6 space-y-4"}
           [:label {:class "block text-sm font-medium" :for "api_key"}
            "API key"]
           [:input {:id "api_key" :name "api_key" :type "password"
                    :class "w-full rounded border border-slate-300 px-3 py-2"
                    :required true}]
           [:button {:class "w-full rounded bg-slate-950 px-4 py-2 text-white"}
            "Sign in"]]]]))

(defn- metric [label value]
  [:section {:class "rounded border border-slate-200 bg-white p-4"}
   [:p {:class "text-xs font-medium uppercase text-slate-500"} label]
   [:p {:class "mt-2 text-2xl font-semibold"} value]])

(defn- dispute-row [dispute]
  [:tr {:class "border-t border-slate-100"}
   [:td {:class "py-3"}
    [:a {:href (str "/disputes/" (:dispute/id dispute))
         :class "font-medium text-slate-900"}
     (:dispute/title dispute)]
    [:p {:class "text-xs text-slate-500"} (:dispute/reference dispute)]]
   [:td {:class "py-3"} (status-label (:dispute/status dispute))]
   [:td {:class "py-3"} (name (:dispute/severity dispute))]
   [:td {:class "py-3 text-right"}
    (money (:dispute/monetary-impact-cents dispute)
           (:dispute/currency dispute))]])

(defn- dispute-table [disputes]
  [:table {:class "w-full text-left text-sm"}
   [:thead {:class "text-xs uppercase text-slate-500"}
    [:tr
     [:th {:class "pb-2"} "Dispute"]
     [:th {:class "pb-2"} "Status"]
     [:th {:class "pb-2"} "Severity"]
     [:th {:class "pb-2 text-right"} "Impact"]]]
   (into [:tbody] (map dispute-row disputes))])

(defn dashboard-page [{:keys [tenant-id tenant-name]}]
  (let [items (disputes/list-by-tenant tenant-id)
        open-count (count (remove #(#{:resolved :withdrawn}
                                    (:dispute/status %))
                                  items))
        assigned-count (count (filter #(some? (:dispute/assigned-user-id %))
                                      items))]
    (shell "Operations dashboard"
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
            (if (seq items)
              (dispute-table items)
              [:p {:class "text-sm text-slate-500"} "No disputes yet."])])))

(defn- dispute-form [counterparties]
  [:section {:class "rounded border border-slate-200 bg-white p-5"}
   [:h2 {:class "text-lg font-semibold"} "Create dispute"]
   [:form {:method "post" :action "/disputes"
           :class "mt-4 grid gap-3 md:grid-cols-2"}
    [:input {:name "title" :placeholder "Title" :required true
             :class "rounded border px-3 py-2"}]
    [:input {:name "currency" :placeholder "Currency" :value "EUR"
             :required true :class "rounded border px-3 py-2"}]
    [:select {:name "category" :class "rounded border px-3 py-2"}
     [:option {:value "billing"} "Billing"]
     [:option {:value "contractual"} "Contractual"]
     [:option {:value "payment"} "Payment"]]
    [:select {:name "severity" :class "rounded border px-3 py-2"}
     [:option {:value "medium"} "Medium"]
     [:option {:value "high"} "High"]
     [:option {:value "critical"} "Critical"]]
    [:select {:name "counterparty_id" :class "rounded border px-3 py-2"}
     [:option {:value ""} "No counterparty"]
     (for [cp counterparties]
       [:option {:value (:counterparty/id cp)}
        (:counterparty/canonical-name cp)])]
    [:textarea {:name "description" :placeholder "Description" :required true
                :class "rounded border px-3 py-2 md:col-span-2"}]
    [:button {:class "rounded bg-slate-950 px-4 py-2 text-white md:w-fit"}
     "Create"]]])

(defn dispute-list-page [tenant-id _filters]
  (let [items (disputes/list-by-tenant tenant-id)]
    (shell "Dispute queue"
           (dispute-form (counterparties/list-by-tenant tenant-id))
           [:section {:class "rounded border border-slate-200 bg-white p-5"}
            (dispute-table items)])))

(defn- action-panel [dispute]
  (let [id (:dispute/id dispute)]
    [:section {:class "grid gap-4 md:grid-cols-2"}
     [:form {:method "post" :action (str "/disputes/" id "/assign")
             :class "rounded border bg-white p-4"}
      [:h2 {:class "font-semibold"} "Assign owner"]
      [:input {:name "user_id" :placeholder "User UUID" :required true
               :class "mt-3 w-full rounded border px-3 py-2"}]
      [:button {:class "mt-3 rounded bg-slate-950 px-4 py-2 text-white"}
       "Assign"]]
     [:form {:method "post" :action (str "/disputes/" id "/transition")
             :class "rounded border bg-white p-4"}
      [:h2 {:class "font-semibold"} "Transition status"]
      (into [:select {:name "to_status" :class "mt-3 w-full rounded border px-3 py-2"}]
            (map (fn [status] [:option {:value (name status)}
                               (status-label status)])
                 statuses))
      [:button {:class "mt-3 rounded bg-slate-950 px-4 py-2 text-white"}
       "Transition"]]]))

(defn- exception-form [id]
  [:form {:method "post" :action (str "/disputes/" id "/exceptions")
          :class "rounded border bg-white p-4"}
   [:h2 {:class "font-semibold"} "Attach manual exception"]
   [:div {:class "mt-3 grid gap-3 md:grid-cols-2"}
    [:input {:name "source_ref" :placeholder "Source reference" :required true
             :class "rounded border px-3 py-2"}]
    [:input {:name "currency" :placeholder "Currency" :value "EUR"
             :required true :class "rounded border px-3 py-2"}]
    [:input {:name "observed_at" :type "datetime-local" :required true
             :class "rounded border px-3 py-2"}]
    [:input {:name "monetary_impact_cents" :type "number" :value "0"
             :class "rounded border px-3 py-2"}]
    [:input {:name "kind" :type "hidden" :value "manual"}]]
   [:button {:class "mt-3 rounded bg-slate-950 px-4 py-2 text-white"} "Attach"]])

(defn- comment-form [id]
  [:form {:method "post" :action (str "/disputes/" id "/comments")
          :class "rounded border bg-white p-4"}
   [:h2 {:class "font-semibold"} "Add comment"]
   [:textarea {:name "body" :required true
               :class "mt-3 w-full rounded border px-3 py-2"}]
   [:button {:class "mt-3 rounded bg-slate-950 px-4 py-2 text-white"} "Comment"]])

(defn dispute-detail-page [tenant-id dispute-id error]
  (let [dispute (disputes/get-by-id tenant-id dispute-id)
        exceptions (exceptions/list-by-tenant tenant-id {:dispute-id dispute-id})
        timeline (disputes/list-timeline tenant-id dispute-id)]
    (if-not dispute
      (shell "Dispute not found" [:p "Dispute not found"])
      (shell (:dispute/title dispute)
             (when error [:p {:class "rounded bg-red-50 p-3 text-red-700"} error])
             [:section {:class "rounded border bg-white p-5"}
              [:p {:class "text-sm text-slate-500"} (:dispute/reference dispute)]
              [:p {:class "mt-2"} (:dispute/description dispute)]
              [:dl {:class "mt-4 grid gap-3 text-sm md:grid-cols-4"}
               [:div [:dt "Status"] [:dd (status-label (:dispute/status dispute))]]
               [:div [:dt "Category"] [:dd (name (:dispute/category dispute))]]
               [:div [:dt "Severity"] [:dd (name (:dispute/severity dispute))]]
               [:div [:dt "Impact"] [:dd (money (:dispute/monetary-impact-cents dispute)
                                                (:dispute/currency dispute))]]]]
             (action-panel dispute)
             [:section {:class "grid gap-4 md:grid-cols-2"}
              (comment-form dispute-id)
              (exception-form dispute-id)]
             [:section {:class "rounded border bg-white p-5"}
              [:h2 {:class "font-semibold"} "Exceptions"]
              (into [:ul {:class "mt-3 space-y-2 text-sm"}]
                    (map (fn [ex] [:li (:exception/source-ref ex)])
                         exceptions))]
             [:section {:class "rounded border bg-white p-5"}
              [:h2 {:class "font-semibold"} "Timeline"]
              (into [:ol {:class "mt-3 space-y-2 text-sm"}]
                    (map (fn [entry]
                           [:li (str (status-label (:timeline/kind entry))
                                     " - " (:timeline/body entry))])
                         timeline))]))))

(defn counterparties-page [tenant-id]
  (shell "Counterparties"
         [:section {:class "rounded border bg-white p-5"}
          (into [:ul {:class "divide-y text-sm"}]
                (map (fn [cp]
                       [:li {:class "py-3"}
                        [:a {:href (str "/counterparties/"
                                        (:counterparty/id cp))
                             :class "font-medium"}
                         (:counterparty/canonical-name cp)]
                        [:span {:class "ml-3 text-slate-500"}
                         (name (:counterparty/kind cp))]])
                     (counterparties/list-by-tenant tenant-id)))]))

(defn counterparty-detail-page [tenant-id counterparty-id]
  (let [cp (counterparties/get-by-id tenant-id counterparty-id)
        related (filter #(= counterparty-id (:dispute/counterparty-id %))
                        (disputes/list-by-tenant tenant-id))]
    (shell (or (:counterparty/canonical-name cp) "Counterparty not found")
           [:section {:class "rounded border bg-white p-5"}
            [:h2 {:class "font-semibold"} "Counterparty history"]
            (if cp
              (dispute-table related)
              [:p "Counterparty not found"])])))

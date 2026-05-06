(ns drw.domain.sla-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.disputes :as disputes]
            [drw.domain.sla :as sla]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]
            [drw.jobs.sla-reaper :as sla-reaper]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def actor {:actor-kind :system :actor-id "sla-reaper"})

(defn- reset-domain! []
  (state/reset-store!))

(defn- assigned-dispute! [status]
  (let [dispute (disputes/create-dispute!
                 {:tenant-id tenant-a
                  :title (str "SLA " (name status))
                  :description "Overdue dispute."
                  :category :billing
                  :severity :high
                  :currency "EUR"
                  :created-by :user
                  :created-at #inst "2026-05-05T08:00:00.000-00:00"}
                 {:actor-kind :user :actor-id "seed"})
        assigned (disputes/assign!
                  tenant-a
                  (:dispute/id dispute)
                  {:user-id #uuid "33333333-3333-3333-3333-333333333333"
                   :assigned-at #inst "2026-05-05T08:00:00.000-00:00"
                   :sla-policies [{:sla-policy/tenant-id tenant-a
                                   :sla-policy/category :billing
                                   :sla-policy/severity :high
                                   :sla-policy/target-minutes 30}]}
                  {:actor-kind :user :actor-id "seed"})]
    (if (= status :assigned)
      assigned
      (disputes/transition!
       tenant-a (:dispute/id assigned)
       {:to :investigating
        :occurred-at #inst "2026-05-05T08:10:00.000-00:00"}
       {:actor-kind :user :actor-id "seed"}))))

(deftest sla-reaper-marks-overdue-disputes-once-and-emits-through-hub-helper
  (reset-domain!)
  (let [sent (atom [])
        dispute (assigned-dispute! :investigating)
        cfg {:notification-hub-enabled true
             :notification-hub-url "https://hub.example.invalid"
             :notification-hub-api-key "placeholder"
             :notification-hub-send-fn #(do (swap! sent conj %) {:status :sent})}
        first-run (sla/mark-overdue! cfg
                                     {:now #inst "2026-05-05T09:00:00.000-00:00"
                                      :actor actor})
        second-run (sla/mark-overdue! cfg
                                      {:now #inst "2026-05-05T09:00:00.000-00:00"
                                       :actor actor})
        timeline (disputes/list-timeline tenant-a (:dispute/id dispute))
        audit (disputes/list-audit-log tenant-a)]
    (is (= 1 (count first-run)))
    (is (= [] second-run))
    (is (= 1 (count @sent)))
    (is (= "dispute.sla_breached"
           (get-in (first @sent) [:event :event_type])))
    (is (= 1 (count (filter #(= :sla-breached (:timeline/kind %)) timeline))))
    (is (= 1 (count (filter #(= "dispute.sla_breached" (:audit/action %))
                            audit))))))

(deftest sla-reaper-ignores-terminal-and-not-yet-due-disputes
  (reset-domain!)
  (let [terminal (assigned-dispute! :assigned)
        _ (doseq [to [:investigating :resolving :resolved]]
            (disputes/transition!
             tenant-a (:dispute/id terminal)
             {:to to :occurred-at #inst "2026-05-05T08:20:00.000-00:00"}
             {:actor-kind :user :actor-id "seed"}))
        cfg {:notification-hub-enabled false}
        result (sla-reaper/run-once!
                cfg {:now #inst "2026-05-05T09:00:00.000-00:00"
                     :actor actor})]
    (is (= {:checked 1 :breached 0} result))
    (is (empty? (filter #(= :sla-breached (:timeline/kind %))
                        (state/timeline))))))

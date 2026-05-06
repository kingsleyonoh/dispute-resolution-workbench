(ns drw.domain.core-queue-exceptions-test
  (:require [clojure.test :refer [deftest is]]
            [drw.domain.disputes :as disputes]
            [drw.domain.exceptions :as exceptions]
            [drw.domain.state :as state]
            [drw.fixtures :as fixtures]))

(def tenants (fixtures/tenants-by-slug))
(def tenant-a (:tenant/id (get tenants "acme-gmbh-de")))
(def tenant-b (:tenant/id (get tenants "globex-inc-us")))
(def actor {:actor-kind :user :actor-id "operator-1"})
(def now #inst "2026-05-05T10:00:00.000-00:00")

(defn reset-domain! []
  (state/reset-store!))

(defn ex-type [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (:type (ex-data e)))))

(deftest manual-exception-create-list-and-duplicate-prevention-are-tenant-scoped
  (reset-domain!)
  (let [exception (exceptions/create-manual!
                   {:tenant-id tenant-a
                    :source-ref "MAN-100"
                    :source-url "https://example.invalid/source/MAN-100"
                    :kind :manual
                    :raw-payload {:note "operator created"}
                    :monetary-impact-cents 2500
                    :currency "EUR"
                    :observed-at now}
                   actor)]
    (is (= :manual (:exception/source-system exception)))
    (is (= "MAN-100" (:exception/source-ref exception)))
    (is (= 1 (count (exceptions/list-by-tenant tenant-a))))
    (is (= 1 (count (exceptions/list-by-tenant
                     tenant-a
                     {:source-system :manual}))))
    (is (= 0 (count (exceptions/list-by-tenant tenant-b))))
    (is (= :exception/duplicate-source-ref
           (ex-type #(exceptions/create-manual!
                      {:tenant-id tenant-a
                       :source-ref "MAN-100"
                       :kind :manual
                       :raw-payload {}
                       :monetary-impact-cents 10
                       :currency "EUR"
                       :observed-at now}
                      actor))))
    (is (some? (exceptions/create-manual!
                {:tenant-id tenant-b
                 :source-ref "MAN-100"
                 :kind :manual
                 :raw-payload {}
                 :monetary-impact-cents 10
                 :currency "USD"
                 :observed-at now}
                actor)))))

(deftest attaching-manual-exceptions-updates-dispute-and-rejects-terminal-targets
  (reset-domain!)
  (let [open-dispute (disputes/create-dispute!
                      {:tenant-id tenant-a
                       :title "Attach target"
                       :description "Open dispute."
                       :category :billing
                       :severity :medium
                       :currency "EUR"
                       :created-by :user
                       :created-at now}
                      actor)
        resolved-dispute (disputes/create-dispute!
                          {:tenant-id tenant-a
                           :title "Closed target"
                           :description "Closed dispute."
                           :category :billing
                           :severity :medium
                           :currency "EUR"
                           :created-by :user
                           :created-at now}
                          actor)
        exception-1 (exceptions/create-manual!
                     {:tenant-id tenant-a
                      :source-ref "MAN-200"
                      :kind :manual
                      :raw-payload {:note "first"}
                      :monetary-impact-cents 1200
                      :currency "EUR"
                      :observed-at now}
                     actor)
        exception-2 (exceptions/create-manual!
                     {:tenant-id tenant-a
                      :source-ref "MAN-201"
                      :kind :manual
                      :raw-payload {:note "second"}
                      :monetary-impact-cents -200
                      :currency "EUR"
                      :observed-at now}
                     actor)]
    (doseq [to [:assigned :investigating :resolving :resolved]]
      (disputes/transition! tenant-a (:dispute/id resolved-dispute) {:to to} actor))
    (let [attached (exceptions/attach-to-dispute!
                    tenant-a
                    {:exception-id (:exception/id exception-1)
                     :dispute-id (:dispute/id open-dispute)}
                    actor)
          attached-again (exceptions/attach-to-dispute!
                          tenant-a
                          {:exception-id (:exception/id exception-2)
                           :dispute-id (:dispute/id open-dispute)}
                          actor)]
      (is (= (:dispute/id open-dispute) (:exception/dispute-id attached)))
      (is (= 1000 (:dispute/monetary-impact-cents attached-again)))
      (is (= 2 (count (exceptions/list-by-tenant
                       tenant-a
                       {:dispute-id (:dispute/id open-dispute)}))))
      (is (= [:dispute-created :exception-attached :exception-attached]
             (map :timeline/kind
                  (disputes/list-timeline tenant-a
                                          (:dispute/id open-dispute))))))
    (is (= :dispute/terminal
           (ex-type #(exceptions/attach-to-dispute!
                      tenant-a
                      {:exception-id (:exception/id exception-1)
                       :dispute-id (:dispute/id resolved-dispute)}
                      actor))))
    (is (= :exception/not-found
           (ex-type #(exceptions/attach-to-dispute!
                      tenant-b
                      {:exception-id (:exception/id exception-1)
                       :dispute-id (:dispute/id open-dispute)}
                      actor))))))

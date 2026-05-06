(ns drw.adapters.protocol-test
  (:require [clojure.test :refer [deftest is]]
            [drw.adapters.protocol :as adapter]))

(deftest exception-adapter-protocol-normalizes-poll-results
  (let [test-adapter (reify adapter/ExceptionAdapter
                       (source-system [_] :invoice-recon)
                       (poll! [this tenant-config cursor]
                         (adapter/poll-result
                          this
                          (:tenant-id tenant-config)
                          [{:source-ref "INV-1"}]
                          (str cursor "-next")))
                       (parse-webhook [this tenant-config payload headers]
                         {:tenant-id (:tenant-id tenant-config)
                          :source-system (adapter/source-system this)
                          :payload payload
                          :headers headers}))
        poll-result (adapter/poll! test-adapter
                                   {:tenant-id "tenant-a"}
                                   "cursor-1")
        webhook (adapter/parse-webhook test-adapter
                                       {:tenant-id "tenant-a"}
                                       {:invoice-id "INV-1"}
                                       {"X-Event-Id" "evt-1"})]
    (is (= {:tenant-id "tenant-a"
            :source-system :invoice-recon
            :exceptions [{:source-ref "INV-1"}]
            :cursor "cursor-1-next"
            :error? false}
           poll-result))
    (is (= :invoice-recon (:source-system webhook)))
    (is (= "tenant-a" (:tenant-id webhook)))))

(deftest exception-adapter-protocol-normalizes-failures-without-cross-tenant-state
  (let [test-adapter (reify adapter/ExceptionAdapter
                       (source-system [_] :transaction-recon)
                       (poll! [this tenant-config _]
                         (adapter/poll-error
                          this
                          (:tenant-id tenant-config)
                          {:reason :upstream-unavailable}))
                       (parse-webhook [_ _ _ _]
                         (throw (ex-info "webhook unsupported"
                                         {:type :adapter/webhook-unsupported}))))
        tenant-a (adapter/poll! test-adapter {:tenant-id "tenant-a"} nil)
        tenant-b (adapter/poll! test-adapter {:tenant-id "tenant-b"} nil)]
    (is (= {:tenant-id "tenant-a"
            :source-system :transaction-recon
            :exceptions []
            :cursor nil
            :error? true
            :error {:reason :upstream-unavailable}}
           tenant-a))
    (is (= "tenant-b" (:tenant-id tenant-b)))
    (is (= :transaction-recon (:source-system tenant-b)))))

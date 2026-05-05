(ns drw.ecosystem.nats-connection-test
  (:require [clojure.test :refer [deftest is]]
            [drw.ecosystem.nats-connection :as nats]))

(deftest disabled-nats-connection-does-not-call-client-factory
  (let [calls (atom 0)
        conn (nats/connect! {:nats-enabled false
                             :nats-url "nats://localhost:4222"
                             :nats-connect-fn #(swap! calls inc)})]
    (is (= 0 @calls))
    (is (= :disabled (:status conn)))
    (is (= false (:enabled? conn)))
    (is (nil? (:connection conn)))))

(deftest enabled-nats-connection-publishes-subscribes-and-closes
  (let [calls (atom [])
        handled (atom [])
        client {:publish-fn (fn [subject payload opts]
                              (swap! calls conj [:publish subject payload opts])
                              {:status :published :sequence 42})
                :subscribe-fn (fn [subject handler opts]
                                (swap! calls conj [:subscribe subject opts])
                                (handler {:subject subject :payload {:id "evt-1"}})
                                {:status :subscribed :subject subject})
                :close-fn (fn []
                            (swap! calls conj [:close])
                            {:status :closed})}
        conn (nats/connect! {:nats-enabled true
                             :nats-url "nats://localhost:4222"
                             :nats-creds-path "config/nats.creds"
                             :nats-stream-name "ECOSYSTEM_EVENTS"
                             :nats-connect-fn
                             (fn [opts]
                               (swap! calls conj [:connect opts])
                               client)})]
    (is (= :connected (:status conn)))
    (is (= {:status :published :sequence 42}
           (nats/publish! conn
                          "contract.obligation.breached"
                          {:obligation-id "OBL-1"}
                          {:message-id "msg-1"})))
    (is (= {:status :subscribed
            :subject "contract.obligation.breached"}
           (nats/subscribe! conn
                            "contract.obligation.breached"
                            #(swap! handled conj %)
                            {:durable-name "drw-contracts"})))
    (is (= {:status :closed} (nats/close! conn)))
    (is (= [{:subject "contract.obligation.breached"
             :payload {:id "evt-1"}}]
           @handled))
    (is (= :connect (ffirst @calls)))
    (is (= "nats://localhost:4222"
           (get-in (first @calls) [1 :url])))))

(deftest enabled-nats-connection-rejects-incomplete-config
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"NATS configuration is incomplete"
       (nats/connect! {:nats-enabled true
                       :nats-url ""
                       :nats-connect-fn (fn [_] {})}))))

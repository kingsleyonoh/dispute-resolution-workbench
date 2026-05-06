(ns drw.observability.sentry)

(defn capture-exception! [cfg exception context]
  (if-not (:sentry-dsn cfg)
    {:status :disabled}
    (let [payload {:exception exception
                   :message (ex-message exception)
                   :context context}]
      (if-let [capture (:sentry-capture-fn cfg)]
        (capture payload)
        nil)
      {:status :captured
       :message (:message payload)})))

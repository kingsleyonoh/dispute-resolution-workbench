(ns drw.adapters.protocol)

(defprotocol ExceptionAdapter
  (source-system [this])
  (poll! [this tenant-config cursor]
    "Return a normalized poll result with exceptions, cursor, and error?.")
  (parse-webhook [this tenant-config payload headers]
    "Return one normalized exception payload or throw an ExceptionInfo."))

(defn poll-result [adapter tenant-id exceptions cursor]
  {:tenant-id tenant-id
   :source-system (source-system adapter)
   :exceptions (vec exceptions)
   :cursor cursor
   :error? false})

(defn poll-error [adapter tenant-id error]
  {:tenant-id tenant-id
   :source-system (source-system adapter)
   :exceptions []
   :cursor nil
   :error? true
   :error error})

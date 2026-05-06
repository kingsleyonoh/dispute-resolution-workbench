(ns drw.db.scope)

(defn entity-by-tenant-id [entities tenant-id id-attr]
  (or (some (fn [entity]
              (when (= tenant-id (get entity id-attr))
                entity))
            entities)
      (throw (ex-info "tenant not found"
                      {:type :tenant/not-found
                       :tenant-id tenant-id}))))

(defn filter-by-tenant [entities tenant-id tenant-attr]
  (filter #(= tenant-id (get % tenant-attr)) entities))

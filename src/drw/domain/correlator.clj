(ns drw.domain.correlator
  (:require [clojure.string :as str]))

(def terminal-statuses #{:resolved :withdrawn})

(def default-config
  {:weights {:source-ref 0.15
             :entity-id 0.15
             :counterparty 0.25
             :currency 0.10
             :amount 0.15
             :date 0.10
             :category 0.10}
   :thresholds {:review 0.55
                :auto-merge 0.85}
   :amount-tolerance 0.10
   :date-window-hours 72
   :correlation-window-days 30})

(def signal-order
  [:source-ref :entity-id :counterparty :currency :amount :date :category])

(def signal-labels
  {:source-ref "source-ref matched attached exception"
   :entity-id "entity-id matched attached exception"
   :counterparty "counterparty matched"
   :currency "currency matched"
   :amount "amount within 10%"
   :date "observed within 72h"
   :category "category matched"})

(def kind-categories
  {:invoice-discrepancy :billing
   :contract-breach :contractual
   :contract-conflict :contractual
   :payment-mismatch :payment
   :delivery-failure :delivery
   :manual :multi})

(defn- value [entity short-key qualified-key]
  (or (get entity qualified-key) (get entity short-key)))

(defn- entity-tenant-id [entity]
  (value entity :tenant-id :exception/tenant-id))

(defn- dispute-tenant-id [dispute]
  (get dispute :dispute/tenant-id))

(defn- dispute-id [dispute]
  (get dispute :dispute/id))

(defn- counterparty-id [entity]
  (value entity :counterparty-id :exception/counterparty-id))

(defn- source-ref [entity]
  (value entity :source-ref :exception/source-ref))

(defn- entity-id [entity]
  (value entity :entity-id :exception/entity-id))

(defn- exception-kind [entity]
  (value entity :kind :exception/kind))

(defn- money [entity]
  (value entity :monetary-impact-cents :exception/monetary-impact-cents))

(defn- currency [entity]
  (value entity :currency :exception/currency))

(defn- observed-at [entity]
  (value entity :observed-at :exception/observed-at))

(defn- same-tenant? [tenant-id exception dispute]
  (and (= tenant-id (entity-tenant-id exception))
       (= tenant-id (dispute-tenant-id dispute))))

(defn- attached-to-dispute [tenant-id id attached]
  (filter #(and (= tenant-id (entity-tenant-id %))
                (= id (get % :exception/dispute-id)))
          attached))

(defn- attached-match? [items field expected]
  (boolean
   (and expected
        (some #(= expected (field %)) items))))

(defn- same-counterparty? [exception dispute]
  (let [exception-counterparty (counterparty-id exception)]
    (and (some? exception-counterparty)
         (= exception-counterparty (:dispute/counterparty-id dispute)))))

(defn- same-currency? [exception dispute]
  (let [exception-currency (currency exception)]
    (and (some? exception-currency)
         (= exception-currency (:dispute/currency dispute)))))

(defn- within-amount? [exception dispute cfg]
  (let [incoming (money exception)
        candidate (:dispute/monetary-impact-cents dispute)]
    (and (number? incoming)
         (number? candidate)
         (same-currency? exception dispute)
         (let [denom (max (abs (double incoming)) (abs (double candidate)) 1.0)
               ratio (/ (abs (- (double incoming) (double candidate))) denom)]
           (<= ratio (:amount-tolerance cfg))))))

(defn- hours-between [a b]
  (when (and a b)
    (/ (abs (- (.getTime a) (.getTime b))) 3600000.0)))

(defn- within-date? [exception dispute cfg]
  (when-let [hours (hours-between (observed-at exception)
                                  (:dispute/created-at dispute))]
    (<= hours (:date-window-hours cfg))))

(defn- within-correlation-window? [exception dispute cfg]
  (when-let [hours (hours-between (observed-at exception)
                                  (:dispute/created-at dispute))]
    (<= hours (* 24 (:correlation-window-days cfg)))))

(defn- category [exception]
  (or (value exception :category :exception/category)
      (kind-categories (exception-kind exception))))

(defn- same-category? [exception dispute]
  (= (category exception) (:dispute/category dispute)))

(defn- signal-status [matched? missing?]
  (cond
    matched? :match
    missing? :missing
    :else :mismatch))

(defn- signal-missing? [signal exception dispute]
  (case signal
    :source-ref (nil? (source-ref exception))
    :entity-id (nil? (entity-id exception))
    :counterparty (or (nil? (counterparty-id exception))
                      (nil? (:dispute/counterparty-id dispute)))
    :currency (or (nil? (currency exception)) (nil? (:dispute/currency dispute)))
    :amount (or (nil? (money exception))
                (nil? (:dispute/monetary-impact-cents dispute)))
    :date (or (nil? (observed-at exception))
              (nil? (:dispute/created-at dispute)))
    :category (or (nil? (category exception))
                  (nil? (:dispute/category dispute)))))

(defn- signal-matched? [signal exception dispute attached cfg]
  (case signal
    :source-ref (attached-match? attached source-ref (source-ref exception))
    :entity-id (attached-match? attached entity-id (entity-id exception))
    :counterparty (same-counterparty? exception dispute)
    :currency (same-currency? exception dispute)
    :amount (within-amount? exception dispute cfg)
    :date (within-date? exception dispute cfg)
    :category (same-category? exception dispute)))

(defn- explanation [exception dispute attached cfg]
  (mapv (fn [signal]
          (let [matched? (signal-matched? signal exception dispute attached cfg)]
            {:signal signal
             :status (signal-status matched?
                                    (signal-missing? signal exception dispute))
             :points (if matched? (get-in cfg [:weights signal]) 0.0)}))
        signal-order))

(defn- score [items]
  (/ (Math/round (* 100.0 (reduce + (map :points items)))) 100.0))

(defn- band [score thresholds]
  (cond
    (>= score (:auto-merge thresholds)) :auto-merge
    (>= score (:review thresholds)) :review
    (pos? score) :weak
    :else :no-match))

(defn- rationale [items]
  (let [labels (keep #(when (= :match (:status %))
                        (signal-labels (:signal %)))
                     items)]
    (if (seq labels)
      (str/join "; " labels)
      "no scoring signals matched")))

(defn- candidate-eligible? [tenant-id exception dispute cfg]
  (and (same-tenant? tenant-id exception dispute)
       (not (contains? terminal-statuses (:dispute/status dispute)))
       (same-counterparty? exception dispute)
       (within-correlation-window? exception dispute cfg)))

(defn- no-match [id rationale]
  {:dispute-id id
   :score 0.0
   :band :no-match
   :rationale rationale
   :explanation []})

(defn score-candidate
  ([tenant-id exception dispute attached]
   (score-candidate tenant-id exception dispute attached {}))
  ([tenant-id exception dispute attached opts]
   (let [cfg (merge default-config opts)
         id (dispute-id dispute)
         attached (attached-to-dispute tenant-id id attached)]
     (if-not (same-tenant? tenant-id exception dispute)
       (no-match id "tenant mismatch")
       (if-not (same-counterparty? exception dispute)
         (no-match id "counterparty mismatch")
         (let [items (explanation exception dispute attached cfg)
               score (score items)]
           {:dispute-id id
            :score score
            :band (band score (:thresholds cfg))
            :rationale (rationale items)
            :explanation items}))))))

(defn score-candidates
  ([tenant-id exception disputes attached]
   (score-candidates tenant-id exception disputes attached {}))
  ([tenant-id exception disputes attached opts]
   (let [cfg (merge default-config opts)
         review (get-in cfg [:thresholds :review])]
     (->> disputes
          (map-indexed vector)
          (filter (fn [[_ dispute]]
                    (candidate-eligible? tenant-id exception dispute cfg)))
          (map (fn [[idx dispute]]
                 (assoc (score-candidate tenant-id exception dispute attached cfg)
                        :sort-index idx)))
          (filter #(>= (:score %) review))
          (sort-by (juxt (comp - :score) :sort-index))
          (mapv #(dissoc % :sort-index))))))

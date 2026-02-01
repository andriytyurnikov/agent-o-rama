(ns alt-frontend.components.rules-form
  "Rule creation form for alt-frontend.

   A multi-section form that allows creating rules with:
   - Basic info (name, scope, status filter, sampling rate, start time)
   - Action selection with dynamic parameters
   - Filter builder for conditional execution"
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.state :as state]
            [alt-frontend.lib.queries :as queries]
            [clojure.string :as str]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

(defn action-friendly-name
  "Returns a user-friendly display name for an action builder ID."
  [action-id]
  (case action-id
    "aor/eval" "Online evaluation"
    "aor/add-to-dataset" "Add to dataset"
    "aor/webhook" "Webhook"
    "aor/add-to-human-feedback-queue" "Add to human feedback queue"
    action-id))

(defn compute-start-time-millis
  "Converts start-time state to epoch milliseconds."
  [{:keys [mode date relative-value relative-unit]}]
  (case mode
    :from-start 0

    :absolute
    (if (str/blank? date)
      (.now js/Date)
      (.parse js/Date date))

    :relative
    (let [now (.now js/Date)
          millis-per-unit {:minutes (* 60 1000)
                           :hours (* 60 60 1000)
                           :days (* 24 60 60 1000)
                           :weeks (* 7 24 60 60 1000)}
          unit-millis (get millis-per-unit relative-unit (* 60 1000))]
      (- now (* (or relative-value 0) unit-millis)))

    (.now js/Date)))

;; =============================================================================
;; COMPARATORS
;; =============================================================================

(def COMPARATORS
  [{:value := :label "="}
   {:value :not= :label "≠"}
   {:value :< :label "<"}
   {:value :> :label ">"}
   {:value :<= :label "≤"}
   {:value :>= :label "≥"}])

;; =============================================================================
;; FILTER COMPONENTS
;; =============================================================================

(defui comparator-spec-input [{:keys [value on-change value-type]}]
  (let [comparator (or (:comparator value) :=)
        comp-value (or (:value value) "")]
    ($ :div {:class "flex gap-2 items-center"}
       ($ :select {:class "select select-bordered select-sm"
                   :value (name comparator)
                   :onChange #(on-change (assoc value :comparator (keyword (.. % -target -value))))}
          (for [comp COMPARATORS]
            ($ :option {:key (name (:value comp))
                        :value (name (:value comp))}
               (:label comp))))

       (case value-type
         :number
         ($ :input {:type "number"
                    :class "input input-bordered input-sm flex-1"
                    :value comp-value
                    :placeholder "Value"
                    :onChange #(on-change (assoc value :value (js/parseFloat (.. % -target -value))))})

         ;; Default: text/json
         ($ :input {:type "text"
                    :class "input input-bordered input-sm flex-1"
                    :value (if (string? comp-value) comp-value (js/JSON.stringify (clj->js comp-value)))
                    :placeholder "Value"
                    :onChange #(on-change (assoc value :value (.. % -target -value)))})))))

(defui error-filter-ui [{:keys []}]
  ($ :div {:class "text-sm text-base-content/70 italic"}
     "Matches runs with errors"))

(defui latency-filter-ui [{:keys [value on-change]}]
  ($ :div {:class "space-y-2"}
     ($ :label {:class "label label-text"} "Latency (ms)")
     ($ comparator-spec-input {:value value :on-change on-change :value-type :number})))

(defui input-match-filter-ui [{:keys [value on-change]}]
  ($ :div {:class "space-y-2"}
     ($ :div {:class "form-control"}
        ($ :label {:class "label label-text"} "JSON Path")
        ($ :input {:type "text"
                   :class "input input-bordered input-sm"
                   :value (or (:json-path value) "")
                   :placeholder "$.field.path"
                   :onChange #(on-change (assoc value :json-path (.. % -target -value)))}))
     ($ :div {:class "form-control"}
        ($ :label {:class "label label-text"} "Regex Pattern")
        ($ :input {:type "text"
                   :class "input input-bordered input-sm"
                   :value (or (:regex value) "")
                   :placeholder ".*pattern.*"
                   :onChange #(on-change (assoc value :regex (.. % -target -value)))}))))

(defui output-match-filter-ui [{:keys [value on-change]}]
  ($ :div {:class "space-y-2"}
     ($ :div {:class "form-control"}
        ($ :label {:class "label label-text"} "JSON Path")
        ($ :input {:type "text"
                   :class "input input-bordered input-sm"
                   :value (or (:json-path value) "")
                   :placeholder "$.field.path"
                   :onChange #(on-change (assoc value :json-path (.. % -target -value)))}))
     ($ :div {:class "form-control"}
        ($ :label {:class "label label-text"} "Regex Pattern")
        ($ :input {:type "text"
                   :class "input input-bordered input-sm"
                   :value (or (:regex value) "")
                   :placeholder ".*pattern.*"
                   :onChange #(on-change (assoc value :regex (.. % -target -value)))}))))

(defui token-count-filter-ui [{:keys [value on-change]}]
  (let [token-type (or (:token-type value) :total)
        comparator-spec (or (:comparator-spec value) {:comparator := :value 0})]
    ($ :div {:class "space-y-2"}
       ($ :div {:class "form-control"}
          ($ :label {:class "label label-text"} "Token Type")
          ($ :select {:class "select select-bordered select-sm w-full"
                      :value (name token-type)
                      :onChange #(on-change (assoc value :token-type (keyword (.. % -target -value))))}
             ($ :option {:value "input"} "Input")
             ($ :option {:value "output"} "Output")
             ($ :option {:value "total"} "Total")))
       ($ :label {:class "label label-text"} "Count")
       ($ comparator-spec-input {:value comparator-spec
                                  :on-change #(on-change (assoc value :comparator-spec %))
                                  :value-type :number}))))

(defui feedback-filter-ui [{:keys [value on-change module-id agent-name]}]
  (let [rule-name-val (or (:rule-name value) "")
        feedback-key (or (:feedback-key value) "")
        comparator-spec (or (:comparator-spec value) {:comparator := :value ""})
        [rules set-rules!] (uix/use-state nil)
        [loading? set-loading!] (uix/use-state false)]

    (uix/use-effect
     (fn []
       (when (and module-id agent-name)
         (set-loading! true)
         (sente/request!
          [:analytics/fetch-rules {:module-id module-id
                                    :agent-name agent-name
                                    :names-only? true
                                    :filter-by-action "aor/eval"}]
          5000
          (fn [reply]
            (set-loading! false)
            (if (:success reply)
              (set-rules! (:data reply))
              nil))))
       js/undefined)
     [module-id agent-name])

    ($ :div {:class "space-y-2"}
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Rule Name")
             ($ :span {:class "text-error"} "*"))
          ($ :select {:class "select select-bordered select-sm w-full"
                      :value rule-name-val
                      :onChange #(on-change (assoc value :rule-name (.. % -target -value)))
                      :disabled loading?}
             ($ :option {:value ""} (if loading? "Loading rules..." "Select a rule"))
             (when rules
               (for [rname rules]
                 ($ :option {:key (name rname) :value (name rname)} (name rname))))))
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Metric Key")
             ($ :span {:class "text-error"} "*"))
          ($ :input {:type "text"
                     :class "input input-bordered input-sm"
                     :value feedback-key
                     :placeholder "score"
                     :onChange #(on-change (assoc value :feedback-key (.. % -target -value)))}))
       ($ :div {:class "form-control"}
          ($ :label {:class "label label-text"} "Value")
          ($ comparator-spec-input {:value comparator-spec
                                     :on-change #(on-change (assoc value :comparator-spec %))
                                     :value-type :json})))))

;; Forward declaration for recursive components
(declare filter-node)

(defui and-filter-ui [{:keys [value on-change module-id agent-name]}]
  (let [filters (or (:filters value) [])]
    ($ :div {:class "space-y-2"}
       ($ :div {:class "text-sm font-medium"} "All of the following:")
       ($ :div {:class "ml-4 space-y-2 border-l-2 border-base-300 pl-4"}
          (for [[idx filter] (map-indexed vector filters)]
            ($ filter-node {:key idx
                            :value filter
                            :on-change #(on-change (assoc value :filters (assoc (vec filters) idx %)))
                            :on-remove #(on-change (assoc value :filters
                                                          (vec (concat (take idx filters)
                                                                       (drop (inc idx) filters)))))
                            :module-id module-id
                            :agent-name agent-name}))
          ($ :button {:type "button"
                      :class "btn btn-ghost btn-sm"
                      :onClick #(on-change (assoc value :filters (conj (vec filters) {:type :error})))}
             "+ Add Filter")))))

(defui or-filter-ui [{:keys [value on-change module-id agent-name]}]
  (let [filters (or (:filters value) [])]
    ($ :div {:class "space-y-2"}
       ($ :div {:class "text-sm font-medium"} "Any of the following:")
       ($ :div {:class "ml-4 space-y-2 border-l-2 border-base-300 pl-4"}
          (for [[idx filter] (map-indexed vector filters)]
            ($ filter-node {:key idx
                            :value filter
                            :on-change #(on-change (assoc value :filters (assoc (vec filters) idx %)))
                            :on-remove #(on-change (assoc value :filters
                                                          (vec (concat (take idx filters)
                                                                       (drop (inc idx) filters)))))
                            :module-id module-id
                            :agent-name agent-name}))
          ($ :button {:type "button"
                      :class "btn btn-ghost btn-sm"
                      :onClick #(on-change (assoc value :filters (conj (vec filters) {:type :error})))}
             "+ Add Filter")))))

(defui not-filter-ui [{:keys [value on-change module-id agent-name]}]
  (let [inner-filter (or (:filter value) {:type :error})]
    ($ :div {:class "space-y-2"}
       ($ :div {:class "text-sm font-medium"} "Not:")
       ($ :div {:class "ml-4 border-l-2 border-base-300 pl-4"}
          ($ filter-node {:value inner-filter
                          :on-change #(on-change (assoc value :filter %))
                          :on-remove nil
                          :module-id module-id
                          :agent-name agent-name})))))

(defui filter-node [{:keys [value on-change on-remove module-id agent-name]}]
  (let [filter-type (or (:type value) :error)]
    ($ :div {:class "card bg-base-100 border border-base-300 p-3"}
       ($ :div {:class "flex gap-2 items-start"}
          ($ :div {:class "flex-1"}
             ($ :div {:class "mb-2"}
                ($ :select {:class "select select-bordered select-sm w-full"
                            :value (name filter-type)
                            :onChange #(let [new-type (keyword (.. % -target -value))
                                             new-filter (case new-type
                                                          :error {:type :error}
                                                          :latency {:type :latency :comparator := :value 0}
                                                          :input-match {:type :input-match :json-path "$" :regex ".*"}
                                                          :output-match {:type :output-match :json-path "$" :regex ".*"}
                                                          :token-count {:type :token-count :token-type :total :comparator-spec {:comparator := :value 0}}
                                                          :feedback {:type :feedback :rule-name "" :feedback-key "" :comparator-spec {:comparator := :value ""}}
                                                          :and {:type :and :filters []}
                                                          :or {:type :or :filters []}
                                                          :not {:type :not :filter {:type :error}}
                                                          {:type :error})]
                                         (on-change new-filter))}
                   ($ :option {:value "error"} "Error")
                   ($ :option {:value "latency"} "Latency")
                   ($ :option {:value "input-match"} "Input Match")
                   ($ :option {:value "output-match"} "Output Match")
                   ($ :option {:value "token-count"} "Token Count")
                   ($ :option {:value "feedback"} "Feedback")
                   ($ :option {:value "and"} "AND (all of)")
                   ($ :option {:value "or"} "OR (any of)")
                   ($ :option {:value "not"} "NOT")))

             ($ :div {:class "mt-2"}
                (case filter-type
                  :error ($ error-filter-ui {:value value :on-change on-change})
                  :latency ($ latency-filter-ui {:value value :on-change on-change})
                  :input-match ($ input-match-filter-ui {:value value :on-change on-change})
                  :output-match ($ output-match-filter-ui {:value value :on-change on-change})
                  :token-count ($ token-count-filter-ui {:value value :on-change on-change})
                  :feedback ($ feedback-filter-ui {:value value :on-change on-change :module-id module-id :agent-name agent-name})
                  :and ($ and-filter-ui {:value value :on-change on-change :module-id module-id :agent-name agent-name})
                  :or ($ or-filter-ui {:value value :on-change on-change :module-id module-id :agent-name agent-name})
                  :not ($ not-filter-ui {:value value :on-change on-change :module-id module-id :agent-name agent-name})
                  ($ :div {:class "text-sm text-base-content/50 italic"} "Unknown filter type"))))

          (when on-remove
            ($ :button {:type "button"
                        :class "btn btn-ghost btn-sm text-error"
                        :onClick on-remove
                        :title "Remove filter"}
               "✕"))))))

(defui filter-builder [{:keys [filter-value on-filter-change module-id agent-name]}]
  (let [filters (or (:filters filter-value) [])]
    ($ :div {:class "space-y-2"}
       ($ :label {:class "label"}
          ($ :span {:class "label-text font-medium"} "Filter (optional)"))
       ($ :div {:class "bg-base-200 p-4 rounded-lg space-y-2"}
          ($ :div {:class "text-sm font-medium mb-2"} "Match all of:")
          ($ :div {:class "space-y-2"}
             (for [[idx filter] (map-indexed vector filters)]
               ($ filter-node {:key idx
                               :value filter
                               :on-change #(on-filter-change
                                            (assoc filter-value :filters (assoc (vec filters) idx %)))
                               :on-remove #(on-filter-change
                                            (assoc filter-value :filters
                                                   (vec (concat (take idx filters)
                                                                (drop (inc idx) filters)))))
                               :module-id module-id
                               :agent-name agent-name}))
             ($ :button {:type "button"
                         :class "btn btn-outline btn-sm"
                         :onClick #(on-filter-change
                                    (assoc filter-value :filters
                                           (conj (vec filters) {:type :error})))}
                "+ Add Filter"))))))

;; =============================================================================
;; MAIN FORM COMPONENTS
;; =============================================================================

(defui scope-selector [{:keys [value on-change]}]
  ($ :div {:class "space-y-2"}
     ($ :label {:class "label"}
        ($ :span {:class "label-text font-medium"} "Scope"))
     ($ :div {:class "flex gap-4"}
        ($ :label {:class "flex items-center gap-2 cursor-pointer"}
           ($ :input {:type "radio"
                      :name "scope-type"
                      :class "radio radio-sm"
                      :checked (= value :agent)
                      :onChange #(on-change :agent)})
           ($ :span {:class "label-text"} "Agent"))
        ($ :label {:class "flex items-center gap-2 cursor-pointer"}
           ($ :input {:type "radio"
                      :name "scope-type"
                      :class "radio radio-sm"
                      :checked (= value :node)
                      :onChange #(on-change :node)})
           ($ :span {:class "label-text"} "Node")))))

(defui node-selector [{:keys [module-id agent-name value on-change]}]
  (let [{:keys [data loading?]}
        (queries/use-sente-query
         {:query-key [:graph module-id agent-name]
          :sente-event [:invocations/get-graph {:module-id module-id :agent-name agent-name}]
          :enabled? (boolean (and module-id agent-name))})

        nodes (when-let [graph (:graph data)]
                (sort (keys (:node-map graph))))]

    ($ :div {:class "form-control"}
       ($ :label {:class "label"}
          ($ :span {:class "label-text"} "Node")
          ($ :span {:class "text-error"} "*"))
       ($ :select {:class "select select-bordered select-sm w-full"
                   :value (or value "")
                   :onChange #(on-change (.. % -target -value))
                   :disabled (or loading? (not agent-name))}
          ($ :option {:value ""} (cond
                                   (not agent-name) "← Select an agent first"
                                   loading? "Loading nodes..."
                                   :else "Select a node..."))
          (for [node-name nodes]
            ($ :option {:key node-name :value node-name} node-name))))))

(defui start-time-field [{:keys [value on-change]}]
  (let [{:keys [mode date relative-value relative-unit]} value]
    ($ :div {:class "space-y-2"}
       ($ :label {:class "label"}
          ($ :span {:class "label-text font-medium"} "Start Time"))

       ($ :div {:class "space-y-2"}
          ($ :label {:class "flex items-center gap-2 cursor-pointer"}
             ($ :input {:type "radio"
                        :name "time-mode"
                        :class "radio radio-sm"
                        :checked (= mode :from-start)
                        :onChange #(on-change (assoc value :mode :from-start))})
             ($ :span {:class "label-text"} "From start"))

          ($ :label {:class "flex items-center gap-2 cursor-pointer"}
             ($ :input {:type "radio"
                        :name "time-mode"
                        :class "radio radio-sm"
                        :checked (= mode :absolute)
                        :onChange #(on-change (assoc value :mode :absolute))})
             ($ :span {:class "label-text"} "Specific date/time"))

          ($ :label {:class "flex items-center gap-2 cursor-pointer"}
             ($ :input {:type "radio"
                        :name "time-mode"
                        :class "radio radio-sm"
                        :checked (= mode :relative)
                        :onChange #(on-change (assoc value :mode :relative))})
             ($ :span {:class "label-text"} "Relative time ago")))

       (when (= mode :absolute)
         ($ :input {:type "datetime-local"
                    :class "input input-bordered input-sm w-full"
                    :value (or date "")
                    :onChange #(on-change (assoc value :date (.. % -target -value)))}))

       (when (= mode :relative)
         ($ :div {:class "flex gap-2 items-center"}
            ($ :input {:type "number"
                       :min "1"
                       :class "input input-bordered input-sm w-20"
                       :placeholder "5"
                       :value (or relative-value "")
                       :onChange #(let [parsed (js/parseInt (.. % -target -value))]
                                    (on-change (assoc value :relative-value
                                                      (if (js/isNaN parsed) nil parsed))))})
            ($ :select {:class "select select-bordered select-sm"
                        :value (name (or relative-unit :minutes))
                        :onChange #(on-change (assoc value :relative-unit (keyword (.. % -target -value))))}
               ($ :option {:value "minutes"} "minutes ago")
               ($ :option {:value "hours"} "hours ago")
               ($ :option {:value "days"} "days ago")
               ($ :option {:value "weeks"} "weeks ago")))))))

;; Separate components for each param type to avoid conditional hooks

(defui dataset-param-field [{:keys [value on-change module-id required?]}]
  (let [{:keys [data loading?]}
        (queries/use-sente-query
         {:query-key [:datasets module-id ""]
          :sente-event [:datasets/get-all {:module-id module-id}]
          :enabled? (boolean module-id)})
        datasets (or (:datasets data) [])]
    ($ :div {:class "form-control"}
       ($ :label {:class "label"}
          ($ :span {:class "label-text"} "Dataset")
          (when required? ($ :span {:class "text-error"} "*")))
       ($ :select {:class "select select-bordered select-sm w-full"
                   :value (or value "")
                   :onChange #(on-change (.. % -target -value))
                   :disabled loading?}
          ($ :option {:value ""} (if loading? "Loading..." "Select a dataset"))
          (for [ds datasets]
            ($ :option {:key (:dataset-id ds)
                        :value (str (:dataset-id ds))}
               (:name ds)))))))

(defui queue-param-field [{:keys [value on-change module-id required?]}]
  (let [[queues set-queues!] (uix/use-state [])
        [loading? set-loading!] (uix/use-state true)]
    (uix/use-effect
     (fn []
       (when module-id
         (set-loading! true)
         (sente/request!
          [:human-feedback/get-queues {:module-id module-id}]
          5000
          (fn [reply]
            (set-loading! false)
            (when (:success reply)
              (set-queues! (or (:items (:data reply)) []))))))
       js/undefined)
     [module-id])
    ($ :div {:class "form-control"}
       ($ :label {:class "label"}
          ($ :span {:class "label-text"} "Queue")
          (when required? ($ :span {:class "text-error"} "*")))
       ($ :select {:class "select select-bordered select-sm w-full"
                   :value (or value "")
                   :onChange #(on-change (.. % -target -value))
                   :disabled loading?}
          ($ :option {:value ""} (if loading? "Loading..." "Select a queue"))
          (for [q queues]
            ($ :option {:key (:name q) :value (:name q)}
               (:name q)))))))

(defui text-param-field [{:keys [param-name param-info value on-change required?]}]
  (let [description (:description param-info)]
    ($ :div {:class "form-control"}
       ($ :label {:class "label"}
          ($ :span {:class "label-text"} param-name)
          (when required? ($ :span {:class "text-error"} "*")))
       (when description
         ($ :p {:class "text-xs text-base-content/60 -mt-1 mb-1"} description))
       ($ :input {:type "text"
                  :class "input input-bordered input-sm w-full"
                  :value (or value (:default param-info) "")
                  :onChange #(on-change (.. % -target -value))}))))

(defui action-param-field [{:keys [param-name param-info value on-change module-id]}]
  (let [required? (nil? (:default param-info))]
    ;; Use separate components based on param type to avoid conditional hooks
    (case param-name
      "datasetId"
      ($ dataset-param-field {:value value
                              :on-change on-change
                              :module-id module-id
                              :required? required?})

      "queueName"
      ($ queue-param-field {:value value
                            :on-change on-change
                            :module-id module-id
                            :required? required?})

      ;; Default: text input
      ($ text-param-field {:param-name param-name
                           :param-info param-info
                           :value value
                           :on-change on-change
                           :required? required?}))))

(defui action-selector [{:keys [module-id agent-name action-name on-action-change action-params on-params-change]}]
  (let [[action-builders set-action-builders!] (uix/use-state nil)
        [loading? set-loading!] (uix/use-state true)]

    (uix/use-effect
     (fn []
       (when (and module-id agent-name)
         (set-loading! true)
         (sente/request!
          [:analytics/all-action-builders {:module-id module-id :agent-name agent-name}]
          5000
          (fn [reply]
            (set-loading! false)
            (when (:success reply)
              (set-action-builders! (:data reply))))))
       js/undefined)
     [module-id agent-name])

    (let [action-info (when action-builders (get action-builders action-name))
          params (when action-info (get-in action-info [:options :params]))]

      ($ :div {:class "space-y-4"}
         ($ :div {:class "form-control"}
            ($ :label {:class "label"}
               ($ :span {:class "label-text font-medium"} "Action")
               ($ :span {:class "text-error"} "*"))
            ($ :select {:class "select select-bordered w-full"
                        :value (or action-name "")
                        :disabled loading?
                        :onChange #(on-action-change (.. % -target -value))}
               ($ :option {:value ""} (if loading? "Loading actions..." "Select an action"))
               (when action-builders
                 (for [[action-id _] action-builders]
                   ($ :option {:key action-id :value action-id}
                      (action-friendly-name action-id))))))

         (when (seq params)
           ($ :div {:class "bg-base-200 p-4 rounded-lg space-y-3"}
              ($ :div {:class "text-sm font-medium"} "Action Parameters")
              (for [[param-name param-info] params]
                ($ action-param-field
                   {:key param-name
                    :param-name param-name
                    :param-info param-info
                    :value (get action-params param-name)
                    :on-change #(on-params-change (assoc action-params param-name %))
                    :module-id module-id}))))))))

;; =============================================================================
;; MAIN FORM
;; =============================================================================

(defui add-rule-form [{:keys [module-id agent-name on-success on-cancel]}]
  (let [;; Form state
        [rule-name set-rule-name!] (uix/use-state "")
        [scope-type set-scope-type!] (uix/use-state :agent)
        [node-name set-node-name!] (uix/use-state nil)
        [status-filter set-status-filter!] (uix/use-state :success)
        [sampling-rate set-sampling-rate!] (uix/use-state 1.0)
        [start-time set-start-time!] (uix/use-state {:mode :from-start
                                                      :date ""
                                                      :relative-value 1
                                                      :relative-unit :minutes})
        [action-name set-action-name!] (uix/use-state "")
        [action-params set-action-params!] (uix/use-state {})
        [filter-value set-filter-value!] (uix/use-state {:type :and :filters []})

        [submitting? set-submitting!] (uix/use-state false)
        [error set-error!] (uix/use-state nil)

        ;; Validation
        valid? (and (not (str/blank? rule-name))
                    (not (str/blank? action-name))
                    (or (= scope-type :agent) (not (str/blank? node-name))))

        handle-submit (fn []
                        (set-error! nil)
                        (set-submitting! true)
                        (let [start-time-millis (compute-start-time-millis start-time)
                              rule-spec {:node-name (when (= scope-type :node) node-name)
                                         :action-name action-name
                                         :action-params action-params
                                         :filter filter-value
                                         :sampling-rate sampling-rate
                                         :start-time-millis start-time-millis
                                         :status-filter status-filter}]
                          (sente/request!
                           [:analytics/add-rule {:module-id module-id
                                                 :agent-name agent-name
                                                 :rule-name rule-name
                                                 :rule-spec rule-spec}]
                           10000
                           (fn [reply]
                             (set-submitting! false)
                             (if (:success reply)
                               (on-success)
                               (set-error! (or (:error reply) "Failed to create rule")))))))]

    ($ :div {:class "space-y-6"}
       ;; Rule name
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text font-medium"} "Rule Name")
             ($ :span {:class "text-error"} "*"))
          ($ :input {:type "text"
                     :class "input input-bordered w-full"
                     :placeholder "my-rule"
                     :data-testid "input-rule-name"
                     :value rule-name
                     :onChange #(set-rule-name! (.. % -target -value))}))

       ;; Scope
       ($ scope-selector {:value scope-type
                          :on-change (fn [v]
                                       (set-scope-type! v)
                                       (when (= v :agent)
                                         (set-node-name! nil)))})

       ;; Node selector (when scope is node)
       (when (= scope-type :node)
         ($ node-selector {:module-id module-id
                           :agent-name agent-name
                           :value node-name
                           :on-change set-node-name!}))

       ;; Status filter
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text font-medium"} "Status Filter")
             ($ :span {:class "text-error"} "*"))
          ($ :select {:class "select select-bordered w-full"
                      :value (name status-filter)
                      :onChange #(set-status-filter! (keyword (.. % -target -value)))}
             ($ :option {:value "success"} "Success only")
             ($ :option {:value "all"} "All invocations")
             ($ :option {:value "fail"} "Failures only")))

       ;; Sampling rate
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text font-medium"} "Sampling Rate")
             ($ :span {:class "text-error"} "*"))
          ($ :input {:type "number"
                     :class "input input-bordered w-full"
                     :min "0"
                     :max "1"
                     :step "0.1"
                     :value sampling-rate
                     :onChange #(let [v (js/parseFloat (.. % -target -value))]
                                  (set-sampling-rate! (if (js/isNaN v) 1.0 (max 0 (min 1 v)))))})
          ($ :label {:class "label"}
             ($ :span {:class "label-text-alt text-base-content/60"}
                "Value between 0.0 and 1.0 (e.g., 0.1 = 10% of invocations)")))

       ;; Start time
       ($ start-time-field {:value start-time
                            :on-change set-start-time!})

       ;; Action
       ($ action-selector {:module-id module-id
                           :agent-name agent-name
                           :action-name action-name
                           :on-action-change set-action-name!
                           :action-params action-params
                           :on-params-change set-action-params!})

       ;; Filter builder
       ($ filter-builder {:filter-value filter-value
                          :on-filter-change set-filter-value!
                          :module-id module-id
                          :agent-name agent-name})

       ;; Error display
       (when error
         ($ :div {:class "alert alert-error"}
            ($ :span error)))

       ;; Actions
       ($ :div {:class "flex justify-end gap-2 pt-4 border-t border-base-300"}
          ($ :button {:class "btn btn-ghost"
                      :data-testid "btn-cancel"
                      :onClick on-cancel
                      :disabled submitting?}
             "Cancel")
          ($ :button {:class "btn btn-primary"
                      :data-testid "btn-submit-rule"
                      :onClick handle-submit
                      :disabled (or (not valid?) submitting?)}
             (if submitting?
               ($ :<>
                  ($ :span {:class "loading loading-spinner loading-sm"})
                  "Creating...")
               "Create Rule"))))))

;; =============================================================================
;; MODAL HELPERS
;; =============================================================================

(defn show-add-rule-modal! [module-id agent-name on-success]
  (state/dispatch
   [:modal/show
    {:title "Add Rule"
     :content ($ add-rule-form
                 {:module-id module-id
                  :agent-name agent-name
                  :on-success (fn []
                                (state/dispatch [:modal/hide])
                                (when on-success (on-success)))
                  :on-cancel #(state/dispatch [:modal/hide])})
     :size :lg
     :hide-actions? true}]))

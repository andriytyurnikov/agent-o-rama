(ns alt-frontend.routes.agents.$module-id.human-feedback-queues.$queue-id.items.$item-id
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.state :as state]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            [reitit.frontend.easy :as rfe]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.components.modal :as modal]
            [clojure.string :as str]
            [cljs.pprint]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

(defn url-encode [s]
  (when s (js/encodeURIComponent s)))

(defn to-json
  "Convert clojure data to JSON string for display."
  [data]
  (try
    (js/JSON.stringify (clj->js data) nil 2)
    (catch :default _
      (str data))))

(def TARGET-DOES-NOT-EXIST :com.rpl.agent-o-rama.impl.queries/target-does-not-exist)

(defn agent-target?
  "Returns true if the target is an agent (not a node)"
  [target]
  (nil? (:node-invoke target)))

(defn unwrap-agent-output
  "For agent targets, output is {:val <value> :failure? <bool>}.
   Returns {:failed? bool :value <unwrapped-value-or-original>}"
  [output target]
  (if (agent-target? target)
    {:failed? (:failure? output)
     :value (:val output)}
    {:failed? false
     :value output}))

;; Local storage for reviewer name persistence
(def REVIEWER_NAME_KEY "alt-frontend-reviewer-name")

(defn get-reviewer-name []
  (or (.getItem js/localStorage REVIEWER_NAME_KEY) ""))

(defn save-reviewer-name! [name]
  (.setItem js/localStorage REVIEWER_NAME_KEY name))

;; =============================================================================
;; METRIC HELPERS
;; =============================================================================

(defn numeric-metric? [metric]
  (and (contains? metric :min) (contains? metric :max)))

(defn category-metric? [metric]
  (contains? metric :categories))

;; =============================================================================
;; COMPONENTS
;; =============================================================================

(defui target-info-panel [{:keys [target module-id]}]
  (let [agent-name (:agent-name target)
        agent-invoke (:agent-invoke target)
        node-invoke (:node-invoke target)
        agent-task-id (:task-id agent-invoke)
        agent-invoke-id (:agent-invoke-id agent-invoke)
        is-node-target? (some? node-invoke)
        decoded-module-id (url-decode module-id)
        ;; Build URL to agent invocation trace
        base-url (str "/alt/agents/" (url-encode decoded-module-id)
                      "/agent/" (url-encode agent-name)
                      "/invocations/" agent-task-id "-" agent-invoke-id)
        trace-url (if node-invoke
                    (let [node-task-id (:task-id node-invoke)
                          node-invoke-id (:node-invoke-id node-invoke)
                          node-id (str node-task-id "-" node-invoke-id)]
                      (str base-url "?node=" (url-encode node-id)))
                    base-url)]
    ($ :div {:class "card bg-base-200 border border-base-300"}
       ($ :div {:class "card-body py-3"}
          ($ :div {:class "flex items-center justify-between mb-2"}
             ($ :span {:class "text-sm font-semibold"} "Target Information")
             ($ :a {:href trace-url
                    :target "_blank"
                    :class "btn btn-ghost btn-xs gap-1"}
                "View Trace"
                ($ icons/arrow-external {:class "h-4 w-4"})))
          ($ :div {:class "grid grid-cols-2 gap-x-4 gap-y-1 text-sm"}
             ($ :span {:class "text-base-content/60"} "Type:")
             ($ :span {:class "font-medium"} (if is-node-target? "Node" "Agent"))
             ($ :span {:class "text-base-content/60"} "Agent:")
             ($ :span {:class "font-mono"} agent-name)
             ($ :span {:class "text-base-content/60"} "Invocation:")
             ($ :span {:class "font-mono text-xs"} (str agent-task-id "-" agent-invoke-id))
             (when node-invoke
               ($ :<>
                  ($ :span {:class "text-base-content/60"} "Node Invoke:")
                  ($ :span {:class "font-mono text-xs"}
                     (str (:task-id node-invoke) "-" (:node-invoke-id node-invoke))))))))))

(defui expandable-json [{:keys [data title max-lines]
                         :or {max-lines 20}}]
  (let [json-str (if (string? data) data (to-json data))
        lines (str/split-lines json-str)
        truncated? (> (count lines) max-lines)
        preview (if truncated?
                  (str/join "\n" (take max-lines lines))
                  json-str)]
    ($ :div
       ($ :pre {:class "text-xs bg-base-200 p-3 rounded overflow-auto max-h-64 font-mono whitespace-pre"}
          preview)
       (when truncated?
         ($ :div {:class "mt-2 text-center"}
            ($ :button {:class "btn btn-ghost btn-xs"
                        :onClick #(modal/show-confirm!
                                   {:title title
                                    :message ($ :pre {:class "text-xs bg-base-200 p-4 rounded overflow-auto max-h-96 font-mono whitespace-pre"}
                                                json-str)
                                    :confirm-text "Close"
                                    :cancel-text nil})}
               (str "Show all " (count lines) " lines")))))))

(defui metric-input [{:keys [rubric value on-change error]}]
  (let [metric (:metric rubric)
        metric-name (:name rubric)
        is-required? (:required rubric)
        is-numeric? (numeric-metric? metric)
        is-category? (category-metric? metric)]
    ($ :div {:class "form-control"}
       ($ :label {:class "label"}
          ($ :span {:class "label-text font-medium"}
             metric-name
             (when is-required?
               ($ :span {:class "text-error ml-1"} "*")))
          (when (:description rubric)
            ($ :span {:class "label-text-alt text-base-content/60"}
               (:description rubric))))
       (cond
         ;; Numeric input
         is-numeric?
         ($ :input {:type "number"
                    :class (str "input input-bordered w-full "
                                (when error "input-error"))
                    :placeholder (str "Range: " (:min metric) " - " (:max metric))
                    :min (:min metric)
                    :max (:max metric)
                    :value (or value "")
                    :onChange #(on-change (.. % -target -value))})

         ;; Categorical select
         is-category?
         ($ :select {:class (str "select select-bordered w-full "
                                 (when error "select-error"))
                     :value (or value "")
                     :onChange #(on-change (.. % -target -value))}
            ($ :option {:value ""} "Select...")
            (for [category (sort (:categories metric))]
              ($ :option {:key category :value category} category)))

         ;; Fallback text input
         :else
         ($ :input {:type "text"
                    :class (str "input input-bordered w-full "
                                (when error "input-error"))
                    :value (or value "")
                    :onChange #(on-change (.. % -target -value))}))
       (when error
         ($ :label {:class "label"}
            ($ :span {:class "label-text-alt text-error"} error))))))

;; =============================================================================
;; ITEM REVIEW VIEW
;; =============================================================================

(defui view [{:keys [module-id queue-id item-id]}]
  (let [decoded-module-id (url-decode module-id)
        decoded-queue-id (url-decode queue-id)
        item-id-str (str item-id)

        ;; Fetch queue info for rubrics
        {:keys [data loading?]}
        (queries/use-sente-query
         {:query-key [:human-feedback-queue-info module-id queue-id]
          :sente-event [:human-feedback/get-queue-info
                        {:module-id decoded-module-id
                         :queue-name decoded-queue-id}]
          :enabled? (boolean (and decoded-module-id decoded-queue-id))})
        queue-info data
        queue-info-loading? loading?

        ;; Fetch queue items
        {:keys [data isLoading hasMore loadMore]}
        (queries/use-paginated-query
         {:query-key [:human-feedback-queue-items module-id queue-id]
          :sente-event [:human-feedback/get-queue-items
                        {:module-id decoded-module-id
                         :queue-name decoded-queue-id}]
          :page-size 20
          :enabled? (boolean (and decoded-module-id decoded-queue-id))})
        items-loading? isLoading
        items (or data [])

        ;; Find current item and navigation
        current-idx (some (fn [[idx item]] (when (= (str (:id item)) item-id-str) idx))
                          (map-indexed vector items))
        current-item (when current-idx (nth items current-idx nil))

        has-prev? (and current-idx (> current-idx 0))
        has-next? (and current-idx (or (< current-idx (dec (count items))) hasMore))
        prev-item-id (when (and current-idx (> current-idx 0))
                       (str (:id (nth items (dec current-idx)))))
        next-item-id (when (and current-idx (< current-idx (dec (count items))))
                       (str (:id (nth items (inc current-idx)))))

        ;; Form state
        [scores set-scores] (uix/use-state {})
        [comment set-comment] (uix/use-state "")
        [reviewer-name set-reviewer-name] (uix/use-state (get-reviewer-name))
        [errors set-errors] (uix/use-state {})
        [submitting? set-submitting?] (uix/use-state false)

        ;; Pending navigation flags for async load more
        [pending-next? set-pending-next?] (uix/use-state false)

        ;; Validation
        validate-metric (fn [rubric value]
                          (let [metric (:metric rubric)]
                            (cond
                              (or (nil? value) (= value "")) nil
                              (numeric-metric? metric)
                              (let [int-val (js/parseInt value 10)]
                                (cond
                                  (js/isNaN int-val) "Must be an integer"
                                  (< int-val (:min metric)) (str "Must be at least " (:min metric))
                                  (> int-val (:max metric)) (str "Must be at most " (:max metric))
                                  :else nil))
                              :else nil)))

        validate-form (fn []
                        (let [errs (reduce
                                    (fn [acc rubric]
                                      (let [metric-name (:name rubric)
                                            value (get scores metric-name)
                                            metric (:metric rubric)]
                                        (cond
                                          (and (:required rubric) (or (nil? value) (= value "")))
                                          (assoc acc metric-name "This field is required")

                                          (and (numeric-metric? metric) value (not= value ""))
                                          (if-let [err (validate-metric rubric value)]
                                            (assoc acc metric-name err)
                                            acc)

                                          :else acc)))
                                    {}
                                    (or (:rubrics queue-info) []))]
                          (if (str/blank? reviewer-name)
                            (assoc errs :reviewer-name "Reviewer name is required")
                            errs)))

        ;; Navigation handlers
        handle-prev (fn []
                      (when prev-item-id
                        (rfe/push-state :module/human-feedback-queue-item
                                        {:module-id module-id
                                         :queue-id queue-id
                                         :item-id prev-item-id})))

        handle-next (fn []
                      (cond
                        next-item-id
                        (rfe/push-state :module/human-feedback-queue-item
                                        {:module-id module-id
                                         :queue-id queue-id
                                         :item-id next-item-id})
                        hasMore
                        (do
                          (set-pending-next? true)
                          (loadMore))))

        ;; Submit handler
        handle-submit (fn []
                        (let [validation-errors (validate-form)]
                          (if (empty? validation-errors)
                            (do
                              (set-submitting? true)
                              (save-reviewer-name! reviewer-name)
                              (sente/request!
                               [:human-feedback/resolve-queue-item
                                {:module-id decoded-module-id
                                 :queue-name decoded-queue-id
                                 :item-id item-id-str
                                 :target (:target current-item)
                                 :reviewer-name reviewer-name
                                 :scores scores
                                 :comment comment}]
                               10000
                               (fn [reply]
                                 (set-submitting? false)
                                 (if (:success reply)
                                   (do
                                     ;; Invalidate cache
                                     (state/dispatch [:query/invalidate
                                                      {:query-key-pattern [:human-feedback-queue-items module-id queue-id]}])
                                     ;; Clear form
                                     (set-scores {})
                                     (set-comment "")
                                     (set-errors {})
                                     ;; Auto-advance
                                     (if has-next?
                                       (handle-next)
                                       (rfe/push-state :module/human-feedback-queue-end
                                                       {:module-id module-id
                                                        :queue-id queue-id})))
                                   (js/alert (str "Error submitting: " (:error reply)))))))
                            (set-errors validation-errors))))

        ;; Dismiss handler
        handle-dismiss (fn []
                         (modal/show-confirm!
                          {:title "Dismiss Item"
                           :message "Remove this item from the queue without adding feedback? This action cannot be undone."
                           :confirm-text "Dismiss"
                           :confirm-variant :error
                           :on-confirm (fn []
                                         (sente/request!
                                          [:human-feedback/dismiss-queue-item
                                           {:module-id decoded-module-id
                                            :queue-name decoded-queue-id
                                            :item-id item-id-str}]
                                          10000
                                          (fn [reply]
                                            (if (:success reply)
                                              (do
                                                (state/dispatch [:query/invalidate
                                                                 {:query-key-pattern [:human-feedback-queue-items module-id queue-id]}])
                                                (if has-next?
                                                  (handle-next)
                                                  (rfe/push-state :module/human-feedback-queue-detail
                                                                  {:module-id module-id
                                                                   :queue-id queue-id})))
                                              (js/alert (str "Error dismissing: " (:error reply)))))))}))]

    ;; Effect: handle pending navigation after load more completes
    (uix/use-effect
     (fn []
       (when (and pending-next? next-item-id)
         (set-pending-next? false)
         (rfe/push-state :module/human-feedback-queue-item
                         {:module-id module-id
                          :queue-id queue-id
                          :item-id next-item-id}))
       js/undefined)
     [pending-next? next-item-id module-id queue-id])

    (cond
      ;; Loading
      (or queue-info-loading? items-loading?)
      ($ ui/loading-state {:message "Loading item..."})

      ;; Item not found
      (not current-item)
      ($ :div {:class "space-y-4"}
         ($ ui/warning-alert {:title "Item Not Found"
                              :message "The requested item could not be found in this queue."})
         ($ ui/button {:variant :ghost
                       :on-click #(rfe/push-state :module/human-feedback-queue-detail
                                                  {:module-id module-id
                                                   :queue-id queue-id})}
            "Back to Queue"))

      ;; Review form
      :else
      (let [{:keys [failed? value]} (unwrap-agent-output (:output current-item) (:target current-item))]
        ($ :div {:class "space-y-6 max-w-4xl mx-auto"}
           ;; Header with navigation
           ($ :div {:class "flex items-center justify-between"}
              ($ :h1 {:class "text-2xl font-bold"} "Review Item")
              ($ :div {:class "join"}
                 ($ :button {:class "join-item btn btn-sm"
                             :data-testid "btn-previous"
                             :disabled (not has-prev?)
                             :onClick handle-prev}
                    ($ icons/chevron-left {:class "h-5 w-5"}))
                 ($ :button {:class "join-item btn btn-sm"
                             :data-testid "btn-next"
                             :disabled (not has-next?)
                             :onClick handle-next}
                    ($ icons/chevron-right {:class "h-5 w-5"}))))

           ;; Target info
           ($ target-info-panel {:target (:target current-item)
                                 :module-id module-id})

           ;; Comment (if present)
           (when-let [item-comment (:comment current-item)]
             (when-not (str/blank? item-comment)
               ($ :div {:class "alert alert-info"}
                  ($ :span item-comment))))

           ;; Input/Output display
           ($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
              ;; Input
              ($ :div {:class "card bg-base-100 border border-base-300"}
                 ($ :div {:class "card-body py-3"}
                    ($ :h3 {:class "text-sm font-semibold mb-2"} "Input")
                    ($ expandable-json {:data (:input current-item)
                                        :title "Input"})))
              ;; Output
              ($ :div {:class "card bg-base-100 border border-base-300"}
                 ($ :div {:class "card-body py-3"}
                    ($ :h3 {:class (str "text-sm font-semibold mb-2 "
                                        (when failed? "text-error"))}
                       (if failed? "Output (Failed)" "Output"))
                    ($ expandable-json {:data value
                                        :title "Output"}))))

           ;; Evaluation form
           ($ :div {:class "card bg-base-100 border border-base-300"}
              ($ :div {:class "card-body"}
                 ($ :h2 {:class "card-title mb-4"} "Evaluation")

                 ;; Metrics
                 ($ :div {:class "space-y-4"}
                    (for [rubric (or (:rubrics queue-info) [])]
                      (let [metric-name (:name rubric)]
                        ($ metric-input {:key metric-name
                                         :rubric rubric
                                         :value (get scores metric-name)
                                         :on-change (fn [v]
                                                      (set-scores (assoc scores metric-name v))
                                                      (let [err (validate-metric rubric v)]
                                                        (set-errors (if err
                                                                      (assoc errors metric-name err)
                                                                      (dissoc errors metric-name)))))
                                         :error (get errors metric-name)}))))

                 ;; Comment
                 ($ :div {:class "form-control mt-4"}
                    ($ :label {:class "label"}
                       ($ :span {:class "label-text"} "Comment (optional)"))
                    ($ :textarea {:class "textarea textarea-bordered"
                                  :rows 3
                                  :placeholder "Add any notes about this evaluation..."
                                  :value comment
                                  :onChange #(set-comment (.. % -target -value))}))

                 ;; Reviewer name
                 ($ :div {:class "form-control mt-4"}
                    ($ :label {:class "label"}
                       ($ :span {:class "label-text"}
                          "Reviewer Name"
                          ($ :span {:class "text-error ml-1"} "*")))
                    ($ :input {:type "text"
                               :class (str "input input-bordered w-full "
                                           (when (:reviewer-name errors) "input-error"))
                               :placeholder "Your name"
                               :data-testid "input-reviewer-name"
                               :value reviewer-name
                               :onChange #(set-reviewer-name (.. % -target -value))})
                    (when (:reviewer-name errors)
                      ($ :label {:class "label"}
                         ($ :span {:class "label-text-alt text-error"}
                            (:reviewer-name errors)))))))

           ;; Action buttons
           ($ :div {:class "flex justify-between"}
              ($ :button {:class "btn btn-outline btn-error"
                          :data-testid "btn-skip"
                          :onClick handle-dismiss}
                 ($ icons/x-mark {:class "h-5 w-5 mr-2"})
                 "Dismiss")
              (let [has-required-empty? (some (fn [rubric]
                                                (and (:required rubric)
                                                     (let [v (get scores (:name rubric))]
                                                       (or (nil? v) (= v "")))))
                                              (or (:rubrics queue-info) []))
                    is-invalid? (or (seq errors)
                                    (str/blank? reviewer-name)
                                    has-required-empty?)]
                ($ :button {:class (str "btn btn-primary "
                                        (when submitting? "loading"))
                            :data-testid "btn-submit-feedback"
                            :disabled (or is-invalid? submitting?)
                            :onClick handle-submit}
                   (when-not submitting?
                     "Submit & Continue")))))))))

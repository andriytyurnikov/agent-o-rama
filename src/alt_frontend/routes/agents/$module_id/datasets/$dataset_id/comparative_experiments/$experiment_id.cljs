(ns alt-frontend.routes.agents.$module-id.datasets.$dataset-id.comparative-experiments.$experiment-id
  "Comparative experiment detail page showing side-by-side results."
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.state :as state]
            [alt-frontend.components.ui :as ui]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

;; =============================================================================
;; URL ENCODING HELPERS
;; =============================================================================

(defn url-decode
  "Decode URL-encoded string"
  [s]
  (when s
    (try
      (js/decodeURIComponent s)
      (catch js/Error _ s))))

(defn url-encode
  "URL-encode a string"
  [s]
  (when s
    (js/encodeURIComponent s)))

;; =============================================================================
;; FORMATTING HELPERS
;; =============================================================================

(defn format-relative-time
  "Format timestamp as relative time"
  [timestamp-ms]
  (when timestamp-ms
    (let [now (js/Date.now)
          diff-ms (- now timestamp-ms)
          diff-sec (/ diff-ms 1000)
          diff-min (/ diff-sec 60)
          diff-hr (/ diff-min 60)
          diff-day (/ diff-hr 24)]
      (cond
        (< diff-sec 60) "just now"
        (< diff-min 60) (str (int diff-min) " min ago")
        (< diff-hr 24) (str (int diff-hr) " hr ago")
        (< diff-day 7) (str (int diff-day) " days ago")
        :else (.toLocaleDateString (js/Date. timestamp-ms))))))

(defn format-duration-ms
  "Format milliseconds as human-readable duration"
  [ms]
  (cond
    (nil? ms) nil
    (< ms 1000) (str ms "ms")
    (< ms 60000) (str (.toFixed (/ ms 1000) 1) "s")
    :else (str (int (/ ms 60000)) "m " (int (mod (/ ms 1000) 60)) "s")))

(defn pretty-print-json
  "Pretty print a value as JSON"
  [value]
  (try
    (js/JSON.stringify (clj->js value) nil 2)
    (catch js/Error _
      (pr-str value))))

;; =============================================================================
;; SELECTOR EVALUATOR HELPERS
;; =============================================================================

(defn find-all-selector-evaluators
  "Find all evaluators that return an 'index' key (selector evaluators).
   Returns a map of evaluator-name -> index value."
  [evals]
  (into {}
        (keep (fn [[eval-name metrics]]
                (when-let [idx (or (get metrics "index")
                                   (get metrics :index))]
                  [eval-name idx]))
              evals)))

(defn find-winner-index
  "Find the winning target index from a specific evaluator's results."
  ([evals] (find-winner-index evals nil))
  ([evals evaluator-name]
   (if evaluator-name
     (let [metrics (get evals evaluator-name)]
       (or (get metrics "index")
           (get metrics :index)))
     (some (fn [[_eval-name metrics]]
             (or (get metrics "index")
                 (get metrics :index)))
           evals))))

(defn filter-non-selector-evals
  "Filter out evaluators that have an 'index' key (selector evaluators)."
  [evals]
  (into {}
        (remove (fn [[_eval-name metrics]]
                  (or (contains? metrics "index")
                      (contains? metrics :index)))
                evals)))

;; =============================================================================
;; STATUS COMPONENTS
;; =============================================================================

(defui status-badge
  "Badge showing experiment status"
  [{:keys [status]}]
  (case status
    :completed ($ :span {:class "badge badge-success"} "Completed")
    :failed ($ :span {:class "badge badge-error"} "Failed")
    ($ :span {:class "badge badge-info gap-1"}
       ($ :span {:class "loading loading-spinner loading-xs"})
       "Running")))

;; =============================================================================
;; HEADER
;; =============================================================================

(defui experiment-header
  "Header with back button, title, and status"
  [{:keys [info status module-id dataset-id show-details? on-toggle-details]}]
  ($ :div {:class "flex flex-col gap-4"}
     ($ :div {:class "flex items-center justify-between"}
        ($ :div {:class "flex items-center gap-4"}
           ($ :a {:href (rfe/href :comparative-experiments {:module-id module-id :dataset-id dataset-id})
                  :class "btn btn-ghost btn-sm gap-1"}
              "← Back")
           ($ :h1 {:class "text-2xl font-bold"} (:name info))
           ($ :button {:class "btn btn-ghost btn-sm"
                       :onClick on-toggle-details}
              (if show-details? "Hide Details ▲" "Show Details ▼")))
        ($ status-badge {:status status}))))

;; =============================================================================
;; DETAILS PANEL
;; =============================================================================

(defui detail-item
  "Key-value item in details panel"
  [{:keys [label children]}]
  ($ :div {:class "py-2"}
     ($ :dt {:class "text-sm font-medium text-base-content/60"} label)
     ($ :dd {:class "mt-1"} children)))

(defui details-panel
  "Collapsible panel showing experiment configuration"
  [{:keys [info]}]
  (let [spec (:spec info)
        targets (:targets spec)]
    ($ :div {:class "bg-base-200 rounded-lg p-4"}
       ($ :dl {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
          (when-let [id (:id info)]
            ($ detail-item {:label "ID"}
               ($ :code {:class "text-xs bg-base-300 p-1 rounded"} (str id))))
          ($ detail-item {:label "Type"}
             ($ :span "Comparative"))
          (for [[idx t] (map-indexed vector (or targets []))]
            ($ detail-item {:key (str "target-" idx) :label (str "Target " (inc idx))}
               ($ :pre {:class "text-xs bg-base-300 p-2 rounded overflow-auto max-h-32"}
                  (pretty-print-json t))))
          ($ detail-item {:label "Snapshot"}
             ($ :pre {:class "text-xs bg-base-300 p-2 rounded overflow-auto max-h-32"}
                (pretty-print-json (:snapshot info))))
          ($ detail-item {:label "Selector"}
             ($ :pre {:class "text-xs bg-base-300 p-2 rounded overflow-auto max-h-32"}
                (pretty-print-json (:selector info))))
          (when-let [evaluators (:evaluators info)]
            ($ detail-item {:label "Evaluators"}
               ($ :pre {:class "text-xs bg-base-300 p-2 rounded overflow-auto max-h-32"}
                  (pretty-print-json evaluators))))))))

;; =============================================================================
;; ERROR PANEL
;; =============================================================================

(defui error-panel
  "Panel showing experiment error"
  [{:keys [error-info]}]
  ($ :div {:class "alert alert-error"}
     ($ :div
        ($ :h3 {:class "font-bold"} "Experiment Failed")
        (when-let [cause (:cause error-info)]
          ($ :pre {:class "text-sm mt-2 whitespace-pre-wrap"} cause)))))

;; =============================================================================
;; EVALUATOR METRICS
;; =============================================================================

(defn format-metric-value
  "Format an evaluator metric value for display"
  [value]
  (cond
    (true? value) ($ :span {:class "text-success"} "True")
    (false? value) ($ :span {:class "text-error"} "False")
    (number? value) (if (and (float? value) (not= value (js/Math.floor value)))
                      (.toFixed value 2)
                      (str value))
    (string? value) (if (> (count value) 20) (str (subs value 0 17) "…") value)
    (nil? value) ($ :span {:class "italic text-base-content/50"} "nil")
    :else ($ :span {:class "italic text-base-content/50"} "…")))

(defui eval-badge
  "Badge showing an evaluator result"
  [{:keys [eval-name metric-key metric-value failure?]}]
  (let [label (str (name eval-name) "/" (name metric-key))
        [badge-class content]
        (cond
          failure?
          ["badge-error" "Failed"]

          (true? metric-value)
          ["badge-success" "✓"]

          (false? metric-value)
          ["badge-warning" "✗"]

          (number? metric-value)
          ["badge-info" (format-metric-value metric-value)]

          :else
          ["badge-ghost" (format-metric-value metric-value)])]
    ($ :span {:class (str "badge badge-sm gap-1 " badge-class)
              :title label}
       ($ :span {:class "text-xs truncate max-w-[6rem]"} (name metric-key))
       content)))

;; =============================================================================
;; RESULTS TABLE
;; =============================================================================

(defui cell-content
  "Truncatable cell content"
  [{:keys [content truncated?]}]
  (let [content-str (if (string? content) content (pretty-print-json content))]
    ($ :div {:class (if truncated?
                      "max-w-xs truncate"
                      "max-w-xl whitespace-pre-wrap break-words")}
       content-str)))

(defui output-cell
  "Cell for a single target output"
  [{:keys [agent-result is-winner? show-full-text?]}]
  (let [failed? (:failure? (:result agent-result))
        output-val (get-in agent-result [:result :val])
        duration-ms (when (and (:start-time-millis agent-result)
                               (:finish-time-millis agent-result))
                      (- (:finish-time-millis agent-result)
                         (:start-time-millis agent-result)))
        token-count (:total-token-count agent-result)]
    ($ :td {:class (str "align-top " (when is-winner? "bg-success/10"))}
       (if agent-result
         ($ :div {:class "space-y-2"}
            ;; Output content
            (if failed?
              ($ :span {:class "badge badge-error"} "FAILED")
              ($ cell-content {:content output-val :truncated? (not show-full-text?)}))
            ;; Stats badges
            ($ :div {:class "flex flex-wrap gap-1"}
               (when duration-ms
                 ($ :span {:class "badge badge-ghost badge-sm"}
                    (format-duration-ms duration-ms)))
               (when token-count
                 ($ :span {:class "badge badge-ghost badge-sm"}
                    (str token-count " tokens")))
               (when is-winner?
                 ($ :span {:class "badge badge-success badge-sm"} "Winner"))))
         ($ :div {:class "flex items-center gap-2 text-base-content/50"}
            ($ :span {:class "loading loading-spinner loading-sm"})
            "Running...")))))

(defui result-row
  "Single row in comparative results table"
  [{:keys [run num-targets show-full-text? active-selector]}]
  (let [winning-index (find-winner-index (:evals run) active-selector)
        non-selector-evals (filter-non-selector-evals (:evals run))]
    ($ :tr {:class "hover"}
       ;; Input
       ($ :td {:class "align-top"}
          ($ cell-content {:content (:input run) :truncated? (not show-full-text?)}))
       ;; Reference Output
       ($ :td {:class "align-top"}
          ($ cell-content {:content (:reference-output run) :truncated? (not show-full-text?)}))
       ;; Output columns for each target
       (for [i (range num-targets)]
         (let [agent-result (get-in run [:agent-results i])
               is-winner? (and winning-index (= i winning-index))]
           ($ output-cell {:key (str "output-" i)
                           :agent-result agent-result
                           :is-winner? is-winner?
                           :show-full-text? show-full-text?})))
       ;; Evals column - non-selector evaluators only
       ($ :td {:class "align-top"}
          ($ :div {:class "flex flex-wrap gap-1"}
             (for [[eval-name metrics] non-selector-evals
                   [metric-key metric-value] metrics]
               ($ eval-badge {:key (str eval-name "-" metric-key)
                              :eval-name eval-name
                              :metric-key metric-key
                              :metric-value metric-value}))
             (for [[eval-name _] (:eval-failures run)]
               ($ eval-badge {:key (str eval-name "-failure")
                              :eval-name eval-name
                              :metric-key :error
                              :failure? true})))))))

(defui results-table
  "Table of comparative experiment results"
  [{:keys [data module-id]}]
  (let [[show-full-text? set-show-full-text] (uix/use-state false)
        [selected-selector set-selected-selector] (uix/use-state nil)

        experiment-info (:experiment-info data)
        targets (get-in experiment-info [:spec :targets])
        num-targets (count targets)
        results (vals (:results data))

        ;; Find all selector evaluators across all results
        all-selector-evals (reduce (fn [acc run]
                                     (merge acc (find-all-selector-evaluators (:evals run))))
                                   {}
                                   results)
        selector-eval-names (vec (keys all-selector-evals))
        active-selector (or selected-selector (first selector-eval-names))]

    ($ :div {:class "space-y-4"}
       ;; Controls
       ($ :div {:class "flex items-center justify-between flex-wrap gap-4"}
          ($ :h3 {:class "text-xl font-bold"} "Comparative Results")
          ($ :div {:class "flex items-center gap-4"}
             ;; Selector evaluator dropdown
             (when (seq selector-eval-names)
               ($ :div {:class "flex items-center gap-2"}
                  ($ :span {:class "text-sm text-base-content/60"} "Highlighting:")
                  ($ :select {:class "select select-bordered select-sm"
                              :value (or active-selector "")
                              :onChange #(set-selected-selector (.. % -target -value))}
                     (for [eval-name selector-eval-names]
                       ($ :option {:key eval-name :value eval-name} eval-name)))))
             ;; Full text toggle
             ($ :label {:class "label cursor-pointer gap-2"}
                ($ :span {:class "label-text"} "Full text")
                ($ :input {:type "checkbox"
                           :class "toggle toggle-sm"
                           :checked show-full-text?
                           :onChange #(set-show-full-text (not show-full-text?))}))))

       ;; Table
       (if (empty? results)
         ($ :div {:class "text-center py-8 text-base-content/50 bg-base-200 rounded-lg"}
            "No results yet.")
         ($ :div {:class "overflow-x-auto"}
            ($ :table {:class "table"}
               ($ :thead
                  ($ :tr
                     ($ :th "Input")
                     ($ :th "Reference Output")
                     (for [i (range num-targets)]
                       ($ :th {:key (str "output-header-" i)}
                          (str "Output " (inc i))))
                     ($ :th "Evals")))
               ($ :tbody
                  (for [[idx run] (map-indexed vector results)]
                    ($ result-row {:key (str (:example-id run) "-" idx)
                                   :run run
                                   :num-targets num-targets
                                   :show-full-text? show-full-text?
                                   :active-selector active-selector})))))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Comparative experiment detail view"
  [{:keys [module-id dataset-id experiment-id]}]
  (let [;; Query for experiment results
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiment-results module-id dataset-id experiment-id]
          :sente-event [:experiments/get-results
                        {:module-id module-id
                         :dataset-id dataset-id
                         :experiment-id experiment-id}]
          :enabled? (boolean (and module-id dataset-id experiment-id))
          :refetch-interval-ms 2000})

        [show-details? set-show-details] (uix/use-state false)

        inv-error (:invocation-error data)
        status (cond
                 inv-error :failed
                 (:finish-time-millis data) :completed
                 :else :running)]

    (cond
      (and loading? (not data))
      ($ ui/loading-state {:message "Loading experiment..."})

      error
      ($ ui/error-alert {:message (str "Error loading experiment: " error)})

      (not data)
      ($ ui/empty-state
         {:title "Experiment not found"
          :description "The requested experiment could not be found."})

      :else
      ($ :div {:class "space-y-6"}
         ;; Header
         ($ experiment-header
            {:info (:experiment-info data)
             :status status
             :module-id module-id
             :dataset-id dataset-id
             :show-details? show-details?
             :on-toggle-details #(set-show-details (not show-details?))})

         ;; Details panel (collapsible)
         (when show-details?
           ($ details-panel {:info (:experiment-info data)}))

         ;; Error panel
         (when inv-error
           ($ error-panel {:error-info inv-error}))

         ;; Results table
         ($ results-table {:data data :module-id module-id})))))

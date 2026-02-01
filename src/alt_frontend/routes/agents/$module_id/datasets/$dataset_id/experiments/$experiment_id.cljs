(ns alt-frontend.routes.agents.$module-id.datasets.$dataset-id.experiments.$experiment-id
  "Regular experiment detail page showing results and statistics."
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
     ;; Top row: Back link and status
     ($ :div {:class "flex items-center justify-between"}
        ($ :div {:class "flex items-center gap-4"}
           ($ :a {:href (rfe/href :experiments {:module-id module-id :dataset-id dataset-id})
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
        exp-type (name (or (:type spec) (if (:target spec) :regular :comparative)))
        targets (if (:target spec)
                  [(:target spec)]
                  (:targets spec))]
    ($ :div {:class "bg-base-200 rounded-lg p-4"}
       ($ :dl {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
          (when-let [id (:id info)]
            ($ detail-item {:label "ID"}
               ($ :code {:class "text-xs bg-base-300 p-1 rounded"} (str id))))
          ($ detail-item {:label "Type"}
             ($ :span exp-type))
          (for [[idx t] (map-indexed vector (or targets []))]
            (let [label (if (= 1 (count targets))
                          "Target"
                          (str "Target " (inc idx)))]
              ($ detail-item {:key (str "target-" idx) :label label}
                 ($ :pre {:class "text-xs bg-base-300 p-2 rounded overflow-auto max-h-32"}
                    (pretty-print-json t)))))
          ($ detail-item {:label "Snapshot"}
             ($ :pre {:class "text-xs bg-base-300 p-2 rounded overflow-auto max-h-32"}
                (pretty-print-json (:snapshot info))))
          ($ detail-item {:label "Selector"}
             ($ :pre {:class "text-xs bg-base-300 p-2 rounded overflow-auto max-h-32"}
                (pretty-print-json (:selector info))))
          (when-let [evaluators (:evaluators info)]
            ($ detail-item {:label "Evaluators"}
               ($ :pre {:class "text-xs bg-base-300 p-2 rounded overflow-auto max-h-32"}
                  (pretty-print-json evaluators))))
          (when-let [nr (:num-repetitions info)]
            ($ detail-item {:label "Repetitions"}
               ($ :span (str nr))))
          (when-let [conc (:concurrency info)]
            ($ detail-item {:label "Concurrency"}
               ($ :span (str conc))))))))

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
;; SUMMARY STATS
;; =============================================================================

(defui stat-card
  "Single statistic card"
  [{:keys [label value tooltip]}]
  ($ :div {:class "stat place-items-center"
           :title tooltip}
     ($ :div {:class "stat-title"} label)
     ($ :div {:class "stat-value text-lg"} (or value "N/A"))))

(defui summary-stats
  "Summary statistics cards"
  [{:keys [data]}]
  (let [latency-stats (:latency-number-stats data)
        token-stats (:total-token-number-stats data)
        num-examples (or (:count latency-stats) 0)
        avg-latency (when (and latency-stats (pos? num-examples))
                      (str (int (/ (:total latency-stats) num-examples)) " ms"))
        p99-latency (when-let [p99 (get-in latency-stats [:percentiles 0.99])]
                      (str p99 " ms"))
        avg-tokens (when (and token-stats (pos? num-examples))
                     (.toLocaleString (int (/ (:total token-stats) num-examples))))]
    ($ :div {:class "stats stats-vertical lg:stats-horizontal shadow bg-base-100 w-full"}
       ($ stat-card {:label "Examples" :value num-examples})
       ($ stat-card {:label "Avg Latency" :value avg-latency})
       ($ stat-card {:label "P99 Latency" :value p99-latency
                     :tooltip "99% of runs completed faster than this time"})
       ($ stat-card {:label "Avg Tokens" :value avg-tokens
                     :tooltip "Average total tokens per example"}))))

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
  (let [content-str (if (string? content) content (pretty-print-json content))
        is-long? (> (count content-str) 100)]
    ($ :div {:class (if truncated?
                      "max-w-xs truncate"
                      "max-w-xl whitespace-pre-wrap break-words")}
       content-str)))

(defui result-row
  "Single row in results table"
  [{:keys [run show-full-text? module-id]}]
  (let [agent-result (get-in run [:agent-results 0])
        failed? (:failure? (:result agent-result))
        output-val (get-in agent-result [:result :val])
        duration-ms (when (and (:start-time-millis agent-result)
                               (:finish-time-millis agent-result))
                      (- (:finish-time-millis agent-result)
                         (:start-time-millis agent-result)))
        token-info {:input (:input-token-count agent-result)
                    :output (:output-token-count agent-result)
                    :total (:total-token-count agent-result)}
        target-initiate (-> run :agent-initiates vals first)
        trace-url (when target-initiate
                    (let [task-id (:task-id (:agent-invoke target-initiate))
                          invoke-id (:agent-invoke-id (:agent-invoke target-initiate))]
                      (when (and task-id invoke-id)
                        (rfe/href :invocations-detail
                                  {:module-id module-id
                                   :agent-name (url-encode (:agent-name target-initiate))
                                   :invoke-id (str task-id "-" invoke-id)}))))]
    ($ :tr {:class "hover"}
       ;; Input
       ($ :td
          ($ :div {:class "space-y-2"}
             ($ cell-content {:content (:input run) :truncated? (not show-full-text?)})
             ($ :div {:class "flex flex-wrap gap-1"}
                (when duration-ms
                  ($ :span {:class "badge badge-ghost badge-sm"}
                     (format-duration-ms duration-ms)))
                (when (:total token-info)
                  ($ :span {:class "badge badge-ghost badge-sm"}
                     (str (:total token-info) " tokens")))
                (when trace-url
                  ($ :a {:href trace-url
                         :target "_blank"
                         :class "badge badge-primary badge-sm gap-1"
                         :onClick #(.stopPropagation %)}
                     "View Trace →")))))
       ;; Reference Output
       ($ :td
          ($ cell-content {:content (:reference-output run) :truncated? (not show-full-text?)}))
       ;; Output & Evals
       ($ :td
          (if agent-result
            ($ :div {:class "space-y-2"}
               ;; Output
               (if failed?
                 ($ :span {:class "badge badge-error"} "FAILED")
                 ($ cell-content {:content output-val :truncated? (not show-full-text?)}))
               ;; Evaluator badges
               ($ :div {:class "flex flex-wrap gap-1"}
                  (for [[eval-name metrics] (:evals run)
                        [metric-key metric-value] metrics]
                    ($ eval-badge {:key (str eval-name "-" metric-key)
                                   :eval-name eval-name
                                   :metric-key metric-key
                                   :metric-value metric-value}))
                  (for [[eval-name _] (:eval-failures run)]
                    ($ eval-badge {:key (str eval-name "-failure")
                                   :eval-name eval-name
                                   :metric-key :error
                                   :failure? true}))))
            ($ :div {:class "flex items-center gap-2 text-base-content/50"}
               ($ :span {:class "loading loading-spinner loading-sm"})
               "Running..."))))))

(defui results-table
  "Table of experiment results"
  [{:keys [results module-id]}]
  (let [[show-full-text? set-show-full-text] (uix/use-state false)
        [filter-type set-filter-type] (uix/use-state :all)

        ;; Filter results
        is-failure? (fn [run]
                      (or (get-in run [:agent-results 0 :result :failure?])
                          (seq (:eval-failures run))))
        filtered-results (case filter-type
                           :all results
                           :success (remove is-failure? results)
                           :failure (filter is-failure? results))]

    ($ :div {:class "space-y-4"}
       ;; Controls
       ($ :div {:class "flex items-center justify-between"}
          ($ :h3 {:class "text-xl font-bold"} "Results")
          ($ :div {:class "flex items-center gap-4"}
             ;; Filter buttons
             ($ :div {:class "join"}
                ($ :button {:class (str "join-item btn btn-sm "
                                        (when (= filter-type :all) "btn-active"))
                            :onClick #(set-filter-type :all)}
                   "All")
                ($ :button {:class (str "join-item btn btn-sm "
                                        (when (= filter-type :success) "btn-active"))
                            :onClick #(set-filter-type :success)}
                   "Success")
                ($ :button {:class (str "join-item btn btn-sm "
                                        (when (= filter-type :failure) "btn-active"))
                            :onClick #(set-filter-type :failure)}
                   "Failure"))
             ;; Full text toggle
             ($ :label {:class "label cursor-pointer gap-2"}
                ($ :span {:class "label-text"} "Full text")
                ($ :input {:type "checkbox"
                           :class "toggle toggle-sm"
                           :checked show-full-text?
                           :onChange #(set-show-full-text (not show-full-text?))}))))

       ;; Table
       (if (empty? filtered-results)
         ($ :div {:class "text-center py-8 text-base-content/50 bg-base-200 rounded-lg"}
            "No results match the filter.")
         ($ :div {:class "overflow-x-auto"}
            ($ :table {:class "table"}
               ($ :thead
                  ($ :tr
                     ($ :th "Input")
                     ($ :th "Reference Output")
                     ($ :th {:class "w-1/3"} "Output & Evaluations")))
               ($ :tbody
                  (for [[idx run] (map-indexed vector filtered-results)]
                    ($ result-row {:key (str (:example-id run) "-" idx)
                                   :run run
                                   :show-full-text? show-full-text?
                                   :module-id module-id})))))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Experiment detail view"
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
                 :else :running)
        results (vals (:results data))]

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

         ;; Summary stats
         ($ summary-stats {:data data})

         ;; Results table
         ($ results-table {:results results :module-id module-id})))))

(ns alt-frontend.routes.agents.$module-id.datasets.$dataset-id.experiments.index
  "Regular experiments list for a dataset."
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

;; =============================================================================
;; TIME FORMATTING
;; =============================================================================

(defn format-relative-time
  "Format timestamp as relative time (e.g. '5 min ago')"
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

;; =============================================================================
;; STATUS BADGE
;; =============================================================================

(defui status-badge
  "Badge showing experiment status"
  [{:keys [finished?]}]
  (if finished?
    ($ :span {:class "badge badge-success badge-sm"
              :data-testid "status-badge"} "Completed")
    ($ :span {:class "badge badge-info badge-sm gap-1"
              :data-testid "status-badge"}
       ($ :span {:class "loading loading-spinner loading-xs"})
       "Running")))

;; =============================================================================
;; STAT CELL
;; =============================================================================

(defui stat-cell
  "Table cell for statistics with optional tooltip"
  [{:keys [value tooltip]}]
  ($ :td {:class "text-right font-mono text-sm"
          :title tooltip}
     (if (some? value) (str value) "N/A")))

;; =============================================================================
;; EXPERIMENT ROW
;; =============================================================================

(defui experiment-row
  "Single row in experiments table"
  [{:keys [experiment module-id dataset-id row-num]}]
  (let [info (:experiment-info experiment)
        exp-id (:id info)
        latency-stats (:latency-number-stats experiment)
        token-stats (:total-token-number-stats experiment)
        num-examples (or (:count latency-stats) 0)
        avg-latency (when (and latency-stats (pos? num-examples))
                      (int (/ (:total latency-stats) num-examples)))
        p99-latency (get-in latency-stats [:percentiles 0.99])
        avg-tokens (when (and token-stats (pos? num-examples))
                     (int (/ (:total token-stats) num-examples)))]

    ($ :tr {:class "hover cursor-pointer"
            :data-testid (str "experiment-row-" exp-id)
            :onClick #(rfe/push-state :experiments-detail
                                      {:module-id module-id
                                       :dataset-id dataset-id
                                       :experiment-id (str exp-id)})}
       ;; Row number
       ($ :td {:class "font-mono text-base-content/60"}
          (str "#" row-num))
       ;; Name
       ($ :td {:class "font-medium"}
          ($ :div {:class "truncate max-w-xs" :title (:name info)}
             (:name info)))
       ;; Status
       ($ :td
          ($ status-badge {:finished? (some? (:finish-time-millis experiment))}))
       ;; Started
       ($ :td {:class "text-sm"}
          (format-relative-time (:start-time-millis experiment)))
       ;; # Examples
       ($ stat-cell {:value num-examples})
       ;; Avg Latency
       ($ stat-cell {:value (when avg-latency (str avg-latency " ms"))
                     :tooltip "Average latency per example"})
       ;; P99 Latency
       ($ stat-cell {:value (when p99-latency (str p99-latency " ms"))
                     :tooltip "99th percentile latency"})
       ;; Avg Tokens
       ($ stat-cell {:value avg-tokens
                     :tooltip "Average total tokens per example"}))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Experiments list view"
  [{:keys [module-id dataset-id]}]
  (let [decoded-dataset (url-decode dataset-id)

        ;; Query for experiments
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiments module-id dataset-id :regular]
          :sente-event [:experiments/get-all-for-dataset
                        {:module-id module-id
                         :dataset-id dataset-id
                         :filters {:type :regular}}]
          :enabled? (boolean (and module-id dataset-id))
          :refetch-interval-ms 2000})

        experiments (get data :items [])

        ;; Sort by start time for row numbering
        sorted-experiments (sort-by :start-time-millis experiments)
        experiment-numbers (into {}
                                 (map-indexed (fn [idx exp]
                                                [(get-in exp [:experiment-info :id]) (inc idx)])
                                              sorted-experiments))]

    (cond
      (and loading? (empty? experiments))
      ($ ui/loading-state {:message "Loading experiments..."})

      error
      ($ ui/error-alert {:message (str "Error loading experiments: " error)})

      (empty? experiments)
      ($ ui/empty-state
         {:title "No experiments yet"
          :description (str "No experiments have been run for " decoded-dataset ".")})

      :else
      ($ :div {:class "space-y-4"}
         ;; Header
         ($ :div {:class "flex items-center justify-between"}
            ($ :div
               ($ :h1 {:class "text-2xl font-bold"} "Experiments")
               ($ :p {:class "text-base-content/60"}
                  (str decoded-dataset " • " (count experiments) " experiment" (when (not= 1 (count experiments)) "s")))))

         ;; Table
         ($ :div {:class "overflow-x-auto"}
            ($ :table {:class "table table-zebra"
                       :data-testid "data-table"}
               ($ :thead
                  ($ :tr
                     ($ :th "#")
                     ($ :th "Name")
                     ($ :th "Status")
                     ($ :th "Started")
                     ($ :th {:class "text-right"} "Examples")
                     ($ :th {:class "text-right"} "Avg Latency")
                     ($ :th {:class "text-right"} "P99 Latency")
                     ($ :th {:class "text-right"} "Avg Tokens")))
               ($ :tbody
                  (for [exp experiments
                        :let [exp-id (get-in exp [:experiment-info :id])]]
                    ($ experiment-row
                       {:key (str exp-id)
                        :experiment exp
                        :module-id module-id
                        :dataset-id dataset-id
                        :row-num (get experiment-numbers exp-id)})))))))))

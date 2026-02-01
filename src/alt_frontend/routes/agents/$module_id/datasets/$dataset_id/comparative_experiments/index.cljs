(ns alt-frontend.routes.agents.$module-id.datasets.$dataset-id.comparative-experiments.index
  "Comparative experiments list for a dataset."
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
    ($ :span {:class "badge badge-success badge-sm"} "Completed")
    ($ :span {:class "badge badge-info badge-sm gap-1"}
       ($ :span {:class "loading loading-spinner loading-xs"})
       "Running")))

;; =============================================================================
;; EXPERIMENT ROW
;; =============================================================================

(defui experiment-row
  "Single row in experiments table"
  [{:keys [experiment module-id dataset-id]}]
  (let [info (:experiment-info experiment)
        exp-id (:id info)
        targets (get-in info [:spec :targets])
        num-targets (count targets)]

    ($ :tr {:class "hover cursor-pointer"
            :onClick #(rfe/push-state :comparative-experiments-detail
                                      {:module-id module-id
                                       :dataset-id dataset-id
                                       :experiment-id (str exp-id)})}
       ;; Name
       ($ :td {:class "font-medium"}
          ($ :div {:class "truncate max-w-xs" :title (:name info)}
             (:name info)))
       ;; Status
       ($ :td
          ($ status-badge {:finished? (some? (:finish-time-millis experiment))}))
       ;; # Targets
       ($ :td {:class "text-center"}
          ($ :span {:class "badge badge-ghost"} (str num-targets " targets")))
       ;; Started
       ($ :td {:class "text-sm"}
          (format-relative-time (:start-time-millis experiment)))
       ;; Finished
       ($ :td {:class "text-sm"}
          (if (:finish-time-millis experiment)
            (format-relative-time (:finish-time-millis experiment))
            "--")))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Comparative experiments list view"
  [{:keys [module-id dataset-id]}]
  (let [decoded-dataset (url-decode dataset-id)

        ;; Query for comparative experiments
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiments module-id dataset-id :comparative]
          :sente-event [:experiments/get-all-for-dataset
                        {:module-id module-id
                         :dataset-id dataset-id
                         :filters {:type :comparative}}]
          :enabled? (boolean (and module-id dataset-id))
          :refetch-interval-ms 2000})

        experiments (get data :items [])]

    (cond
      (and loading? (empty? experiments))
      ($ ui/loading-state {:message "Loading comparative experiments..."})

      error
      ($ ui/error-alert {:message (str "Error loading experiments: " error)})

      (empty? experiments)
      ($ ui/empty-state
         {:title "No comparative experiments yet"
          :description (str "No comparative experiments have been run for " decoded-dataset ".")})

      :else
      ($ :div {:class "space-y-4"}
         ;; Header
         ($ :div {:class "flex items-center justify-between"}
            ($ :div
               ($ :h1 {:class "text-2xl font-bold"} "Comparative Experiments")
               ($ :p {:class "text-base-content/60"}
                  (str decoded-dataset " • " (count experiments) " experiment" (when (not= 1 (count experiments)) "s")))))

         ;; Table
         ($ :div {:class "overflow-x-auto"}
            ($ :table {:class "table table-zebra"}
               ($ :thead
                  ($ :tr
                     ($ :th "Name")
                     ($ :th "Status")
                     ($ :th {:class "text-center"} "Targets")
                     ($ :th "Started")
                     ($ :th "Finished")))
               ($ :tbody
                  (for [exp experiments
                        :let [exp-id (get-in exp [:experiment-info :id])]]
                    ($ experiment-row
                       {:key (str exp-id)
                        :experiment exp
                        :module-id module-id
                        :dataset-id dataset-id})))))))))

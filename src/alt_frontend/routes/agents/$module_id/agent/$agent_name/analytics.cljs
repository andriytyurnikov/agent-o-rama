(ns alt-frontend.routes.agents.$module-id.agent.$agent-name.analytics
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.components.icons :as icons]
            [alt-frontend.components.chart :as chart]))

;; =============================================================================
;; GRANULARITY CONFIG
;; =============================================================================

(def granularities
  [{:id :minute :label "Minute" :seconds 60}
   {:id :hour :label "Hour" :seconds 3600}
   {:id :day :label "Day" :seconds 86400}
   {:id :30-day :label "30 Days" :seconds (* 30 86400)}])

(defn format-time [ms granularity-id]
  (let [date (js/Date. ms)]
    (case granularity-id
      :minute (.toLocaleString date "en-US"
                               #js {:month "short" :day "numeric"
                                    :hour "numeric" :minute "2-digit" :hour12 true})
      :hour (.toLocaleString date "en-US"
                             #js {:month "short" :day "numeric"
                                  :hour "numeric" :minute "2-digit" :hour12 true})
      (:day :30-day) (.toLocaleString date "en-US"
                                      #js {:month "short" :day "numeric" :year "numeric"})
      (str date))))

(defn calculate-time-window [granularity-seconds offset]
  (let [now-millis (.now js/Date)
        granularity-millis (* granularity-seconds 1000)
        current-bucket (js/Math.floor (/ now-millis granularity-millis))
        end-bucket (+ current-bucket offset)
        start-bucket (- end-bucket 59)
        start-time-millis (* start-bucket granularity-millis)
        end-time-millis (* (inc end-bucket) granularity-millis)]
    {:start-time-millis start-time-millis
     :end-time-millis end-time-millis
     :is-live? (= offset 0)}))

;; =============================================================================
;; CHART CONFIGURATIONS
;; =============================================================================

(def chart-configs
  [{:id :agent-invokes
    :title "Agent Invocations"
    :description "Total agent runs per time bucket"
    :variant :count
    :metric-id [:agent :success-rate]
    :metrics-set #{:count}
    :color "#6366f1"}

   {:id :agent-success-rate
    :title "Agent Success Rate"
    :description "Percentage of successful runs"
    :variant :percentage
    :metric-id [:agent :success-rate]
    :metrics-set #{:mean :count}
    :color "#22c55e"}

   {:id :agent-latency
    :title "Agent Latency"
    :description "End-to-end execution time distribution"
    :variant :distribution
    :metric-id [:agent :latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Latency (ms)"}

   {:id :model-calls
    :title "Model Calls per Invoke"
    :description "LLM calls per agent invocation"
    :variant :distribution
    :metric-id [:agent :model-call-count]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Calls"}

   {:id :model-latency
    :title "Model Latency"
    :description "Individual LLM call latency distribution"
    :variant :distribution
    :metric-id [:agent :model-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Latency (ms)"}

   {:id :token-usage
    :title "Token Usage"
    :description "Tokens consumed by LLM calls"
    :variant :distribution
    :metric-id [:agent :token-counts]
    :metrics-set #{:min 0.5 :max}
    :y-label "Tokens"}])

;; =============================================================================
;; GLOBAL CONTROLS
;; =============================================================================

(defui global-controls [{:keys [granularity set-granularity time-offset set-time-offset]}]
  (let [granularity-config (first (filter #(= (:id %) granularity) granularities))
        time-window (calculate-time-window (:seconds granularity-config) time-offset)
        is-live? (:is-live? time-window)]

    ($ :div {:class "card bg-base-100 border border-base-300 mb-6"}
       ($ :div {:class "card-body py-4"}
          ($ :div {:class "flex flex-wrap items-center justify-between gap-4"}
             ;; Granularity selector
             ($ :div {:class "flex items-center gap-2"}
                ($ :span {:class "text-sm font-medium"} "Granularity:")
                ($ :select {:class "select select-bordered select-sm"
                            :data-testid "analytics-granularity-select"
                            :value (name granularity)
                            :onChange #(set-granularity (keyword (.. % -target -value)))}
                   (for [g granularities]
                     ($ :option {:key (:id g) :value (name (:id g))} (:label g)))))

             ;; Time navigation
             ($ :div {:class "flex items-center gap-2"}
                ;; Back button
                ($ :button {:class "btn btn-sm btn-ghost"
                            :data-testid "btn-time-back"
                            :onClick #(set-time-offset (fn [o] (- o 60)))}
                   ($ icons/chevron-left {:class "h-5 w-5"}))

                ;; Time range display
                ($ :span {:class "text-sm"
                          :data-testid "analytics-time-range"}
                   (str (format-time (:start-time-millis time-window) granularity)
                        " — "
                        (format-time (:end-time-millis time-window) granularity)))

                ;; Forward button
                ($ :button {:class "btn btn-sm btn-ghost"
                            :data-testid "btn-time-forward"
                            :disabled is-live?
                            :onClick #(set-time-offset (fn [o] (+ o 60)))}
                   ($ icons/chevron-right {:class "h-5 w-5"})))

             ;; Live indicator
             (if is-live?
               ($ :div {:class "flex items-center gap-2 px-3 py-1 bg-error/10 border border-error/20 rounded-full"
                        :data-testid "analytics-live-indicator"}
                  ($ :div {:class "h-2 w-2 bg-error rounded-full animate-pulse"})
                  ($ :span {:class "text-sm font-medium text-error"} "LIVE"))
               ($ :button {:class "btn btn-sm btn-primary"
                           :data-testid "btn-go-to-live"
                           :onClick #(set-time-offset 0)}
                  "Go to Live")))))))

;; =============================================================================
;; METRIC CARD (WITH CHART)
;; =============================================================================

(defui metric-card [{:keys [config data loading? error granularity-config time-window]}]
  (let [{:keys [title description variant metrics-set y-label color]} config
        {:keys [start-time-millis end-time-millis]} time-window

        ;; Transform data based on variant
        transformed (uix/use-memo
                     (fn []
                       (case variant
                         :count
                         (chart/transform-count-data
                          {:data data
                           :granularity (:seconds granularity-config)
                           :start-time-millis start-time-millis
                           :end-time-millis end-time-millis
                           :color color})

                         :percentage
                         (chart/transform-percentage-data
                          {:data data
                           :granularity (:seconds granularity-config)
                           :start-time-millis start-time-millis
                           :end-time-millis end-time-millis
                           :color color})

                         :distribution
                         (chart/transform-telemetry-data
                          {:data data
                           :metrics metrics-set
                           :granularity (:seconds granularity-config)
                           :start-time-millis start-time-millis
                           :end-time-millis end-time-millis})))
                     [data variant metrics-set granularity-config start-time-millis end-time-millis color])

        chart-data (:data transformed)
        series-configs (:series-configs transformed)

        ;; Calculate summary stats from data
        summary-stats (uix/use-memo
                       (fn []
                         (when (seq data)
                           (let [all-values (mapcat (fn [[_ bucket-data]]
                                                      (if (map? bucket-data)
                                                        (vals (get bucket-data "_aor/default" {}))
                                                        []))
                                                    data)
                                 counts (keep #(when (map? %) (:count %)) (vals data))
                                 means (keep #(when (map? %) (get-in % ["_aor/default" :mean])) (vals data))]
                             (cond-> {}
                               (seq counts)
                               (assoc :total (reduce + counts))

                               (seq means)
                               (assoc :avg-mean (/ (reduce + means) (count means)))))))
                       [data])]

    ($ :div {:class "card bg-base-100 border border-base-300"}
       ($ :div {:class "card-body"}
          ;; Header
          ($ :h3 {:class "card-title text-base"} title)
          (when description
            ($ :p {:class "text-sm text-base-content/70 mb-2"} description))

          ;; Content
          (cond
            loading?
            ($ :div {:class "flex items-center justify-center h-48"}
               ($ :span {:class "loading loading-spinner loading-md"}))

            error
            ($ :div {:class "alert alert-error"}
               ($ :span (str "Error: " error)))

            :else
            ($ :<>
               ;; Summary stats row
               (when summary-stats
                 ($ :div {:class "stats stats-horizontal bg-base-200 w-full mb-4"}
                    (when (:total summary-stats)
                      ($ :div {:class "stat place-items-center py-2 px-4"}
                         ($ :div {:class "stat-title text-xs"} "Total")
                         ($ :div {:class "stat-value text-lg"} (:total summary-stats))))
                    (when (:avg-mean summary-stats)
                      ($ :div {:class "stat place-items-center py-2 px-4"}
                         ($ :div {:class "stat-title text-xs"} "Avg")
                         ($ :div {:class "stat-value text-lg"}
                            (if (= variant :percentage)
                              (str (.toFixed (* (:avg-mean summary-stats) 100) 1) "%")
                              (.toFixed (:avg-mean summary-stats) 2)))))))

               ;; Chart
               ($ chart/time-series-chart
                  {:chart-data chart-data
                   :series-configs series-configs
                   :height 200
                   :y-label y-label
                   :y-range (when (= variant :percentage) [0 100])
                   :start-time-millis start-time-millis
                   :end-time-millis end-time-millis})))))))

;; =============================================================================
;; ANALYTICS CHART (WITH DATA FETCHING)
;; =============================================================================

(defui analytics-chart [{:keys [config module-id agent-name granularity-config time-window refresh-counter]}]
  (let [{:keys [metric-id metrics-set]} config

        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:analytics-telemetry
                      metric-id
                      module-id
                      agent-name
                      (:seconds granularity-config)
                      (:start-time-millis time-window)
                      refresh-counter]
          :sente-event [:analytics/fetch-telemetry
                        {:module-id module-id
                         :agent-name agent-name
                         :granularity (:seconds granularity-config)
                         :metric-id metric-id
                         :start-time-millis (:start-time-millis time-window)
                         :end-time-millis (:end-time-millis time-window)
                         :metrics-set metrics-set}]
          :enabled? (boolean (and module-id agent-name))})]

    ($ metric-card {:config config
                    :data data
                    :loading? loading?
                    :error error
                    :granularity-config granularity-config
                    :time-window time-window})))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view [{:keys [module-id agent-name]}]
  (let [decoded-module-id (utils/url-decode module-id)
        decoded-agent-name (utils/url-decode agent-name)

        ;; State
        [granularity set-granularity] (uix/use-state :minute)
        [time-offset set-time-offset] (uix/use-state 0)
        [refresh-counter set-refresh-counter] (uix/use-state 0)

        ;; Calculate time window
        granularity-config (first (filter #(= (:id %) granularity) granularities))
        time-window (calculate-time-window (:seconds granularity-config) time-offset)
        is-live? (:is-live? time-window)

        ;; Auto-refresh in live mode
        _ (uix/use-effect
           (fn []
             (if is-live?
               (let [interval-id (js/setInterval
                                  #(set-refresh-counter (fn [c] (inc c)))
                                  60000)]
                 #(js/clearInterval interval-id))
               js/undefined))
           [is-live?])]

    ($ :div {:class "space-y-6"}
       ;; Header
       ($ :div {:class "flex items-center gap-3"}
          ($ icons/chart {:class "h-8 w-8 text-primary"})
          ($ :h1 {:class "text-2xl font-bold"}
             (str "Analytics: " decoded-agent-name)))

       ;; Global controls
       ($ global-controls {:granularity granularity
                           :set-granularity set-granularity
                           :time-offset time-offset
                           :set-time-offset set-time-offset})

       ;; Metric cards grid
       ($ :div {:class "grid grid-cols-1 lg:grid-cols-2 gap-6"}
          (for [config chart-configs]
            ($ analytics-chart {:key (:id config)
                                :config config
                                :module-id decoded-module-id
                                :agent-name decoded-agent-name
                                :granularity-config granularity-config
                                :time-window time-window
                                :refresh-counter refresh-counter}))))))

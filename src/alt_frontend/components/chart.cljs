(ns alt-frontend.components.chart
  "uPlot-based charting components for alt-frontend analytics."
  (:require [uix.core :as uix :refer [defui $]]
            ["uplot" :as uPlot]))

;; =============================================================================
;; UPLOT HOOK
;; =============================================================================

(defn use-uplot
  "React hook to manage a uPlot chart instance lifecycle.
   Returns [target-ref chart-ref]"
  [options data]
  (let [chart-ref (uix/use-ref nil)
        target-ref (uix/use-ref nil)]

    ;; Create or update chart when data or options change
    (uix/use-layout-effect
     (fn []
       (when (and @target-ref data (seq data))
         (let [current-chart @chart-ref]
           ;; Destroy existing chart
           (when current-chart
             (.destroy current-chart)
             (reset! chart-ref nil))
           ;; Create new chart
           (let [new-chart (uPlot. (clj->js options) (clj->js data) @target-ref)]
             (reset! chart-ref new-chart))))
       js/undefined)
     [data options])

    ;; Cleanup on unmount
    (uix/use-layout-effect
     (fn []
       (fn []
         (when-let [chart @chart-ref]
           (.destroy chart)
           (reset! chart-ref nil))))
     [])

    [target-ref chart-ref]))

;; =============================================================================
;; COLOR PALETTES
;; =============================================================================

(def metric-colors
  {:min "#22c55e"      ; green-500
   :max "#ef4444"      ; red-500
   :mean "#3b82f6"     ; blue-500
   0.25 "#06b6d4"      ; cyan-500
   0.5 "#3b82f6"       ; blue-500
   0.75 "#f59e0b"      ; amber-500
   0.9 "#f97316"       ; orange-500
   0.99 "#a855f7"})    ; purple-500

(def series-colors
  ["#3b82f6"   ; blue-500
   "#22c55e"   ; green-500
   "#f59e0b"   ; amber-500
   "#ef4444"   ; red-500
   "#a855f7"   ; purple-500
   "#ec4899"   ; pink-500
   "#14b8a6"   ; teal-500
   "#f97316"]) ; orange-500

;; =============================================================================
;; DATA TRANSFORMATION
;; =============================================================================

(defn- sort-metrics
  "Sort metrics in display order: min, percentiles (ascending), mean, max"
  [metrics]
  (let [order {:min 0, 0.25 1, 0.5 2, 0.75 3, 0.9 4, 0.99 5, :mean 6, :max 7}]
    (sort-by #(get order % 99) metrics)))

(defn- format-metric-label
  "Format a metric key for display"
  [m]
  (cond
    (keyword? m) (name m)
    (number? m) (str "p" (int (* m 100)))
    :else (str m)))

(defn transform-telemetry-data
  "Transform telemetry data from backend format to uPlot format.

   Backend format: {bucket-timestamp {category {metric value}}}
   uPlot format: [[timestamps] [series1-values] [series2-values] ...]

   Args:
   - data: Raw telemetry data
   - metrics: Set of metric keys to extract (e.g., #{:min :max 0.5 0.9 0.99})
   - granularity: Time bucket size in seconds
   - start-time-millis, end-time-millis: Time window bounds
   - category: Category to extract from (default \"_aor/default\")

   Returns: {:data [[timestamps] [series1] ...] :series-configs [...]}"
  [{:keys [data metrics granularity start-time-millis end-time-millis category]
    :or {category "_aor/default"}}]
  (if (seq data)
    (let [sorted-buckets (sort (keys data))
          timestamps (mapv #(* % granularity) sorted-buckets)
          sorted-metrics (sort-metrics metrics)

          ;; Extract values for each metric
          series-data (mapv (fn [metric]
                              (mapv (fn [bucket]
                                      (get-in data [bucket category metric]))
                                    sorted-buckets))
                            sorted-metrics)

          ;; Build series configs
          series-configs (mapv (fn [idx metric]
                                 {:label (format-metric-label metric)
                                  :stroke (get metric-colors metric
                                               (nth series-colors (mod idx (count series-colors))))
                                  :width 2
                                  :points {:show true :size 4}
                                  :spanGaps true})
                               (range)
                               sorted-metrics)]

      {:data (into [timestamps] series-data)
       :series-configs series-configs})

    ;; Empty data - generate placeholder
    (let [start-sec (/ start-time-millis 1000)
          end-sec (/ end-time-millis 1000)
          timestamps (mapv #(+ start-sec (* % (/ (- end-sec start-sec) 59))) (range 60))
          sorted-metrics (sort-metrics metrics)
          empty-series (vec (repeat (count sorted-metrics) (vec (repeat 60 nil))))
          series-configs (mapv (fn [idx metric]
                                 {:label (format-metric-label metric)
                                  :stroke (get metric-colors metric
                                               (nth series-colors (mod idx (count series-colors))))
                                  :width 2
                                  :points {:show true :size 4}
                                  :spanGaps true})
                               (range)
                               sorted-metrics)]
      {:data (into [timestamps] empty-series)
       :series-configs series-configs})))

(defn transform-count-data
  "Transform telemetry data for count-based charts (single metric).

   Returns: {:data [[timestamps] [counts]] :series-configs [...]}"
  [{:keys [data granularity start-time-millis end-time-millis color]
    :or {color "#6366f1"}}]
  (if (seq data)
    (let [sorted-buckets (sort (keys data))
          timestamps (mapv #(* % granularity) sorted-buckets)
          counts (mapv #(get-in data [% "_aor/default" :count]) sorted-buckets)]
      {:data [timestamps counts]
       :series-configs [{:label "Count"
                         :stroke color
                         :width 2
                         :points {:show true :size 4}
                         :spanGaps true
                         :fill (str color "20")}]})

    ;; Empty data
    (let [start-sec (/ start-time-millis 1000)
          end-sec (/ end-time-millis 1000)
          timestamps (mapv #(+ start-sec (* % (/ (- end-sec start-sec) 59))) (range 60))]
      {:data [timestamps (vec (repeat 60 nil))]
       :series-configs [{:label "Count"
                         :stroke color
                         :width 2
                         :points {:show true :size 4}
                         :spanGaps true}]})))

(defn transform-percentage-data
  "Transform telemetry data for percentage charts (0-100 scale).

   Returns: {:data [[timestamps] [percentages]] :series-configs [...]}"
  [{:keys [data granularity start-time-millis end-time-millis color]
    :or {color "#22c55e"}}]
  (if (seq data)
    (let [sorted-buckets (sort (keys data))
          timestamps (mapv #(* % granularity) sorted-buckets)
          percentages (mapv (fn [bucket]
                              (when-let [v (get-in data [bucket "_aor/default" :mean])]
                                (* v 100)))
                            sorted-buckets)]
      {:data [timestamps percentages]
       :series-configs [{:label "Success %"
                         :stroke color
                         :width 2
                         :points {:show true :size 4}
                         :spanGaps true
                         :fill (str color "20")}]})

    ;; Empty data
    (let [start-sec (/ start-time-millis 1000)
          end-sec (/ end-time-millis 1000)
          timestamps (mapv #(+ start-sec (* % (/ (- end-sec start-sec) 59))) (range 60))]
      {:data [timestamps (vec (repeat 60 nil))]
       :series-configs [{:label "Success %"
                         :stroke color
                         :width 2
                         :points {:show true :size 4}
                         :spanGaps true}]})))

;; =============================================================================
;; CHART COMPONENT
;; =============================================================================

(defui time-series-chart
  "Time series chart using uPlot.

   Props:
   - :chart-data - uPlot data format [[timestamps] [series1] [series2] ...]
   - :series-configs - Series configuration [{:label :stroke :width :points :spanGaps} ...]
   - :height - Chart height in pixels (default 200)
   - :y-label - Y-axis label (optional)
   - :y-range - Fixed y-axis range [min max] (optional, e.g., [0 100] for percentages)
   - :start-time-millis, :end-time-millis - Time window bounds"
  [{:keys [chart-data series-configs height y-label y-range start-time-millis end-time-millis]
    :or {height 200}}]
  (let [container-ref (uix/use-ref nil)

        ;; Build uPlot options
        options (uix/use-memo
                 (fn []
                   {:width 100 ; Will be resized
                    :height height
                    :series (into [{:label "Time"}] series-configs)
                    :axes [{:stroke "#64748b"
                            :grid {:show true :stroke "#e5e7eb" :width 1}
                            :ticks {:show true :stroke "#d1d5db"}
                            :values (fn [_self splits]
                                      (.map splits
                                            (fn [ts]
                                              (.toLocaleString (js/Date. (* ts 1000))
                                                               "en-US"
                                                               #js {:hour "numeric"
                                                                    :minute "2-digit"
                                                                    :hour12 true}))))}
                           {:stroke "#64748b"
                            :grid {:show true :stroke "#e5e7eb" :width 1}
                            :ticks {:show true :stroke "#d1d5db"}
                            :label y-label
                            :labelSize 50
                            :values (when y-range
                                      (fn [_self splits]
                                        (.map splits (fn [v] (str (int v) (when (= (second y-range) 100) "%"))))))}]
                    :scales {:x {:time true
                                 :range (fn [_self _min _max]
                                          #js [(/ start-time-millis 1000)
                                               (/ end-time-millis 1000)])}
                             :y (if y-range
                                  {:auto false
                                   :range (fn [_self _min _max] (clj->js y-range))}
                                  {:auto true})}
                    :legend {:show true :live true}})
                 [height y-label y-range start-time-millis end-time-millis series-configs])

        [target-ref chart-ref] (use-uplot options chart-data)

        ;; Handle resize
        _ (uix/use-effect
           (fn []
             (let [handle-resize
                   (fn []
                     (when-let [chart @chart-ref]
                       (when-let [container @container-ref]
                         (let [rect (.getBoundingClientRect container)
                               w (.-width rect)]
                           (when (> w 0)
                             (.setSize chart (clj->js {:width w :height height})))))))]
               (js/requestAnimationFrame handle-resize)
               (.addEventListener js/window "resize" handle-resize)
               (fn [] (.removeEventListener js/window "resize" handle-resize))))
           [height chart-data])]

    ($ :div {:ref container-ref :class "w-full"}
       ($ :div {:ref target-ref}))))

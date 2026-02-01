(ns alt-frontend.lib.time
  "Time formatting utilities for the alt-frontend.
   Provides consistent time display across all views.")

;; =============================================================================
;; RELATIVE TIME
;; =============================================================================

(defn format-relative-time
  "Format a timestamp as relative time (e.g., '2m ago', '3h ago').
   Returns nil for nil input.

   Examples:
   - 30 seconds ago -> 'just now'
   - 5 minutes ago -> '5m ago'
   - 3 hours ago -> '3h ago'
   - 2 days ago -> '2d ago'
   - 10 days ago -> '1/15' (month/day)"
  [timestamp-ms]
  (when timestamp-ms
    (let [now (js/Date.now)
          diff-ms (- now timestamp-ms)
          diff-seconds (/ diff-ms 1000)
          diff-minutes (/ diff-seconds 60)
          diff-hours (/ diff-minutes 60)
          diff-days (/ diff-hours 24)]
      (cond
        (< diff-seconds 60) "just now"
        (< diff-minutes 60) (str (int diff-minutes) "m ago")
        (< diff-hours 24) (str (int diff-hours) "h ago")
        (< diff-days 7) (str (int diff-days) "d ago")
        :else (let [date (js/Date. timestamp-ms)]
                (str (inc (.getMonth date)) "/" (.getDate date)))))))

;; =============================================================================
;; FULL TIMESTAMP
;; =============================================================================

(defn format-timestamp
  "Format a timestamp as a full date/time string using locale conventions.
   Returns nil for nil input.

   Example output: '1/15/2024, 3:45:30 PM'"
  [timestamp-ms]
  (when timestamp-ms
    (let [date (js/Date. timestamp-ms)]
      (.toLocaleString date))))

(defn format-timestamp-with-ms
  "Format a timestamp with millisecond precision.
   Returns nil for nil input.

   Example output: '1/15/2024, 3:45:30 PM.123'"
  [timestamp-ms]
  (when timestamp-ms
    (let [date (js/Date. timestamp-ms)
          base (.toLocaleString date)
          millis (.padStart (str (.getMilliseconds date)) 3 "0")]
      (str base "." millis))))

;; =============================================================================
;; DURATION
;; =============================================================================

(defn format-duration
  "Format a duration between two timestamps.
   Returns nil if either timestamp is nil.

   Examples:
   - 500ms difference -> '500ms'
   - 2500ms difference -> '2.50s'
   - 90000ms difference -> '1.50min'"
  [start-ms end-ms]
  (when (and start-ms end-ms)
    (let [duration-ms (- end-ms start-ms)]
      (cond
        (< duration-ms 1000) (str duration-ms "ms")
        (< duration-ms 60000) (str (.toFixed (/ duration-ms 1000) 2) "s")
        :else (str (.toFixed (/ duration-ms 60000) 2) "min")))))

(defn format-duration-ms
  "Format a duration in milliseconds directly.

   Examples:
   - 500 -> '500ms'
   - 2500 -> '2.50s'
   - 90000 -> '1.50min'"
  [duration-ms]
  (when duration-ms
    (cond
      (< duration-ms 1000) (str duration-ms "ms")
      (< duration-ms 60000) (str (.toFixed (/ duration-ms 1000) 2) "s")
      :else (str (.toFixed (/ duration-ms 60000) 2) "min"))))

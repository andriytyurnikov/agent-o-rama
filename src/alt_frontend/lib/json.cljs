(ns alt-frontend.lib.json
  "JSON formatting utilities for the alt-frontend.
   Provides consistent JSON display and truncation.")

;; =============================================================================
;; PRETTY PRINT
;; =============================================================================

(defn pretty-json
  "Pretty print a value as formatted JSON with 2-space indentation.
   Falls back to pr-str for values that can't be JSON-serialized.

   Usage:
   (pretty-json {:foo \"bar\"})
   ;; => \"{\\n  \\\"foo\\\": \\\"bar\\\"\\n}\""
  [value]
  (try
    (js/JSON.stringify (clj->js value) nil 2)
    (catch js/Error _
      (pr-str value))))

(defn to-json
  "Convert a value to a compact JSON string (no formatting).
   Falls back to str for values that can't be JSON-serialized.

   Usage:
   (to-json {:foo \"bar\"})
   ;; => \"{\\\"foo\\\":\\\"bar\\\"}\""
  [value]
  (try
    (js/JSON.stringify (clj->js value))
    (catch js/Error _
      (str value))))

;; =============================================================================
;; TRUNCATION
;; =============================================================================

(defn truncate-json
  "Convert to pretty JSON and truncate if longer than max-length.
   Adds '...' suffix when truncated.

   Usage:
   (truncate-json {:long \"value\"} 20)
   ;; => \"{\\n  \\\"long\\\": \\\"va...\""
  [value max-length]
  (let [json-str (pretty-json value)]
    (if (> (count json-str) max-length)
      (str (subs json-str 0 (- max-length 3)) "...")
      json-str)))

(defn truncate-json-compact
  "Convert to compact JSON and truncate if longer than max-length.
   Adds '...' suffix when truncated.

   Usage:
   (truncate-json-compact {:long \"value\"} 15)
   ;; => \"{\\\"long\\\":\\\"v...\""
  [value max-length]
  (let [json-str (to-json value)]
    (if (> (count json-str) max-length)
      (str (subs json-str 0 (- max-length 3)) "...")
      json-str)))

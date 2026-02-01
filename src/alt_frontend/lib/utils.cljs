(ns alt-frontend.lib.utils
  "Shared utility functions for the alt-frontend.
   Includes URL encoding/decoding and common hooks."
  (:require [uix.core :as uix]))

;; =============================================================================
;; URL ENCODING
;; =============================================================================

(defn url-decode
  "Decode a URL-encoded string. Returns the original string on error."
  [s]
  (when s
    (try
      (js/decodeURIComponent s)
      (catch js/Error _ s))))

(defn url-encode
  "Encode a string for use in URLs."
  [s]
  (when s
    (js/encodeURIComponent s)))

;; =============================================================================
;; DEBOUNCE HOOK
;; =============================================================================

(defn use-debounced-value
  "Returns a debounced version of value, updating after delay-ms of no changes.

   Usage:
   (let [debounced (use-debounced-value search-term 300)]
     ;; debounced updates 300ms after search-term stops changing
     ...)"
  [value delay-ms]
  (let [[debounced set-debounced] (uix/use-state value)]
    (uix/use-effect
     (fn []
       (let [timeout-id (js/setTimeout #(set-debounced value) delay-ms)]
         #(js/clearTimeout timeout-id)))
     [value delay-ms])
    debounced))

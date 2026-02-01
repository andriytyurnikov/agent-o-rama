(ns alt-frontend.routes.agents.$module-id.agent.$agent-name.rules.$rule-name.action-log
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.state :as state]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            [alt-frontend.components.modal :as modal]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

(defn format-timestamp
  "Format timestamp millis as human-readable date/time."
  [millis]
  (when millis
    (let [date (js/Date. millis)]
      (.toLocaleString date))))

(defn format-duration-ms
  "Format duration in milliseconds to human-readable string."
  [ms]
  (when ms
    (cond
      (< ms 1000) (str ms "ms")
      (< ms 60000) (str (.toFixed (/ ms 1000) 1) "s")
      :else (str (.toFixed (/ ms 60000) 1) "min"))))

(defn pretty-print-json
  "Convert a map to pretty-printed JSON string."
  [obj]
  (try
    (js/JSON.stringify (clj->js obj) nil 2)
    (catch js/Error _
      (pr-str obj))))

;; =============================================================================
;; INFO MAP MODAL
;; =============================================================================

(defn show-info-modal! [info-map]
  (state/dispatch
   [:modal/show
    {:title "Action Info"
     :content ($ :pre {:class "bg-base-200 p-4 rounded-lg text-sm overflow-auto max-h-96 font-mono"}
                 (pretty-print-json info-map))
     :size :lg}]))

;; =============================================================================
;; ACTION ROW COMPONENT
;; =============================================================================

(defui action-row [{:keys [action-entry]}]
  (let [action (:action action-entry)
        success? (:success? action)
        info-map (:info-map action)
        start-time (:start-time-millis action)
        finish-time (:finish-time-millis action)
        execution-time (when (and start-time finish-time)
                         (- finish-time start-time))

        ;; Truncated info display
        info-str (when info-map
                   (let [s (pr-str info-map)]
                     (if (> (count s) 60)
                       (str (subs s 0 60) "...")
                       s)))]

    ($ :tr {:class "hover"}
       ;; Start time
       ($ :td {:class "text-sm"}
          (format-timestamp start-time))

       ;; Execution time
       ($ :td {:class "text-sm"}
          (if execution-time
            (format-duration-ms execution-time)
            ($ :span {:class "italic text-base-content/50"} "—")))

       ;; Status
       ($ :td
          ($ :span {:class (str "badge badge-sm "
                                (if success? "badge-success" "badge-error"))}
             (if success? "Success" "Failed")))

       ;; Agent invoke link placeholder
       ($ :td {:class "text-sm"}
          (when-let [agent-invoke (:agent-invoke action)]
            ($ :span {:class "font-mono text-xs text-base-content/70"}
               (str (:invoke-id agent-invoke)))))

       ;; Info (clickable)
       ($ :td {:class "max-w-xs"}
          (when info-map
            ($ :div {:class "cursor-pointer hover:bg-base-200 p-1 rounded text-sm truncate"
                     :onClick #(show-info-modal! info-map)}
               ($ :span {:class "font-mono text-xs text-base-content/70"}
                  info-str)))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view [{:keys [module-id agent-name rule-name]}]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)
        decoded-rule-name (url-decode rule-name)

        ;; State
        [actions set-actions!] (uix/use-state [])
        [pagination-params set-pagination-params!] (uix/use-state nil)
        [has-more? set-has-more!] (uix/use-state false)
        [loading? set-loading!] (uix/use-state true)
        [loading-more? set-loading-more!] (uix/use-state false)
        [error set-error!] (uix/use-state nil)
        connected? (state/use-state [:sente :connected?])

        fetch-actions (uix/use-callback
                       (fn [page-params append?]
                         (if append?
                           (set-loading-more! true)
                           (set-loading! true))
                         (sente/request!
                          [:analytics/fetch-action-log
                           {:module-id decoded-module-id
                            :agent-name decoded-agent-name
                            :rule-name decoded-rule-name
                            :page-size 50
                            :pagination-params page-params}]
                          10000
                          (fn [reply]
                            (set-loading! false)
                            (set-loading-more! false)
                            (if (:success reply)
                              (let [data (:data reply)
                                    new-actions (:actions data)
                                    new-pagination (:pagination-params data)
                                    more? (and new-pagination
                                               (not (empty? new-pagination))
                                               (some (fn [[_ item-id]] (not (nil? item-id)))
                                                     new-pagination))]
                                (if append?
                                  (set-actions! (fn [prev] (concat prev new-actions)))
                                  (set-actions! new-actions))
                                (set-pagination-params! (when more? new-pagination))
                                (set-has-more! more?))
                              (set-error! (or (:error reply) "Failed to fetch action log"))))))
                       [decoded-module-id decoded-agent-name decoded-rule-name])

        ;; Initial fetch
        _ (uix/use-effect
           (fn []
             (when (and connected? decoded-module-id decoded-agent-name decoded-rule-name)
               (fetch-actions nil false))
             js/undefined)
           [fetch-actions])

        load-more (fn []
                    (when (and has-more? (not loading?) (not loading-more?) pagination-params)
                      (fetch-actions pagination-params true)))]

    ($ :div {:class "space-y-4"}
       ;; Header
       ($ :div {:class "flex items-center gap-3"}
          ($ icons/document {:class "h-8 w-8 text-primary"})
          ($ :div
             ($ :h1 {:class "text-2xl font-bold"} "Action Log")
             ($ :p {:class "text-base-content/70 text-sm"}
                (str "Rule: " decoded-rule-name))))

       ;; Content
       (cond
         ;; Initial loading
         (and loading? (empty? actions))
         ($ ui/loading-state {:message "Loading action log..."})

         ;; Error
         error
         ($ ui/error-alert {:message error})

         ;; Empty
         (empty? actions)
         ($ ui/empty-state
            {:title "No Actions"
             :description "No actions have been logged for this rule yet."
             :icon ($ icons/document {:class "h-12 w-12"})})

         ;; Action log table
         :else
         ($ :<>
            ($ :div {:class "overflow-x-auto"}
               ($ :table {:class "table table-zebra"}
                  ($ :thead
                     ($ :tr
                        ($ :th "Start Time")
                        ($ :th "Duration")
                        ($ :th "Status")
                        ($ :th "Invoke ID")
                        ($ :th "Info")))
                  ($ :tbody
                     (for [action-entry actions]
                       ($ action-row {:key (:action-id action-entry)
                                      :action-entry action-entry})))))

            ;; Load more button
            (when has-more?
              ($ :div {:class "flex justify-center mt-4"}
                 ($ ui/button {:variant :ghost
                               :loading? loading-more?
                               :on-click load-more}
                    (if loading-more? "Loading..." "Load More")))))))))

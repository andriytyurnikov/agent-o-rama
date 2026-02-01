(ns alt-frontend.routes.agents.$module-id.agent.$agent-name.invocations.$invoke-id
  "Invocation detail view with summary, result, and node-by-node trace.
   Includes both a collapsible trace view and an interactive DAG graph view."
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.state :as state]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.lib.time :as time]
            [alt-frontend.lib.json :as json]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            [alt-frontend.components.invocation-graph :as graph]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn parse-invoke-id
  "Parse invoke-id string (task-id-agent-id) into [task-id agent-id]"
  [invoke-id]
  (when invoke-id
    (let [idx (str/index-of invoke-id "-")]
      (when (and idx (pos? idx))
        [(subs invoke-id 0 idx)
         (try
           (uuid (subs invoke-id (inc idx)))
           (catch js/Error _ nil))]))))

;; =============================================================================
;; STATUS BADGE
;; =============================================================================

(defui status-badge
  "Badge showing invocation status"
  [{:keys [is-complete is-failure is-live]}]
  (cond
    is-live
    ($ :span {:class "badge badge-info gap-1"}
       ($ :span {:class "loading loading-spinner loading-xs"})
       "Running")

    is-failure
    ($ :span {:class "badge badge-error"} "Failed")

    is-complete
    ($ :span {:class "badge badge-success"} "Success")

    :else
    ($ :span {:class "badge badge-ghost"} "Unknown")))

;; =============================================================================
;; SUMMARY CARD
;; =============================================================================

(defui summary-card
  "Card showing invocation summary information"
  [{:keys [summary is-complete is-live]}]
  (let [start-time (:start-time-millis summary)
        finish-time (:finish-time-millis summary)
        result (:result summary)
        is-failure (:failure? result)
        invoke-args (:invoke-args summary)
        graph-version (:graph-version summary)]

    ($ ui/card {:title "Summary"}
       ($ :div {:class "space-y-4"}
          ;; Status and timing row
          ($ :div {:class "flex flex-wrap gap-4"}
             ($ :div
                ($ :div {:class "text-sm text-base-content/60"} "Status")
                ($ :div {:class "mt-1"}
                   ($ status-badge {:is-complete is-complete
                                    :is-failure is-failure
                                    :is-live is-live})))
             ($ :div
                ($ :div {:class "text-sm text-base-content/60"} "Duration")
                ($ :div {:class "font-mono font-medium"}
                   (or (time/format-duration start-time finish-time)
                       (if is-live "Running..." "—"))))
             ($ :div
                ($ :div {:class "text-sm text-base-content/60"} "Graph Version")
                ($ :div {:class "font-mono text-sm"}
                   (or graph-version "—"))))

          ;; Timing details
          ($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
             ($ :div
                ($ :div {:class "text-sm text-base-content/60"} "Started")
                ($ :div {:class "font-mono text-sm"}
                   (or (time/format-timestamp-with-ms start-time) "—")))
             ($ :div
                ($ :div {:class "text-sm text-base-content/60"} "Finished")
                ($ :div {:class "font-mono text-sm"}
                   (or (time/format-timestamp-with-ms finish-time) (if is-live "In progress..." "—")))))

          ;; Arguments
          (when invoke-args
            ($ :div
               ($ :div {:class "text-sm text-base-content/60 mb-1"} "Arguments")
               ($ :pre {:class "bg-base-200 p-3 rounded-lg text-sm font-mono overflow-x-auto max-h-32"}
                  (json/pretty-json invoke-args))))))))

;; =============================================================================
;; RESULT CARD
;; =============================================================================

(defui result-card
  "Card showing the final result"
  [{:keys [result]}]
  (when result
    (let [is-failure (:failure? result)
          result-val (:val result)]
      ($ ui/card {:title "Final Result"}
         ($ :div {:class "space-y-3"}
            ($ :div {:class (str "badge " (if is-failure "badge-error" "badge-success"))}
               (if is-failure "Failed" "Success"))
            ($ :pre {:class (str "p-4 rounded-lg text-sm font-mono overflow-x-auto max-h-64 "
                                 (if is-failure "bg-error/10" "bg-success/10"))}
               (json/pretty-json result-val)))))))

;; =============================================================================
;; EXCEPTIONS CARD
;; =============================================================================

(defui exceptions-card
  "Card showing exception summaries"
  [{:keys [exceptions on-select-node]}]
  (when (seq exceptions)
    ($ ui/card {:title (str "Exceptions (" (count exceptions) ")")}
       ($ :div {:class "space-y-3"}
          (for [[idx exc] (map-indexed vector exceptions)]
            (let [first-line (first (str/split-lines (:throwable-str exc)))]
              ($ :div {:key idx
                       :class "bg-error/10 p-3 rounded-lg border border-error/30"}
                 ($ :div {:class "flex justify-between items-start"}
                    ($ :div {:class "flex-1 min-w-0"}
                       ($ :div {:class "font-semibold text-error"} (:node exc))
                       ($ :div {:class "font-mono text-xs text-error/80 mt-1 truncate"
                                :title (:throwable-str exc)}
                          first-line))
                    (when on-select-node
                      ($ :button {:class "btn btn-xs btn-ghost"
                                  :onClick #(on-select-node (:invoke-id exc))}
                         "Go to node"))))))))))

;; =============================================================================
;; METADATA CARD
;; =============================================================================

(defui metadata-card
  "Card showing invocation metadata"
  [{:keys [metadata]}]
  (when (seq metadata)
    ($ ui/card {:title "Metadata"}
       ($ :div {:class "space-y-2"}
          (for [[k v] (sort-by key metadata)]
            ($ :div {:key (str k)
                     :class "flex justify-between items-start py-2 border-b border-base-200 last:border-b-0"}
               ($ :span {:class "font-mono text-sm font-medium text-base-content/70"}
                  (str k))
               ($ :span {:class "font-mono text-sm text-base-content/90 max-w-md truncate"
                         :title (pr-str v)}
                  (json/truncate-json v 100))))))))

;; =============================================================================
;; NODE CARD
;; =============================================================================

(defui node-card
  "Card showing a single node's details"
  [{:keys [node-id node-data expanded? on-toggle]}]
  (let [node-name (:node node-data)
        input (:input node-data)
        result (:result node-data)
        exceptions (:exceptions node-data)
        emits (:emits node-data)
        start-time (:start-time-millis node-data)
        finish-time (:finish-time-millis node-data)
        nested-ops (:nested-ops node-data)
        human-request (:human-request node-data)
        is-in-progress (and start-time (not finish-time))
        has-error (seq exceptions)]

    ($ :div {:class (str "collapse collapse-arrow bg-base-100 border rounded-lg "
                         (cond
                           has-error "border-error"
                           is-in-progress "border-info"
                           result "border-success"
                           :else "border-base-300"))}
       ($ :input {:type "checkbox"
                  :checked (boolean expanded?)
                  :onChange on-toggle})
       ;; Header
       ($ :div {:class "collapse-title font-medium flex items-center gap-3"}
          ;; Status indicator
          (cond
            is-in-progress
            ($ :span {:class "loading loading-spinner loading-sm text-info"})
            has-error
            ($ :span {:class "badge badge-error badge-sm"} "!")
            result
            ($ :span {:class "badge badge-success badge-sm"} "✓")
            :else
            ($ :span {:class "badge badge-ghost badge-sm"} "○"))
          ;; Node name
          ($ :span {:class "font-mono"} node-name)
          ;; Duration
          (when (and start-time finish-time)
            ($ :span {:class "text-sm text-base-content/60"}
               (str "(" (- finish-time start-time) "ms)")))
          ;; Human request indicator
          (when human-request
            ($ :span {:class "badge badge-warning badge-sm"} "🙋 Needs input")))

       ;; Content
       ($ :div {:class "collapse-content"}
          ($ :div {:class "space-y-4 pt-2"}

             ;; Timing
             (when (or start-time finish-time)
               ($ :div {:class "grid grid-cols-2 gap-4 text-sm"}
                  (when start-time
                    ($ :div
                       ($ :span {:class "text-base-content/60"} "Started: ")
                       ($ :span {:class "font-mono"} (time/format-timestamp-with-ms start-time))))
                  (when finish-time
                    ($ :div
                       ($ :span {:class "text-base-content/60"} "Finished: ")
                       ($ :span {:class "font-mono"} (time/format-timestamp-with-ms finish-time))))))

             ;; Input
             (when input
               ($ :div
                  ($ :div {:class "text-sm font-medium text-base-content/70 mb-1"} "Input")
                  ($ :pre {:class "bg-base-200 p-3 rounded-lg text-xs font-mono overflow-x-auto max-h-32"}
                     (json/pretty-json input))))

             ;; Human request
             (when human-request
               ($ :div {:class "alert alert-warning"}
                  ($ :div
                     ($ :div {:class "font-medium"} "Human Input Required")
                     ($ :div {:class "text-sm"} (:prompt human-request)))))

             ;; Exceptions
             (when (seq exceptions)
               ($ :div
                  ($ :div {:class "text-sm font-medium text-error mb-1"}
                     (str "Exceptions (" (count exceptions) ")"))
                  (for [[idx exc-str] (map-indexed vector exceptions)]
                    ($ :pre {:key idx
                             :class "bg-error/10 p-3 rounded-lg text-xs font-mono overflow-x-auto max-h-32 mb-2"}
                       exc-str))))

             ;; Nested operations
             (when (seq nested-ops)
               ($ :div
                  ($ :div {:class "text-sm font-medium text-base-content/70 mb-1"}
                     (str "Operations (" (count nested-ops) ")"))
                  ($ :div {:class "space-y-2"}
                     (for [[idx op] (map-indexed vector nested-ops)]
                       (let [op-type (:type op)
                             info (:info op)
                             op-start (:start-time-millis op)
                             op-end (:finish-time-millis op)]
                         ($ :div {:key idx
                                  :class "bg-base-200 p-2 rounded text-sm"}
                            ($ :div {:class "flex justify-between items-center"}
                               ($ :span {:class "badge badge-outline badge-sm"} op-type)
                               (when (and op-start op-end)
                                 ($ :span {:class "text-xs text-base-content/60"}
                                    (str (- op-end op-start) "ms"))))
                            (when (:objectName info)
                              ($ :div {:class "font-mono text-xs mt-1"}
                                 (:objectName info)))))))))

             ;; Result
             (when result
               ($ :div
                  ($ :div {:class "text-sm font-medium text-base-content/70 mb-1"} "Result")
                  ($ :pre {:class "bg-success/10 p-3 rounded-lg text-xs font-mono overflow-x-auto max-h-32"}
                     (json/pretty-json result))))

             ;; Emits
             (when (seq emits)
               ($ :div
                  ($ :div {:class "text-sm font-medium text-base-content/70 mb-1"}
                     (str "Emits (" (count emits) ")"))
                  ($ :div {:class "space-y-1"}
                     (for [[idx emit] (map-indexed vector emits)]
                       ($ :div {:key idx
                                :class "bg-base-200 p-2 rounded text-sm font-mono"}
                          ($ :span {:class "text-base-content/60"} "→ ")
                          (:node-name emit)))))))))))

;; =============================================================================
;; NODES TRACE VIEW
;; =============================================================================

(defui nodes-trace-view
  "List view of all nodes in the trace"
  [{:keys [nodes root-invoke-id]}]
  (let [[expanded-nodes set-expanded-nodes] (uix/use-state #{})

        toggle-node (fn [node-id]
                      (set-expanded-nodes
                       (fn [current]
                         (if (contains? current node-id)
                           (disj current node-id)
                           (conj current node-id)))))

        expand-all (fn []
                     (set-expanded-nodes (set (keys nodes))))

        collapse-all (fn []
                       (set-expanded-nodes #{}))

        ;; Sort nodes: root first, then by start time
        sorted-nodes (->> nodes
                          (sort-by (fn [[id data]]
                                     [(if (= id root-invoke-id) 0 1)
                                      (or (:start-time-millis data) js/Number.MAX_VALUE)])))]

    ($ :div {:class "space-y-3"}
       ;; Controls
       ($ :div {:class "flex justify-between items-center"}
          ($ :h2 {:class "text-lg font-semibold"}
             (str "Trace (" (count nodes) " nodes)"))
          ($ :div {:class "flex gap-2"}
             ($ :button {:class "btn btn-xs btn-ghost"
                         :onClick expand-all}
                "Expand All")
             ($ :button {:class "btn btn-xs btn-ghost"
                         :onClick collapse-all}
                "Collapse All")))

       ;; Node list
       ($ :div {:class "space-y-2"}
          (for [[node-id node-data] sorted-nodes]
            ($ node-card {:key (str node-id)
                          :node-id node-id
                          :node-data node-data
                          :expanded? (contains? expanded-nodes node-id)
                          :on-toggle #(toggle-node node-id)}))))))

;; =============================================================================
;; VIEW TOGGLE
;; =============================================================================

(defui view-toggle
  "Toggle between Graph View and Trace View"
  [{:keys [current-view on-change]}]
  ($ :div {:class "flex items-center justify-center"}
     ($ :div {:class "join"}
        ($ :button {:class (str "join-item btn btn-sm "
                                (when (= current-view :graph) "btn-active"))
                    :onClick #(on-change :graph)}
           ($ icons/grid {:class "h-4 w-4 mr-2"})
           "Graph View")
        ($ :button {:class (str "join-item btn btn-sm "
                                (when (= current-view :trace) "btn-active"))
                    :onClick #(on-change :trace)}
           ($ icons/document {:class "h-4 w-4 mr-2"})
           "Trace View"))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Invocation detail view with graph and trace toggle"
  [{:keys [module-id agent-name invoke-id]}]
  (let [decoded-agent (utils/url-decode agent-name)
        [task-id agent-invoke-id] (parse-invoke-id invoke-id)

        ;; Local state for polling
        [poll-count set-poll-count] (uix/use-state 0)

        ;; View mode state (persisted in localStorage)
        [view-mode set-view-mode] (uix/use-state :graph)

        ;; Fetch invocation data
        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:invocation-detail module-id agent-name invoke-id poll-count]
          :sente-event [:invocations/get-graph-page
                        {:module-id module-id
                         :agent-name agent-name
                         :invoke-pair [task-id agent-invoke-id]}]
          :enabled? (boolean (and module-id agent-name task-id agent-invoke-id))})

        ;; Extract data
        nodes (:nodes data)
        summary (:summary data)
        is-complete (:is-complete data)
        root-invoke-id (:root-invoke-id data)
        historical-graph (:historical-graph data)

        ;; Derive states
        is-live (and (not loading?) (not is-complete))
        result (:result summary)
        exceptions (:exception-summaries summary)
        metadata (:metadata summary)

        ;; Selected node callback for graph view
        [selected-node-id set-selected-node-id] (uix/use-state nil)]

    ;; Polling effect for live invocations
    (uix/use-effect
     (fn []
       (when is-live
         (let [timer-id (js/setInterval
                         #(set-poll-count (fn [c] (inc c)))
                         1500)]
           (fn [] (js/clearInterval timer-id)))))
     [is-live])

    ;; Load view preference from localStorage
    (uix/use-effect
     (fn []
       (when-let [stored (js/localStorage.getItem "invocation-view-mode")]
         (set-view-mode (keyword stored)))
       js/undefined)
     [])

    ;; Save view preference to localStorage
    (uix/use-effect
     (fn []
       (js/localStorage.setItem "invocation-view-mode" (name view-mode))
       js/undefined)
     [view-mode])

    (cond
      (and loading? (not data))
      ($ ui/loading-state {:message "Loading invocation..."})

      error
      ($ ui/error-alert {:message (str "Error loading invocation: " error)})

      (not data)
      ($ ui/empty-state
         {:title "Invocation not found"
          :description "The requested invocation could not be found."})

      :else
      ($ :div {:class "space-y-6"}
         ;; Header
         ($ :div {:class "flex items-center justify-between"}
            ($ :div
               ($ :h1 {:class "text-2xl font-bold flex items-center gap-3"}
                  "Invocation"
                  (when is-live
                    ($ :span {:class "loading loading-dots loading-sm text-info"})))
               ($ :p {:class "text-base-content/60 font-mono text-sm"}
                  invoke-id))
            ;; Back button
            ($ :button {:class "btn btn-ghost btn-sm"
                        :onClick #(rfe/push-state :invocations
                                                  {:module-id module-id
                                                   :agent-name agent-name})}
               "← Back to list"))

         ;; Live indicator
         (when is-live
           ($ :div {:class "alert alert-info"}
              ($ :span {:class "loading loading-spinner loading-sm"})
              ($ :span "This invocation is still running. Data updates automatically.")))

         ;; Summary
         ($ summary-card {:summary summary
                          :is-complete is-complete
                          :is-live is-live})

         ;; Result
         ($ result-card {:result result})

         ;; Exceptions
         ($ exceptions-card {:exceptions exceptions
                             :on-select-node (fn [node-id]
                                               (set-selected-node-id node-id)
                                               (set-view-mode :graph))})

         ;; Metadata
         ($ metadata-card {:metadata metadata})

         ;; View toggle and trace/graph content
         (when (seq nodes)
           ($ :div {:class "space-y-4"}
              ;; View mode toggle
              ($ view-toggle {:current-view view-mode
                              :on-change set-view-mode})

              ;; Conditional view rendering
              (if (= view-mode :graph)
                ;; Graph View
                ($ graph/invocation-graph
                   {:nodes nodes
                    :root-invoke-id root-invoke-id
                    :is-complete is-complete
                    :on-select-node set-selected-node-id})

                ;; Trace View (existing collapsible list)
                ($ nodes-trace-view {:nodes nodes
                                     :root-invoke-id root-invoke-id}))))))))

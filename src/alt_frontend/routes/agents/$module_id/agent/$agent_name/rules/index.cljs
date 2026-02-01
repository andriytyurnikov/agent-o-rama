(ns alt-frontend.routes.agents.$module-id.agent.$agent-name.rules.index
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.state :as state]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.modal :as modal]
            [alt-frontend.components.rules-form :as rules-form]
            [alt-frontend.components.icons :as icons]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

(defn url-encode [s]
  (when s (js/encodeURIComponent s)))

(defn action-friendly-name
  "Returns a user-friendly display name for an action builder ID."
  [action-id]
  (case action-id
    "aor/eval" "Online evaluation"
    "aor/add-to-dataset" "Add to dataset"
    "aor/webhook" "Webhook"
    "aor/add-to-human-feedback-queue" "Add to human feedback queue"
    action-id))

(defn format-timestamp
  "Format timestamp millis as human-readable date/time."
  [millis]
  (when millis
    (let [date (js/Date. millis)
          now (js/Date.)
          diff-ms (- (.getTime now) millis)
          diff-hours (/ diff-ms 1000 60 60)]
      (if (< diff-hours 24)
        (str (.toLocaleTimeString date) " (today)")
        (.toLocaleString date)))))

(defn truncate-str
  "Truncate string to max-length characters."
  [s max-length]
  (if (and s (> (count s) max-length))
    (str (subs s 0 max-length) "...")
    s))

;; =============================================================================
;; FILTER FORMATTING
;; =============================================================================

(defn format-filter-compact
  "Format filter structure as compact string representation."
  [filter-map]
  (when filter-map
    (let [type (get filter-map "type" (:type filter-map))]
      (if-not type
        nil
        (case (keyword type)
          :and
          (let [filters (get filter-map "filters" (:filters filter-map))]
            (if (empty? filters)
              nil
              (str "and(" (str/join ", " (keep format-filter-compact filters)) ")")))

          :or
          (let [filters (get filter-map "filters" (:filters filter-map))]
            (if (empty? filters)
              "or()"
              (str "or(" (str/join ", " (keep format-filter-compact filters)) ")")))

          :not
          (let [inner-filter (get filter-map "filter" (:filter filter-map))]
            (str "not(" (format-filter-compact inner-filter) ")"))

          :token-count
          (let [token-type (get filter-map "token-type" (:token-type filter-map))
                comp-spec (get filter-map "comparator-spec" (:comparator-spec filter-map))
                comparator (get comp-spec "comparator" (:comparator comp-spec))
                value (get comp-spec "value" (:value comp-spec))]
            (str "tokenCount(" token-type ", " (name comparator) ", " value ")"))

          :error "error()"

          :latency
          (let [comp-spec (get filter-map "comparator-spec" (:comparator-spec filter-map))
                comparator (get comp-spec "comparator" (:comparator comp-spec))
                value (get comp-spec "value" (:value comp-spec))]
            (str "latency(" (name comparator) ", " value ")"))

          :feedback
          (let [rule-name (get filter-map "rule-name" (:rule-name filter-map))
                feedback-key (get filter-map "feedback-key" (:feedback-key filter-map))
                comp-spec (get filter-map "comparator-spec" (:comparator-spec filter-map))
                comparator (get comp-spec "comparator" (:comparator comp-spec))
                value (get comp-spec "value" (:value comp-spec))]
            (str "feedback(" rule-name ", " feedback-key ", " (name comparator) ", " value ")"))

          :input-match
          (let [json-path (get filter-map "json-path" (:json-path filter-map))
                regex (get filter-map "regex" (:regex filter-map))]
            (str "inputMatch(" json-path ", " regex ")"))

          :output-match
          (let [json-path (get filter-map "json-path" (:json-path filter-map))
                regex (get filter-map "regex" (:regex filter-map))]
            (str "outputMatch(" json-path ", " regex ")"))

          (str type))))))

;; =============================================================================
;; RULE DETAILS MODAL
;; =============================================================================

(defui rule-details-content [{:keys [rule-name rule]}]
  (let [{:keys [node-name action-name action-params filter sampling-rate
                start-time-millis status-filter]} rule]
    ($ :div {:class "space-y-4"}
       ;; Basic info
       ($ :div {:class "grid grid-cols-2 gap-4"}
          ($ :div
             ($ :span {:class "text-sm font-medium text-base-content/70"} "Rule Name")
             ($ :p {:class "font-mono"} rule-name))
          ($ :div
             ($ :span {:class "text-sm font-medium text-base-content/70"} "Node")
             ($ :p (or node-name ($ :span {:class "italic text-base-content/50"} "agent-level"))))
          ($ :div
             ($ :span {:class "text-sm font-medium text-base-content/70"} "Action")
             ($ :p (action-friendly-name action-name)))
          ($ :div
             ($ :span {:class "text-sm font-medium text-base-content/70"} "Status Filter")
             ($ :p {:class (case (keyword status-filter)
                             :success "text-success"
                             :failure "text-error"
                             :all "text-info"
                             "")}
                (name (or status-filter "N/A")))))

       ;; Sampling and timing
       ($ :div {:class "grid grid-cols-2 gap-4"}
          ($ :div
             ($ :span {:class "text-sm font-medium text-base-content/70"} "Sampling Rate")
             ($ :p (if sampling-rate
                     (str (int (* sampling-rate 100)) "%")
                     "N/A")))
          ($ :div
             ($ :span {:class "text-sm font-medium text-base-content/70"} "Start Time")
             ($ :p (or (format-timestamp start-time-millis)
                       ($ :span {:class "italic text-base-content/50"} "N/A")))))

       ;; Action params
       (when (seq action-params)
         ($ :div
            ($ :div {:class "divider"} "Action Parameters")
            ($ :pre {:class "bg-base-200 p-3 rounded-lg text-sm overflow-auto max-h-48"}
               (js/JSON.stringify (clj->js action-params) nil 2))))

       ;; Filter
       (when filter
         (let [compact (format-filter-compact filter)]
           (when (and compact (not (str/blank? compact)))
             ($ :div
                ($ :div {:class "divider"} "Filter")
                ($ :pre {:class "bg-base-200 p-3 rounded-lg text-sm overflow-auto max-h-32"}
                   compact))))))))

(defn show-rule-details! [rule-name rule]
  (state/dispatch
   [:modal/show
    {:title (str "Rule: " rule-name)
     :content ($ rule-details-content {:rule-name rule-name :rule rule})
     :size :lg}]))

;; =============================================================================
;; RULE ROW COMPONENT
;; =============================================================================

(defui rule-row [{:keys [rule-name rule module-id agent-name on-delete]}]
  (let [{:keys [node-name action-name action-params filter sampling-rate
                start-time-millis status-filter]} rule
        [deleting? set-deleting!] (uix/use-state false)

        handle-delete (fn [e]
                        (.stopPropagation e)
                        (modal/show-confirm!
                         {:title "Delete Rule"
                          :message (str "Are you sure you want to delete rule \"" rule-name "\"? This action cannot be undone.")
                          :confirm-text "Delete"
                          :confirm-variant :error
                          :on-confirm (fn []
                                        (set-deleting! true)
                                        (sente/request!
                                         [:analytics/delete-rule
                                          {:module-id module-id
                                           :agent-name agent-name
                                           :rule-name rule-name}]
                                         5000
                                         (fn [reply]
                                           (set-deleting! false)
                                           (if (:success reply)
                                             (on-delete)
                                             (js/alert (str "Failed to delete rule: "
                                                            (or (:error reply) "Unknown error")))))))}))]

    ($ :tr {:class "hover cursor-pointer"
            :data-testid (str "rule-row-" rule-name)
            :onClick #(show-rule-details! rule-name rule)}
       ;; Rule name
       ($ :td {:class "font-medium"} rule-name)

       ;; Node
       ($ :td {:class "text-base-content/70"}
          (or node-name ($ :span {:class "italic text-base-content/50"} "agent-level")))

       ;; Action
       ($ :td (action-friendly-name action-name))

       ;; Action params (truncated)
       ($ :td {:class "max-w-32"}
          (if action-params
            ($ :span {:class "text-xs font-mono truncate block"}
               (truncate-str (js/JSON.stringify (clj->js action-params)) 30))
            ($ :span {:class "italic text-base-content/50"} "—")))

       ;; Status filter
       ($ :td
          (let [status-kw (keyword status-filter)]
            ($ :span {:class (str "badge badge-sm "
                                  (case status-kw
                                    :success "badge-success"
                                    :failure "badge-error"
                                    :all "badge-info"
                                    "badge-ghost"))}
               (name (or status-kw "N/A")))))

       ;; Filter (truncated)
       ($ :td {:class "max-w-32"}
          (let [compact (format-filter-compact filter)]
            (if (and compact (not (str/blank? compact)))
              ($ :span {:class "text-xs truncate block"} (truncate-str compact 25))
              ($ :span {:class "italic text-base-content/50"} "—"))))

       ;; Sampling rate
       ($ :td
          (if sampling-rate
            (str (int (* sampling-rate 100)) "%")
            ($ :span {:class "italic text-base-content/50"} "N/A")))

       ;; Start time
       ($ :td {:class "text-xs"}
          (if start-time-millis
            (format-timestamp start-time-millis)
            ($ :span {:class "italic text-base-content/50"} "N/A")))

       ;; Action log link
       ($ :td
          ($ :a {:class "link link-primary text-sm"
                 :onClick (fn [e]
                            (.stopPropagation e)
                            (rfe/push-state :agent/action-log
                                            {:module-id (url-encode module-id)
                                             :agent-name (url-encode agent-name)
                                             :rule-name (url-encode rule-name)}))}
             "View"))

       ;; Delete
       ($ :td {:class "text-center"}
          ($ :button {:class (str "btn btn-ghost btn-sm text-error "
                                  (when deleting? "loading"))
                      :data-testid "btn-delete-rule"
                      :disabled deleting?
                      :onClick handle-delete}
             (when-not deleting?
               ($ icons/trash {:class "h-5 w-5"})))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view [{:keys [module-id agent-name]}]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)

        ;; Manual fetch since rules don't use standard query pattern
        [rules set-rules!] (uix/use-state nil)
        [loading? set-loading!] (uix/use-state true)
        [error set-error!] (uix/use-state nil)
        connected? (state/use-state [:sente :connected?])

        refetch (uix/use-callback
                 (fn []
                   (when connected?
                     (set-loading! true)
                     (set-error! nil)
                     (sente/request!
                      [:analytics/fetch-rules
                       {:module-id decoded-module-id
                        :agent-name decoded-agent-name}]
                      15000
                      (fn [reply]
                        (set-loading! false)
                        (if (:success reply)
                          (set-rules! (:data reply))
                          (set-error! (or (:error reply)
                                          (when (= reply :chsk/closed) "Connection closed")
                                          "Failed to fetch rules")))))))
                 [decoded-module-id decoded-agent-name])

        ;; Initial fetch
        _ (uix/use-effect
           (fn []
             (refetch)
             js/undefined)
           [refetch])]

    ($ :div {:class "space-y-4"}
       ;; Header
       ($ :div {:class "flex items-center justify-between"}
          ($ :div {:class "flex items-center gap-3"}
             ($ icons/rules {:class "h-8 w-8 text-primary"})
             ($ :h1 {:class "text-2xl font-bold"} "Rules"))

          ;; Add rule button
          ($ :button {:class "btn btn-primary btn-sm"
                      :data-testid "btn-create-rule"
                      :onClick #(rules-form/show-add-rule-modal! decoded-module-id decoded-agent-name refetch)}
             "+ Add Rule"))

       ;; Content
       (cond
         ;; Loading
         loading?
         ($ ui/loading-state {:message "Loading rules..."})

         ;; Error
         error
         ($ ui/error-alert {:message error})

         ;; Empty
         (empty? rules)
         ($ ui/empty-state
            {:title "No Rules"
             :description "No rules configured for this agent."
             :icon ($ icons/rules {:class "h-12 w-12"})})

         ;; Rules table
         :else
         ($ :div {:class "overflow-x-auto"}
            ($ :table {:class "table table-zebra"
                       :data-testid "data-table"}
               ($ :thead
                  ($ :tr
                     ($ :th "Rule Name")
                     ($ :th "Node")
                     ($ :th "Action")
                     ($ :th "Params")
                     ($ :th "Status")
                     ($ :th "Filter")
                     ($ :th "Sample")
                     ($ :th "Start Time")
                     ($ :th "Log")
                     ($ :th {:class "w-16"} "")))
               ($ :tbody
                  (for [[rule-name rule] rules]
                    ($ rule-row {:key (name rule-name)
                                 :rule-name (name rule-name)
                                 :rule rule
                                 :module-id decoded-module-id
                                 :agent-name decoded-agent-name
                                 :on-delete refetch})))))))))

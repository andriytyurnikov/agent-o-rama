(ns alt-frontend.routes.agents.$module-id.human-feedback-queues.$queue-id.index
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.components.ui :as ui]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]
            [cljs.pprint]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

(defn url-encode [s]
  (when s (js/encodeURIComponent s)))

(defn to-json
  "Convert clojure data to JSON string for display."
  [data]
  (try
    (js/JSON.stringify (clj->js data) nil 2)
    (catch :default _
      (str data))))

(defn truncate-json
  "Convert data to JSON and truncate for preview."
  [data max-len]
  (let [json (to-json data)]
    (if (> (count json) max-len)
      (str (subs json 0 max-len) "...")
      json)))

(def TARGET-DOES-NOT-EXIST :com.rpl.agent-o-rama.impl.queries/target-does-not-exist)

(defn agent-target?
  "Returns true if the target is an agent (not a node)"
  [target]
  (nil? (:node-invoke target)))

(defn unwrap-agent-output
  "For agent targets, output is {:val <value> :failure? <bool>}.
   Returns {:failed? bool :value <unwrapped-value-or-original>}"
  [output target]
  (if (agent-target? target)
    {:failed? (:failure? output)
     :value (:val output)}
    {:failed? false
     :value output}))

;; =============================================================================
;; QUEUE INFO HEADER
;; =============================================================================

(defn numeric-metric? [metric]
  (and (contains? metric :min) (contains? metric :max)))

(defn category-metric? [metric]
  (contains? metric :categories))

(defui queue-info-header [{:keys [queue-info]}]
  (let [rubrics (or (:rubrics queue-info) [])]
    ($ :div {:class "card bg-base-100 border border-base-300"}
       ($ :div {:class "card-body"}
          ;; Header
          ($ :h2 {:class "card-title"} "Queue Information")

          ;; Description
          (when-let [desc (:description queue-info)]
            (when-not (str/blank? desc)
              ($ :p {:class "text-base-content/70"} desc)))

          ;; Rubrics
          (when (seq rubrics)
            ($ :div {:class "mt-4"}
               ($ :h3 {:class "text-sm font-semibold mb-2"} "Metrics:")
               ($ :div {:class "flex flex-wrap gap-2"}
                  (for [rubric rubrics]
                    (let [metric (:metric rubric)
                          metric-name (:name rubric)
                          is-required? (:required rubric)
                          is-numeric? (numeric-metric? metric)
                          is-category? (category-metric? metric)]
                      ($ :div {:key metric-name
                               :class "tooltip"
                               :data-tip (cond
                                           is-numeric? (str "Range: " (:min metric) "-" (:max metric))
                                           is-category? (str "Options: " (str/join ", " (:categories metric)))
                                           :else "")}
                         ($ :span {:class (str "badge "
                                               (if is-required? "badge-primary" "badge-ghost"))}
                            metric-name
                            (when-not is-required?
                              ($ :span {:class "text-xs opacity-60 ml-1"} "(opt)")))))))))))))

;; =============================================================================
;; QUEUE ITEM ROW
;; =============================================================================

(defui queue-item-row [{:keys [item module-id queue-id]}]
  (let [input (:input item)
        output (:output item)
        target (:target item)
        input-unavailable? (= input TARGET-DOES-NOT-EXIST)
        output-unavailable? (= output TARGET-DOES-NOT-EXIST)
        {:keys [failed? value]} (unwrap-agent-output output target)
        item-id-str (str (:id item))]
    ($ :tr {:class "hover cursor-pointer"
            :data-testid (str "queue-item-row-" item-id-str)
            :onClick #(rfe/push-state :module/human-feedback-queue-item
                                      {:module-id module-id
                                       :queue-id queue-id
                                       :item-id item-id-str})}
       ;; ID (shortened)
       ($ :td {:class "font-mono text-xs"}
          (let [id-str item-id-str]
            (if (> (count id-str) 8)
              (str (subs id-str 0 8) "...")
              id-str)))
       ;; Comment
       ($ :td {:class "text-base-content/70 max-w-xs truncate"}
          (or (:comment item) ""))
       ;; Input
       ($ :td {:class "text-base-content/70 max-w-xs truncate font-mono text-xs"}
          (if input-unavailable?
            ($ :span {:class "italic opacity-50"} "Data unavailable")
            (truncate-json input 100)))
       ;; Output
       ($ :td {:class (str "max-w-xs truncate font-mono text-xs "
                           (if failed? "text-error font-semibold" "text-base-content/70"))}
          (if output-unavailable?
            ($ :span {:class "italic opacity-50"} "Data unavailable")
            (truncate-json value 100))))))

;; =============================================================================
;; QUEUE DETAIL VIEW
;; =============================================================================

(defui view [{:keys [module-id queue-id]}]
  (let [decoded-module-id (url-decode module-id)
        decoded-queue-id (url-decode queue-id)

        ;; Fetch queue info (description, rubrics)
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:human-feedback-queue-info module-id queue-id]
          :sente-event [:human-feedback/get-queue-info
                        {:module-id decoded-module-id
                         :queue-name decoded-queue-id}]
          :enabled? (boolean (and decoded-module-id decoded-queue-id))})

        queue-info data
        queue-info-loading? loading?
        queue-info-error error

        ;; Fetch queue items with pagination
        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-paginated-query
         {:query-key [:human-feedback-queue-items module-id queue-id]
          :sente-event [:human-feedback/get-queue-items
                        {:module-id decoded-module-id
                         :queue-name decoded-queue-id}]
          :page-size 20
          :enabled? (boolean (and decoded-module-id decoded-queue-id))})

        items-loading? isLoading
        items-error error
        items (or data [])]

    (cond
      ;; Loading
      (or queue-info-loading? items-loading?)
      ($ ui/loading-state {:message "Loading queue..."})

      ;; Error
      (or queue-info-error items-error)
      ($ ui/error-alert {:message (str "Failed to load queue: " (or queue-info-error items-error))})

      ;; Content
      :else
      ($ :div {:class "space-y-6"}
         ;; Page header
         ($ :div {:class "flex items-center justify-between"}
            ($ :h1 {:class "text-2xl font-bold"} (or decoded-queue-id "Queue Detail")))

         ;; Queue info card
         ($ queue-info-header {:queue-info queue-info})

         ;; Items section
         ($ :div {:class "space-y-4"}
            ($ :h2 {:class "text-lg font-semibold"} "Queue Items")

            (if (empty? items)
              ($ ui/empty-state
                 {:title "No Items"
                  :description "This queue has no items to review."})

              ($ :div
                 ;; Table
                 ($ :div {:class "overflow-x-auto"}
                    ($ :table {:class "table table-zebra"
                               :data-testid "data-table"}
                       ($ :thead
                          ($ :tr
                             ($ :th "ID")
                             ($ :th "Comment")
                             ($ :th "Input")
                             ($ :th "Output")))
                       ($ :tbody
                          (for [item items]
                            ($ queue-item-row {:key (str (:id item))
                                               :item item
                                               :module-id module-id
                                               :queue-id queue-id})))))

                 ;; Load more
                 (when hasMore
                   ($ :div {:class "flex justify-center mt-4"}
                      ($ ui/button {:variant :ghost
                                    :loading? isFetchingMore
                                    :on-click loadMore}
                         (if isFetchingMore "Loading..." "Load More")))))))))))

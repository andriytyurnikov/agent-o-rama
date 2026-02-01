(ns alt-frontend.routes.agents.$module-id.evaluations
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            [alt-frontend.components.modal :as modal]
            [alt-frontend.components.evaluator-form :as evaluator-form]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.state :as state]
            [clojure.string :as str]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

(defn get-type-badge-class [type]
  (case type
    :regular "badge-primary"
    :comparative "badge-secondary"
    :summary "badge-accent"
    "badge-ghost"))

(defn get-type-display [type]
  (case type
    :regular "Regular"
    :comparative "Comparative"
    :summary "Summary"
    (str type)))

;; =============================================================================
;; EVALUATOR DETAILS MODAL
;; =============================================================================

(defui evaluator-details-content [{:keys [spec module-id]}]
  (let [{:keys [name type description builder-name builder-params
                input-json-path output-json-path reference-output-json-path]} spec]
    ($ :div {:class "space-y-4"}
       ;; Try Evaluator button (only for regular and comparative types)
       (when (#{:regular :comparative} type)
         ($ :div {:class "flex justify-end"}
            ($ :button {:class "btn btn-primary btn-sm"
                        :onClick (fn []
                                   (state/dispatch [:modal/hide])
                                   ;; Small delay to let the first modal close
                                   (js/setTimeout
                                    #(evaluator-form/show-run-evaluator-modal!
                                      {:module-id module-id
                                       :evaluator spec})
                                    100))}
               ($ icons/play {:class "h-4 w-4 mr-1"})
               "Try Evaluator")))

       ;; Basic Info
       ($ :div {:class "grid grid-cols-3 gap-4"}
          ($ :div
             ($ :span {:class "text-sm font-medium text-base-content/70"} "Name")
             ($ :p {:class "font-mono"} name))
          ($ :div
             ($ :span {:class "text-sm font-medium text-base-content/70"} "Type")
             ($ :p
                ($ :span {:class (str "badge " (get-type-badge-class type))}
                   (get-type-display type))))
          ($ :div
             ($ :span {:class "text-sm font-medium text-base-content/70"} "Builder")
             ($ :p {:class "font-mono text-sm"} builder-name)))

       ;; Description
       (when-not (str/blank? description)
         ($ :div
            ($ :span {:class "text-sm font-medium text-base-content/70"} "Description")
            ($ :p {:class "mt-1"} description)))

       ;; Builder Parameters
       (when (seq builder-params)
         ($ :div
            ($ :span {:class "text-sm font-medium text-base-content/70"} "Parameters")
            ($ :div {:class "mt-2 bg-base-200 rounded-lg p-3 space-y-2"}
               (for [[k v] (sort-by key builder-params)]
                 ($ :div {:key (str k)
                          :class "flex gap-4"}
                    ($ :span {:class "font-mono text-sm text-base-content/70 min-w-32"}
                       (clojure.core/name k))
                    ($ :pre {:class "text-sm bg-base-100 px-2 py-1 rounded whitespace-pre-wrap flex-1"}
                       (str v)))))))

       ;; JSONPath Configuration
       ($ :div {:class "divider"} "JSONPath Configuration")
       ($ :div {:class "grid grid-cols-1 gap-3"}
          ($ :div {:class "flex items-center gap-2"}
             ($ :span {:class "text-sm font-medium text-base-content/70 w-40"} "Input Path:")
             (if (str/blank? input-json-path)
               ($ :span {:class "italic text-base-content/50"} "Not configured")
               ($ :code {:class "bg-base-200 px-2 py-1 rounded text-sm"} input-json-path)))
          ($ :div {:class "flex items-center gap-2"}
             ($ :span {:class "text-sm font-medium text-base-content/70 w-40"} "Output Path:")
             (if (str/blank? output-json-path)
               ($ :span {:class "italic text-base-content/50"} "Not configured")
               ($ :code {:class "bg-base-200 px-2 py-1 rounded text-sm"} output-json-path)))
          ($ :div {:class "flex items-center gap-2"}
             ($ :span {:class "text-sm font-medium text-base-content/70 w-40"} "Reference Output Path:")
             (if (str/blank? reference-output-json-path)
               ($ :span {:class "italic text-base-content/50"} "Not configured")
               ($ :code {:class "bg-base-200 px-2 py-1 rounded text-sm"} reference-output-json-path)))))))

(defn show-evaluator-details! [spec module-id]
  (state/dispatch
   [:modal/show
    {:title (str "Evaluator: " (:name spec))
     :content ($ evaluator-details-content {:spec spec :module-id module-id})
     :size :lg}]))

;; =============================================================================
;; EVALUATOR ROW
;; =============================================================================

(defui evaluator-row [{:keys [spec on-delete module-id]}]
  (let [{:keys [name type description builder-name]} spec]
    ($ :tr {:class "hover cursor-pointer"
            :data-testid (str "evaluator-row-" name)
            :onClick #(show-evaluator-details! spec module-id)}
       ;; Name
       ($ :td {:class "font-medium"} name)
       ;; Description
       ($ :td {:class "text-base-content/70 max-w-xs truncate"}
          (if (str/blank? description)
            ($ :span {:class "italic text-base-content/50"} "—")
            description))
       ;; Builder
       ($ :td
          ($ :code {:class "text-xs"} builder-name))
       ;; Type
       ($ :td
          ($ :span {:class (str "badge " (get-type-badge-class type))}
             (get-type-display type)))
       ;; Actions
       ($ :td
          ($ :button {:class "btn btn-ghost btn-sm text-error"
                      :data-testid "btn-delete-evaluator"
                      :onClick (fn [e]
                                 (.stopPropagation e)
                                 (on-delete name))}
             ($ icons/trash {:class "h-5 w-5"}))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view [{:keys [module-id]}]
  (let [decoded-module-id (url-decode module-id)

        ;; Search and filter state
        [search-term set-search-term] (uix/use-state "")
        [debounced-search set-debounced-search] (uix/use-state "")
        [selected-type set-selected-type] (uix/use-state "all")

        ;; Debounce search input
        _ (uix/use-effect
           (fn []
             (let [timeout-id (js/setTimeout #(set-debounced-search search-term) 300)]
               #(js/clearTimeout timeout-id)))
           [search-term])

        ;; Fetch evaluators with pagination
        {:keys [data isLoading isFetchingMore hasMore loadMore error refetch]}
        (queries/use-paginated-query
         {:query-key [:evaluator-instances module-id debounced-search selected-type]
          :sente-event [:evaluators/get-all-instances
                        {:module-id decoded-module-id
                         :filters (cond-> {}
                                    (not (str/blank? debounced-search))
                                    (assoc :search-string debounced-search)

                                    (not= selected-type "all")
                                    (assoc :types #{(keyword selected-type)}))}]
          :page-size 20
          :enabled? (boolean decoded-module-id)})

        evaluators (or (:items data) data [])

        ;; Delete handler
        handle-delete (uix/use-callback
                       (fn [evaluator-name]
                         (modal/show-confirm!
                          {:title "Delete Evaluator"
                           :message (str "Are you sure you want to delete evaluator \"" evaluator-name "\"? This action cannot be undone.")
                           :confirm-text "Delete"
                           :confirm-variant :error
                           :on-confirm (fn []
                                         (sente/request!
                                          [:evaluators/delete
                                           {:module-id decoded-module-id
                                            :name evaluator-name}]
                                          15000
                                          (fn [reply]
                                            (if (:success reply)
                                              (refetch)
                                              (js/alert (str "Failed to delete evaluator: " (:error reply)))))))}))
                       [decoded-module-id refetch])]

    ($ :div {:class "space-y-4"}
       ;; Header
       ($ :div {:class "flex items-center justify-between"}
          ($ :div {:class "flex items-center gap-3"}
             ($ icons/evaluator {:class "h-8 w-8 text-primary"})
             ($ :h1 {:class "text-2xl font-bold"} "Evaluators"))
          ($ :button {:class "btn btn-primary"
                      :data-testid "btn-create-evaluator"
                      :onClick #(evaluator-form/show-create-evaluator-modal!
                                 {:module-id decoded-module-id
                                  :on-success refetch})}
             ($ icons/plus {:class "h-5 w-5 mr-1"})
             "Create Evaluator"))

       ;; Search and filters
       ($ :div {:class "flex flex-wrap gap-4"}
          ;; Search input
          ($ :div {:class "form-control flex-1 min-w-64"}
             ($ :input {:type "text"
                        :class "input input-bordered w-full"
                        :placeholder "Search evaluators..."
                        :data-testid "input-search-evaluators"
                        :value search-term
                        :onChange #(set-search-term (.. % -target -value))}))

          ;; Type filter
          ($ :select {:class "select select-bordered"
                      :value selected-type
                      :onChange #(set-selected-type (.. % -target -value))}
             ($ :option {:value "all"} "All Types")
             ($ :option {:value "regular"} "Regular")
             ($ :option {:value "comparative"} "Comparative")
             ($ :option {:value "summary"} "Summary")))

       ;; Content
       (cond
         ;; Loading state
         isLoading
         ($ ui/loading-state {:message "Loading evaluators..."})

         ;; Error state
         error
         ($ ui/error-alert {:message (str "Failed to load evaluators: " error)})

         ;; Empty state
         (empty? evaluators)
         ($ :div {:class "text-center py-12"}
            ($ icons/evaluator {:class "mx-auto h-12 w-12 text-base-content/40 mb-4"})
            ($ :h3 {:class "text-lg font-medium mb-2"}
               (if (or (not (str/blank? search-term)) (not= selected-type "all"))
                 "No Evaluators Found"
                 "No Evaluators Yet"))
            ($ :p {:class "text-base-content/70 mb-6"}
               (if (or (not (str/blank? search-term)) (not= selected-type "all"))
                 "No evaluators match your filters."
                 "Create your first evaluator to get started."))
            (when (and (str/blank? search-term) (= selected-type "all"))
              ($ :button {:class "btn btn-primary"
                          :data-testid "btn-create-evaluator"
                          :onClick #(evaluator-form/show-create-evaluator-modal!
                                     {:module-id decoded-module-id
                                      :on-success refetch})}
                 ($ icons/plus {:class "h-5 w-5 mr-1"})
                 "Create Evaluator")))

         ;; Evaluator list
         :else
         ($ :div {:class "overflow-x-auto"}
            ($ :table {:class "table table-zebra"
                       :data-testid "data-table"}
               ($ :thead
                  ($ :tr
                     ($ :th "Name")
                     ($ :th "Description")
                     ($ :th "Builder")
                     ($ :th "Type")
                     ($ :th {:class "w-24"} "Actions")))
               ($ :tbody
                  (for [spec evaluators]
                    ($ evaluator-row {:key (:name spec)
                                      :spec spec
                                      :module-id decoded-module-id
                                      :on-delete handle-delete}))))

            ;; Load more button
            (when hasMore
              ($ :div {:class "flex justify-center mt-4"}
                 ($ ui/button {:variant :ghost
                               :loading? isFetchingMore
                               :on-click loadMore}
                    (if isFetchingMore "Loading..." "Load More")))))))))

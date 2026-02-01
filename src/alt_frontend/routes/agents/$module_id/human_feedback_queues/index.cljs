(ns alt-frontend.routes.agents.$module-id.human-feedback-queues.index
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.state :as state]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            [alt-frontend.components.modal :as modal]
            [reitit.frontend.easy :as rfe]
            [alt-frontend.lib.ws.sente :as sente]
            [clojure.string :as str]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

(defn url-encode [s]
  (when s (js/encodeURIComponent s)))

;; =============================================================================
;; QUEUE LIST VIEW
;; =============================================================================

(defui view [{:keys [module-id]}]
  (let [decoded-module-id (url-decode module-id)

        ;; Search state
        [search-term set-search-term] (uix/use-state "")
        [debounced-search set-debounced-search] (uix/use-state "")

        ;; Debounce search input
        _ (uix/use-effect
           (fn []
             (let [timeout-id (js/setTimeout #(set-debounced-search search-term) 300)]
               #(js/clearTimeout timeout-id)))
           [search-term])

        ;; Fetch queues with pagination
        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-paginated-query
         {:query-key [:human-feedback-queues module-id debounced-search]
          :sente-event [:human-feedback/get-queues
                        {:module-id decoded-module-id
                         :filters (when-not (str/blank? debounced-search)
                                    {:search-string debounced-search})}]
          :page-size 20
          :enabled? (boolean decoded-module-id)})

        queues (or data [])

        ;; Delete handler
        handle-delete (uix/use-callback
                       (fn [queue-name]
                         (modal/show-confirm!
                          {:title "Delete Queue"
                           :message (str "Are you sure you want to delete queue \"" queue-name "\"? This action cannot be undone.")
                           :confirm-text "Delete"
                           :confirm-variant :error
                           :on-confirm (fn []
                                         (sente/request!
                                          [:human-feedback/delete-queue
                                           {:module-id decoded-module-id
                                            :name queue-name}]
                                          10000
                                          (fn [reply]
                                            (if (:success reply)
                                              (state/dispatch [:query/invalidate
                                                               {:query-key-pattern [:human-feedback-queues module-id]}])
                                              (js/alert (str "Error deleting queue: " (:error reply)))))))}))
                       [decoded-module-id module-id])]

    ($ :div {:class "space-y-4"}
       ;; Header
       ($ :div {:class "flex items-center justify-between"}
          ($ :h1 {:class "text-2xl font-bold"} "Human Feedback Queues")
          ;; Create button would go here if we implement create form
          )

       ;; Search input
       ($ :div {:class "form-control"}
          ($ :input {:type "text"
                     :class "input input-bordered w-full"
                     :placeholder "Search queues..."
                     :data-testid "input-search-queues"
                     :value search-term
                     :onChange #(set-search-term (.. % -target -value))}))

       ;; Content
       (cond
         ;; Loading state
         isLoading
         ($ ui/loading-state {:message "Loading queues..."})

         ;; Error state
         error
         ($ ui/error-alert {:message (str "Failed to load queues: " error)})

         ;; Empty state
         (empty? queues)
         ($ ui/empty-state
            {:title "No Feedback Queues"
             :description (if (str/blank? search-term)
                            "No human feedback queues have been created yet."
                            "No queues match your search.")})

         ;; Queue list
         :else
         ($ :<>
            ($ :div {:class "overflow-x-auto"}
               ($ :table {:class "table table-zebra"
                          :data-testid "data-table"}
                  ($ :thead
                     ($ :tr
                        ($ :th "Name")
                        ($ :th "Description")
                        ($ :th "Rubrics")
                        ($ :th {:class "w-24"} "Actions")))
                  ($ :tbody
                     (for [queue queues]
                       (let [queue-name (:name queue)
                             rubric-count (count (:rubrics queue))]
                         ($ :tr {:key queue-name
                                 :class "hover cursor-pointer"
                                 :data-testid (str "queue-row-" queue-name)
                                 :onClick #(rfe/push-state :module/human-feedback-queue-detail
                                                           {:module-id module-id
                                                            :queue-id (url-encode queue-name)})}
                            ;; Name
                            ($ :td {:class "font-medium"}
                               queue-name)
                            ;; Description
                            ($ :td {:class "text-base-content/70 max-w-xs truncate"}
                               (or (:description queue) ""))
                            ;; Rubrics count
                            ($ :td
                               ($ :span {:class "badge badge-ghost"}
                                  (str rubric-count " rubric" (when (not= rubric-count 1) "s"))))
                            ;; Actions
                            ($ :td
                               ($ :button {:class "btn btn-ghost btn-sm text-error"
                                           :onClick (fn [e]
                                                      (.stopPropagation e)
                                                      (handle-delete queue-name))}
                                  ($ icons/trash {:class "h-5 w-5"})))))))))
            ;; Load more button
            (when hasMore
              ($ :div {:class "flex justify-center mt-4"}
                 ($ ui/button {:variant :ghost
                               :loading? isFetchingMore
                               :on-click loadMore}
                    (if isFetchingMore "Loading..." "Load More")))))))))

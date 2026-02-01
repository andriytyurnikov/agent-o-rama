(ns alt-frontend.routes.agents.$module-id.agent.$agent-name.invocations.index
  "Paginated invocations list for an agent."
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.state :as state]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.lib.time :as time]
            [alt-frontend.lib.json :as json]
            [alt-frontend.components.ui :as ui]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

;; =============================================================================
;; STATUS BADGE
;; =============================================================================

(defui status-badge
  "Badge showing invocation status"
  [{:keys [status human-request?]}]
  (cond
    human-request?
    ($ :span {:class "badge badge-warning gap-1"
              :data-testid "status-badge"}
       "🙋 Needs input")

    (= status :pending)
    ($ :span {:class "badge badge-info gap-1"
              :data-testid "status-badge"}
       ($ :span {:class "loading loading-spinner loading-xs"})
       "Pending")

    (= status :failure)
    ($ :span {:class "badge badge-error"
              :data-testid "status-badge"} "Failed")

    :else
    ($ :span {:class "badge badge-success"
              :data-testid "status-badge"} "Success")))

;; =============================================================================
;; INVOCATION ROW
;; =============================================================================

(defui invocation-row
  "Single row in invocations table"
  [{:keys [invoke module-id agent-name]}]
  (let [task-id (:task-id invoke)
        agent-id (:agent-id invoke)
        invoke-id (str task-id "-" agent-id)
        start-time (:start-time-millis invoke)
        args-preview (json/truncate-json-compact (:invoke-args invoke) 60)]

    ($ :tr {:class "hover cursor-pointer"
            :data-testid (str "invocation-row-" invoke-id)
            :onClick #(rfe/push-state :invocations-detail
                                      {:module-id module-id
                                       :agent-name agent-name
                                       :invoke-id invoke-id})}
       ;; View trace button
       ($ :td
          ($ :button {:class "btn btn-xs btn-primary"
                      :onClick (fn [e]
                                 (.stopPropagation e)
                                 (rfe/push-state :invocations-detail
                                                 {:module-id module-id
                                                  :agent-name agent-name
                                                  :invoke-id invoke-id}))}
             "View"))
       ;; Time
       ($ :td {:class "font-mono text-sm"
               :title (time/format-timestamp start-time)}
          (time/format-relative-time start-time))
       ;; Arguments
       ($ :td {:class "max-w-xs"}
          ($ :div {:class "truncate font-mono text-sm"
                   :title args-preview}
             args-preview))
       ;; Graph version
       ($ :td {:class "font-mono text-sm text-base-content/60"}
          (:graph-version invoke))
       ;; Status
       ($ :td
          ($ status-badge {:status (:status invoke)
                           :human-request? (:human-request? invoke)})))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Invocations list view with pagination"
  [{:keys [module-id agent-name]}]
  (let [decoded-agent (utils/url-decode agent-name)

        ;; Paginated query for invocations
        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-paginated-query
         {:query-key [:invocations module-id agent-name]
          :sente-event [:invocations/get-page {:module-id module-id
                                               :agent-name agent-name}]
          :page-size 20
          :enabled? (boolean (and module-id agent-name))})

        ;; Extract invocations from the response
        invocations (or (:agent-invokes data) data)]

    (cond
      (and isLoading (empty? invocations))
      ($ ui/loading-state {:message "Loading invocations..."})

      error
      ($ ui/error-alert {:message (str "Error loading invocations: " error)})

      (empty? invocations)
      ($ ui/empty-state
         {:title "No invocations"
          :description (str "No invocations found for " decoded-agent)})

      :else
      ($ :div {:class "space-y-4"}
         ;; Header
         ($ :div {:class "flex items-center justify-between"}
            ($ :div
               ($ :h1 {:class "text-2xl font-bold"} "Invocations")
               ($ :p {:class "text-base-content/60"}
                  (str decoded-agent " • " (count invocations) " shown"))))

         ;; Table
         ($ :div {:class "overflow-x-auto"}
            ($ :table {:class "table table-zebra"
                       :data-testid "data-table"}
               ($ :thead
                  ($ :tr
                     ($ :th "Trace")
                     ($ :th "Start Time")
                     ($ :th "Arguments")
                     ($ :th "Version")
                     ($ :th "Status")))
               ($ :tbody
                  (for [invoke invocations]
                    ($ invocation-row
                       {:key (str (:task-id invoke) "-" (:agent-id invoke))
                        :invoke invoke
                        :module-id module-id
                        :agent-name agent-name})))))

         ;; Load More
         (when hasMore
           ($ :div {:class "flex justify-center"}
              ($ :button {:class (str "btn btn-outline "
                                      (when isFetchingMore "loading"))
                          :onClick loadMore
                          :disabled isFetchingMore}
                 (if isFetchingMore "Loading..." "Load More"))))))))

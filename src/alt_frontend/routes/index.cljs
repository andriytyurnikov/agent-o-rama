(ns alt-frontend.routes.index
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.state :as state]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

;; =============================================================================
;; MODULE CARD COMPONENT
;; =============================================================================

(defui module-card [{:keys [module-id agent-count]}]
  (let [decoded-id (utils/url-decode module-id)]
    ($ :div {:class "card bg-base-100 border border-base-300 hover:shadow-md transition-shadow cursor-pointer"
             :data-testid (str "module-card-" module-id)
             :onClick #(rfe/push-state :agents-detail {:module-id module-id})}
       ($ :div {:class "card-body p-4"}
          ($ :div {:class "flex items-center gap-3"}
             ($ :div {:class "avatar placeholder"}
                ($ :div {:class "bg-primary text-primary-content rounded-lg w-12"}
                   ($ :span {:class "text-xl"}
                      (-> decoded-id first str/upper-case))))
             ($ :div {:class "flex-1 min-w-0"}
                ($ :h3 {:class "font-semibold truncate"} decoded-id)
                ($ :p {:class "text-sm text-base-content/60"}
                   (str agent-count " agent" (when (not= agent-count 1) "s")))))))))

;; =============================================================================
;; QUICK LINKS
;; =============================================================================

(defui quick-links []
  ($ :div {:class "card bg-base-100 border border-base-300"}
     ($ :div {:class "card-body"}
        ($ :h2 {:class "card-title text-lg"} "Quick Links")
        ($ :div {:class "grid grid-cols-2 gap-2 mt-2"}
           ($ :a {:href "/alt/agents"
                  :class "btn btn-outline btn-sm gap-2"}
              ($ icons/agent {:class "h-4 w-4"})
              "All Agents")))))

;; =============================================================================
;; CONNECTION STATUS
;; =============================================================================

(defui connection-status []
  (let [connected? (state/use-state [:sente :connected?])]
    ($ :div {:class (str "badge gap-2 "
                         (if connected? "badge-success" "badge-error"))}
       ($ :div {:class (str "w-2 h-2 rounded-full "
                            (if connected? "bg-success-content" "bg-error-content"))})
       (if connected? "Connected" "Disconnected"))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view []
  (let [;; Search state
        [module-search set-module-search] (uix/use-state "")
        debounced-search (utils/use-debounced-value module-search 300)

        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:agents]
          :sente-event [:agents/get-all]
          :refetch-interval-ms 5000})

        ;; Group agents by module
        agents-by-module (when data
                           (group-by :module-id data))

        ;; Filter modules by search term
        filtered-modules (when agents-by-module
                           (if (str/blank? debounced-search)
                             (sort-by first agents-by-module)
                             (->> agents-by-module
                                  (filter (fn [[module-id _]]
                                            (str/includes?
                                             (str/lower-case (utils/url-decode module-id))
                                             (str/lower-case debounced-search))))
                                  (sort-by first))))

        module-count (count agents-by-module)
        agent-count (count data)]

    ($ :div {:class "space-y-6"}
       ;; Header
       ($ :div {:class "flex items-center justify-between"}
          ($ :div {:class "flex items-center gap-3"}
             ($ :div {:class "avatar"}
                ($ :div {:class "w-12 rounded-lg"}
                   ($ :img {:src "/logo-black.png" :alt "Agent-O-Rama"})))
             ($ :div
                ($ :h1 {:class "text-2xl font-bold"} "Agent-O-Rama")
                ($ :p {:class "text-base-content/60 text-sm"} "LLM Agent Platform")))
          ($ connection-status))

       ;; Stats
       ($ :div {:class "grid grid-cols-1 md:grid-cols-3 gap-4"}
          ($ ui/stat-card
             {:title "Total Modules"
              :value module-count
              :loading? loading?
              :icon ($ icons/grid {:class "h-8 w-8"})})

          ($ ui/stat-card
             {:title "Total Agents"
              :value agent-count
              :loading? loading?
              :icon ($ icons/agent {:class "h-8 w-8"})})

          ($ ui/stat-card
             {:title "Status"
              :value "Active"
              :loading? false
              :icon ($ icons/success {:class "h-8 w-8"})}))

       ;; Main content
       ($ :div {:class "grid grid-cols-1 lg:grid-cols-3 gap-6"}
          ;; Modules list (2 columns)
          ($ :div {:class "lg:col-span-2"}
             ($ :div {:class "card bg-base-100 border border-base-300"}
                ($ :div {:class "card-body"}
                   ($ :div {:class "flex items-center justify-between mb-4"}
                      ($ :h2 {:class "card-title text-lg"} "Deployed Modules")
                      ($ :a {:href "/alt/agents"
                             :class "link link-primary text-sm"}
                         "View all agents →"))

                   ;; Search input for modules
                   ($ :div {:class "form-control mb-4"}
                      ($ :input {:type "text"
                                 :class "input input-bordered input-sm w-full"
                                 :data-testid "input-search-modules"
                                 :placeholder "Search modules..."
                                 :value module-search
                                 :onChange #(set-module-search (.. % -target -value))}))

                   (cond
                     loading?
                     ($ ui/loading-state {:message "Loading modules..."})

                     error
                     ($ ui/error-alert {:message error})

                     (empty? filtered-modules)
                     ($ ui/empty-state
                        {:title (if (str/blank? debounced-search)
                                  "No Modules"
                                  "No Matching Modules")
                         :description (if (str/blank? debounced-search)
                                        "No agent modules have been deployed yet."
                                        "No modules match your search.")
                         :icon ($ icons/grid {:class "h-12 w-12"})})

                     :else
                     ($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-3"}
                        (for [[module-id agents] filtered-modules]
                          ($ module-card {:key module-id
                                          :module-id module-id
                                          :agent-count (count agents)})))))))

          ;; Sidebar
          ($ :div {:class "space-y-4"}
             ($ quick-links)

             ;; Info card
             ($ :div {:class "card bg-base-100 border border-base-300"}
                ($ :div {:class "card-body"}
                   ($ :h2 {:class "card-title text-lg"} "About")
                   ($ :p {:class "text-sm text-base-content/70"}
                      "Agent-O-Rama is an end-to-end LLM agent platform for building, tracing, testing, and monitoring intelligent agents.")
                   ($ :div {:class "mt-4 text-xs text-base-content/50"}
                      ($ :div "Version: 0.8.0-SNAPSHOT")
                      ($ :div "Alt-Frontend (DaisyUI)")))))))))

(ns alt-frontend.routes.agents.index
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.components.ui :as ui]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

(defui view []
  (let [;; Search state
        [search-term set-search-term] (uix/use-state "")
        debounced-search (utils/use-debounced-value search-term 300)

        {:keys [data loading? error]}
        (queries/use-sente-query {:query-key [:agents]
                                  :sente-event [:agents/get-all]
                                  :refetch-interval-ms 2000})

        ;; Filter and sort agents
        filtered-sorted-agents
        (when data
          (let [search-lower (str/lower-case debounced-search)
                filtered (if (str/blank? debounced-search)
                           data
                           (filter (fn [agent]
                                     (let [module-name (str/lower-case (utils/url-decode (:module-id agent)))
                                           agent-name (str/lower-case (utils/url-decode (:agent-name agent)))]
                                       (or (str/includes? module-name search-lower)
                                           (str/includes? agent-name search-lower))))
                                   data))]
            ;; Sort by module name, then by agent name (with _ prefixed last)
            (sort-by
             (fn [agent]
               (let [module-name (utils/url-decode (:module-id agent))
                     agent-name (utils/url-decode (:agent-name agent))]
                 [module-name
                  (str/starts-with? agent-name "_")
                  agent-name]))
             filtered)))]

    ($ :div {:class "space-y-4"}
       ($ :h1 {:class "text-2xl font-bold"} "All Agents")

       ;; Search input
       ($ :div {:class "form-control"}
          ($ :input {:type "text"
                     :class "input input-bordered w-full"
                     :data-testid "input-search-agents"
                     :placeholder "Search by module or agent name..."
                     :value search-term
                     :onChange #(set-search-term (.. % -target -value))}))

       ;; Content
       (cond
         loading?
         ($ ui/loading-state {:message "Loading agents..."})

         error
         ($ ui/error-alert {:title "Error loading agents"
                            :message error})

         (empty? data)
         ($ ui/empty-state {:title "No agents found"
                            :description "No agent modules have been deployed yet."})

         (empty? filtered-sorted-agents)
         ($ ui/empty-state {:title "No matching agents"
                            :description "No agents match your search."})

         :else
         ($ ui/data-table
            {:columns [{:key :module-id
                        :header "Module"
                        :render (fn [row]
                                  (utils/url-decode (:module-id row)))}
                       {:key :agent-name
                        :header "Agent"
                        :render (fn [row]
                                  (utils/url-decode (:agent-name row)))}]
             :rows filtered-sorted-agents
             :row-key-fn (fn [row] (str (:module-id row) "/" (:agent-name row)))
             :on-row-click (fn [row]
                             (rfe/push-state :agent-detail
                                             {:module-id (utils/url-decode (:module-id row))
                                              :agent-name (utils/url-decode (:agent-name row))}))
             :empty-message "No modules deployed"})))))

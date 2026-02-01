(ns alt-frontend.routes.agents.$module-id.index
  "Module detail route showing agents, datasets, and evaluators for a module."
  (:require [uix.core :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

;; =============================================================================
;; ICONS (using Heroicons)
;; =============================================================================

(defui agents-icon []
  ($ icons/agent {:class "h-5 w-5"}))

(defui datasets-icon []
  ($ icons/dataset {:class "h-5 w-5"}))

(defui evaluators-icon []
  ($ icons/evaluator {:class "h-5 w-5"}))

;; =============================================================================
;; AGENTS TABLE
;; =============================================================================

(defui agents-table
  "Table of agents in the module."
  [{:keys [agents module-id]}]
  (if (empty? agents)
    ($ ui/empty-state
       {:title "No agents defined"
        :description "This module has no agents defined."})
    ($ ui/data-table
       {:columns [{:key :agent-name
                   :header "Agent Name"
                   :render (fn [row]
                             (utils/url-decode (:agent-name row)))}]
        :rows agents
        :row-key-fn :agent-name
        :row-testid-prefix "agent-row-"
        :on-row-click (fn [row]
                        (rfe/push-state :agent-detail
                                        {:module-id module-id
                                         :agent-name (:agent-name row)}))
        :empty-message "No agents defined in this module."})))

;; =============================================================================
;; DATASETS TABLE
;; =============================================================================

(defui datasets-table
  "Table of datasets in the module."
  [{:keys [datasets module-id]}]
  (if (empty? datasets)
    ($ ui/empty-state
       {:title "No datasets found"
        :description "Create a dataset to store examples for experiments."})
    ($ ui/data-table
       {:columns [{:key :name
                   :header "Dataset Name"}
                  {:key :description
                   :header "Description"
                   :render (fn [row]
                             (or (:description row) "—"))}]
        :rows datasets
        :row-key-fn :dataset-id
        :on-row-click (fn [row]
                        (rfe/push-state :examples
                                        {:module-id module-id
                                         :dataset-id (:dataset-id row)}))
        :empty-message "No datasets found for this module."})))

;; =============================================================================
;; EVALUATORS TABLE
;; =============================================================================

(defui evaluators-table
  "Table of evaluators in the module."
  [{:keys [evaluators module-id]}]
  (if (empty? evaluators)
    ($ ui/empty-state
       {:title "No evaluators created"
        :description "Create evaluators to run experiments on datasets."})
    ($ ui/data-table
       {:columns [{:key :name
                   :header "Evaluator Name"}
                  {:key :builder-name
                   :header "Builder"
                   :render (fn [row]
                             ($ :code {:class "font-mono text-xs"}
                                (:builder-name row)))}]
        :rows evaluators
        :row-key-fn :name
        :row-testid-prefix "evaluator-row-"
        :on-row-click (fn [_row]
                        (rfe/push-state :evaluations
                                        {:module-id module-id}))
        :empty-message "No evaluators created for this module."})))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Module detail view showing agents, datasets, and evaluators."
  [{:keys [module-id]}]
  (let [decoded-module-id (utils/url-decode module-id)

        ;; Fetch data for all three sections
        agents-query (queries/use-sente-query
                      {:query-key [:module-agents module-id]
                       :sente-event [:agents/get-for-module {:module-id module-id}]})

        datasets-query (queries/use-sente-query
                        {:query-key [:datasets module-id]
                         :sente-event [:datasets/get-all {:module-id module-id}]})

        evaluators-query (queries/use-sente-query
                          {:query-key [:evaluator-instances-list module-id]
                           :sente-event [:evaluators/get-all-instances {:module-id module-id}]})]

    ($ :div {:class "space-y-6"}
       ;; Page title
       ($ :h1 {:class "text-2xl font-bold"}
          (str "Module: " decoded-module-id))

       ;; Grid of cards
       ($ :div {:class "grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6"}

          ;; Agents card
          ($ ui/section-card
             {:title "Agents"
              :icon ($ agents-icon)
              :loading? (:loading? agents-query)
              :error (:error agents-query)}
             ($ agents-table
                {:agents (:data agents-query)
                 :module-id module-id}))

          ;; Datasets card
          ($ ui/section-card
             {:title "Datasets"
              :icon ($ datasets-icon)
              :loading? (:loading? datasets-query)
              :error (:error datasets-query)}
             ($ datasets-table
                {:datasets (get-in datasets-query [:data :datasets])
                 :module-id module-id}))

          ;; Evaluators card
          ($ ui/section-card
             {:title "Evaluators"
              :icon ($ evaluators-icon)
              :loading? (:loading? evaluators-query)
              :error (:error evaluators-query)}
             ($ evaluators-table
                {:evaluators (get-in evaluators-query [:data :items])
                 :module-id module-id}))))))

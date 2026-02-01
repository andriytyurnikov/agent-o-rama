(ns alt-frontend.routes.agents.$module-id.datasets.$dataset-id.index
  "Dataset detail route showing overview and navigation to examples/experiments."
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
;; NAVIGATION CARDS
;; =============================================================================

(defui nav-cards
  "Quick navigation cards for dataset features"
  [{:keys [module-id dataset-id]}]
  ($ :div {:class "grid grid-cols-1 md:grid-cols-3 gap-4"}
     ($ ui/nav-card {:title "Examples"
                     :description "View and manage dataset examples"
                     :on-click #(rfe/push-state :examples {:module-id module-id :dataset-id dataset-id})})
     ($ ui/nav-card {:title "Experiments"
                     :description "Run and view experiments"
                     :on-click #(rfe/push-state :experiments {:module-id module-id :dataset-id dataset-id})})
     ($ ui/nav-card {:title "Comparative"
                     :description "Compare agent behaviors"
                     :on-click #(rfe/push-state :comparative-experiments {:module-id module-id :dataset-id dataset-id})})))

;; =============================================================================
;; REMOTE DATASET INFO
;; =============================================================================

(defui remote-dataset-info
  "Info display for remote datasets"
  [{:keys [dataset]}]
  (let [host (:remote-host dataset)
        port (:remote-port dataset)
        module (:module-name dataset)]
    ($ ui/info-alert
       {:title "Remote Dataset"
        :message ($ :div {:class "space-y-2"}
                    ($ :p "This dataset's examples are stored on a remote cluster. You cannot view or edit examples directly, but you can run experiments against this data.")
                    ($ :div {:class "font-mono text-sm mt-2"}
                       ($ :div "Module: " module)
                       (when host ($ :div "Host: " host))
                       (when port ($ :div "Port: " port))))})))

;; =============================================================================
;; SCHEMA DISPLAY
;; =============================================================================

(defui schema-display
  "Display input/output JSON schemas"
  [{:keys [input-schema output-schema]}]
  (when (or input-schema output-schema)
    ($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
       ;; Input Schema
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text font-medium"} "Input Schema"))
          (if input-schema
            ($ :pre {:class "bg-base-200 p-4 rounded-lg text-sm font-mono overflow-x-auto max-h-48"}
               (json/pretty-json input-schema))
            ($ :div {:class "bg-base-200 p-4 rounded-lg text-base-content/50 italic"}
               "No schema defined")))

       ;; Output Schema
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text font-medium"} "Output Schema"))
          (if output-schema
            ($ :pre {:class "bg-base-200 p-4 rounded-lg text-sm font-mono overflow-x-auto max-h-48"}
               (json/pretty-json output-schema))
            ($ :div {:class "bg-base-200 p-4 rounded-lg text-base-content/50 italic"}
               "No schema defined"))))))

;; =============================================================================
;; DATASET PROPERTIES
;; =============================================================================

(defui dataset-properties
  "Display dataset properties in a card"
  [{:keys [dataset]}]
  (let [is-remote? (boolean (:module-name dataset))]
    ($ ui/card {:title "Dataset Properties"}
       ($ :div {:class "space-y-4"}
          ;; Basic info
          ($ :div {:class "grid grid-cols-2 md:grid-cols-4 gap-4"}
             ($ :div
                ($ :div {:class "text-sm text-base-content/60"} "Created")
                ($ :div {:class "font-medium"} (time/format-timestamp (:created-at dataset))))
             ($ :div
                ($ :div {:class "text-sm text-base-content/60"} "Modified")
                ($ :div {:class "font-medium"} (time/format-timestamp (:modified-at dataset))))
             (when-not is-remote?
               ($ :<>
                  ($ :div
                     ($ :div {:class "text-sm text-base-content/60"} "Dataset ID")
                     ($ :div {:class "font-mono text-sm truncate"
                              :title (str (:dataset-id dataset))}
                        (str (:dataset-id dataset)))))))

          ;; Description
          (when (seq (:description dataset))
            ($ :div
               ($ :div {:class "text-sm text-base-content/60 mb-1"} "Description")
               ($ :div (:description dataset))))

          ;; Remote info
          (when is-remote?
            ($ remote-dataset-info {:dataset dataset}))

          ;; Schemas
          (when-not is-remote?
            ($ schema-display {:input-schema (:input-json-schema dataset)
                               :output-schema (:output-json-schema dataset)}))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Dataset detail view showing properties and navigation."
  [{:keys [module-id dataset-id]}]
  (let [decoded-module-id (utils/url-decode module-id)
        decoded-dataset-id (utils/url-decode dataset-id)

        ;; Fetch dataset properties
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:dataset-props module-id dataset-id]
          :sente-event [:datasets/get-props {:module-id module-id
                                             :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})

        dataset data
        is-remote? (boolean (:module-name dataset))]

    (cond
      loading?
      ($ ui/loading-state {:message "Loading dataset..."})

      error
      ($ ui/error-alert {:message (str "Error loading dataset: " error)})

      (not dataset)
      ($ ui/empty-state
         {:title "Dataset not found"
          :description "The requested dataset could not be found."})

      :else
      ($ :div {:class "space-y-6"}
         ;; Header
         ($ :div {:class "flex items-center justify-between"}
            ($ :div
               ($ :h1 {:class "text-2xl font-bold"} (or (:name dataset) decoded-dataset-id))
               ($ :p {:class "text-base-content/60"}
                  (str "Module: " decoded-module-id)))
            ;; View examples button
            ($ :button {:class "btn btn-primary"
                        :data-testid "btn-view-examples"
                        :onClick #(rfe/push-state :examples {:module-id module-id :dataset-id dataset-id})}
               "View Examples →"))

         ;; Remote dataset banner
         (when is-remote?
           ($ :div {:class "alert alert-info"}
              ($ :span "This is a remote dataset. Examples are stored on another cluster.")))

         ;; Navigation cards
         ($ nav-cards {:module-id module-id :dataset-id dataset-id})

         ;; Dataset properties
         ($ dataset-properties {:dataset dataset})))))

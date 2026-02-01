(ns alt-frontend.routes.agents.$module-id.datasets.index
  "Datasets list route with CRUD operations."
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.forms :as forms]
            [alt-frontend.lib.state :as state]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.lib.time :as time]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.modal :as modal]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

;; =============================================================================
;; CREATE DATASET FORM
;; =============================================================================

(forms/reg-form :create-dataset
                {:fields {:name ""
                          :description ""
                          :input-schema ""
                          :output-schema ""}
                 :validators {:name [forms/required]}
                 :on-submit
                 (fn [values {:keys [set-error! set-submitting! clear!]}]
                   (let [module-id (get-in @state/app-state [:ui :create-dataset-module-id])]
                     (sente/request!
                      [:datasets/create {:module-id module-id
                                         :name (:name values)
                                         :description (:description values)
                                         :input-schema (:input-schema values)
                                         :output-schema (:output-schema values)}]
                      10000
                      (fn [reply]
                        (if (:success reply)
                          (do
                            (clear!)
                            (state/dispatch [:modal/hide])
              ;; Invalidate datasets query to refetch
                            (state/dispatch [:query/invalidate {:query-key-pattern [:datasets module-id]}]))
                          (set-error! (or (:error reply) "Failed to create dataset")))))))})

(defui create-dataset-modal
  "Modal form for creating a new dataset"
  [{:keys [module-id]}]
  (let [form-id :create-dataset
        _ (uix/use-effect
           (fn []
             (state/dispatch [:db/set-value [:ui :create-dataset-module-id] module-id])
             (state/dispatch [:form/initialize form-id])
             (fn []
               (state/dispatch [:form/clear form-id])))
           [form-id module-id])
        form (forms/use-form form-id)
        name-field (forms/use-field form-id :name)
        desc-field (forms/use-field form-id :description)
        input-schema-field (forms/use-field form-id :input-schema)
        output-schema-field (forms/use-field form-id :output-schema)

        handle-submit (fn [e]
                        (.preventDefault e)
                        ((:submit! form)))]

    ($ :form {:onSubmit handle-submit
              :class "space-y-4"}
       ;; Name field
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Name")
             ($ :span {:class "label-text-alt text-error"} "*"))
          ($ :input
             {:type "text"
              :class (str "input input-bordered "
                          (when (:error name-field) "input-error"))
              :data-testid "input-dataset-name"
              :placeholder "Enter dataset name"
              :value (:value name-field)
              :onChange (:on-change name-field)
              :onBlur (:on-blur name-field)
              :disabled (:submitting? form)})
          (when (:error name-field)
            ($ :label {:class "label"}
               ($ :span {:class "label-text-alt text-error"} (:error name-field)))))

       ;; Description field
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Description"))
          ($ :textarea
             {:class "textarea textarea-bordered"
              :placeholder "Optional description"
              :rows 2
              :value (:value desc-field)
              :onChange (:on-change desc-field)
              :disabled (:submitting? form)}))

       ;; Input schema field
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Input JSON Schema")
             ($ :span {:class "label-text-alt"} "optional"))
          ($ :textarea
             {:class "textarea textarea-bordered font-mono text-sm"
              :placeholder "{\"type\": \"object\", ...}"
              :rows 3
              :value (:value input-schema-field)
              :onChange (:on-change input-schema-field)
              :disabled (:submitting? form)}))

       ;; Output schema field
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Output JSON Schema")
             ($ :span {:class "label-text-alt"} "optional"))
          ($ :textarea
             {:class "textarea textarea-bordered font-mono text-sm"
              :placeholder "{\"type\": \"object\", ...}"
              :rows 3
              :value (:value output-schema-field)
              :onChange (:on-change output-schema-field)
              :disabled (:submitting? form)}))

       ;; Form error
       (when (:form-error form)
         ($ :div {:role "alert"
                  :class "alert alert-error"}
            ($ :span (:form-error form))))

       ;; Actions
       ($ modal/modal-actions
          ($ :button {:type "button"
                      :class "btn"
                      :data-testid "btn-cancel"
                      :onClick #(state/dispatch [:modal/hide])}
             "Cancel")
          ($ :button {:type "submit"
                      :class (str "btn btn-primary "
                                  (when (:submitting? form) "loading"))
                      :data-testid "btn-submit-create-dataset"
                      :disabled (or (not (:valid? form)) (:submitting? form))}
             (if (:submitting? form) "Creating..." "Create Dataset"))))))

;; =============================================================================
;; EDIT DATASET FORM
;; =============================================================================

(declare edit-dataset-modal)

(defn show-edit-modal!
  "Show the edit dataset modal"
  [{:keys [module-id dataset-id name description]}]
  (state/dispatch [:db/set-value [:ui :edit-dataset] {:module-id module-id
                                                      :dataset-id dataset-id
                                                      :original-name name
                                                      :original-desc description}])
  (state/dispatch
   [:modal/show
    {:title "Edit Dataset"
     :content ($ edit-dataset-modal {:module-id module-id
                                     :dataset-id dataset-id
                                     :initial-name name
                                     :initial-description description})}]))

(forms/reg-form :edit-dataset
                {:fields (fn [props]
                           {:name (or (:initial-name props) "")
                            :description (or (:initial-description props) "")})
                 :validators {:name [forms/required]}
                 :on-submit
                 (fn [values {:keys [set-error! clear!]}]
                   (let [edit-ctx (get-in @state/app-state [:ui :edit-dataset])
                         module-id (:module-id edit-ctx)
                         dataset-id (:dataset-id edit-ctx)]
       ;; Update name if changed
                     (when (not= (:name values) (:original-name edit-ctx))
                       (sente/request!
                        [:datasets/set-name {:module-id module-id
                                             :dataset-id dataset-id
                                             :name (:name values)}]
                        5000
                        (fn [_reply] nil)))
       ;; Update description if changed
                     (when (not= (:description values) (:original-desc edit-ctx))
                       (sente/request!
                        [:datasets/set-description {:module-id module-id
                                                    :dataset-id dataset-id
                                                    :description (:description values)}]
                        5000
                        (fn [_reply] nil)))
       ;; Close modal and refresh
                     (clear!)
                     (state/dispatch [:modal/hide])
                     (state/dispatch [:query/invalidate {:query-key-pattern [:datasets module-id]}])))})

(defui edit-dataset-modal
  "Modal form for editing a dataset"
  [{:keys [module-id dataset-id initial-name initial-description]}]
  (let [form-id :edit-dataset
        _ (uix/use-effect
           (fn []
             (state/dispatch [:form/initialize form-id {:initial-name initial-name
                                                        :initial-description initial-description}])
             (fn []
               (state/dispatch [:form/clear form-id])))
           [form-id initial-name initial-description])
        form (forms/use-form form-id)
        name-field (forms/use-field form-id :name)
        desc-field (forms/use-field form-id :description)

        handle-submit (fn [e]
                        (.preventDefault e)
                        ((:submit! form)))]

    ($ :form {:onSubmit handle-submit
              :class "space-y-4"}
       ;; Name field
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Name")
             ($ :span {:class "label-text-alt text-error"} "*"))
          ($ :input
             {:type "text"
              :class (str "input input-bordered "
                          (when (:error name-field) "input-error"))
              :value (:value name-field)
              :onChange (:on-change name-field)
              :onBlur (:on-blur name-field)
              :disabled (:submitting? form)})
          (when (:error name-field)
            ($ :label {:class "label"}
               ($ :span {:class "label-text-alt text-error"} (:error name-field)))))

       ;; Description field
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Description"))
          ($ :textarea
             {:class "textarea textarea-bordered"
              :rows 3
              :value (:value desc-field)
              :onChange (:on-change desc-field)
              :disabled (:submitting? form)}))

       ;; Form error
       (when (:form-error form)
         ($ :div {:role "alert"
                  :class "alert alert-error"}
            ($ :span (:form-error form))))

       ;; Actions
       ($ modal/modal-actions
          ($ :button {:type "button"
                      :class "btn"
                      :onClick #(state/dispatch [:modal/hide])}
             "Cancel")
          ($ :button {:type "submit"
                      :class (str "btn btn-primary "
                                  (when (:submitting? form) "loading"))
                      :disabled (or (not (:valid? form)) (:submitting? form))}
             (if (:submitting? form) "Saving..." "Save Changes"))))))

;; =============================================================================
;; DELETE DATASET
;; =============================================================================

(defn delete-dataset!
  "Delete a dataset with confirmation"
  [module-id dataset-id dataset-name]
  (modal/show-confirm!
   {:title "Delete Dataset"
    :message (str "Are you sure you want to delete \"" dataset-name "\"? This action cannot be undone.")
    :confirm-text "Delete"
    :confirm-variant :error
    :on-confirm (fn []
                  (sente/request!
                   [:datasets/delete {:module-id module-id :dataset-id dataset-id}]
                   10000
                   (fn [reply]
                     (if (:success reply)
                       (state/dispatch [:query/invalidate {:query-key-pattern [:datasets module-id]}])
                       (js/alert (str "Error deleting dataset: " (:error reply)))))))}))

;; =============================================================================
;; DATASETS TABLE
;; =============================================================================

(defui datasets-table
  "Table of datasets with actions"
  [{:keys [datasets module-id loading? error has-more? is-fetching-more? load-more]}]
  (cond
    (and loading? (empty? datasets))
    ($ ui/loading-state {:message "Loading datasets..."})

    error
    ($ ui/error-alert {:message (str "Error loading datasets: " error)})

    (empty? datasets)
    ($ ui/empty-state
       {:title "No datasets yet"
        :description "Create your first dataset to get started with examples and experiments."})

    :else
    ($ :div {:class "overflow-x-auto"}
       ($ :table {:class "table table-zebra"
                  :data-testid "data-table"}
          ($ :thead
             ($ :tr
                ($ :th "Name")
                ($ :th "Description")
                ($ :th "Created")
                ($ :th "Modified")
                ($ :th {:class "w-32"} "Actions")))
          ($ :tbody
             (for [dataset datasets
                   :let [dsid (:dataset-id dataset)
                         name (:name dataset)
                         desc (:description dataset)
                         is-remote? (:remote? dataset)]]
               ($ :tr {:key (str dsid)
                       :data-testid (str "dataset-row-" dsid)
                       :class (str "hover cursor-pointer "
                                   (when is-remote? "bg-purple-50"))
                       :onClick #(rfe/push-state :examples {:module-id module-id
                                                            :dataset-id (str dsid)})}
                  ;; Name column
                  ($ :td
                     (if is-remote?
                       ($ :div {:class "flex flex-col gap-1"}
                          ($ :span {:class "badge badge-sm badge-secondary"} "REMOTE")
                          ($ :span {:class "text-sm"} name))
                       ($ :span {:class "font-medium text-primary"} name)))

                  ;; Description column
                  ($ :td {:class "max-w-xs"}
                     (if is-remote?
                       ($ :span {:class "font-mono text-sm text-purple-600"}
                          (let [host (:remote-host dataset)
                                port (:remote-port dataset)
                                module (:remote-module-name dataset)]
                            (cond
                              (and host port) (str host ":" port " " module)
                              host (str host " " module)
                              :else module)))
                       (if (seq desc)
                         ($ :span {:class "truncate"
                                   :title desc} desc)
                         ($ :span {:class "text-base-content/50 italic"} "—"))))

                  ;; Created column
                  ($ :td {:class "text-sm"
                          :title (time/format-timestamp (:created-at dataset))}
                     (when-not is-remote?
                       (time/format-relative-time (:created-at dataset))))

                  ;; Modified column
                  ($ :td {:class "text-sm"
                          :title (time/format-timestamp (:modified-at dataset))}
                     (when-not is-remote?
                       (time/format-relative-time (:modified-at dataset))))

                  ;; Actions column
                  ($ :td
                     ($ :div {:class "flex items-center gap-2"}
                        (when-not is-remote?
                          ($ :button {:class "btn btn-ghost btn-xs"
                                      :data-testid "btn-edit-dataset"
                                      :onClick (fn [e]
                                                 (.stopPropagation e)
                                                 (show-edit-modal! {:module-id module-id
                                                                    :dataset-id dsid
                                                                    :name name
                                                                    :description desc}))}
                             "Edit"))
                        ($ :button {:class "btn btn-ghost btn-xs text-error"
                                    :data-testid "btn-delete-dataset"
                                    :onClick (fn [e]
                                               (.stopPropagation e)
                                               (delete-dataset! module-id dsid name))}
                           "Delete"))))))

          ;; Load more footer
          (when has-more?
            ($ :tfoot {:class "bg-base-200"}
               ($ :tr {:class (when-not is-fetching-more? "hover cursor-pointer")
                       :onClick (when-not is-fetching-more? load-more)}
                  ($ :td {:colSpan 5 :class "text-center py-3"}
                     (if is-fetching-more?
                       ($ :span {:class "loading loading-spinner loading-sm"})
                       "Load more...")))))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Datasets list view for a module"
  [{:keys [module-id]}]
  (let [decoded-module-id (utils/url-decode module-id)
        [search-term set-search-term] (uix/use-state "")

        ;; Debounce search
        [debounced-search set-debounced-search] (uix/use-state "")
        _ (uix/use-effect
           (fn []
             (let [timer (js/setTimeout #(set-debounced-search search-term) 300)]
               (fn [] (js/clearTimeout timer))))
           [search-term])

        ;; Fetch datasets with pagination
        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-paginated-query
         {:query-key [:datasets module-id debounced-search]
          :sente-event [:datasets/get-all
                        {:module-id module-id
                         :filters (when-not (str/blank? debounced-search)
                                    {:search-string debounced-search})}]
          :page-size 25
          :enabled? (boolean module-id)})]

    ($ :div {:class "space-y-6"}
       ;; Header
       ($ :div {:class "flex items-center justify-between"}
          ($ :div {:class "flex items-center gap-4"}
             ($ :h1 {:class "text-2xl font-bold"} "Datasets")
             ;; Search input
             ($ :div {:class "form-control"}
                ($ :input
                   {:type "text"
                    :class "input input-bordered input-sm w-64"
                    :placeholder "Search datasets..."
                    :value search-term
                    :onChange #(set-search-term (.. % -target -value))})))

          ;; Create button
          ($ :button {:class "btn btn-primary"
                      :data-testid "btn-create-dataset"
                      :onClick #(state/dispatch
                                 [:modal/show
                                  {:title "Create Dataset"
                                   :content ($ create-dataset-modal {:module-id module-id})}])}
             "+ Create Dataset"))

       ;; Module context
       ($ :p {:class "text-base-content/60"} (str "Module: " decoded-module-id))

       ;; Datasets table
       ($ ui/card
          ($ datasets-table
             {:datasets data
              :module-id module-id
              :loading? isLoading
              :error error
              :has-more? hasMore
              :is-fetching-more? isFetchingMore
              :load-more loadMore})))))

(ns alt-frontend.routes.agents.$module-id.datasets.$dataset-id.examples
  "Dataset examples route with paginated list and CRUD operations."
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.forms :as forms]
            [alt-frontend.lib.state :as state]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.lib.time :as time]
            [alt-frontend.lib.json :as json]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.modal :as modal]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

;; =============================================================================
;; ADD EXAMPLE FORM
;; =============================================================================

(declare add-example-modal)

(forms/reg-form :add-example
                {:fields {:input ""
                          :reference-output ""
                          :tags ""}
                 :validators {:input [forms/required forms/valid-json]}
                 :on-submit
                 (fn [values {:keys [set-error! clear!]}]
                   (let [ctx (get-in @state/app-state [:ui :add-example-ctx])
                         module-id (:module-id ctx)
                         dataset-id (:dataset-id ctx)
                         snapshot-name (:snapshot-name ctx)
                         parsed-input (try (js->clj (js/JSON.parse (:input values)) :keywordize-keys true)
                                           (catch js/Error _ nil))
                         parsed-output (when-not (str/blank? (:reference-output values))
                                         (try (js->clj (js/JSON.parse (:reference-output values)) :keywordize-keys true)
                                              (catch js/Error _ nil)))
                         tags-vec (when-not (str/blank? (:tags values))
                                    (vec (map str/trim (str/split (:tags values) #","))))]
                     (sente/request!
                      [:datasets/add-example {:module-id module-id
                                              :dataset-id dataset-id
                                              :snapshot-name snapshot-name
                                              :input parsed-input
                                              :output parsed-output
                                              :tags (or tags-vec [])}]
                      10000
                      (fn [reply]
                        (if (:success reply)
                          (do
                            (clear!)
                            (state/dispatch [:modal/hide])
                            (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}]))
                          (set-error! (or (:error reply) "Failed to add example")))))))})

(defn show-add-example-modal!
  "Show the add example modal"
  [{:keys [module-id dataset-id snapshot-name]}]
  (state/dispatch [:db/set-value [:ui :add-example-ctx]
                   {:module-id module-id
                    :dataset-id dataset-id
                    :snapshot-name snapshot-name}])
  (state/dispatch
   [:modal/show
    {:title "Add Example"
     :size :lg
     :content ($ add-example-modal)}]))

(defui add-example-modal
  "Modal form for adding a new example"
  []
  (let [form-id :add-example
        _ (uix/use-effect
           (fn []
             (state/dispatch [:form/initialize form-id])
             (fn []
               (state/dispatch [:form/clear form-id])))
           [form-id])
        form (forms/use-form form-id)
        input-field (forms/use-field form-id :input)
        output-field (forms/use-field form-id :reference-output)
        tags-field (forms/use-field form-id :tags)

        handle-submit (fn [e]
                        (.preventDefault e)
                        ((:submit! form)))]

    ($ :form {:onSubmit handle-submit
              :class "space-y-4"}
       ;; Input field
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Input (JSON)")
             ($ :span {:class "label-text-alt text-error"} "*"))
          ($ :textarea
             {:class (str "textarea textarea-bordered font-mono text-sm "
                          (when (:error input-field) "textarea-error"))
              :data-testid "input-example-json"
              :placeholder "{\"key\": \"value\"}"
              :rows 6
              :value (:value input-field)
              :onChange (:on-change input-field)
              :onBlur (:on-blur input-field)
              :disabled (:submitting? form)})
          (when (:error input-field)
            ($ :label {:class "label"}
               ($ :span {:class "label-text-alt text-error"} (:error input-field)))))

       ;; Reference output field
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Reference Output (JSON)")
             ($ :span {:class "label-text-alt"} "optional"))
          ($ :textarea
             {:class (str "textarea textarea-bordered font-mono text-sm "
                          (when (:error output-field) "textarea-error"))
              :placeholder "{\"result\": \"expected\"}"
              :rows 4
              :value (:value output-field)
              :onChange (:on-change output-field)
              :onBlur (:on-blur output-field)
              :disabled (:submitting? form)})
          (when (:error output-field)
            ($ :label {:class "label"}
               ($ :span {:class "label-text-alt text-error"} (:error output-field)))))

       ;; Tags field
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Tags")
             ($ :span {:class "label-text-alt"} "comma separated"))
          ($ :input
             {:type "text"
              :class "input input-bordered"
              :placeholder "tag1, tag2, tag3"
              :value (:value tags-field)
              :onChange (:on-change tags-field)
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
                      :data-testid "btn-submit-add-example"
                      :disabled (or (not (:valid? form)) (:submitting? form))}
             (if (:submitting? form) "Adding..." "Add Example"))))))

;; =============================================================================
;; DELETE EXAMPLE
;; =============================================================================

(defn delete-example!
  "Delete an example with confirmation"
  [module-id dataset-id snapshot-name example-id]
  (modal/show-confirm!
   {:title "Delete Example"
    :message "Are you sure you want to delete this example? This action cannot be undone."
    :confirm-text "Delete"
    :confirm-variant :error
    :on-confirm (fn []
                  (sente/request!
                   [:datasets/delete-example {:module-id module-id
                                              :dataset-id dataset-id
                                              :snapshot-name snapshot-name
                                              :example-id example-id}]
                   10000
                   (fn [reply]
                     (if (:success reply)
                       (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                       (js/alert (str "Error deleting example: " (:error reply)))))))}))

;; =============================================================================
;; EXAMPLE DETAIL MODAL
;; =============================================================================

(defui example-detail-modal
  "Modal showing full example details"
  [{:keys [example module-id dataset-id snapshot-name is-read-only?]}]
  (let [example-id (:id example)]
    ($ :div {:class "space-y-6"}
       ;; Input section
       ($ :div
          ($ :label {:class "text-sm font-medium text-base-content/60"} "Input")
          ($ :pre {:class "bg-base-200 p-4 rounded-lg text-sm font-mono overflow-auto max-h-64 mt-1"}
             (json/pretty-json (:input example))))

       ;; Reference output section
       ($ :div
          ($ :label {:class "text-sm font-medium text-base-content/60"} "Reference Output")
          (if (:reference-output example)
            ($ :pre {:class "bg-base-200 p-4 rounded-lg text-sm font-mono overflow-auto max-h-64 mt-1"}
               (json/pretty-json (:reference-output example)))
            ($ :div {:class "bg-base-200 p-4 rounded-lg text-base-content/50 italic mt-1"}
               "No reference output")))

       ;; Tags section
       ($ :div
          ($ :label {:class "text-sm font-medium text-base-content/60"} "Tags")
          (if (seq (:tags example))
            ($ :div {:class "flex flex-wrap gap-2 mt-1"}
               (for [tag (sort (map name (:tags example)))]
                 ($ :span {:key tag :class "badge badge-primary"} tag)))
            ($ :div {:class "text-base-content/50 italic mt-1"} "No tags")))

       ;; Metadata section
       ($ :div {:class "grid grid-cols-2 gap-4"}
          ($ :div
             ($ :label {:class "text-sm font-medium text-base-content/60"} "Created")
             ($ :div {:class "mt-1"} (time/format-timestamp (:created-at example))))
          ($ :div
             ($ :label {:class "text-sm font-medium text-base-content/60"} "Modified")
             ($ :div {:class "mt-1"} (time/format-timestamp (:modified-at example)))))

       ;; Source section
       (when (:source-string example)
         ($ :div
            ($ :label {:class "text-sm font-medium text-base-content/60"} "Source")
            ($ :div {:class "mt-1 text-sm"} (:source-string example))))

       ;; Example ID
       ($ :div
          ($ :label {:class "text-sm font-medium text-base-content/60"} "Example ID")
          ($ :code {:class "text-xs bg-base-200 px-2 py-1 rounded mt-1 block"}
             (str example-id)))

       ;; Actions
       (when-not is-read-only?
         ($ modal/modal-actions
            ($ :button {:class "btn btn-error btn-outline"
                        :onClick (fn []
                                   (state/dispatch [:modal/hide])
                                   (delete-example! module-id dataset-id snapshot-name example-id))}
               "Delete Example"))))))

;; =============================================================================
;; EXAMPLES TABLE
;; =============================================================================

(defui examples-table
  "Table of examples with actions"
  [{:keys [examples module-id dataset-id snapshot-name loading? error
           has-more? is-fetching-more? load-more is-read-only?
           selected-ids set-selected-ids]}]
  (let [all-ids (set (map :id examples))
        all-selected? (and (seq all-ids) (= all-ids selected-ids))]

    (cond
      (and loading? (empty? examples))
      ($ ui/loading-state {:message "Loading examples..."})

      error
      ($ ui/error-alert {:message (str "Error loading examples: " error)})

      (empty? examples)
      ($ ui/empty-state
         {:title "No examples yet"
          :description "Add your first example to get started with experiments."})

      :else
      ($ :div {:class "overflow-x-auto"}
         ($ :table {:class "table table-zebra"
                    :data-testid "data-table"}
            ($ :thead
               ($ :tr
                  ;; Checkbox header
                  ($ :th {:class "w-12"}
                     ($ :input {:type "checkbox"
                                :class "checkbox checkbox-sm"
                                :checked all-selected?
                                :onChange #(set-selected-ids
                                            (if all-selected? #{} all-ids))}))
                  ($ :th "Input")
                  ($ :th "Reference Output")
                  ($ :th "Tags")
                  ($ :th "Created")
                  ($ :th {:class "w-24"} "Actions")))
            ($ :tbody
               (for [example examples
                     :let [example-id (:id example)
                           is-selected? (contains? selected-ids example-id)]]
                 ($ :tr {:key (str example-id)
                         :data-testid (str "example-row-" example-id)
                         :class (str "hover cursor-pointer "
                                     (when is-selected? "bg-primary/10"))
                         :onClick #(state/dispatch
                                    [:modal/show
                                     {:title "Example Details"
                                      :size :lg
                                      :content ($ example-detail-modal
                                                  {:example example
                                                   :module-id module-id
                                                   :dataset-id dataset-id
                                                   :snapshot-name snapshot-name
                                                   :is-read-only? is-read-only?})}])}

                    ;; Checkbox
                    ($ :td {:onClick #(.stopPropagation %)}
                       ($ :input {:type "checkbox"
                                  :class "checkbox checkbox-sm"
                                  :checked is-selected?
                                  :onChange #(set-selected-ids
                                              (if is-selected?
                                                (disj selected-ids example-id)
                                                (conj selected-ids example-id)))}))

                    ;; Input column
                    ($ :td {:class "max-w-xs"}
                       ($ :div {:class "truncate font-mono text-xs"
                                :title (json/pretty-json (:input example))}
                          (json/truncate-json (:input example) 60)))

                    ;; Reference output column
                    ($ :td {:class "max-w-xs"}
                       (if (:reference-output example)
                         ($ :div {:class "truncate font-mono text-xs"
                                  :title (json/pretty-json (:reference-output example))}
                            (json/truncate-json (:reference-output example) 60))
                         ($ :span {:class "text-base-content/50 italic"} "—")))

                    ;; Tags column
                    ($ :td
                       (if (seq (:tags example))
                         ($ :div {:class "flex flex-wrap gap-1"}
                            (for [tag (take 3 (sort (map name (:tags example))))]
                              ($ :span {:key tag :class "badge badge-sm badge-outline"} tag))
                            (when (> (count (:tags example)) 3)
                              ($ :span {:class "badge badge-sm badge-ghost"}
                                 (str "+" (- (count (:tags example)) 3)))))
                         ($ :span {:class "text-base-content/50 italic text-xs"} "no tags")))

                    ;; Created column
                    ($ :td {:class "text-sm"
                            :title (time/format-timestamp (:created-at example))}
                       (time/format-relative-time (:created-at example)))

                    ;; Actions column
                    ($ :td {:onClick #(.stopPropagation %)}
                       (when-not is-read-only?
                         ($ :button {:class "btn btn-ghost btn-xs text-error"
                                     :data-testid "btn-delete-example"
                                     :onClick #(delete-example! module-id dataset-id
                                                                snapshot-name example-id)}
                            "Delete"))))))

            ;; Load more footer
            (when has-more?
              ($ :tfoot {:class "bg-base-200"}
                 ($ :tr {:class (when-not is-fetching-more? "hover cursor-pointer")
                         :onClick (when-not is-fetching-more? load-more)}
                    ($ :td {:colSpan 6 :class "text-center py-3"}
                       (if is-fetching-more?
                         ($ :span {:class "loading loading-spinner loading-sm"})
                         "Load more..."))))))))))

;; =============================================================================
;; SNAPSHOT SELECTOR
;; =============================================================================

(defui snapshot-selector
  "Dropdown for selecting dataset snapshots"
  [{:keys [module-id dataset-id selected-snapshot on-select]}]
  (let [{:keys [data loading?]}
        (queries/use-sente-query
         {:query-key [:snapshot-names module-id dataset-id]
          :sente-event [:datasets/get-snapshot-names {:module-id module-id
                                                      :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})

        snapshots (or (:snapshot-names data) [])]

    ($ :select {:class "select select-bordered select-sm w-48"
                :value (or selected-snapshot "")
                :disabled loading?
                :onChange #(on-select (let [v (.. % -target -value)]
                                        (when-not (str/blank? v) v)))}
       ($ :option {:value ""} "Live (editable)")
       (for [snapshot snapshots]
         ($ :option {:key snapshot :value snapshot} snapshot)))))

;; =============================================================================
;; BULK ACTIONS BAR
;; =============================================================================

(defui bulk-actions-bar
  "Action bar when examples are selected"
  [{:keys [selected-count module-id dataset-id snapshot-name on-clear is-read-only?]}]
  (when (> selected-count 0)
    ($ :div {:class "bg-primary/10 border-b border-primary/20 px-4 py-2 flex items-center justify-between"}
       ($ :span {:class "text-sm font-medium"}
          (str selected-count " example" (when (> selected-count 1) "s") " selected"))
       ($ :div {:class "flex items-center gap-2"}
          ($ :button {:class "btn btn-ghost btn-sm"
                      :onClick on-clear}
             "Clear selection")
          (when-not is-read-only?
            ($ :button {:class "btn btn-error btn-sm btn-outline"
                        :onClick #(js/alert "Bulk delete coming soon")}
               "Delete selected"))))))

;; =============================================================================
;; REMOTE DATASET VIEW
;; =============================================================================

(defui remote-dataset-view
  "View shown for remote datasets (cannot view examples)"
  [{:keys [dataset]}]
  ($ :div {:class "flex items-center justify-center min-h-64"}
     ($ ui/info-alert
        {:title "Remote Dataset"
         :message ($ :div {:class "space-y-2"}
                     ($ :p "This dataset's examples are stored on a remote cluster and cannot be viewed or edited here.")
                     ($ :p "You can run experiments with your local agents against this remote data by navigating to the Experiments tab."))})))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Dataset examples view with paginated list and search."
  [{:keys [module-id dataset-id]}]
  (let [decoded-module-id (utils/url-decode module-id)
        decoded-dataset-id (utils/url-decode dataset-id)

        ;; Local state
        [search-term set-search-term] (uix/use-state "")
        [selected-snapshot set-selected-snapshot] (uix/use-state nil)
        [selected-ids set-selected-ids] (uix/use-state #{})

        is-read-only? (boolean selected-snapshot)

        ;; Debounce search
        [debounced-search set-debounced-search] (uix/use-state "")
        _ (uix/use-effect
           (fn []
             (let [timer (js/setTimeout #(set-debounced-search search-term) 300)]
               (fn [] (js/clearTimeout timer))))
           [search-term])

        ;; Fetch dataset props to check if remote
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:dataset-props module-id dataset-id]
          :sente-event [:datasets/get-props {:module-id module-id
                                             :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})

        dataset data
        is-remote? (boolean (:module-name dataset))

        ;; Fetch examples with pagination
        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-paginated-query
         {:query-key [:dataset-examples module-id dataset-id selected-snapshot debounced-search]
          :sente-event [:datasets/search-examples
                        {:module-id module-id
                         :dataset-id dataset-id
                         :snapshot-name selected-snapshot
                         :filters (when-not (str/blank? debounced-search)
                                    {:search-string debounced-search})}]
          :page-size 20
          :enabled? (boolean (and module-id dataset-id (not is-remote?)))})]

    (cond
      loading?
      ($ ui/loading-state {:message "Loading dataset..."})

      (not dataset)
      ($ ui/empty-state
         {:title "Dataset not found"
          :description "The requested dataset could not be found."})

      :else
      ($ :div {:class "space-y-4"}
         ;; Header
         ($ :div {:class "flex items-center justify-between"}
            ($ :div
               ($ :h1 {:class "text-2xl font-bold"} "Examples")
               ($ :p {:class "text-base-content/60"}
                  (str (or (:name dataset) decoded-dataset-id) " · " decoded-module-id)))

            ;; Actions
            (when-not is-remote?
              ($ :div {:class "flex items-center gap-4"}
                 ;; Snapshot selector
                 ($ snapshot-selector {:module-id module-id
                                       :dataset-id dataset-id
                                       :selected-snapshot selected-snapshot
                                       :on-select (fn [s]
                                                    (set-selected-snapshot s)
                                                    (set-selected-ids #{}))})

                 ;; Export button
                 ($ :a {:class "btn btn-outline btn-sm"
                        :href (str "/api/datasets/" (utils/url-encode module-id) "/"
                                   (utils/url-encode (str dataset-id)) "/export"
                                   (when selected-snapshot
                                     (str "?snapshot=" (utils/url-encode selected-snapshot))))
                        :target "_blank"}
                    "Export")

                 ;; Add example button
                 ($ :button {:class "btn btn-primary btn-sm"
                             :data-testid "btn-add-example"
                             :disabled is-read-only?
                             :onClick #(show-add-example-modal!
                                        {:module-id module-id
                                         :dataset-id dataset-id
                                         :snapshot-name selected-snapshot})}
                    "+ Add Example"))))

         ;; Read-only warning
         (when is-read-only?
           ($ ui/warning-alert
              {:message "You are viewing a read-only snapshot. Editing is disabled."}))

         ;; Remote dataset message
         (when is-remote?
           ($ remote-dataset-view {:dataset dataset}))

         ;; Examples content
         (when-not is-remote?
           ($ :<>
              ;; Search bar
              ($ :div {:class "flex items-center gap-4"}
                 ($ :div {:class "form-control flex-1"}
                    ($ :input
                       {:type "text"
                        :class "input input-bordered w-full"
                        :placeholder "Search examples..."
                        :value search-term
                        :onChange #(set-search-term (.. % -target -value))})))

              ;; Bulk actions bar
              ($ bulk-actions-bar {:selected-count (count selected-ids)
                                   :module-id module-id
                                   :dataset-id dataset-id
                                   :snapshot-name selected-snapshot
                                   :on-clear #(set-selected-ids #{})
                                   :is-read-only? is-read-only?})

              ;; Examples table
              ($ ui/card
                 ($ examples-table
                    {:examples data
                     :module-id module-id
                     :dataset-id dataset-id
                     :snapshot-name selected-snapshot
                     :loading? isLoading
                     :error error
                     :has-more? hasMore
                     :is-fetching-more? isFetchingMore
                     :load-more loadMore
                     :is-read-only? is-read-only?
                     :selected-ids selected-ids
                     :set-selected-ids set-selected-ids}))))))))

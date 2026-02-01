(ns alt-frontend.routes.agents.$module-id.human-metrics
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.state :as state]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            [alt-frontend.components.modal :as modal]
            [clojure.string :as str]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

(defn numeric-metric? [metric]
  (and (contains? metric :min) (contains? metric :max)))

(defn categorical-metric? [metric]
  (contains? metric :categories))

;; =============================================================================
;; METRIC DETAIL MODAL
;; =============================================================================

(defn show-metric-detail! [metric]
  (let [metric-def (:metric metric)
        is-numeric? (numeric-metric? metric-def)
        is-categorical? (categorical-metric? metric-def)]
    (state/dispatch
     [:modal/show
      {:title (str "Metric: " (:name metric))
       :content ($ :div {:class "space-y-4"}
                   ;; Type badge
                   ($ :div {:class "flex items-center gap-2"}
                      ($ :span {:class "text-sm font-medium text-base-content/70"} "Type:")
                      ($ :span {:class (str "badge "
                                            (if is-numeric? "badge-info" "badge-secondary"))}
                         (if is-numeric? "Numeric" "Categorical")))

                   ;; Configuration
                   (cond
                     is-numeric?
                     ($ :div {:class "bg-base-200 p-4 rounded-lg"}
                        ($ :div {:class "text-sm font-medium mb-2"} "Range Configuration")
                        ($ :div {:class "grid grid-cols-2 gap-4"}
                           ($ :div
                              ($ :span {:class "text-xs text-base-content/60"} "Minimum")
                              ($ :p {:class "font-mono text-lg"} (:min metric-def)))
                           ($ :div
                              ($ :span {:class "text-xs text-base-content/60"} "Maximum")
                              ($ :p {:class "font-mono text-lg"} (:max metric-def)))))

                     is-categorical?
                     ($ :div {:class "bg-base-200 p-4 rounded-lg"}
                        ($ :div {:class "text-sm font-medium mb-2"} "Categories")
                        ($ :div {:class "flex flex-wrap gap-2"}
                           (for [category (sort (:categories metric-def))]
                             ($ :span {:key category
                                       :class "badge badge-outline"}
                                category))))))
       :size :md}])))

;; =============================================================================
;; CREATE METRIC FORM (Simplified inline form)
;; =============================================================================

(defui create-metric-form [{:keys [module-id on-success on-cancel]}]
  (let [[form-state set-form-state!] (uix/use-state {:name ""
                                                      :type :numeric
                                                      :min "1"
                                                      :max "10"
                                                      :categories ""})
        [errors set-errors!] (uix/use-state {})
        [submitting? set-submitting!] (uix/use-state false)

        validate (fn []
                   (let [errs (cond-> {}
                                (str/blank? (:name form-state))
                                (assoc :name "Name is required")

                                (and (= (:type form-state) :numeric)
                                     (or (str/blank? (:min form-state))
                                         (js/isNaN (js/parseInt (:min form-state) 10))))
                                (assoc :min "Valid minimum required")

                                (and (= (:type form-state) :numeric)
                                     (or (str/blank? (:max form-state))
                                         (js/isNaN (js/parseInt (:max form-state) 10))))
                                (assoc :max "Valid maximum required")

                                (and (= (:type form-state) :numeric)
                                     (not (str/blank? (:min form-state)))
                                     (not (str/blank? (:max form-state)))
                                     (>= (js/parseInt (:min form-state) 10)
                                         (js/parseInt (:max form-state) 10)))
                                (assoc :max "Max must be greater than min")

                                (and (= (:type form-state) :categorical)
                                     (str/blank? (:categories form-state)))
                                (assoc :categories "Categories required")

                                (and (= (:type form-state) :categorical)
                                     (not (str/blank? (:categories form-state)))
                                     (< (count (->> (str/split (:categories form-state) #",")
                                                    (map str/trim)
                                                    (filter #(not (str/blank? %)))))
                                        2))
                                (assoc :categories "At least 2 categories required"))]
                     (set-errors! errs)
                     (empty? errs)))

        handle-submit (fn []
                        (when (validate)
                          (set-submitting! true)
                          (let [payload (if (= (:type form-state) :numeric)
                                          {:module-id module-id
                                           :name (:name form-state)
                                           :type :numeric
                                           :min (js/parseInt (:min form-state) 10)
                                           :max (js/parseInt (:max form-state) 10)}
                                          {:module-id module-id
                                           :name (:name form-state)
                                           :type :categorical
                                           :categories (:categories form-state)})]
                            (sente/request!
                             [:human-feedback/create-metric payload]
                             10000
                             (fn [reply]
                               (set-submitting! false)
                               (if (:success reply)
                                 (on-success)
                                 (set-errors! {:submit (or (:error reply) "Failed to create metric")})))))))]

    ($ :div {:class "space-y-4"}
       ;; Name
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Metric Name")
             ($ :span {:class "label-text-alt text-error"} "*"))
          ($ :input {:type "text"
                     :class (str "input input-bordered " (when (:name errors) "input-error"))
                     :placeholder "e.g., helpfulness, accuracy"
                     :data-testid "input-metric-name"
                     :value (:name form-state)
                     :onChange #(set-form-state! assoc :name (.. % -target -value))})
          (when (:name errors)
            ($ :label {:class "label"}
               ($ :span {:class "label-text-alt text-error"} (:name errors)))))

       ;; Type selector
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Metric Type"))
          ($ :select {:class "select select-bordered"
                      :data-testid "select-metric-type"
                      :value (name (:type form-state))
                      :onChange #(set-form-state! assoc :type (keyword (.. % -target -value)))}
             ($ :option {:value "numeric"} "Numeric Range")
             ($ :option {:value "categorical"} "Categorical")))

       ;; Conditional fields
       (if (= (:type form-state) :numeric)
         ;; Numeric fields
         ($ :div {:class "grid grid-cols-2 gap-4"}
            ($ :div {:class "form-control"}
               ($ :label {:class "label"}
                  ($ :span {:class "label-text"} "Minimum"))
               ($ :input {:type "number"
                          :class (str "input input-bordered " (when (:min errors) "input-error"))
                          :data-testid "input-min"
                          :value (:min form-state)
                          :onChange #(set-form-state! assoc :min (.. % -target -value))})
               (when (:min errors)
                 ($ :label {:class "label"}
                    ($ :span {:class "label-text-alt text-error"} (:min errors)))))

            ($ :div {:class "form-control"}
               ($ :label {:class "label"}
                  ($ :span {:class "label-text"} "Maximum"))
               ($ :input {:type "number"
                          :class (str "input input-bordered " (when (:max errors) "input-error"))
                          :data-testid "input-max"
                          :value (:max form-state)
                          :onChange #(set-form-state! assoc :max (.. % -target -value))})
               (when (:max errors)
                 ($ :label {:class "label"}
                    ($ :span {:class "label-text-alt text-error"} (:max errors))))))

         ;; Categorical field
         ($ :div {:class "form-control"}
            ($ :label {:class "label"}
               ($ :span {:class "label-text"} "Categories (comma-separated)"))
            ($ :input {:type "text"
                       :class (str "input input-bordered " (when (:categories errors) "input-error"))
                       :placeholder "Good, Average, Poor"
                       :data-testid "input-options"
                       :value (:categories form-state)
                       :onChange #(set-form-state! assoc :categories (.. % -target -value))})
            (when (:categories errors)
              ($ :label {:class "label"}
                 ($ :span {:class "label-text-alt text-error"} (:categories errors))))

            ;; Preview
            (when-not (str/blank? (:categories form-state))
              (let [cats (->> (str/split (:categories form-state) #",")
                              (map str/trim)
                              (filter #(not (str/blank? %))))]
                (when (seq cats)
                  ($ :div {:class "mt-2"}
                     ($ :span {:class "text-xs text-base-content/60"} "Preview: ")
                     ($ :div {:class "flex flex-wrap gap-1 mt-1"}
                        (for [cat cats]
                          ($ :span {:key cat
                                    :class "badge badge-secondary badge-sm"}
                             cat)))))))))

       ;; Submit error
       (when (:submit errors)
         ($ :div {:class "alert alert-error"}
            ($ :span (:submit errors))))

       ;; Actions
       ($ :div {:class "flex justify-end gap-2 mt-4"}
          ($ :button {:class "btn btn-ghost"
                      :data-testid "btn-cancel"
                      :onClick on-cancel
                      :disabled submitting?}
             "Cancel")
          ($ :button {:class "btn btn-primary"
                      :data-testid "btn-submit-create-metric"
                      :onClick handle-submit
                      :disabled submitting?}
             (if submitting?
               ($ :<>
                  ($ :span {:class "loading loading-spinner loading-sm"})
                  "Creating...")
               "Create Metric"))))))

(defn show-create-metric-modal! [module-id on-success]
  (state/dispatch
   [:modal/show
    {:title "Create Human Metric"
     :content ($ create-metric-form
                 {:module-id module-id
                  :on-success (fn []
                                (state/dispatch [:modal/hide])
                                (on-success))
                  :on-cancel #(state/dispatch [:modal/hide])})
     :size :md
     :hide-actions? true}]))

;; =============================================================================
;; METRIC ROW
;; =============================================================================

(defui metric-row [{:keys [metric module-id on-delete]}]
  (let [metric-name (:name metric)
        metric-def (:metric metric)
        is-numeric? (numeric-metric? metric-def)
        is-categorical? (categorical-metric? metric-def)
        [deleting? set-deleting!] (uix/use-state false)

        handle-delete (fn [e]
                        (.stopPropagation e)
                        (modal/show-confirm!
                         {:title "Delete Metric"
                          :message (str "Are you sure you want to delete metric \"" metric-name "\"? This action cannot be undone.")
                          :confirm-text "Delete"
                          :confirm-variant :error
                          :on-confirm (fn []
                                        (set-deleting! true)
                                        (sente/request!
                                         [:human-feedback/delete-metric
                                          {:module-id module-id
                                           :name metric-name}]
                                         10000
                                         (fn [reply]
                                           (set-deleting! false)
                                           (if (:success reply)
                                             (on-delete)
                                             (js/alert (str "Failed to delete: " (or (:error reply) "Unknown error")))))))}))]

    ($ :tr {:class "hover cursor-pointer"
            :onClick #(show-metric-detail! metric)}
       ;; Name
       ($ :td {:class "font-medium"} metric-name)

       ;; Type
       ($ :td
          ($ :span {:class (str "badge badge-sm "
                                (if is-numeric? "badge-info" "badge-secondary"))}
             (if is-numeric? "Numeric" "Categorical")))

       ;; Configuration
       ($ :td {:class "text-sm text-base-content/70"}
          (cond
            is-numeric?
            (str "Range: " (:min metric-def) " - " (:max metric-def))

            is-categorical?
            (let [cats (sort (:categories metric-def))]
              (if (> (count cats) 3)
                (str (str/join ", " (take 3 cats)) " +" (- (count cats) 3) " more")
                (str/join ", " cats)))))

       ;; Actions
       ($ :td {:class "text-right"}
          ($ :button {:class (str "btn btn-ghost btn-sm text-error "
                                  (when deleting? "loading"))
                      :data-testid "btn-delete-metric"
                      :disabled deleting?
                      :onClick handle-delete}
             (when-not deleting?
               ($ icons/trash {:class "h-5 w-5"})))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view [{:keys [module-id]}]
  (let [decoded-module-id (url-decode module-id)

        ;; State
        [metrics set-metrics!] (uix/use-state [])
        [pagination-params set-pagination-params!] (uix/use-state nil)
        [has-more? set-has-more!] (uix/use-state false)
        [loading? set-loading!] (uix/use-state true)
        [loading-more? set-loading-more!] (uix/use-state false)
        [error set-error!] (uix/use-state nil)
        [search-term set-search-term!] (uix/use-state "")
        [debounced-search set-debounced-search!] (uix/use-state "")
        connected? (state/use-state [:sente :connected?])

        ;; Debounce search
        _ (uix/use-effect
           (fn []
             (let [timer (js/setTimeout #(set-debounced-search! search-term) 300)]
               #(js/clearTimeout timer)))
           [search-term])

        fetch-metrics (uix/use-callback
                       (fn [page-params append?]
                         (if append?
                           (set-loading-more! true)
                           (set-loading! true))
                         (sente/request!
                          [:human-feedback/get-metrics
                           (cond-> {:module-id decoded-module-id
                                    :pagination-params page-params
                                    :page-size 20}
                             (not (str/blank? debounced-search))
                             (assoc :filters {:search-string debounced-search}))]
                          10000
                          (fn [reply]
                            (set-loading! false)
                            (set-loading-more! false)
                            (if (:success reply)
                              (let [data (:data reply)
                                    new-metrics (or (:items data) [])
                                    new-pagination (:pagination-params data)
                                    more? (and new-pagination
                                               (not (empty? new-pagination))
                                               (some (fn [[_ v]] (some? v)) new-pagination))]
                                (if append?
                                  (set-metrics! (fn [prev] (concat prev new-metrics)))
                                  (set-metrics! new-metrics))
                                (set-pagination-params! (when more? new-pagination))
                                (set-has-more! more?))
                              (set-error! (or (:error reply) "Failed to fetch metrics"))))))
                       [decoded-module-id debounced-search])

        refetch (uix/use-callback
                 (fn []
                   (set-metrics! [])
                   (set-pagination-params! nil)
                   (fetch-metrics nil false))
                 [fetch-metrics])

        ;; Initial fetch and refetch on search change
        _ (uix/use-effect
           (fn []
             (when connected?
               (refetch))
             js/undefined)
           [debounced-search refetch])

        load-more (fn []
                    (when (and has-more? (not loading?) (not loading-more?) pagination-params)
                      (fetch-metrics pagination-params true)))]

    ($ :div {:class "space-y-4"}
       ;; Header
       ($ :div {:class "flex items-center justify-between"}
          ($ :div {:class "flex items-center gap-3"}
             ($ icons/user {:class "h-8 w-8 text-primary"})
             ($ :div
                ($ :h1 {:class "text-2xl font-bold"} "Human Metrics")
                ($ :p {:class "text-base-content/70 text-sm"}
                   "Define metrics for human feedback evaluation")))

          ;; Create button
          ($ :button {:class "btn btn-primary btn-sm"
                      :data-testid "btn-create-metric"
                      :onClick #(show-create-metric-modal! decoded-module-id refetch)}
             "+ Create Metric"))

       ;; Search
       ($ :div {:class "form-control"}
          ($ :input {:type "text"
                     :class "input input-bordered w-full"
                     :placeholder "Search metrics..."
                     :value search-term
                     :onChange #(set-search-term! (.. % -target -value))}))

       ;; Content
       (cond
         ;; Initial loading
         (and loading? (empty? metrics))
         ($ ui/loading-state {:message "Loading metrics..."})

         ;; Error
         error
         ($ ui/error-alert {:message error})

         ;; Empty
         (empty? metrics)
         ($ ui/empty-state
            {:title "No Metrics"
             :description (if (str/blank? search-term)
                            "No human metrics defined yet. Create one to get started."
                            "No metrics match your search.")
             :icon ($ icons/user {:class "h-12 w-12"})})

         ;; Metrics table
         :else
         ($ :<>
            ($ :div {:class "overflow-x-auto"}
               ($ :table {:class "table table-zebra"}
                  ($ :thead
                     ($ :tr
                        ($ :th "Name")
                        ($ :th "Type")
                        ($ :th "Configuration")
                        ($ :th {:class "w-16"} "")))
                  ($ :tbody
                     (for [metric metrics]
                       ($ metric-row {:key (:name metric)
                                      :metric metric
                                      :module-id decoded-module-id
                                      :on-delete refetch})))))

            ;; Load more button
            (when has-more?
              ($ :div {:class "flex justify-center mt-4"}
                 ($ ui/button {:variant :ghost
                               :loading? loading-more?
                               :on-click load-more}
                    (if loading-more? "Loading..." "Load More")))))))))

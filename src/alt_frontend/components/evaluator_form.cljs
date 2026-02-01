(ns alt-frontend.components.evaluator-form
  "Create Evaluator multi-step form for alt-frontend.

   A two-step wizard:
   1. Select an evaluator builder from available builders
   2. Configure evaluator settings (name, params, JSONPath)

   Usage:
   ($ create-evaluator-modal {:module-id \"my-module\"
                              :on-success (fn [] ...)
                              :on-cancel (fn [] ...)})"
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.state :as state]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            [clojure.string :as str]))

;; =============================================================================
;; HELPERS
;; =============================================================================

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
;; STEP 1: SELECT BUILDER
;; =============================================================================

(defui builder-card [{:keys [builder-name builder-spec on-select]}]
  (let [{:keys [type description]} builder-spec]
    ($ :div {:class "card bg-base-100 border border-base-300 hover:border-primary hover:shadow-md transition-all cursor-pointer"
             :onClick #(on-select {:name builder-name :spec builder-spec})}
       ($ :div {:class "card-body p-4"}
          ($ :div {:class "flex justify-between items-start mb-2"}
             ($ :h3 {:class "card-title text-base"} (str builder-name))
             ($ :span {:class (str "badge " (get-type-badge-class type))}
                (get-type-display type)))
          ($ :p {:class "text-sm text-base-content/70"}
             (or description "No description available"))))))

(defui select-builder-step [{:keys [module-id on-select]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:evaluator-builders module-id]
          :sente-event [:evaluators/get-all-builders {:module-id module-id}]
          :enabled? (boolean module-id)})]

    (cond
      loading?
      ($ :div {:class "flex justify-center items-center py-12"}
         ($ :span {:class "loading loading-spinner loading-lg"}))

      error
      ($ ui/error-alert {:message (str "Error loading builders: " error)})

      (empty? data)
      ($ ui/empty-state
         {:icon ($ icons/evaluator {:class "h-12 w-12"})
          :title "No Builders Available"
          :description "No evaluator builders are registered in this module."})

      :else
      ($ :div {:class "space-y-4"}
         ($ :p {:class "text-base-content/70"}
            "Select an evaluator builder to configure:")
         ($ :div {:class "grid gap-3 max-h-96 overflow-y-auto pr-2"}
            (for [[builder-name builder-spec] data]
              ($ builder-card {:key builder-name
                               :builder-name builder-name
                               :builder-spec builder-spec
                               :on-select on-select})))))))

;; =============================================================================
;; STEP 2: CONFIGURE EVALUATOR
;; =============================================================================

(defui configure-evaluator-step [{:keys [module-id selected-builder on-back on-submit submitting? error]}]
  (let [{:keys [name spec]} selected-builder
        builder-params (get-in spec [:options :params] {})
        builder-options (get-in spec [:options] {})
        show-input-path? (get builder-options :input-path? true)
        show-output-path? (get builder-options :output-path? true)
        show-ref-output-path? (get builder-options :reference-output-path? true)

        ;; Form state
        [form-values set-form-values] (uix/use-state
                                       {:name ""
                                        :description ""
                                        :input-json-path "$"
                                        :output-json-path "$"
                                        :reference-output-json-path "$"
                                        :params (into {} (for [[k v] builder-params]
                                                           [k (or (:default v) "")]))})

        ;; Validation state
        [touched set-touched] (uix/use-state #{})

        ;; Update field helper
        update-field (fn [field value]
                       (set-form-values (fn [v] (assoc v field value))))

        update-param (fn [param-key value]
                       (set-form-values (fn [v] (assoc-in v [:params param-key] value))))

        touch-field (fn [field]
                      (set-touched (fn [t] (conj t field))))

        ;; Validation
        name-error (when (and (contains? touched :name)
                              (str/blank? (:name form-values)))
                     "Name is required")

        valid? (not (str/blank? (:name form-values)))

        ;; Submit handler
        handle-submit (fn []
                        (set-touched #{:name})
                        (when valid?
                          (on-submit {:builder-name name
                                      :name (:name form-values)
                                      :description (:description form-values)
                                      :params (:params form-values)
                                      :input-json-path (:input-json-path form-values)
                                      :output-json-path (:output-json-path form-values)
                                      :reference-output-json-path (:reference-output-json-path form-values)})))]

    ($ :div {:class "space-y-6"}
       ;; Builder info banner
       ($ :div {:class "bg-base-200 rounded-lg p-3 flex items-center gap-3"}
          ($ icons/evaluator {:class "h-6 w-6 text-primary"})
          ($ :div
             ($ :div {:class "font-medium"} (str "Builder: " name))
             ($ :span {:class (str "badge badge-sm " (get-type-badge-class (:type spec)))}
                (get-type-display (:type spec)))))

       ;; Basic fields
       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Name")
             ($ :span {:class "label-text-alt text-error"} "*"))
          ($ :input {:type "text"
                     :class (str "input input-bordered w-full "
                                 (when name-error "input-error"))
                     :placeholder "my-evaluator"
                     :data-testid "input-evaluator-name"
                     :value (:name form-values)
                     :onChange #(update-field :name (.. % -target -value))
                     :onBlur #(touch-field :name)})
          (when name-error
            ($ :label {:class "label"}
               ($ :span {:class "label-text-alt text-error"} name-error))))

       ($ :div {:class "form-control"}
          ($ :label {:class "label"}
             ($ :span {:class "label-text"} "Description"))
          ($ :textarea {:class "textarea textarea-bordered w-full"
                        :placeholder "Optional description..."
                        :data-testid "input-evaluator-description"
                        :rows 2
                        :value (:description form-values)
                        :onChange #(update-field :description (.. % -target -value))}))

       ;; Builder parameters
       (when (seq builder-params)
         ($ :div {:class "space-y-4"}
            ($ :div {:class "divider"} "Builder Parameters")
            (for [[param-key param-spec] builder-params]
              (let [param-desc (:description param-spec)
                    param-value (get-in form-values [:params param-key] "")
                    value-str (if (string? param-value) param-value (str param-value))
                    has-newlines? (str/includes? value-str "\n")]
                ($ :div {:key (str param-key) :class "form-control"}
                   ($ :label {:class "label"}
                      ($ :span {:class "label-text font-mono"} (clojure.core/name param-key))
                      (when param-desc
                        ($ :span {:class "label-text-alt tooltip tooltip-left"
                                  :data-tip param-desc}
                           ($ icons/info {:class "h-4 w-4"}))))
                   (if has-newlines?
                     ($ :textarea {:class "textarea textarea-bordered w-full font-mono text-sm"
                                   :rows 6
                                   :value value-str
                                   :onChange #(update-param param-key (.. % -target -value))})
                     ($ :input {:type "text"
                                :class "input input-bordered w-full font-mono text-sm"
                                :value value-str
                                :onChange #(update-param param-key (.. % -target -value))})))))))

       ;; JSONPath configuration
       (when (or show-input-path? show-output-path? show-ref-output-path?)
         ($ :div {:class "space-y-4"}
            ($ :div {:class "divider"} "JSONPath Configuration")
            ($ :p {:class "text-sm text-base-content/70 mb-2"}
               "Configure JSONPath expressions to extract values from example data. "
               ($ :a {:class "link link-primary"
                      :href "https://en.wikipedia.org/wiki/JSONPath"
                      :target "_blank"}
                  "Learn more"))

            (when show-input-path?
              ($ :div {:class "form-control"}
                 ($ :label {:class "label"}
                    ($ :span {:class "label-text"} "Input JSON Path"))
                 ($ :input {:type "text"
                            :class "input input-bordered w-full font-mono"
                            :placeholder "$"
                            :data-testid "input-json-path-input"
                            :value (:input-json-path form-values)
                            :onChange #(update-field :input-json-path (.. % -target -value))})))

            (when show-ref-output-path?
              ($ :div {:class "form-control"}
                 ($ :label {:class "label"}
                    ($ :span {:class "label-text"} "Reference Output JSON Path"))
                 ($ :input {:type "text"
                            :class "input input-bordered w-full font-mono"
                            :placeholder "$"
                            :data-testid "input-json-path-reference"
                            :value (:reference-output-json-path form-values)
                            :onChange #(update-field :reference-output-json-path (.. % -target -value))})))

            (when show-output-path?
              ($ :div {:class "form-control"}
                 ($ :label {:class "label"}
                    ($ :span {:class "label-text"} "Output JSON Path"))
                 ($ :input {:type "text"
                            :class "input input-bordered w-full font-mono"
                            :placeholder "$"
                            :data-testid "input-json-path-output"
                            :value (:output-json-path form-values)
                            :onChange #(update-field :output-json-path (.. % -target -value))})))))

       ;; Error display
       (when error
         ($ :div {:role "alert" :class "alert alert-error"}
            ($ icons/error {:class "h-6 w-6"})
            ($ :span error)))

       ;; Actions
       ($ :div {:class "flex justify-between pt-4"}
          ($ :button {:class "btn btn-ghost"
                      :data-testid "btn-back"
                      :onClick on-back
                      :disabled submitting?}
             ($ icons/chevron-left {:class "h-5 w-5 mr-1"})
             "Back")
          ($ :button {:class "btn btn-primary"
                      :data-testid "btn-submit-create-evaluator"
                      :onClick handle-submit
                      :disabled (or (not valid?) submitting?)}
             (if submitting?
               ($ :<>
                  ($ :span {:class "loading loading-spinner loading-sm mr-2"})
                  "Creating...")
               "Create Evaluator"))))))

;; =============================================================================
;; MAIN MODAL COMPONENT
;; =============================================================================

(defui create-evaluator-modal [{:keys [module-id on-success on-cancel]}]
  (let [;; Wizard step state
        [step set-step] (uix/use-state :select-builder) ;; :select-builder or :configure
        [selected-builder set-selected-builder] (uix/use-state nil)
        [submitting? set-submitting] (uix/use-state false)
        [error set-error] (uix/use-state nil)

        ;; Handlers
        handle-builder-select (fn [builder]
                                (set-selected-builder builder)
                                (set-step :configure))

        handle-back (fn []
                      (set-step :select-builder)
                      (set-error nil))

        handle-submit (fn [form-data]
                        (set-submitting true)
                        (set-error nil)
                        (sente/request!
                         [:evaluators/create
                          {:module-id module-id
                           :builder-name (:builder-name form-data)
                           :name (:name form-data)
                           :description (:description form-data)
                           :params (:params form-data)
                           :input-json-path (:input-json-path form-data)
                           :output-json-path (:output-json-path form-data)
                           :reference-output-json-path (:reference-output-json-path form-data)}]
                         15000
                         (fn [reply]
                           (set-submitting false)
                           (if (:success reply)
                             (do
                               (state/dispatch [:modal/hide])
                               (when on-success (on-success)))
                             (set-error (or (:error reply) "Failed to create evaluator"))))))]

    ($ :div {:class "min-h-64"}
       ;; Step indicator
       ($ :ul {:class "steps w-full mb-6"}
          ($ :li {:class (str "step " (when (#{:select-builder :configure} step) "step-primary"))}
             "Select Builder")
          ($ :li {:class (str "step " (when (= step :configure) "step-primary"))}
             "Configure"))

       ;; Step content
       (case step
         :select-builder
         ($ select-builder-step {:module-id module-id
                                 :on-select handle-builder-select})

         :configure
         ($ configure-evaluator-step {:module-id module-id
                                      :selected-builder selected-builder
                                      :on-back handle-back
                                      :on-submit handle-submit
                                      :submitting? submitting?
                                      :error error})))))

;; =============================================================================
;; RUN/TRY EVALUATOR MODAL
;; =============================================================================

(defn validate-json [value]
  "Returns error message if invalid JSON, nil if valid"
  (when-not (str/blank? value)
    (try
      (js/JSON.parse value)
      nil
      (catch js/Error e
        (str "Invalid JSON: " (.-message e))))))

(defn pp-json [value]
  "Pretty-print a value as JSON"
  (try
    (js/JSON.stringify (clj->js value) nil 2)
    (catch :default _
      (str value))))

(defui run-evaluator-modal [{:keys [module-id evaluator]}]
  (let [{:keys [name type builder-name]} evaluator

        ;; Fetch builder options to determine which fields to show
        {:keys [data]}
        (queries/use-sente-query
         {:query-key [:evaluator-builders module-id]
          :sente-event [:evaluators/get-all-builders {:module-id module-id}]
          :enabled? (boolean module-id)})

        builder-options (get-in data [builder-name :options] {})
        show-input-path? (get builder-options :input-path? true)
        show-output-path? (get builder-options :output-path? true)
        show-ref-output-path? (get builder-options :reference-output-path? true)

        ;; Form state
        [input-str set-input-str] (uix/use-state "{}")
        [ref-output-str set-ref-output-str] (uix/use-state "{}")
        [output-str set-output-str] (uix/use-state "{}")

        ;; For comparative type - multiple outputs
        [outputs set-outputs] (uix/use-state [{:id (random-uuid) :value "{}"}])

        ;; Validation errors
        [input-error set-input-error] (uix/use-state nil)
        [ref-output-error set-ref-output-error] (uix/use-state nil)
        [output-error set-output-error] (uix/use-state nil)

        ;; Execution state
        [running? set-running] (uix/use-state false)
        [result set-result] (uix/use-state nil)
        [error set-error] (uix/use-state nil)

        ;; Check for validation errors
        has-errors? (or input-error ref-output-error output-error
                        (some :error outputs))

        ;; Run handler
        handle-run (fn []
                     (set-running true)
                     (set-error nil)
                     (set-result nil)

                     (try
                       (let [parsed-input (when (and show-input-path? (not (str/blank? input-str)))
                                            (-> input-str js/JSON.parse js->clj))
                             parsed-ref-output (when (and show-ref-output-path? (not (str/blank? ref-output-str)))
                                                 (-> ref-output-str js/JSON.parse js->clj))

                             run-data (case type
                                        :regular
                                        (cond-> {}
                                          show-input-path? (assoc :input parsed-input)
                                          show-ref-output-path? (assoc :referenceOutput parsed-ref-output)
                                          show-output-path? (assoc :output (when-not (str/blank? output-str)
                                                                             (-> output-str js/JSON.parse js->clj))))

                                        :comparative
                                        (cond-> {}
                                          show-input-path? (assoc :input parsed-input)
                                          show-ref-output-path? (assoc :referenceOutput parsed-ref-output)
                                          show-output-path? (assoc :outputs (mapv #(-> % :value js/JSON.parse js->clj) outputs)))

                                        ;; Summary type not supported in this modal
                                        {})]
                         (sente/request!
                          [:evaluators/run
                           {:module-id module-id
                            :name name
                            :type type
                            :run-data run-data}]
                          60000
                          (fn [reply]
                            (set-running false)
                            (if (:success reply)
                              (set-result (:data reply))
                              (set-error (or (:error reply) "Failed to run evaluator"))))))
                       (catch js/Error e
                         (set-running false)
                         (set-error (str "Invalid JSON: " (.-message e))))))]

    ($ :div {:class "space-y-6"}
       ;; Evaluator info
       ($ :div {:class "bg-base-200 rounded-lg p-3 flex items-center justify-between"}
          ($ :div {:class "flex items-center gap-3"}
             ($ icons/evaluator {:class "h-6 w-6 text-primary"})
             ($ :div
                ($ :div {:class "font-medium"} name)
                ($ :span {:class (str "badge badge-sm " (get-type-badge-class type))}
                   (get-type-display type))))
          ($ :code {:class "text-xs text-base-content/70"} builder-name))

       ;; Input field
       (when show-input-path?
         ($ :div {:class "form-control"}
            ($ :label {:class "label"}
               ($ :span {:class "label-text"} "Input (JSON)"))
            ($ :textarea {:class (str "textarea textarea-bordered w-full font-mono text-sm "
                                      (when input-error "textarea-error"))
                          :rows 4
                          :value input-str
                          :onChange (fn [e]
                                      (let [v (.. e -target -value)]
                                        (set-input-str v)
                                        (set-input-error (validate-json v))))})
            (when input-error
              ($ :label {:class "label"}
                 ($ :span {:class "label-text-alt text-error"} input-error)))))

       ;; Reference output field
       (when show-ref-output-path?
         ($ :div {:class "form-control"}
            ($ :label {:class "label"}
               ($ :span {:class "label-text"} "Reference Output (JSON)"))
            ($ :textarea {:class (str "textarea textarea-bordered w-full font-mono text-sm "
                                      (when ref-output-error "textarea-error"))
                          :rows 4
                          :value ref-output-str
                          :onChange (fn [e]
                                      (let [v (.. e -target -value)]
                                        (set-ref-output-str v)
                                        (set-ref-output-error (validate-json v))))})
            (when ref-output-error
              ($ :label {:class "label"}
                 ($ :span {:class "label-text-alt text-error"} ref-output-error)))))

       ;; Output field(s)
       (when show-output-path?
         (case type
           :regular
           ($ :div {:class "form-control"}
              ($ :label {:class "label"}
                 ($ :span {:class "label-text"} "Model Output (JSON)"))
              ($ :textarea {:class (str "textarea textarea-bordered w-full font-mono text-sm "
                                        (when output-error "textarea-error"))
                            :rows 4
                            :placeholder "{\"result\": \"...\"}"
                            :value output-str
                            :onChange (fn [e]
                                        (let [v (.. e -target -value)]
                                          (set-output-str v)
                                          (set-output-error (validate-json v))))})
              (when output-error
                ($ :label {:class "label"}
                   ($ :span {:class "label-text-alt text-error"} output-error))))

           :comparative
           ($ :div {:class "space-y-3"}
              ($ :label {:class "label"}
                 ($ :span {:class "label-text"} "Model Outputs (JSON, one per output)"))
              (for [output outputs]
                ($ :div {:key (:id output) :class "flex gap-2"}
                   ($ :textarea {:class (str "textarea textarea-bordered flex-1 font-mono text-sm "
                                             (when (:error output) "textarea-error"))
                                 :rows 2
                                 :value (:value output)
                                 :onChange (fn [e]
                                             (let [v (.. e -target -value)
                                                   err (validate-json v)]
                                               (set-outputs
                                                (mapv (fn [o]
                                                        (if (= (:id o) (:id output))
                                                          (assoc o :value v :error err)
                                                          o))
                                                      outputs))))})
                   ($ :button {:class "btn btn-ghost btn-sm text-error"
                               :onClick #(set-outputs (filterv (fn [o] (not= (:id o) (:id output))) outputs))}
                      ($ icons/x-mark {:class "h-4 w-4"}))))
              ($ :button {:class "btn btn-ghost btn-sm"
                          :onClick #(set-outputs (conj outputs {:id (random-uuid) :value "{}"}))}
                 ($ icons/plus {:class "h-4 w-4 mr-1"})
                 "Add Output"))

           nil))

       ;; Run button
       ($ :div {:class "flex justify-center"}
          ($ :button {:class "btn btn-primary"
                      :onClick handle-run
                      :disabled (or running? has-errors?)}
             (if running?
               ($ :<>
                  ($ :span {:class "loading loading-spinner loading-sm mr-2"})
                  "Running...")
               ($ :<>
                  ($ icons/play {:class "h-5 w-5 mr-1"})
                  "Run Evaluator"))))

       ;; Error display
       (when error
         ($ :div {:role "alert" :class "alert alert-error"}
            ($ icons/error {:class "h-6 w-6"})
            ($ :span error)))

       ;; Result display
       (when result
         ($ :div {:class "space-y-2"}
            ($ :label {:class "label"}
               ($ :span {:class "label-text font-medium"} "Evaluator Result"))
            ($ :div {:class "bg-success/10 rounded-lg p-4 border border-success/20"}
               ($ :pre {:class "text-sm whitespace-pre-wrap font-mono overflow-x-auto"}
                  (pp-json result))))))))

;; =============================================================================
;; HELPER FUNCTIONS
;; =============================================================================

(defn show-create-evaluator-modal!
  "Show the create evaluator modal.

   Options:
   - :module-id - Required, the module ID
   - :on-success - Callback after successful creation"
  [{:keys [module-id on-success]}]
  (state/dispatch
   [:modal/show
    {:title "Create Evaluator"
     :size :lg
     :content ($ create-evaluator-modal
                 {:module-id module-id
                  :on-success on-success})}]))

(defn show-run-evaluator-modal!
  "Show the run/try evaluator modal.

   Options:
   - :module-id - Required, the module ID
   - :evaluator - Required, the evaluator spec"
  [{:keys [module-id evaluator]}]
  (state/dispatch
   [:modal/show
    {:title (str "Try Evaluator: " (:name evaluator))
     :size :lg
     :content ($ run-evaluator-modal
                 {:module-id module-id
                  :evaluator evaluator})}]))

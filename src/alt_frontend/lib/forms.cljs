(ns alt-frontend.lib.forms
  "Form state management with validation for alt-frontend.

   Usage:
   1. Register a form spec:
      (reg-form :my-form
        {:fields {:name \"\" :email \"\"}
         :validators {:name [required]
                      :email [required valid-email]}
         :on-submit (fn [values] ...)})

   2. Initialize form in component:
      (state/dispatch [:form/initialize :my-form])

   3. Use form hooks:
      (let [form (use-form :my-form)
            name-field (use-field :my-form :name)]
        ...)"
  (:require [uix.core :as uix :refer [defui defhook $]]
            [alt-frontend.lib.state :as state]
            [alt-frontend.components.icons :as icons]
            [clojure.string :as str]
            [com.rpl.specter :as s]))

;; =============================================================================
;; FORM REGISTRY
;; =============================================================================

(defonce form-specs (atom {}))

(defn reg-form
  "Register a form specification.

   Spec keys:
   - :fields - Map of field names to initial values
   - :validators - Map of field names to vector of validator fns
   - :on-submit - Function called with form values on valid submit"
  [form-id spec]
  (swap! form-specs assoc form-id spec))

;; =============================================================================
;; VALIDATORS
;; =============================================================================

(defn required
  "Validator: field must not be blank"
  [value]
  (when (str/blank? value)
    "This field is required"))

(defn min-length
  "Validator factory: minimum string length"
  [n]
  (fn [value]
    (when (and (string? value) (< (count value) n))
      (str "Must be at least " n " characters"))))

(defn max-length
  "Validator factory: maximum string length"
  [n]
  (fn [value]
    (when (and (string? value) (> (count value) n))
      (str "Must be no more than " n " characters"))))

(defn valid-json
  "Validator: value must be valid JSON"
  [value]
  (when-not (str/blank? value)
    (try
      (js/JSON.parse value)
      nil
      (catch js/Error e
        (str "Invalid JSON: " (.-message e))))))

(defn valid-json-array
  "Validator: value must be valid JSON array"
  [value]
  (when-not (str/blank? value)
    (try
      (let [parsed (js/JSON.parse value)]
        (when-not (array? parsed)
          "Must be a JSON array"))
      (catch js/Error e
        (str "Invalid JSON: " (.-message e))))))

(defn matches-pattern
  "Validator factory: value must match regex pattern"
  [pattern message]
  (fn [value]
    (when (and (not (str/blank? value))
               (not (re-matches pattern value)))
      message)))

;; =============================================================================
;; VALIDATION HELPERS
;; =============================================================================

(defn- validate-field
  "Run validators for a single field, return first error or nil"
  [value validators]
  (some #(% value) validators))

(defn- validate-all-fields
  "Validate all fields, return map of field -> error"
  [values validators]
  (reduce-kv
   (fn [errors field field-validators]
     (if-let [error (validate-field (get values field) field-validators)]
       (assoc errors field error)
       errors))
   {}
   validators))

;; =============================================================================
;; FORM EVENTS
;; =============================================================================

(state/reg-event
 :form/initialize
 (fn [_db form-id & [props]]
   (let [spec (get @form-specs form-id)
         initial-fields (if (fn? (:fields spec))
                          ((:fields spec) props)
                          (or (:fields spec) {}))
         validators (:validators spec)
         errors (validate-all-fields initial-fields validators)]
     [:forms form-id (s/terminal-val {:values initial-fields
                                      :errors errors
                                      :touched #{}
                                      :submitting? false
                                      :submitted? false
                                      :form-error nil})])))

(state/reg-event
 :form/set-field
 (fn [db form-id field value]
   (let [spec (get @form-specs form-id)
         validators (:validators spec)
         field-validators (get validators field [])
         error (validate-field value field-validators)
         current-values (get-in db [:forms form-id :values])
         new-values (assoc current-values field value)
         all-errors (validate-all-fields new-values validators)]
     [:forms form-id (s/terminal-val {:values new-values
                                      :errors all-errors
                                      :touched (conj (get-in db [:forms form-id :touched] #{}) field)
                                      :submitting? (get-in db [:forms form-id :submitting?])
                                      :submitted? (get-in db [:forms form-id :submitted?])
                                      :form-error nil})])))

(state/reg-event
 :form/touch-field
 (fn [db form-id field]
   (let [touched (get-in db [:forms form-id :touched] #{})]
     [:forms form-id :touched (s/terminal-val (conj touched field))])))

(state/reg-event
 :form/set-submitting
 (fn [_db form-id submitting?]
   [:forms form-id :submitting? (s/terminal-val submitting?)]))

(state/reg-event
 :form/set-error
 (fn [_db form-id error]
   [:forms form-id :form-error (s/terminal-val error)]))

(state/reg-event
 :form/clear
 (fn [db form-id]
   ;; Remove form from state
   [:forms (s/terminal #(dissoc % form-id))]))

(state/reg-event
 :form/reset
 (fn [db form-id]
   ;; Re-initialize to initial values
   (let [spec (get @form-specs form-id)
         initial-fields (or (:fields spec) {})]
     [:forms form-id (s/terminal-val {:values initial-fields
                                      :errors {}
                                      :touched #{}
                                      :submitting? false
                                      :submitted? false
                                      :form-error nil})])))

;; =============================================================================
;; FORM HOOKS
;; =============================================================================

(defhook use-form
  "Hook for form-level state.

   Returns:
   - :values - Map of field values
   - :errors - Map of field errors
   - :touched - Set of touched field names
   - :valid? - Whether all fields are valid
   - :submitting? - Whether form is submitting
   - :form-error - Form-level error message
   - :set-field! - Function to set field value
   - :submit! - Function to submit form"
  [form-id]
  (let [form-state (state/use-state [:forms form-id])
        spec (get @form-specs form-id)
        {:keys [values errors touched submitting? form-error]} form-state
        valid? (empty? errors)]

    {:values (or values {})
     :errors (or errors {})
     :touched (or touched #{})
     :valid? valid?
     :submitting? (boolean submitting?)
     :form-error form-error
     :set-field! (fn [field value]
                   (state/dispatch [:form/set-field form-id field value]))
     :touch-field! (fn [field]
                     (state/dispatch [:form/touch-field form-id field]))
     :submit! (fn []
                (when (and valid? (not submitting?))
                  (state/dispatch [:form/set-submitting form-id true])
                  (when-let [on-submit (:on-submit spec)]
                    (on-submit values
                               {:set-error! (fn [error]
                                              (state/dispatch [:form/set-error form-id error])
                                              (state/dispatch [:form/set-submitting form-id false]))
                                :set-submitting! (fn [v]
                                                   (state/dispatch [:form/set-submitting form-id v]))
                                :clear! (fn []
                                          (state/dispatch [:form/clear form-id]))}))))
     :reset! (fn []
               (state/dispatch [:form/reset form-id]))
     :clear! (fn []
               (state/dispatch [:form/clear form-id]))}))

(defhook use-field
  "Hook for single field state.

   Returns:
   - :value - Current field value
   - :error - Field error (only if touched)
   - :touched? - Whether field has been touched
   - :on-change - Change handler
   - :on-blur - Blur handler (marks field as touched)"
  [form-id field]
  (let [form-state (state/use-state [:forms form-id])
        {:keys [values errors touched]} form-state
        value (get values field)
        error (get errors field)
        touched? (contains? touched field)]

    {:value (or value "")
     :error (when touched? error)
     :touched? touched?
     :on-change (fn [e-or-value]
                  (let [v (if (string? e-or-value)
                            e-or-value
                            (.. e-or-value -target -value))]
                    (state/dispatch [:form/set-field form-id field v])))
     :on-blur (fn []
                (state/dispatch [:form/touch-field form-id field]))}))

;; =============================================================================
;; FORM UI COMPONENTS
;; =============================================================================

(defui form-field
  "A DaisyUI form field with label, input, and error display.

   Props:
   - :label - Field label
   - :type - Input type: :text :textarea :email :password (default :text)
   - :field - Field state from use-field hook
   - :placeholder - Placeholder text
   - :rows - For textarea, number of rows
   - :disabled? - Whether field is disabled
   - :required? - Whether to show required indicator"
  [{:keys [label type field placeholder rows disabled? required?]
    :or {type :text rows 3}}]
  (let [{:keys [value error on-change on-blur]} field]
    ($ :div {:class "form-control w-full"}
       ;; Label
       (when label
         ($ :label {:class "label"}
            ($ :span {:class "label-text"}
               label
               (when required?
                 ($ :span {:class "text-error ml-1"} "*")))))

       ;; Input
       (case type
         :textarea
         ($ :textarea
            {:class (str "textarea textarea-bordered w-full "
                         (when error "textarea-error"))
             :value value
             :placeholder placeholder
             :rows rows
             :disabled disabled?
             :onChange on-change
             :onBlur on-blur})

         ;; Default: text input
         ($ :input
            {:type (name type)
             :class (str "input input-bordered w-full "
                         (when error "input-error"))
             :value value
             :placeholder placeholder
             :disabled disabled?
             :onChange on-change
             :onBlur on-blur}))

       ;; Error message
       (when error
         ($ :label {:class "label"}
            ($ :span {:class "label-text-alt text-error"} error))))))

(defui form-error-alert
  "Form-level error alert"
  [{:keys [error]}]
  (when error
    ($ :div {:role "alert"
             :class "alert alert-error mt-4"}
       ($ icons/error {:class "h-6 w-6 shrink-0"})
       ($ :span error))))

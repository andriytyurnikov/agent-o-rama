(ns alt-frontend.routes.agents.$module-id.agent.$agent-name.index
  "Agent detail route showing recent invocations and basic info."
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.forms :as forms]
            [alt-frontend.lib.state :as state]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.lib.time :as time]
            [alt-frontend.lib.json :as json]
            [alt-frontend.components.ui :as ui]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

;; =============================================================================
;; STATUS BADGE
;; =============================================================================

(defui result-badge
  "Badge showing invocation status"
  [{:keys [status human-request?]}]
  (cond
    human-request?
    ($ ui/badge {:variant :warning} "Needs input")

    (= status :pending)
    ($ :span {:class "badge badge-info gap-1"}
       ($ ui/loading-spinner {:size :xs})
       "Pending")

    (= status :failure)
    ($ ui/badge {:variant :error} "Failed")

    :else
    ($ ui/badge {:variant :success} "Success")))

;; =============================================================================
;; INVOCATIONS TABLE
;; =============================================================================

(defui invocations-table
  "Table of recent invocations"
  [{:keys [invocations module-id agent-name loading? error]}]
  (cond
    loading?
    ($ ui/loading-state {:message "Loading invocations..."})

    error
    ($ ui/error-alert {:message (str "Error loading invocations: " error)})

    (empty? invocations)
    ($ ui/empty-state
       {:title "No invocations yet"
        :description "This agent hasn't been invoked yet. Run it manually or trigger it programmatically."})

    :else
    ($ :div {:class "overflow-x-auto"}
       ($ :table {:class "table table-zebra"}
          ($ :thead
             ($ :tr
                ($ :th "Trace")
                ($ :th "Start Time")
                ($ :th "Arguments")
                ($ :th "Version")
                ($ :th "Status")))
          ($ :tbody
             (for [invoke invocations
                   :let [task-id (:task-id invoke)
                         agent-id (:agent-id invoke)
                         invoke-id (str task-id "-" agent-id)]]
               ($ :tr {:key invoke-id}
                  ;; View trace button
                  ($ :td
                     ($ :button
                        {:class "btn btn-xs btn-primary"
                         :onClick (fn [e]
                                    (.stopPropagation e)
                                    (rfe/push-state :invocations-detail
                                                    {:module-id module-id
                                                     :agent-name agent-name
                                                     :invoke-id invoke-id}))}
                        "View"))

                  ;; Start time
                  ($ :td {:class "font-mono text-sm"
                          :title (time/format-timestamp (:start-time-millis invoke))}
                     (time/format-relative-time (:start-time-millis invoke)))

                  ;; Arguments (truncated)
                  ($ :td {:class "max-w-xs"}
                     ($ :div {:class "truncate font-mono text-xs"
                              :title (json/pretty-json (:invoke-args invoke))}
                        (json/truncate-json (:invoke-args invoke) 50)))

                  ;; Graph version
                  ($ :td {:class "font-mono text-sm"}
                     (:graph-version invoke))

                  ;; Status badge
                  ($ :td
                     ($ result-badge {:status (:status invoke)
                                      :human-request? (:human-request? invoke)})))))))))

;; =============================================================================
;; NAVIGATION CARDS
;; =============================================================================

(defui nav-cards
  "Quick navigation cards for agent features"
  [{:keys [module-id agent-name]}]
  ($ :div {:class "grid grid-cols-2 md:grid-cols-4 gap-4"}
     ($ ui/nav-card {:title "All Invocations"
                     :description "View complete history"
                     :on-click #(rfe/push-state :invocations {:module-id module-id :agent-name agent-name})})
     ($ ui/nav-card {:title "Analytics"
                     :description "Performance metrics"
                     :on-click #(rfe/push-state :analytics {:module-id module-id :agent-name agent-name})})
     ($ ui/nav-card {:title "Rules"
                     :description "Conditional logic"
                     :on-click #(rfe/push-state :rules {:module-id module-id :agent-name agent-name})})
     ($ ui/nav-card {:title "Config"
                     :description "Agent settings"
                     :on-click #(rfe/push-state :config {:module-id module-id :agent-name agent-name})})))

;; =============================================================================
;; MANUAL RUN FORM
;; =============================================================================

(defn make-form-id
  "Create a unique form ID for manual run"
  [module-id agent-name]
  (keyword (str "manual-run-" module-id "-" agent-name)))

(defn register-manual-run-form!
  "Register the manual run form spec"
  [form-id module-id agent-name]
  (forms/reg-form form-id
                  {:fields {:args ""
                            :metadata ""}
                   :validators {:args [forms/required forms/valid-json-array]}
                   :on-submit
                   (fn [values {:keys [set-error! set-submitting! clear!]}]
                     (let [parsed-args (try (js->clj (js/JSON.parse (:args values))) (catch js/Error _ nil))
                           parsed-metadata (try
                                             (if (str/blank? (:metadata values))
                                               {}
                                               (js->clj (js/JSON.parse (:metadata values))))
                                             (catch js/Error _ {}))]
                       (sente/request!
                        [:invocations/run-agent {:module-id module-id
                                                 :agent-name agent-name
                                                 :args parsed-args
                                                 :metadata parsed-metadata}]
                        10000
                        (fn [reply]
                          (if (:success reply)
                            (let [data (:data reply)
                                  invoke-id (str (:task-id data) "-" (:invoke-id data))]
                              (clear!)
                              (rfe/push-state :invocations-detail
                                              {:module-id module-id
                                               :agent-name agent-name
                                               :invoke-id invoke-id}))
                            (set-error! (or (:error reply) "Failed to run agent")))))))}))

(defui manual-run-form
  "Form for manually running the agent"
  [{:keys [module-id agent-name]}]
  (let [form-id (make-form-id module-id agent-name)
        _ (register-manual-run-form! form-id module-id agent-name)
        form (forms/use-form form-id)
        args-field (forms/use-field form-id :args)
        metadata-field (forms/use-field form-id :metadata)

        handle-submit (fn [e]
                        (.preventDefault e)
                        ((:submit! form)))]

    ;; Initialize form on mount
    (uix/use-effect
     (fn []
       (state/dispatch [:form/initialize form-id])
       (fn []
         (state/dispatch [:form/clear form-id])))
     [form-id])

    ($ ui/card {:title "Manual Run"}
       ($ :form {:onSubmit handle-submit
                 :class "space-y-4"}
          ;; Arguments field
          ($ :div {:class "form-control"}
             ($ :label {:class "label"}
                ($ :span {:class "label-text"} "Arguments (JSON array)")
                ($ :span {:class "label-text-alt text-error"} "*"))
             ($ :textarea
                {:class (str "textarea textarea-bordered font-mono text-sm "
                             (when (:error args-field) "textarea-error"))
                 :data-testid "input-invoke-args"
                 :placeholder "[\"arg1\", \"arg2\", ...]"
                 :rows 3
                 :value (:value args-field)
                 :onChange (:on-change args-field)
                 :onBlur (:on-blur args-field)
                 :disabled (:submitting? form)})
             (when (:error args-field)
               ($ :label {:class "label"}
                  ($ :span {:class "label-text-alt text-error"} (:error args-field)))))

          ;; Metadata field (optional)
          ($ :div {:class "form-control"}
             ($ :label {:class "label"}
                ($ :span {:class "label-text"} "Metadata (JSON object, optional)"))
             ($ :textarea
                {:class (str "textarea textarea-bordered font-mono text-sm "
                             (when (:error metadata-field) "textarea-error"))
                 :data-testid "input-invoke-metadata"
                 :placeholder "{\"key\": \"value\"}"
                 :rows 2
                 :value (:value metadata-field)
                 :onChange (:on-change metadata-field)
                 :onBlur (:on-blur metadata-field)
                 :disabled (:submitting? form)})
             (when (:error metadata-field)
               ($ :label {:class "label"}
                  ($ :span {:class "label-text-alt text-error"} (:error metadata-field)))))

          ;; Form error
          (when (:form-error form)
            ($ :div {:role "alert"
                     :class "alert alert-error"}
               ($ :span (:form-error form))))

          ;; Submit button
          ($ :div {:class "form-control mt-4"}
             ($ :button
                {:type "submit"
                 :data-testid "btn-submit-invoke"
                 :class (str "btn btn-primary "
                             (when (:submitting? form) "loading"))
                 :disabled (or (not (:valid? form)) (:submitting? form))}
                (if (:submitting? form)
                  "Running..."
                  "Run Agent")))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view
  "Agent detail view showing recent invocations and navigation."
  [{:keys [module-id agent-name]}]
  (let [decoded-agent-name (utils/url-decode agent-name)
        decoded-module-id (utils/url-decode module-id)

        ;; Fetch recent invocations (first page)
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:mini-invocations module-id agent-name]
          :sente-event [:invocations/get-page {:module-id module-id
                                               :agent-name agent-name
                                               :pagination {}}]
          :refetch-interval-ms 2000})]

    ($ :div {:class "space-y-6"}
       ;; Header
       ($ :div {:class "flex items-center justify-between"}
          ($ :div
             ($ :h1 {:class "text-2xl font-bold"} decoded-agent-name)
             ($ :p {:class "text-base-content/60"}
                (str "Module: " decoded-module-id))))

       ;; Navigation cards
       ($ nav-cards {:module-id module-id :agent-name agent-name})

       ;; Two-column layout: Manual run + Recent invocations
       ($ :div {:class "grid grid-cols-1 lg:grid-cols-3 gap-6"}
          ;; Manual run form (1 column on large screens)
          ($ :div {:class "lg:col-span-1"}
             ($ manual-run-form {:module-id module-id :agent-name agent-name}))

          ;; Recent invocations (2 columns on large screens)
          ($ :div {:class "lg:col-span-2"}
             ($ ui/card {:title "Recent Invocations"}
                ($ invocations-table
                   {:invocations (:agent-invokes data)
                    :module-id module-id
                    :agent-name agent-name
                    :loading? loading?
                    :error error})

                ;; View all link
                (when (seq (:agent-invokes data))
                  ($ :div {:class "mt-4 text-center"}
                     ($ :button
                        {:class "btn btn-ghost btn-sm"
                         :onClick #(rfe/push-state :invocations {:module-id module-id :agent-name agent-name})}
                        "View all invocations →")))))))))

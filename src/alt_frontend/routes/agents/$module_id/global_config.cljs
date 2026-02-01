(ns alt-frontend.routes.agents.$module-id.global-config
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.queries :as queries]
            [alt-frontend.lib.ws.sente :as sente]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

;; =============================================================================
;; CONFIG ITEM COMPONENT
;; =============================================================================

(defui config-item [{:keys [module-id item refetch]}]
  (let [{:keys [key doc current-value default-value input-type]} item
        [edit-value set-edit-value] (uix/use-state current-value)
        [submitting? set-submitting!] (uix/use-state false)
        [error set-error!] (uix/use-state nil)

        is-dirty? (not= (str current-value) (str edit-value))

        ;; Sync with server value when it changes
        _ (uix/use-effect
           (fn []
             (set-edit-value current-value)
             js/undefined)
           [current-value])

        handle-save (fn []
                      (set-submitting! true)
                      (set-error! nil)
                      (sente/request!
                       [:config/set-global
                        {:module-id module-id
                         :key key
                         :value edit-value}]
                       10000
                       (fn [reply]
                         (set-submitting! false)
                         (if (:success reply)
                           (refetch)
                           (set-error! (or (:error reply) "Failed to save"))))))]

    ($ :div {:class "card bg-base-100 border border-base-300"}
       ($ :div {:class "card-body p-4"}
          ;; Header
          ($ :div {:class "flex justify-between items-center mb-2"}
             ($ :h3 {:class "font-semibold font-mono text-base"} key))

          ;; Documentation
          (when doc
            ($ :p {:class "text-sm text-base-content/70 mb-4"} doc))

          ;; Input and save button
          ($ :div {:class "flex items-center gap-4"}
             ($ :input {:type (name (or input-type :text))
                        :class (str "input input-bordered flex-1 font-mono text-sm "
                                    (when error "input-error"))
                        :value (or edit-value "")
                        :onChange #(set-edit-value (.. % -target -value))
                        :disabled submitting?})
             ($ :button {:class "btn btn-primary btn-sm"
                         :data-testid "btn-save-config"
                         :disabled (or (not is-dirty?) submitting?)
                         :onClick handle-save}
                (if submitting?
                  ($ :<>
                     ($ :span {:class "loading loading-spinner loading-sm"})
                     "Saving...")
                  ($ :<>
                     ($ icons/check {:class "h-4 w-4"})
                     "Save"))))

          ;; Default value and reset
          ($ :div {:class "flex justify-between items-center mt-2 text-xs text-base-content/60"}
             ($ :span "Default: " ($ :code {:class "font-mono bg-base-200 px-1 rounded"} (str default-value)))
             (when (not= (str current-value) (str default-value))
               ($ :button {:class "link link-primary"
                           :data-testid "btn-reset-config"
                           :onClick #(set-edit-value default-value)}
                  "Reset to default")))

          ;; Error display
          (when error
            ($ :div {:class "mt-3 text-sm text-error bg-error/10 p-2 rounded border border-error/20"}
               error))))))

;; =============================================================================
;; MAIN VIEW
;; =============================================================================

(defui view [{:keys [module-id]}]
  (let [decoded-module-id (url-decode module-id)

        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:global-config decoded-module-id]
          :sente-event [:config/get-all-global {:module-id decoded-module-id}]
          :refetch-interval-ms 5000
          :enabled? (boolean decoded-module-id)})

        config-items (or data [])]

    ($ :div {:class "space-y-6"}
       ;; Header
       ($ :div
          ($ :div {:class "flex items-center gap-3 mb-2"}
             ($ icons/settings {:class "h-8 w-8 text-primary"})
             ($ :h1 {:class "text-2xl font-bold"} "Global Module Configuration"))
          ($ :p {:class "text-base-content/70"}
             "These settings apply to all agents within this module."))

       ;; Content
       (cond
         ;; Loading
         loading?
         ($ ui/loading-state {:message "Loading configuration..."})

         ;; Error
         error
         ($ ui/error-alert {:message (str "Error loading configuration: " error)})

         ;; Empty
         (empty? config-items)
         ($ ui/empty-state
            {:title "No Configuration Options"
             :description "This module has no configurable settings."})

         ;; Config items
         :else
         ($ :div {:class "space-y-4 max-w-2xl"}
            (for [item (sort-by :key config-items)]
              ($ config-item {:key (:key item)
                              :module-id decoded-module-id
                              :item item
                              :refetch refetch})))))))

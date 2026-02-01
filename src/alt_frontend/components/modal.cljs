(ns alt-frontend.components.modal
  "DaisyUI modal component with state management.

   Usage:
   - Show modal: (state/dispatch [:modal/show {:title \"Title\" :content component}])
   - Hide modal: (state/dispatch [:modal/hide])
   - Modal state lives at [:ui :modal]"
  (:require [uix.core :refer [defui $]]
            [alt-frontend.lib.state :as state]
            [com.rpl.specter :as s]))

;; =============================================================================
;; MODAL EVENTS
;; =============================================================================

(state/reg-event
 :modal/show
 (fn [_db data]
   [:ui :modal (s/terminal-val {:active? true :data data})]))

(state/reg-event
 :modal/hide
 (fn [_db]
   [:ui :modal (s/terminal-val {:active? false :data nil})]))

;; =============================================================================
;; MODAL COMPONENT
;; =============================================================================

(defui modal
  "A modal dialog using DaisyUI.

   Props:
   - :title - Modal title
   - :children - Modal content
   - :on-close - Optional close handler (defaults to dispatching :modal/hide)
   - :show-close-button? - Whether to show X button (default true)
   - :size - Modal size: :sm :md :lg :xl (default :md)"
  [{:keys [title children on-close show-close-button? size]
    :or {show-close-button? true size :md}}]
  (let [handle-close (or on-close #(state/dispatch [:modal/hide]))
        size-class (case size
                     :sm "max-w-sm"
                     :md "max-w-lg"
                     :lg "max-w-2xl"
                     :xl "max-w-4xl"
                     "max-w-lg")]
    ($ :dialog {:class "modal modal-open"
                :data-testid "modal-dialog"
                :onClick (fn [e]
                           ;; Close on backdrop click
                           (when (= (.-target e) (.-currentTarget e))
                             (handle-close)))}
       ($ :div {:class (str "modal-box " size-class)
                :data-testid "modal-content"}
          ;; Header
          (when (or title show-close-button?)
            ($ :div {:class "flex items-center justify-between mb-4"}
               (when title
                 ($ :h3 {:class "font-bold text-lg"} title))
               (when show-close-button?
                 ($ :button {:class "btn btn-sm btn-circle btn-ghost"
                             :data-testid "modal-close"
                             :onClick handle-close}
                    "✕"))))

          ;; Content
          children))))

(defui modal-actions
  "Footer actions for modal.

   Props:
   - :children - Action buttons"
  [{:keys [children]}]
  ($ :div {:class "modal-action"}
     children))

;; =============================================================================
;; GLOBAL MODAL
;; =============================================================================

(defui global-modal
  "Global modal that reads from [:ui :modal] state.

   Modal data shape:
   {:title \"Title\"
    :content component-or-element
    :size :md
    :on-close optional-fn}"
  []
  (let [modal-state (state/use-state [:ui :modal])
        {:keys [active? data]} modal-state
        {:keys [title content size on-close]} data]
    (when active?
      ($ modal {:title title
                :size (or size :md)
                :on-close on-close}
         content))))

;; =============================================================================
;; CONFIRM DIALOG
;; =============================================================================

(defui confirm-dialog
  "A confirmation dialog using DaisyUI.

   Props:
   - :title - Dialog title
   - :message - Confirmation message
   - :confirm-text - Text for confirm button (default 'Confirm')
   - :cancel-text - Text for cancel button (default 'Cancel')
   - :confirm-variant - Button variant: :primary :error :warning (default :primary)
   - :on-confirm - Callback when confirmed
   - :on-cancel - Callback when cancelled (defaults to hiding modal)"
  [{:keys [title message confirm-text cancel-text confirm-variant on-confirm on-cancel]
    :or {confirm-text "Confirm"
         cancel-text "Cancel"
         confirm-variant :primary}}]
  (let [handle-cancel (or on-cancel #(state/dispatch [:modal/hide]))
        handle-confirm (fn []
                         (when on-confirm (on-confirm))
                         (state/dispatch [:modal/hide]))
        btn-class (case confirm-variant
                    :error "btn-error"
                    :warning "btn-warning"
                    "btn-primary")]
    ($ :div
       (when title
         ($ :h3 {:class "font-bold text-lg mb-2"} title))
       ($ :p {:class "py-4"} message)
       ($ modal-actions
          ($ :button {:class "btn"
                      :onClick handle-cancel}
             cancel-text)
          ($ :button {:class (str "btn " btn-class)
                      :onClick handle-confirm}
             confirm-text)))))

;; =============================================================================
;; HELPER FUNCTIONS
;; =============================================================================

(defn show-confirm!
  "Show a confirmation dialog.

   Options:
   - :title - Dialog title
   - :message - Message to display
   - :on-confirm - Callback when confirmed
   - :confirm-text - Confirm button text
   - :confirm-variant - :primary :error :warning"
  [{:keys [title message on-confirm confirm-text confirm-variant]
    :or {confirm-text "Confirm" confirm-variant :primary}}]
  (state/dispatch
   [:modal/show
    {:title nil ;; Title is rendered inside confirm-dialog
     :content ($ confirm-dialog
                 {:title title
                  :message message
                  :on-confirm on-confirm
                  :confirm-text confirm-text
                  :confirm-variant confirm-variant})}]))

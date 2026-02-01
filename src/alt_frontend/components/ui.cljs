(ns alt-frontend.components.ui
  "Shared UI components using DaisyUI semantics.
   These components provide consistent, themeable UI elements across the alt-frontend."
  (:require [uix.core :refer [defui $]]
            [alt-frontend.components.icons :as icons]))

;; =============================================================================
;; LOADING COMPONENTS
;; =============================================================================

(defui loading-spinner
  "A loading spinner using DaisyUI's loading component.

   Props:
   - :size - :xs :sm :md :lg (default :md)
   - :class - Additional CSS classes
   - :testid - Optional custom data-testid"
  [{:keys [size class testid]
    :or {size :md}}]
  (let [size-class (case size
                     :xs "loading-xs"
                     :sm "loading-sm"
                     :md "loading-md"
                     :lg "loading-lg"
                     "loading-md")]
    ($ :span {:class (str "loading loading-spinner " size-class " " class)
              :data-testid (or testid "loading-spinner")})))

(defui loading-state
  "A centered loading state with optional message.

   Props:
   - :message - Optional loading message"
  [{:keys [message]}]
  ($ :div {:class "flex flex-col items-center justify-center py-12 gap-4"}
     ($ loading-spinner {:size :lg})
     (when message
       ($ :p {:class "text-base-content/60"} message))))

;; =============================================================================
;; ALERT COMPONENTS
;; =============================================================================

(defui error-alert
  "An error alert using DaisyUI's alert component.

   Props:
   - :message - The error message to display
   - :title - Optional title for the error
   - :class - Additional CSS classes"
  [{:keys [message title class]}]
  ($ :div {:role "alert"
           :class (str "alert alert-error " class)}
     ($ icons/error {:class "h-6 w-6 shrink-0"})
     ($ :div
        (when title
          ($ :h3 {:class "font-bold"} title))
        ($ :div {:class "text-sm"} message))))

(defui warning-alert
  "A warning alert using DaisyUI's alert component.

   Props:
   - :message - The warning message to display
   - :title - Optional title for the warning
   - :class - Additional CSS classes"
  [{:keys [message title class]}]
  ($ :div {:role "alert"
           :class (str "alert alert-warning " class)}
     ($ icons/warning {:class "h-6 w-6 shrink-0"})
     ($ :div
        (when title
          ($ :h3 {:class "font-bold"} title))
        ($ :div {:class "text-sm"} message))))

(defui info-alert
  "An info alert using DaisyUI's alert component.

   Props:
   - :message - The info message to display
   - :title - Optional title
   - :class - Additional CSS classes"
  [{:keys [message title class]}]
  ($ :div {:role "alert"
           :class (str "alert alert-info " class)}
     ($ icons/info {:class "h-6 w-6 shrink-0"})
     ($ :div
        (when title
          ($ :h3 {:class "font-bold"} title))
        ($ :div {:class "text-sm"} message))))

;; =============================================================================
;; EMPTY STATE COMPONENTS
;; =============================================================================

(defui empty-state
  "An empty state placeholder with icon and message.

   Props:
   - :title - Main title text
   - :description - Optional description text
   - :icon - Optional icon element
   - :action - Optional action button element
   - :class - Additional CSS classes
   - :testid - Optional custom data-testid"
  [{:keys [title description icon action class testid]}]
  ($ :div {:class (str "flex flex-col items-center justify-center py-12 text-center " class)
           :data-testid (or testid "empty-state")}
     (when icon
       ($ :div {:class "mb-4 text-base-content/30"}
          icon))
     (when-not icon
       ;; Default empty icon
       ($ :div {:class "mb-4 text-base-content/30"}
          ($ icons/document {:class "h-16 w-16"})))
     ($ :h3 {:class "text-lg font-semibold text-base-content"} title)
     (when description
       ($ :p {:class "mt-2 text-base-content/60 max-w-sm"} description))
     (when action
       ($ :div {:class "mt-6"} action))))

;; =============================================================================
;; TABLE COMPONENTS
;; =============================================================================

(defui data-table
  "A data table using DaisyUI's table component.

   Props:
   - :columns - Vector of column definitions, each with :key :header and optional :class
   - :rows - Vector of row data maps
   - :row-key-fn - Function to extract unique key from each row (default :id)
   - :row-testid-prefix - Optional prefix for row testids (e.g., 'agent-row-' produces 'agent-row-myid')
   - :on-row-click - Optional callback when row is clicked
   - :class - Additional CSS classes for table
   - :zebra? - Whether to use zebra striping (default true)
   - :compact? - Whether to use compact padding (default false)
   - :hover? - Whether to highlight rows on hover (default true)
   - :empty-message - Message when no rows (default 'No data available')
   - :testid - Optional custom data-testid"
  [{:keys [columns rows row-key-fn row-testid-prefix on-row-click class zebra? compact? hover? empty-message testid]
    :or {row-key-fn :id
         zebra? true
         compact? false
         hover? true
         empty-message "No data available"}}]
  (let [table-classes (str "table "
                           (when zebra? "table-zebra ")
                           (when compact? "table-xs ")
                           class)]
    ($ :div {:class "overflow-x-auto"}
       ($ :table {:class table-classes
                  :data-testid (or testid "data-table")}
          ($ :thead
             ($ :tr
                (for [{:keys [key header class]} columns]
                  ($ :th {:key key :class class} header))))
          ($ :tbody
             (if (empty? rows)
               ($ :tr
                  ($ :td {:colSpan (count columns)
                          :class "text-center text-base-content/60 py-8"}
                     empty-message))
               (for [row rows]
                 (let [row-id (row-key-fn row)]
                   ($ :tr {:key row-id
                           :data-testid (when row-testid-prefix (str row-testid-prefix row-id))
                           :class (when (and hover? on-row-click) "hover cursor-pointer")
                           :onClick (when on-row-click #(on-row-click row))}
                      (for [{:keys [key render class]} columns]
                        ($ :td {:key key :class class}
                           (if render
                             (render row)
                             (get row key)))))))))))))

;; =============================================================================
;; BADGE COMPONENTS
;; =============================================================================

(defui badge
  "A badge using DaisyUI's badge component.

   Props:
   - :children - Badge content
   - :variant - :primary :secondary :accent :info :success :warning :error :ghost :neutral (default :neutral)
   - :outline? - Whether to use outline style
   - :size - :xs :sm :md :lg (default :md)
   - :class - Additional CSS classes
   - :testid - Optional custom data-testid"
  [{:keys [children variant outline? size class testid]
    :or {variant :neutral size :md}}]
  (let [variant-class (case variant
                        :primary "badge-primary"
                        :secondary "badge-secondary"
                        :accent "badge-accent"
                        :info "badge-info"
                        :success "badge-success"
                        :warning "badge-warning"
                        :error "badge-error"
                        :ghost "badge-ghost"
                        :neutral "badge-neutral"
                        "")
        size-class (case size
                     :xs "badge-xs"
                     :sm "badge-sm"
                     :md ""
                     :lg "badge-lg"
                     "")]
    ($ :span {:class (str "badge " variant-class " " size-class " "
                          (when outline? "badge-outline ") class)
              :data-testid testid}
       children)))

(defui status-badge
  "A status badge for common status values.

   Props:
   - :status - :pending :success :failure :running"
  [{:keys [status]}]
  (case status
    :pending ($ badge {:variant :info} "Pending")
    :success ($ badge {:variant :success} "Success")
    :failure ($ badge {:variant :error} "Failed")
    :running ($ :span {:class "badge badge-info gap-1"}
                ($ loading-spinner {:size :xs})
                "Running")
    ($ badge {:variant :ghost} (str status))))

;; =============================================================================
;; CARD COMPONENTS
;; =============================================================================

(defui card
  "A card using DaisyUI's card component.

   Props:
   - :children - Card content
   - :title - Optional card title
   - :bordered? - Whether to show border (default true)
   - :compact? - Whether to use compact padding
   - :class - Additional CSS classes
   - :body-class - Additional CSS classes for body"
  [{:keys [children title bordered? compact? class body-class]
    :or {bordered? true}}]
  ($ :div {:class (str "card bg-base-100 "
                       (when bordered? "border border-base-300 ")
                       class)}
     ($ :div {:class (str "card-body "
                          (when compact? "p-4 ")
                          body-class)}
        (when title
          ($ :h2 {:class "card-title"} title))
        children)))

;; =============================================================================
;; BUTTON COMPONENTS
;; =============================================================================

(defui button
  "A button using DaisyUI's button component.

   Props:
   - :children - Button content
   - :variant - :primary :secondary :accent :info :success :warning :error :ghost :link (default nil)
   - :outline? - Whether to use outline style
   - :size - :xs :sm :md :lg (default :md)
   - :disabled? - Whether button is disabled
   - :loading? - Whether to show loading state
   - :class - Additional CSS classes
   - :on-click - Click handler
   - :type - Button type (default 'button')"
  [{:keys [children variant outline? size disabled? loading? class on-click type]
    :or {size :md type "button"}}]
  (let [variant-class (when variant
                        (case variant
                          :primary "btn-primary"
                          :secondary "btn-secondary"
                          :accent "btn-accent"
                          :info "btn-info"
                          :success "btn-success"
                          :warning "btn-warning"
                          :error "btn-error"
                          :ghost "btn-ghost"
                          :link "btn-link"
                          ""))
        size-class (case size
                     :xs "btn-xs"
                     :sm "btn-sm"
                     :md ""
                     :lg "btn-lg"
                     "")]
    ($ :button {:type type
                :class (str "btn " variant-class " " size-class " "
                            (when outline? "btn-outline ") class)
                :disabled (or disabled? loading?)
                :onClick on-click}
       (when loading?
         ($ loading-spinner {:size :sm :class "mr-2"}))
       children)))

;; =============================================================================
;; STAT CARD COMPONENT
;; =============================================================================

(defui stat-card
  "A statistics card using DaisyUI's stat component.

   Props:
   - :title - Stat title/label
   - :value - The main value to display
   - :icon - Optional icon element
   - :description - Optional description below value
   - :loading? - Whether to show loading state
   - :class - Additional CSS classes"
  [{:keys [title value icon description loading? class]}]
  ($ :div {:class (str "stat bg-base-100 border border-base-300 rounded-lg " class)}
     (when icon
       ($ :div {:class "stat-figure text-primary"} icon))
     ($ :div {:class "stat-title"} title)
     ($ :div {:class "stat-value text-primary"}
        (if loading?
          ($ :span {:class "loading loading-dots loading-sm"})
          value))
     (when description
       ($ :div {:class "stat-desc"} description))))

;; =============================================================================
;; NAV CARD COMPONENT
;; =============================================================================

(defui nav-card
  "A navigation card for linking to features/sections.

   Props:
   - :title - Card title
   - :description - Short description
   - :icon - Optional icon element
   - :on-click - Click handler
   - :href - Optional href for anchor behavior
   - :class - Additional CSS classes"
  [{:keys [title description icon on-click href class]}]
  ($ :div {:class (str "card bg-base-100 border border-base-300 "
                       "hover:shadow-md transition-shadow cursor-pointer " class)
           :onClick on-click}
     ($ :div {:class "card-body p-4"}
        (when icon
          ($ :div {:class "mb-2 text-primary"} icon))
        ($ :h3 {:class "card-title text-sm"} title)
        (when description
          ($ :p {:class "text-xs text-base-content/60"} description)))))

;; =============================================================================
;; SECTION CARD COMPONENT
;; =============================================================================

(defui section-card
  "A card section with title, icon, and loading/error states.
   Useful for dashboard sections that load data asynchronously.

   Props:
   - :title - Section title
   - :icon - Optional icon element
   - :loading? - Whether content is loading
   - :loading-message - Custom loading message
   - :error - Error message to display
   - :children - Card content
   - :class - Additional CSS classes"
  [{:keys [title icon loading? loading-message error children class]}]
  ($ card {:class class
           :title ($ :div {:class "flex items-center gap-2"}
                     (when icon
                       ($ :span {:class "text-primary"} icon))
                     title)}
     (cond
       loading? ($ loading-state {:message (or loading-message (str "Loading..."))})
       error ($ error-alert {:message error})
       :else children)))

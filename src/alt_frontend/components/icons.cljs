(ns alt-frontend.components.icons
  "Centralized Heroicons for alt-frontend.

   Uses @heroicons/react/24/outline (24px, outline style).
   Browse icons at: https://heroicons.com

   All icons accept :class prop for sizing (default: h-5 w-5).

   Usage:
     (require '[alt-frontend.components.icons :as icons])
     ($ icons/trash {:class \"h-4 w-4 text-error\"})
     ($ icons/chevron-left)  ; uses default size

   Adding new icons:
   1. Find icon at heroicons.com
   2. Add to imports below (use PascalCase from @heroicons/react/24/outline)
   3. Create wrapper: (defui my-icon [props] ($ MyIconComponent (icon-props props)))

   IMPORTANT: Do NOT use inline SVGs in components. Always add icons here first."
  (:require [uix.core :refer [defui $]]
            ["@heroicons/react/24/outline" :refer
             [TrashIcon
              ChevronLeftIcon
              ChevronRightIcon
              ChevronDownIcon
              ChevronUpIcon
              Cog6ToothIcon
              CheckIcon
              XMarkIcon
              MagnifyingGlassIcon
              InformationCircleIcon
              ArrowTopRightOnSquareIcon
              Bars3Icon
              ChartBarIcon
              ClipboardDocumentListIcon
              PlusIcon
              CpuChipIcon
              CircleStackIcon
              BeakerIcon
              HomeIcon
              ExclamationTriangleIcon
              ExclamationCircleIcon
              CheckCircleIcon
              DocumentTextIcon
              ArrowPathIcon
              PlayIcon
              EllipsisVerticalIcon
              QueueListIcon
              ArrowDownTrayIcon
              ArrowUpTrayIcon
              CalendarIcon
              ClockIcon
              Squares2X2Icon
              UserIcon
              FunnelIcon
              AdjustmentsHorizontalIcon]]))

;; =============================================================================
;; ICON WRAPPER
;; =============================================================================

(defn- icon-props
  "Merge default props with user props. Defaults to h-5 w-5 sizing."
  [props]
  (let [default-class "h-5 w-5"
        user-class (:class props)]
    (-> props
        (dissoc :class)
        (assoc :className (or user-class default-class)))))

;; =============================================================================
;; NAVIGATION ICONS
;; =============================================================================

(defui chevron-left [props]
  ($ ChevronLeftIcon (icon-props props)))

(defui chevron-right [props]
  ($ ChevronRightIcon (icon-props props)))

(defui chevron-down [props]
  ($ ChevronDownIcon (icon-props props)))

(defui chevron-up [props]
  ($ ChevronUpIcon (icon-props props)))

(defui arrow-external [props]
  ($ ArrowTopRightOnSquareIcon (icon-props props)))

(defui home [props]
  ($ HomeIcon (icon-props props)))

;; =============================================================================
;; ACTION ICONS
;; =============================================================================

(defui trash [props]
  ($ TrashIcon (icon-props props)))

(defui plus [props]
  ($ PlusIcon (icon-props props)))

(defui check [props]
  ($ CheckIcon (icon-props props)))

(defui x-mark [props]
  ($ XMarkIcon (icon-props props)))

(defui refresh [props]
  ($ ArrowPathIcon (icon-props props)))

(defui play [props]
  ($ PlayIcon (icon-props props)))

(defui download [props]
  ($ ArrowDownTrayIcon (icon-props props)))

(defui upload [props]
  ($ ArrowUpTrayIcon (icon-props props)))

;; =============================================================================
;; UI ICONS
;; =============================================================================

(defui menu [props]
  ($ Bars3Icon (icon-props props)))

(defui search [props]
  ($ MagnifyingGlassIcon (icon-props props)))

(defui settings [props]
  ($ Cog6ToothIcon (icon-props props)))

(defui info [props]
  ($ InformationCircleIcon (icon-props props)))

(defui ellipsis-vertical [props]
  ($ EllipsisVerticalIcon (icon-props props)))

(defui filter-icon [props]
  ($ FunnelIcon (icon-props props)))

(defui adjustments [props]
  ($ AdjustmentsHorizontalIcon (icon-props props)))

;; =============================================================================
;; STATUS/ALERT ICONS
;; =============================================================================

(defui warning [props]
  ($ ExclamationTriangleIcon (icon-props props)))

(defui error [props]
  ($ ExclamationCircleIcon (icon-props props)))

(defui success [props]
  ($ CheckCircleIcon (icon-props props)))

;; =============================================================================
;; DOMAIN ICONS
;; =============================================================================

(defui agent [props]
  ($ CpuChipIcon (icon-props props)))

(defui dataset [props]
  ($ CircleStackIcon (icon-props props)))

(defui evaluator [props]
  ($ BeakerIcon (icon-props props)))

(defui chart [props]
  ($ ChartBarIcon (icon-props props)))

(defui rules [props]
  ($ ClipboardDocumentListIcon (icon-props props)))

(defui document [props]
  ($ DocumentTextIcon (icon-props props)))

(defui queue [props]
  ($ QueueListIcon (icon-props props)))

(defui calendar [props]
  ($ CalendarIcon (icon-props props)))

(defui clock [props]
  ($ ClockIcon (icon-props props)))

(defui grid [props]
  ($ Squares2X2Icon (icon-props props)))

(defui user [props]
  ($ UserIcon (icon-props props)))

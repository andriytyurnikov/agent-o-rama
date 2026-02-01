(ns alt-frontend.routes.agents.$module-id.human-feedback-queues.$queue-id.end
  (:require [uix.core :refer [defui $]]
            [alt-frontend.components.ui :as ui]
            [reitit.frontend.easy :as rfe]))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn url-decode [s]
  (when s (js/decodeURIComponent s)))

;; =============================================================================
;; QUEUE END VIEW
;; =============================================================================

(defui view [{:keys [module-id queue-id]}]
  (let [decoded-queue-id (url-decode queue-id)]
    ($ :div {:class "flex items-center justify-center min-h-96"}
       ($ :div {:class "card bg-base-100 border border-base-300 max-w-lg"}
          ($ :div {:class "card-body text-center"}
             ;; Success icon
             ($ :div {:class "text-6xl mb-4"} "\uD83C\uDF89")

             ;; Title
             ($ :h1 {:class "card-title justify-center text-2xl"}
                "Queue Complete!")

             ;; Message
             ($ :p {:class "text-base-content/70 mb-6"}
                (str "You've reviewed all items in the \"" decoded-queue-id "\" queue. Great work!"))

             ;; Actions
             ($ :div {:class "card-actions justify-center"}
                ($ ui/button {:variant :ghost
                              :on-click #(rfe/push-state :module/human-feedback-queue-detail
                                                         {:module-id module-id
                                                          :queue-id queue-id})}
                   "Back to Queue")
                ($ ui/button {:variant :primary
                              :on-click #(rfe/push-state :module/human-feedback-queues
                                                         {:module-id module-id})}
                   "View All Queues")))))))

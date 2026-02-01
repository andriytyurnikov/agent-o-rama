(ns alt-frontend.router
  (:require [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [reitit.coercion.malli :as rcm]
            [uix.core :as uix :refer [defui $]]
            [alt-frontend.lib.state :as state]
            [alt-frontend.routes.registry :as registry]))

;; Routes are auto-generated from file structure by build hook
;; See: src/alt_frontend/build_hooks/routes.clj
(def routes registry/routes)

(defonce router
  (rf/router routes {:data {:coercion rcm/coercion}}))

(defn on-navigate [match]
  (state/set-state! [:route] match))

(defn start! []
  ;; Use hash-based routing since app is served at /alt/ subpath
  ;; URLs will be /alt/#/agents instead of /alt/agents
  (rfe/start! router on-navigate {:use-fragment true}))

(defn current-view []
  (let [match (state/use-state [:route])]
    (when match
      (let [view (get-in match [:data :view])
            params (merge (get-in match [:path-params])
                          (get-in match [:query-params]))]
        (when view
          ($ view params))))))

(defn href
  "Generate href for route"
  ([name] (href name nil))
  ([name params]
   (rfe/href name params)))

(defn navigate!
  "Navigate to a route programmatically"
  ([name] (navigate! name nil nil))
  ([name params] (navigate! name params nil))
  ([name params query]
   (rfe/push-state name params query)))

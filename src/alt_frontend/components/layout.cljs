(ns alt-frontend.components.layout
  (:require [uix.core :refer [defui $]]
            [alt-frontend.lib.state :as state]
            [alt-frontend.router :as router]
            [alt-frontend.components.modal :as modal]
            [alt-frontend.components.icons :as icons]))

(defui nav-link [{:keys [href label active? testid]}]
  ($ :a {:href href
         :class (if active? "active" "")
         :data-testid testid}
     label))

(defui sidebar-content []
  (let [match (state/use-state [:route])
        route-name (get-in match [:data :name])
        module-id (get-in match [:path-params :module-id])
        agent-name (get-in match [:path-params :agent-name])
        dataset-id (get-in match [:path-params :dataset-id])
        experiment-id (get-in match [:path-params :experiment-id])
        rule-name (get-in match [:path-params :rule-name])]
    ($ :ul {:class "menu bg-base-200 text-base-content min-h-full w-60 p-4"}
       ;; Overview section
       ($ :li {:class "menu-title"} "Overview")
       ($ :li ($ nav-link {:href (router/href :home)
                           :label "Home"
                           :active? (= route-name :home)
                           :testid "nav-home"}))
       ($ :li ($ nav-link {:href (router/href :agents)
                           :label "Agents"
                           :active? (= route-name :agents)
                           :testid "nav-agents"}))

       ;; Module section
       (when module-id
         ($ :<>
            ($ :li {:class "menu-title"} (str "Module: " module-id))
            ($ :li ($ nav-link {:href (router/href :agents-detail {:module-id module-id})
                                :label "Overview"
                                :active? (= route-name :agents-detail)
                                :testid "nav-module-overview"}))
            ($ :li ($ nav-link {:href (router/href :datasets {:module-id module-id})
                                :label "Datasets"
                                :active? (#{:datasets :datasets-detail :examples
                                            :experiments :experiments-detail
                                            :comparative-experiments :comparative-experiments-detail} route-name)
                                :testid "nav-datasets"}))
            ($ :li ($ nav-link {:href (router/href :evaluations {:module-id module-id})
                                :label "Evaluations"
                                :active? (= route-name :evaluations)
                                :testid "nav-evaluations"}))
            ($ :li ($ nav-link {:href (router/href :global-config {:module-id module-id})
                                :label "Global Config"
                                :active? (= route-name :global-config)
                                :testid "nav-global-config"}))))

       ;; Dataset section
       (when dataset-id
         ($ :<>
            ($ :li {:class "menu-title"} (str "Dataset: " dataset-id))
            ($ :li ($ nav-link {:href (router/href :datasets-detail {:module-id module-id :dataset-id dataset-id})
                                :label "Overview"
                                :active? (= route-name :datasets-detail)}))
            ($ :li ($ nav-link {:href (router/href :examples {:module-id module-id :dataset-id dataset-id})
                                :label "Examples"
                                :active? (= route-name :examples)}))
            ($ :li ($ nav-link {:href (router/href :experiments {:module-id module-id :dataset-id dataset-id})
                                :label "Experiments"
                                :active? (#{:experiments :experiments-detail} route-name)}))
            ($ :li ($ nav-link {:href (router/href :comparative-experiments {:module-id module-id :dataset-id dataset-id})
                                :label "Comparative"
                                :active? (#{:comparative-experiments :comparative-experiments-detail} route-name)}))))

       ;; Agent section
       (when agent-name
         ($ :<>
            ($ :li {:class "menu-title"} (str "Agent: " agent-name))
            ($ :li ($ nav-link {:href (router/href :agent-detail {:module-id module-id :agent-name agent-name})
                                :label "Overview"
                                :active? (= route-name :agent-detail)
                                :testid "nav-agent-overview"}))
            ($ :li ($ nav-link {:href (router/href :invocations {:module-id module-id :agent-name agent-name})
                                :label "Invocations"
                                :active? (#{:invocations :invocations-detail} route-name)
                                :testid "nav-invocations"}))
            ($ :li ($ nav-link {:href (router/href :analytics {:module-id module-id :agent-name agent-name})
                                :label "Analytics"
                                :active? (= route-name :analytics)
                                :testid "nav-analytics"}))
            ($ :li ($ nav-link {:href (router/href :rules {:module-id module-id :agent-name agent-name})
                                :label "Rules"
                                :active? (#{:rules :action-log} route-name)
                                :testid "nav-rules"}))
            ($ :li ($ nav-link {:href (router/href :config {:module-id module-id :agent-name agent-name})
                                :label "Config"
                                :active? (= route-name :config)
                                :testid "nav-config"})))))))

(defn route-label [route-name]
  (case route-name
    :datasets-detail "detail"
    :examples "examples"
    :experiments "experiments"
    :experiments-detail "detail"
    :comparative-experiments "comparative"
    :comparative-experiments-detail "detail"
    :evaluations "evaluations"
    :global-config "config"
    :invocations "invocations"
    :invocations-detail "detail"
    :analytics "analytics"
    :rules "rules"
    :action-log "action-log"
    :config "config"
    nil))

(defui breadcrumbs []
  (let [match (state/use-state [:route])
        module-id (get-in match [:path-params :module-id])
        agent-name (get-in match [:path-params :agent-name])
        dataset-id (get-in match [:path-params :dataset-id])
        experiment-id (get-in match [:path-params :experiment-id])
        rule-name (get-in match [:path-params :rule-name])
        invoke-id (get-in match [:path-params :invoke-id])
        route-name (get-in match [:data :name])]
    ($ :div {:class "breadcrumbs text-sm px-4"
             :data-testid "breadcrumbs"}
       ($ :ul
          ($ :li ($ :a {:href (router/href :home)} "Home"))
          (when module-id
            ($ :li ($ :a {:href (router/href :agents-detail {:module-id module-id})} module-id)))
          (when dataset-id
            ($ :li ($ :a {:href (router/href :datasets-detail {:module-id module-id :dataset-id dataset-id})} dataset-id)))
          (when experiment-id
            ($ :li ($ :span experiment-id)))
          (when agent-name
            ($ :li ($ :a {:href (router/href :agent-detail {:module-id module-id :agent-name agent-name})} agent-name)))
          (when invoke-id
            ($ :li ($ :span invoke-id)))
          (when rule-name
            ($ :li ($ :span rule-name)))
          (when-let [label (route-label route-name)]
            ($ :li ($ :span label)))))))

(defui connection-status []
  (let [connected? (state/use-state [:sente :connected?])]
    ($ :div {:class "tooltip tooltip-bottom"
             :data-tip (if connected? "Connected" "Disconnected")
             :data-testid "connection-status"}
       ($ :div {:class (str "badge badge-sm "
                            (if connected? "badge-success" "badge-error"))
                :data-testid "connection-badge"}
          (if connected? "●" "○")))))

(defui navbar []
  ($ :div {:class "navbar bg-base-100 shadow-sm"
           :data-testid "navbar"}
     ($ :div {:class "flex-none lg:hidden"}
        ($ :label {:for "drawer"
                   :class "btn btn-square btn-ghost"
                   :data-testid "mobile-menu-toggle"}
           ($ icons/menu {:class "h-5 w-5"})))
     ($ :div {:class "flex-1"}
        ($ breadcrumbs))
     ($ :div {:class "flex-none"}
        ($ connection-status))))

(defui main-layout [{:keys [children]}]
  ($ :<>
     ($ :div {:class "drawer lg:drawer-open"
              :data-testid "app-layout"}
        ($ :input {:id "drawer" :type "checkbox" :class "drawer-toggle"})
        ($ :div {:class "drawer-content flex flex-col"}
           ($ navbar)
           ($ :main {:class "flex-1 p-4 bg-base-200"}
              children))
        ($ :div {:class "drawer-side"
                 :data-testid "sidebar"}
           ($ :label {:for "drawer" :class "drawer-overlay"})
           ($ sidebar-content)))
     ;; Global modal for dialogs
     ($ modal/global-modal)))

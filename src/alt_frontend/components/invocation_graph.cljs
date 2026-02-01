(ns alt-frontend.components.invocation-graph
  "ReactFlow-based DAG visualization for invocation traces.

   This component displays the execution flow of an agent invocation as an
   interactive directed acyclic graph (DAG). Nodes represent execution steps,
   and edges show the data flow between them.

   Key features:
   - Dagre layout for automatic node positioning
   - Custom node rendering with status indicators
   - Click-to-select node detail panel
   - Zoom, pan, and minimap controls

   Usage:
     ($ invocation-graph {:nodes nodes-map
                          :edges edges-vec
                          :root-invoke-id root-id
                          :on-select-node on-select-fn})"
  (:require [uix.core :as uix :refer [defui $]]
            [alt-frontend.components.ui :as ui]
            [alt-frontend.components.icons :as icons]
            ["@xyflow/react" :refer [ReactFlow Background Controls MiniMap Handle
                                     useNodesState useEdgesState]]
            ["@dagrejs/dagre" :as Dagre]))

;; =============================================================================
;; LAYOUT HELPERS
;; =============================================================================

(defn create-dagre-graph
  "Create a new Dagre graph instance with default configuration."
  []
  (let [g (new (.. Dagre -graphlib -Graph))]
    (.setDefaultEdgeLabel g (fn [] #js {}))
    (.setGraph g #js {:rankdir "TB"    ; Top to Bottom layout
                      :nodesep 50      ; Horizontal spacing between nodes
                      :ranksep 70      ; Vertical spacing between ranks
                      :marginx 20
                      :marginy 20})
    g))

(defn apply-dagre-layout
  "Apply Dagre layout to nodes and edges, returning positioned nodes."
  [nodes-map edges]
  (let [g (create-dagre-graph)
        node-width 180
        node-height 50]

    ;; Add nodes to graph
    (doseq [[id data] nodes-map]
      (.setNode g (str id) #js {:width node-width
                                :height node-height
                                :label (str (:node data))}))

    ;; Add edges to graph
    (doseq [{:keys [source target]} edges]
      (.setEdge g (str source) (str target)))

    ;; Run layout algorithm
    (Dagre/layout g)

    ;; Extract positioned nodes
    (into {}
          (for [[id data] nodes-map]
            (let [positioned (.node g (str id))
                  x (when positioned (.-x positioned))
                  y (when positioned (.-y positioned))]
              [id (assoc data
                         :position-x (or x 0)
                         :position-y (or y 0))])))))

;; =============================================================================
;; EDGE EXTRACTION
;; =============================================================================

(defn extract-edges-from-nodes
  "Extract edges from node emits data.
   Returns a vector of {:source id :target id} maps."
  [nodes-map]
  (->> nodes-map
       (mapcat (fn [[source-id node-data]]
                 (for [emit (:emits node-data)
                       :let [target-id (:invoke-id emit)]
                       :when (and target-id (contains? nodes-map target-id))]
                   {:id (str source-id "->" target-id)
                    :source (str source-id)
                    :target (str target-id)})))
       (distinct)
       (vec)))

;; =============================================================================
;; NODE STATUS HELPERS
;; =============================================================================

(defn node-status
  "Determine the status of a node based on its data."
  [node-data is-invocation-complete]
  (let [start-time (:start-time-millis node-data)
        finish-time (:finish-time-millis node-data)
        result (:result node-data)
        exceptions (:exceptions node-data)
        in-progress? (and start-time (not finish-time))]
    (cond
      ;; Node is stuck (in-progress but invocation completed)
      (and in-progress? is-invocation-complete) :stuck
      ;; Node is currently running
      in-progress? :running
      ;; Node has exceptions
      (seq exceptions) :error
      ;; Node completed successfully
      result :success
      ;; Node is waiting/pending
      :else :pending)))

(defn starter-node?
  "Check if a node is a starter node (started an aggregation)."
  [node-data]
  (some? (:started-agg? node-data)))

(defn agg-node?
  "Check if a node is an aggregation node."
  [node-data]
  (some? (:agg-state node-data)))

;; =============================================================================
;; CUSTOM NODE COMPONENT
;; =============================================================================

(defui graph-node
  "Custom ReactFlow node component with status indicators."
  [{:keys [data id selected]}]
  (let [node-data (js->clj data :keywordize-keys true)
        label (:label node-data)
        status (:status node-data)
        is-starter (:is-starter node-data)
        is-agg (:is-agg node-data)
        has-human-request (:has-human-request node-data)
        has-exceptions (:has-exceptions node-data)

        ;; Determine node colors
        base-classes (cond
                       is-agg "bg-warning text-warning-content border-warning"
                       is-starter "bg-success text-success-content border-success"
                       (= status :running) "bg-info text-info-content border-info"
                       (= status :error) "bg-error text-error-content border-error"
                       (= status :success) "bg-base-100 text-base-content border-success"
                       (= status :stuck) "bg-error/50 text-error-content border-error"
                       :else "bg-base-100 text-base-content border-base-300")

        selection-classes (when selected
                            "ring-2 ring-primary ring-offset-2")]

    ($ :div {:className "relative"}
       ;; Node body
       ($ :div {:className (str "p-3 rounded-lg border-2 shadow-md transition-all duration-200 "
                                base-classes " " selection-classes)
                :style #js {:width "180px" :minHeight "44px"}}

          ;; Node label
          ($ :div {:className "truncate text-sm font-medium"
                   :title label}
             label)

          ;; Status indicator in corner
          ($ :div {:className "absolute -top-2 -right-2 flex items-center gap-0.5"}
             ;; Running spinner
             (when (= status :running)
               ($ :span {:className "w-4 h-4 loading loading-spinner loading-xs"}))

             ;; Stuck indicator
             (when (= status :stuck)
               ($ :span {:className "badge badge-error badge-xs"
                         :title "Node terminated due to max retries"}
                  "×"))

             ;; Human request indicator
             (when has-human-request
               ($ :span {:className "text-xs"
                         :title "Awaiting human input"}
                  "🙋"))

             ;; Exception indicator
             (when (and has-exceptions (not= status :stuck))
               ($ :span {:className "badge badge-warning badge-xs"
                         :title "Has exceptions"}
                  "!"))

             ;; Success indicator
             (when (and (= status :success) (not is-starter) (not is-agg))
               ($ :span {:className "badge badge-success badge-xs"} "✓"))))

       ;; ReactFlow handles for edges
       ($ Handle {:type "target"
                  :position "top"
                  :className "w-2 h-2 !bg-base-300"})
       ($ Handle {:type "source"
                  :position "bottom"
                  :className "w-2 h-2 !bg-base-300"}))))

;; =============================================================================
;; NODE DETAIL PANEL
;; =============================================================================

(defui node-detail-panel
  "Panel showing details of the selected node."
  [{:keys [node-data on-close]}]
  (when node-data
    (let [node-name (:node node-data)
          input (:input node-data)
          result (:result node-data)
          exceptions (:exceptions node-data)
          emits (:emits node-data)
          start-time (:start-time-millis node-data)
          finish-time (:finish-time-millis node-data)
          nested-ops (:nested-ops node-data)
          human-request (:human-request node-data)]

      ($ ui/card {:title (str "Node: " node-name)
                  :class "mt-4"}
         ($ :div {:class "space-y-4"}

            ;; Close button
            ($ :div {:class "flex justify-end"}
               ($ :button {:class "btn btn-ghost btn-sm btn-square"
                           :onClick on-close}
                  ($ icons/x-mark {:class "h-4 w-4"})))

            ;; Timing
            (when (or start-time finish-time)
              ($ :div {:class "grid grid-cols-2 gap-2 text-sm"}
                 (when start-time
                   ($ :div
                      ($ :span {:class "text-base-content/60"} "Started: ")
                      ($ :span {:class "font-mono"} (.toLocaleString (js/Date. start-time)))))
                 (when finish-time
                   ($ :div
                      ($ :span {:class "text-base-content/60"} "Finished: ")
                      ($ :span {:class "font-mono"} (.toLocaleString (js/Date. finish-time)))))
                 (when (and start-time finish-time)
                   ($ :div {:class "col-span-2"}
                      ($ :span {:class "text-base-content/60"} "Duration: ")
                      ($ :span {:class "font-mono"} (str (- finish-time start-time) "ms"))))))

            ;; Human request
            (when human-request
              ($ :div {:class "alert alert-warning"}
                 ($ :div
                    ($ :div {:class "font-medium"} "Human Input Required")
                    ($ :div {:class "text-sm"} (:prompt human-request)))))

            ;; Input
            (when input
              ($ :div
                 ($ :div {:class "text-sm font-medium text-base-content/70 mb-1"} "Input")
                 ($ :pre {:class "bg-base-200 p-3 rounded-lg text-xs font-mono overflow-x-auto max-h-32"}
                    (try
                      (js/JSON.stringify (clj->js input) nil 2)
                      (catch js/Error _ (pr-str input))))))

            ;; Exceptions
            (when (seq exceptions)
              ($ :div
                 ($ :div {:class "text-sm font-medium text-error mb-1"}
                    (str "Exceptions (" (count exceptions) ")"))
                 (for [[idx exc-str] (map-indexed vector exceptions)]
                   ($ :pre {:key idx
                            :class "bg-error/10 p-3 rounded-lg text-xs font-mono overflow-x-auto max-h-32 mb-2"}
                      exc-str))))

            ;; Nested operations
            (when (seq nested-ops)
              ($ :div
                 ($ :div {:class "text-sm font-medium text-base-content/70 mb-1"}
                    (str "Operations (" (count nested-ops) ")"))
                 ($ :div {:class "space-y-2"}
                    (for [[idx op] (map-indexed vector nested-ops)]
                      (let [op-type (:type op)
                            info (:info op)
                            op-start (:start-time-millis op)
                            op-end (:finish-time-millis op)]
                        ($ :div {:key idx
                                 :class "bg-base-200 p-2 rounded text-sm"}
                           ($ :div {:class "flex justify-between items-center"}
                              ($ :span {:class "badge badge-outline badge-sm"} op-type)
                              (when (and op-start op-end)
                                ($ :span {:class "text-xs text-base-content/60"}
                                   (str (- op-end op-start) "ms"))))
                           (when (:objectName info)
                             ($ :div {:class "font-mono text-xs mt-1"}
                                (:objectName info)))))))))

            ;; Result
            (when result
              ($ :div
                 ($ :div {:class "text-sm font-medium text-base-content/70 mb-1"} "Result")
                 ($ :pre {:class "bg-success/10 p-3 rounded-lg text-xs font-mono overflow-x-auto max-h-32"}
                    (try
                      (js/JSON.stringify (clj->js result) nil 2)
                      (catch js/Error _ (pr-str result))))))

            ;; Emits
            (when (seq emits)
              ($ :div
                 ($ :div {:class "text-sm font-medium text-base-content/70 mb-1"}
                    (str "Emits (" (count emits) ")"))
                 ($ :div {:class "space-y-1"}
                    (for [[idx emit] (map-indexed vector emits)]
                      ($ :div {:key idx
                               :class "bg-base-200 p-2 rounded text-sm font-mono"}
                         ($ :span {:class "text-base-content/60"} "→ ")
                         (:node-name emit)))))))))))

;; =============================================================================
;; MAIN GRAPH COMPONENT
;; =============================================================================

(def node-types
  "Custom node types for ReactFlow."
  #js {"custom" (uix.core/as-react graph-node)})

(defui invocation-graph
  "Interactive DAG visualization of an invocation trace.

   Props:
   - :nodes - Map of node-id -> node-data
   - :root-invoke-id - The root node ID
   - :is-complete - Whether the invocation is complete
   - :on-select-node - Callback when a node is selected"
  [{:keys [nodes root-invoke-id is-complete on-select-node]}]
  (let [;; Local state for selected node
        [selected-node-id set-selected-node-id] (uix/use-state nil)

        ;; Prepare edges from node emits
        edges (extract-edges-from-nodes nodes)

        ;; Apply Dagre layout
        positioned-nodes (apply-dagre-layout nodes edges)

        ;; Convert to ReactFlow format
        flow-nodes (->> positioned-nodes
                        (map (fn [[id data]]
                               {:id (str id)
                                :type "custom"
                                :position #js {:x (:position-x data)
                                               :y (:position-y data)}
                                :draggable false
                                :data (clj->js {:label (:node data)
                                                :node-id id
                                                :status (node-status data is-complete)
                                                :is-starter (starter-node? data)
                                                :is-agg (agg-node? data)
                                                :has-human-request (some? (:human-request data))
                                                :has-exceptions (seq (:exceptions data))})}))
                        (into [])
                        (clj->js))

        ;; Convert edges to ReactFlow format
        flow-edges (->> edges
                        (map (fn [{:keys [id source target]}]
                               {:id id
                                :source source
                                :target target
                                :type "default"
                                :animated false
                                :style #js {:strokeWidth 2
                                            :stroke "#a5b4fc"}}))
                        (clj->js))

        ;; ReactFlow state
        [rf-nodes set-rf-nodes on-nodes-change] (useNodesState flow-nodes)
        [rf-edges set-rf-edges on-edges-change] (useEdgesState flow-edges)

        ;; Update ReactFlow state when nodes/edges change
        _ (uix/use-effect
           (fn []
             (set-rf-nodes flow-nodes)
             (set-rf-edges flow-edges)
             js/undefined)
           [flow-edges set-rf-edges flow-nodes set-rf-nodes nodes edges])

        ;; Get selected node data
        selected-node-data (when selected-node-id
                             (get positioned-nodes selected-node-id))

        ;; Handle node click
        handle-node-click (fn [_ node]
                            (let [node-id (.-id node)
                                  parsed-id (try
                                              (uuid node-id)
                                              (catch js/Error _ node-id))]
                              (set-selected-node-id parsed-id)
                              (when on-select-node
                                (on-select-node parsed-id))))]

    (if (empty? nodes)
      ($ ui/empty-state
         {:title "No graph data"
          :description "The invocation graph is empty."})

      ($ :div {:class "space-y-4"}
         ;; Graph container
         ($ :div {:class "w-full h-96 border border-base-300 rounded-lg overflow-hidden bg-base-200"}
            ($ ReactFlow {:nodes rf-nodes
                          :edges rf-edges
                          :onNodesChange on-nodes-change
                          :onEdgesChange on-edges-change
                          :onNodeClick handle-node-click
                          :nodeTypes node-types
                          :proOptions #js {:hideAttribution true}
                          :fitView true
                          :defaultEdgeOptions #js {:type "default"
                                                   :style #js {:strokeWidth 2
                                                               :stroke "#a5b4fc"}}}
               ($ MiniMap {:position "bottom-right"
                           :pannable true
                           :zoomable true
                           :className "!bg-base-100"})
               ($ Background {:variant "dots"
                              :gap 16
                              :size 1
                              :color "#e0e0e0"})
               ($ Controls {:className "!bg-base-100 !border-base-300"})))

         ;; Selected node detail panel
         (when selected-node-data
           ($ node-detail-panel
              {:node-data selected-node-data
               :on-close #(set-selected-node-id nil)}))))))

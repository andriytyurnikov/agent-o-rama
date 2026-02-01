(ns alt-frontend.lib.state
  (:require [uix.core :as uix]
            [com.rpl.specter :as s]))

;; =============================================================================
;; APP STATE: The Single Source of Truth
;; =============================================================================

(def initial-state
  {:route nil
   :sidebar {:collapsed? false}
   :sente {:connected? false}
   :queries {}
   :ui {:modal nil}
   :streaming {:buffers {}}})

(defonce app-state (atom initial-state))

;; Expose state for debugging in dev builds
(when ^boolean js/goog.DEBUG
  (add-watch app-state :console-logger
             (fn [_ _ _ new-state]
               (aset js/window "altState" (clj->js new-state)))))

;; =============================================================================
;; EVENT SYSTEM
;; =============================================================================

;; Registry for event handlers
(defonce event-handlers (atom {}))

(defn reg-event
  "Register an event handler. Handler should return a Specter path (navigator)
   that will be applied to the current app-state via s/multi-transform. Handlers
   may return nil to indicate no state change is needed."
  [event-id handler-fn]
  (if (contains? @event-handlers event-id)
    (js/console.warn "Event handler already registered for event:" event-id)
    (swap! event-handlers assoc event-id handler-fn)))

(defn dispatch
  "Dispatch an event to update app-state. Event is a vector [event-id & args].
   The handler must return a Specter path navigator suitable for s/multi-transform."
  [event]
  (let [event-id (first event)
        event-args (rest event)
        handler (get @event-handlers event-id)]
    (if handler
      (try
        (let [current-state @app-state
              specter-path (apply handler current-state event-args)]
          ;; Allow handlers to return nil to indicate no state change is needed
          (when specter-path
            (let [new-state (s/multi-transform specter-path current-state)]
              (reset! app-state new-state))))
        (catch :default e
          (js/console.error "Error in event handler" event-id ":" e)
          (throw e)))
      (js/console.warn "No handler registered for event:" event-id))))

;; =============================================================================
;; SUBSCRIPTIONS (REACTIVE STATE ACCESS)
;; =============================================================================

(defn path->specter-path
  "Converts a path vector (which may contain UUID objects) into a Specter path.
   UUIDs are wrapped with s/keypath since Specter can't use them directly as navigators.
   Other values (keywords, strings) are left as-is."
  [path]
  (mapv (fn [segment]
          (if (uuid? segment)
            (s/keypath segment)
            segment))
        path))

(defn use-sub
  "Subscribe to a value at the given path in app-state.
   Component will re-render only when the value at that path changes."
  [path]
  (let [specter-path (uix/use-memo
                      (fn [] (path->specter-path path))
                      [path])
        extract-value (uix/use-callback
                       (fn [db] (s/select-one specter-path db))
                       [specter-path])
        [value set-value] (uix/use-state (fn [] (extract-value @app-state)))]

    ;; Note: We intentionally don't include `value` in deps - including it would
    ;; cause infinite re-renders since setting value triggers this effect again.
    ;; The effect only needs to re-run when extract-value changes (i.e., path changes).
    (uix/use-effect ^:lint/disable
     (fn []
       (let [watch-key (gensym "sub-")]
         (add-watch app-state watch-key
                    (fn [_ _ old-state new-state]
                      (let [old-val (extract-value old-state)
                            new-val (extract-value new-state)]
                        (when (not= old-val new-val)
                          (set-value new-val)))))

         ;; Sync with current state immediately after adding watch
         (let [current-value (extract-value @app-state)]
           (when (not= value current-value)
             (set-value current-value)))

         ;; Cleanup function
         (fn []
           (remove-watch app-state watch-key))))
                    [extract-value])

    value))

;; =============================================================================
;; LEGACY HELPERS (for backwards compatibility during migration)
;; =============================================================================

(defn use-state
  "DEPRECATED: Use use-sub instead.
   Subscribe to app state at path using simple get-in."
  [path]
  (let [[val set-val!] (uix/use-state (get-in @app-state path))]
    (uix/use-effect
     (fn []
       (let [key (gensym "state-watcher")]
         (add-watch app-state key
                    (fn [_ _ old new]
                      (let [old-val (get-in old path)
                            new-val (get-in new path)]
                        (when (not= old-val new-val)
                          (set-val! new-val)))))
         #(remove-watch app-state key)))
     [path])
    val))

(defn set-state! [path val]
  "DEPRECATED: Use dispatch with :db/set-value event instead."
  (swap! app-state assoc-in path val))

(defn update-state! [path f & args]
  "DEPRECATED: Use dispatch with :db/update-value event instead."
  (apply swap! app-state update-in path f args))

;; =============================================================================
;; CORE EVENT HANDLERS
;; =============================================================================

;; Generic state update events
;; Usage: (dispatch [:db/set-value [:some :path] value])
(reg-event :db/set-value
           (fn [_db path value]
             (into (path->specter-path path) [(s/terminal-val value)])))

;; Usage: (dispatch [:db/update-value [:some :path] update-fn])
(reg-event :db/update-value
           (fn [_db path update-fn]
             (into (path->specter-path path) [(s/terminal update-fn)])))

;; Usage: (dispatch [:db/set-values [[:path1] v1] [[:path2 :k] v2] ...])
(reg-event :db/set-values
           (fn [_db & path-value-pairs]
             (apply s/multi-path
                    (map (fn [[path value]]
                           (into (path->specter-path path) [(s/terminal-val value)]))
                         path-value-pairs))))

;; =============================================================================
;; ROUTING EVENTS
;; =============================================================================

(reg-event :route/navigated
           (fn [_db new-match]
             [:route (s/terminal-val new-match)]))

;; =============================================================================
;; QUERY EVENTS - For query hooks
;; =============================================================================

(reg-event :query/fetch-start
           (fn [_db {:keys [query-key]}]
             (into (path->specter-path (into [:queries] query-key))
                   [(s/terminal (fn [current-state]
                                  (let [has-data? (some? (:data current-state))]
                                    (-> current-state
                                        (assoc :error nil
                                               :fetching? true)
                                        (cond-> (not has-data?)
                                          (assoc :status :loading))))))])))

(reg-event :query/fetch-success
           (fn [_db {:keys [query-key data]}]
             (into (path->specter-path (into [:queries] query-key))
                   [(s/terminal (fn [_]
                                  {:status :success
                                   :data data
                                   :error nil
                                   :fetching? false}))])))

(reg-event :query/fetch-error
           (fn [_db {:keys [query-key error]}]
             (into (path->specter-path (into [:queries] query-key))
                   [(s/terminal (fn [current-state]
                                  (-> current-state
                                      (assoc :error error
                                             :fetching? false)
                                      (cond-> (nil? (:data current-state))
                                        (assoc :status :error)))))])))

(reg-event :query/invalidate
           (fn [_db {:keys [query-key-pattern]}]
             (let [current-queries (get @app-state :queries {})
                   all-query-keys (letfn [(collect-keys [m prefix acc]
                                            (reduce-kv
                                             (fn [a k v]
                                               (let [new-prefix (conj prefix k)]
                                                 (cond
                                                   (and (map? v) (contains? v :status))
                                                   (conj a (vec new-prefix))

                                                   (map? v)
                                                   (collect-keys v new-prefix a)
                                                   :else a)))
                                             acc
                                             m))]
                                    (collect-keys current-queries [] []))
                   matching-keys (filter
                                  (fn [query-key]
                                    (cond
                                      (keyword? query-key-pattern)
                                      (= (first query-key) query-key-pattern)

                                      (vector? query-key-pattern)
                                      (and (>= (count query-key) (count query-key-pattern))
                                           (= query-key-pattern (subvec query-key 0 (count query-key-pattern))))

                                      (fn? query-key-pattern)
                                      (query-key-pattern query-key)

                                      :else false))
                                  all-query-keys)]
               (when (seq matching-keys)
                 (apply s/multi-path
                        (map (fn [query-key]
                               (into (path->specter-path (into [:queries] query-key))
                                     [:should-refetch? (s/terminal-val true)]))
                             matching-keys))))))

;; =============================================================================
;; STREAMING EVENTS
;; =============================================================================

;; Handle incoming chunks pushed from server via WebSocket
(reg-event :stream/update
  (fn [_db {:keys [stream-id new-chunks reset? complete?]}]
    (let [update-path [:streaming :buffers stream-id]]
      (if reset?
        ;; Reset: replace chunks entirely (node retry happened)
        (s/multi-path
         [update-path :chunks (s/terminal-val new-chunks)]
         [update-path :reset-count (s/terminal #(inc (or % 0)))]
         [update-path :complete? (s/terminal-val complete?)])
        ;; Normal update: append new chunks
        (s/multi-path
         [update-path :chunks (s/terminal #(into (or % []) new-chunks))]
         [update-path :complete? (s/terminal-val complete?)])))))

;; Clear buffer when component unmounts
(reg-event :stream/cleanup
  (fn [_db {:keys [stream-id]}]
    [:streaming :buffers (s/terminal #(dissoc % stream-id))]))

;; =============================================================================
;; DEBUGGING HELPERS
;; =============================================================================

(defn get-state [] @app-state)

(defn reset-state!
  "Reset app-state to initial state. Useful for development."
  []
  (reset! app-state initial-state))

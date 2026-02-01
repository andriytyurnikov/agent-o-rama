(ns alt-frontend.lib.ws.sente
  (:require
   [taoensso.sente :as sente]
   [taoensso.sente.packers.transit :as sente-transit]
   [alt-frontend.lib.state :as state]))

;; Transit packer for serialization (must match server)
(def transit-packer
  (sente-transit/get-transit-packer
   :json
   {:handlers
    {"u" (reify Object
           (tag [_ _] "u")
           (rep [_ v] (str v))
           (stringRep [_ v] (str v)))}}
   {:handlers
    {"u" (fn [v] (uuid v))}}))

(defn firefox? []
  (when (and (exists? js/navigator)
             (.-userAgent js/navigator))
    (re-find #"Firefox" (.-userAgent js/navigator))))

;; Channel socket client
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk"
       nil
       {:type (if (firefox?) :ajax :auto)
        :packer transit-packer})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

;; Event router
(defmulti -event-msg-handler :id)

(defn event-msg-handler [ev-msg]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default
  [{:keys [id ?data]}]
  (js/console.log (str "Unhandled event: " id) ?data))

(defmethod -event-msg-handler :chsk/ws-ping [_])
(defmethod -event-msg-handler :chsk/ws-pong [_])

(defmethod -event-msg-handler :chsk/state
  [{:keys [?data]}]
  (let [[_ new-state] ?data
        connected? (boolean (:open? new-state))]
    (state/set-state! [:sente :connected?] connected?)))

(defmethod -event-msg-handler :chsk/handshake
  [_]
  (state/set-state! [:sente :connected?] true))

(defmethod -event-msg-handler :chsk/recv
  [{:keys [?data]}]
  (let [[event-id event-data] ?data]
    (case event-id
      :stream/update (state/dispatch [:stream/update event-data])
      (js/console.log "Server push:" event-id event-data))))

;; Handle streaming updates pushed from server
(defmethod -event-msg-handler :stream/update
  [{:keys [?data]}]
  (state/dispatch [:stream/update ?data]))

;; Router lifecycle
(defonce router_ (atom nil))

(defn stop-router! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router! ch-chsk event-msg-handler)))

;; Request helpers
(defn request!
  "Make a request with callback. Returns nil."
  ([event-vec callback]
   (request! event-vec 10000 callback))
  ([event-vec timeout-ms callback]
   (chsk-send! event-vec timeout-ms
               (fn [reply]
                 (when callback
                   (callback reply))))))

(defn push!
  "Send one-way message to server."
  [event-vec]
  (chsk-send! event-vec))

(defn init! []
  (start-router!)
  (js/setTimeout
   (fn []
     (when-let [current-state @chsk-state]
       (state/set-state! [:sente :connected?] (boolean (:open? current-state)))))
   100))

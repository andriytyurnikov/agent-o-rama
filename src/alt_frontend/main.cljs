(ns alt-frontend.main
  (:require [uix.core :refer [defui $]]
            [uix.dom :as uix.dom]
            [alt-frontend.components.layout :as layout]
            [alt-frontend.components.views :as views]
            [alt-frontend.router :as router]
            [alt-frontend.lib.ws.sente :as sente]))

(defui app []
  ($ layout/main-layout
     (or (router/current-view)
         ($ views/not-found-view))))

(defn ^:export init []
  (sente/init!)
  (router/start!)
  (uix.dom/render-root ($ app)
                       (uix.dom/create-root (js/document.getElementById "root"))))

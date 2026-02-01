(ns alt-frontend.components.views
  (:require [uix.core :refer [defui $]]))

(defui not-found-view []
  ($ :div {:class "hero min-h-[50vh]"}
     ($ :div {:class "hero-content text-center"}
        ($ :div
           ($ :h1 {:class "text-4xl font-bold"} "404")
           ($ :p {:class "py-4"} "Route not found")
           ($ :a {:href "#/" :class "btn btn-primary"} "Go Home")))))

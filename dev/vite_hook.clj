(ns vite-hook
  "Shadow-cljs build hook: runs Vite CSS build.
   Dev mode: one-shot build + background watcher for live updates.
   Release/compile: one-shot build only."
  (:import [java.lang ProcessBuilder ProcessBuilder$Redirect]))

(defonce ^:private state (atom {}))

(defn- clean-old-css!
  "Removes previously built CSS files and manifest from resource/public/."
  []
  (doseq [f (.listFiles (java.io.File. "resource/public"))]
    (let [name (.getName f)]
      (when (or (and (.startsWith name "main.") (.endsWith name ".css"))
                (= name "css-manifest.json"))
        (.delete f)))))

(defn- run-vite-build!
  "Runs `npx vite build` synchronously. Returns true on success.
   In dev mode, logs warning on failure instead of crashing the REPL."
  []
  (clean-old-css!)
  (let [proc (-> (ProcessBuilder. ["npx" "vite" "build" "--mode" "development"])
                 (.redirectError ProcessBuilder$Redirect/INHERIT)
                 (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                 (.start))
        exit (.waitFor proc)]
    (if (zero? exit)
      true
      (do (println "[vite] CSS build failed (exit" exit ") — fix your CSS and save again")
          false))))

(defn- start-watcher!
  "Starts `vite build --watch` in the background for live CSS updates."
  []
  (when-let [^Process old (:process @state)]
    (when (.isAlive old) (.destroyForcibly old)))
  (let [proc (-> (ProcessBuilder. ["npx" "vite" "build" "--watch" "--mode" "development"])
                 (.redirectError ProcessBuilder$Redirect/INHERIT)
                 (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                 (.start))]
    (swap! state assoc :process proc)
    (when-not (:shutdown-hook? @state)
      (.addShutdownHook (Runtime/getRuntime)
        (Thread. #(when-let [^Process p (:process @state)]
                    (when (.isAlive p) (.destroyForcibly p)))))
      (swap! state assoc :shutdown-hook? true))))

(defn build-css
  "Shadow-cljs build hook. Compiles CSS via Vite at :configure stage.
   In dev mode, also starts a background watcher for live updates."
  {:shadow.build/stage :configure}
  [build-state & _args]
  (let [dev? (= :dev (:shadow.build/mode build-state))
        ok?  (run-vite-build!)]
    (when (and (not ok?) (not dev?))
      (throw (ex-info "[vite] CSS build failed" {})))
    (when dev?
      (start-watcher!)))
  build-state)

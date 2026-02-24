(ns tailwind-hook
  "Shadow-cljs build hooks for Tailwind CSS compilation.
   Integrates Tailwind CLI into the shadow-cljs build lifecycle so that
   existing dev/release commands work unchanged."
  (:require [clojure.java.io :as io])
  (:import [java.lang ProcessBuilder ProcessBuilder$Redirect]
           [java.math BigInteger]
           [java.security MessageDigest]
           [java.nio.file Files StandardCopyOption]))

(def ^:private css-input  "src/cljs/com/rpl/agent_o_rama/ui/css/main.css")
(def ^:private css-output "resource/public/main.css")

(defn- run-tailwind!
  "Run tailwindcss CLI with given args. Blocks until complete.
   Returns exit code."
  [& args]
  (let [cmd (into ["npx" "@tailwindcss/cli" "-i" css-input "-o" css-output] args)
        proc (-> (ProcessBuilder. ^java.util.List cmd)
                 (.redirectError ProcessBuilder$Redirect/INHERIT)
                 (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                 (.start))]
    (.waitFor proc)))

(defonce ^:private tailwind-state
  (atom {:process nil :hook-registered? false}))

(defn start-watch
  "Build hook: starts tailwindcss --watch in dev mode.
   Runs at :configure stage (once when shadow-cljs watch starts).
   Kills any previous watch process on restart and registers a single
   JVM shutdown hook via defonce to avoid hook accumulation."
  {:shadow.build/stage :configure}
  [build-state & _args]
  (if (= :dev (:shadow.build/mode build-state))
    (do
      ;; Kill existing process if running (handles watch restart in same JVM)
      (when-let [^Process old-proc (:process @tailwind-state)]
        (when (.isAlive old-proc)
          (println "[tailwind] Stopping previous CSS watch process...")
          (.destroyForcibly old-proc)
          (.waitFor old-proc)))
      (println "[tailwind] Starting CSS watch process...")
      (let [cmd ["npx" "@tailwindcss/cli" "-i" css-input "-o" css-output "--watch"]
            proc (-> (ProcessBuilder. ^java.util.List cmd)
                     (.redirectError ProcessBuilder$Redirect/INHERIT)
                     (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                     (.start))]
        ;; Register shutdown hook exactly once
        (when-not (:hook-registered? @tailwind-state)
          (.addShutdownHook (Runtime/getRuntime)
            (Thread. (fn []
                       (when-let [^Process p (:process @tailwind-state)]
                         (when (.isAlive p) (.destroyForcibly p))))))
          (swap! tailwind-state assoc :hook-registered? true))
        (swap! tailwind-state assoc :process proc)
        (assoc build-state ::tailwind-process proc)))
    ;; Not dev mode, no-op
    build-state))

(defn- md5-hex
  "Returns the hex MD5 digest of the given byte array."
  ^String [^bytes content]
  (let [digest (.digest (MessageDigest/getInstance "MD5") content)]
    (format "%032x" (BigInteger. 1 digest))))

(defn- hash-and-rename!
  "Hashes the compiled CSS file, renames to main.HASH.css, and writes
   css-manifest.edn in the same format as shadow-cljs manifest.edn:
   [{:output-name \"main.HASH.css\"}]"
  []
  (let [css-file (io/file css-output)
        content  (Files/readAllBytes (.toPath css-file))
        hash     (subs (md5-hex content) 0 8)
        hashed   (str "resource/public/main." hash ".css")
        target   (io/file hashed)]
    (Files/move (.toPath css-file) (.toPath target)
                (into-array [StandardCopyOption/REPLACE_EXISTING]))
    (spit "resource/public/css-manifest.edn"
          (pr-str [{:output-name (str "main." hash ".css")}]))
    (println "[tailwind] CSS hashed:" (.getName target))))

(defn compile-release
  "Build hook: compiles CSS at :flush stage (after JS output is written).
   In release mode: minified and content-hashed. In other modes: plain build."
  {:shadow.build/stage :flush}
  [build-state & _args]
  (let [release? (= :release (:shadow.build/mode build-state))
        args     (if release? ["--minify"] [])]
    (println "[tailwind] Compiling CSS" (if release? "(release, minified)" "(dev)") "...")
    (let [exit-code (apply run-tailwind! args)]
      (when-not (zero? exit-code)
        (throw (ex-info (str "[tailwind] tailwindcss exited with code " exit-code)
                        {:exit-code exit-code}))))
    (when release?
      (hash-and-rename!)))
  build-state)

(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'drw/dispute-resolution-workbench)
(def version "0.1.0-SNAPSHOT")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def uber-file "target/dispute-resolution-workbench.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :ns-compile '[drw.core]})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'drw.core}))

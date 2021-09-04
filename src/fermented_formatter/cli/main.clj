(ns fermented-formatter.cli.main
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [fermented-formatter.cli :as tasks])
  (:import
   (java.io PushbackReader))
  (:gen-class))

(defn -main
  [& args]
  (prn (edn/read (PushbackReader. (io/reader (io/resource "fermented_formatter/config.edn"))))))

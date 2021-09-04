(ns build
  (:require
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]))

(def lib-coord 'org.suskalo/fermented-formatter)
(def version (format "0.1.%s-SNAPSHOT" (b/git-count-revs nil)))

(def compiled-namespaces '[fermented-formatter.cli fermented-formatter.cli.main])
(def main-class 'fermented-formatter.cli.main)

(def source-dirs ["src"])
(def resource-dirs ["resources"])

(def target-dir "target/")
(def class-dir (str target-dir "classes/"))

(def basis (b/create-basis {:project "deps.edn"}))

(def jar-file (str target-dir "fermented-formatter.jar"))
(def uberjar-file (str target-dir "fermented-formatter-standalone.jar"))

(defn- exists?
  [& path-components]
  (.exists ^java.io.File (apply io/file path-components)))

(defn- write-pom
  [opts]
  (when-not (exists? target-dir "pom.xml")
    (b/write-pom {:basis basis
                  :class-dir class-dir
                  :lib lib-coord
                  :version version
                  :scm {:url "https://github.com/IGJoshua/fermented-formatter"
                        :connection "scm:git:git://github.com/IGJoshua/fermented-formatter.git"
                        :developerConnection "scm:git:ssh://git@github.com/IGJoshua/fermented-formatter.git"
                        :tag (str "v" version)}
                  :src-dirs source-dirs
                  :resource-dirs resource-dirs}))
  opts)

(defn- write-jar
  [opts]
  (when-not (exists? target-dir jar-file)
    (b/copy-dir {:target-dir class-dir
                 :src-dirs (concat source-dirs resource-dirs)})
    (b/jar {:class-dir class-dir
            :jar-file jar-file}))
  opts)

(defn- compile-clojure
  [opts]
  (when-not (exists? class-dir)
    (b/compile-clj {:basis basis
                    :class-dir class-dir
                    :src-dirs source-dirs
                    :ns-compile compiled-namespaces
                    :compile-opts {:elide-meta [:doc :file :line]
                                   :direct-linking true}}))
  opts)

(defn- write-uberjar
  [opts]
  (when-not (exists? uberjar-file)
    (b/uber {:class-dir class-dir
             :uber-file uberjar-file
             :basis basis
             :main main-class}))
  opts)

(defn clean
  [opts]
  (b/delete {:path target-dir})
  opts)

(defn pom
  [opts]
  (write-pom opts))

(defn jar
  [opts]
  (-> opts
      write-pom
      write-jar))

(defn uberjar
  [opts]
  (-> opts
      write-pom
      compile-clojure
      write-uberjar))

(defn install-lib
  [opts]
  (jar opts)
  (b/install {:basis basis
              :lib lib-coord
              :version version
              :jar-file jar-file
              :class-dir class-dir})
  opts)

(defn run-tasks
  [opts]
  (binding [*ns* (find-ns 'build)]
    (reduce
     (fn [opts task]
       ((resolve task) opts))
     (:opts opts)
     (:tasks opts))))

(ns build
  "Tasks for compiling and building artifacts for deployment.

  Each task will check to see if its output has already been computed, and if it
  has, it will not execute. To resolve this, just run [[clean]] first.

  Tasks can be chained with the default function, [[run-tasks]], which runs each
  of a sequence of tasks in order, passing the options map from one to the next.

  Each task takes arguments in its options of a form `:task-name/option` so that
  when the options map is passed between them no conflicts will arise.

  While these tasks do construct artifacts, they do not provide for making
  deployments. Use the `:deploy` alias to deploy to clojars."
  (:require
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]))

(def lib-coord 'org.suskalo/fermented-formatter)
(def version (format "0.1.%s-SNAPSHOT" (b/git-count-revs nil)))

(def compiled-namespaces '[fermented-formatter.cli fermented-formatter.cli.main])
(def main-class 'fermented_formatter.cli.main)

(def source-dirs ["src"])
(def resource-dirs ["resources"])

(def target-dir "target/")
(def class-dir (str target-dir "classes/"))

(def basis (b/create-basis {:project "deps.edn"}))

(def jar-file (str target-dir "fermented-formatter.jar"))
(def uberjar-file (str target-dir "fermented-formatter-standalone.jar"))

(defn- exists?
  "Checks if a file composed of the given path segments exists."
  [& path-components]
  (.exists ^java.io.File (apply io/file path-components)))

(defn- write-pom
  "Writes a pom file if one does not already exist."
  [opts]
  (when-not (exists? (b/pom-path {:lib lib-coord
                                  :class-dir class-dir}))
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
  "Writes a jar file if one does not already exist."
  [opts]
  (when-not (exists? target-dir jar-file)
    (b/copy-dir {:target-dir class-dir
                 :src-dirs (concat source-dirs resource-dirs)})
    (b/jar {:class-dir class-dir
            :jar-file jar-file}))
  opts)

(defn- compile-clojure
  "Compiles all the clojure sources from the [[compiled-namespaces]] if no class
  files are in the target directory."
  [opts]
  (when-not (some #(.endsWith (.getName %) ".class")
                  (file-seq (io/file class-dir)))
    (b/compile-clj {:basis basis
                    :class-dir class-dir
                    :src-dirs source-dirs
                    :ns-compile compiled-namespaces
                    :compile-opts {:elide-meta [:doc :file :line]
                                   :direct-linking true}}))
  opts)

(defn- write-uberjar
  "Writes an uberjar file if one does not already exist."
  [opts]
  (when-not (exists? uberjar-file)
    (b/uber {:class-dir class-dir
             :uber-file uberjar-file
             :basis basis
             :main main-class}))
  opts)

(defn clean
  "Deletes the `target/` directory."
  [opts]
  (b/delete {:path target-dir})
  opts)

(defn pom
  "Generates a `pom.xml` file in the `target/classes/META-INF` directory.

  If `:pom/output-path` is specified, copies the resulting pom file to it."
  [opts]
  (write-pom opts)
  (when-some [path (:output-path opts)]
    (b/copy-file {:src (b/pom-path {:lib lib-coord
                                    :class-dir class-dir})
                  :target path}))
  opts)

(defn jar
  "Generates a `fermented-formatter.jar` file in the `target/` directory.

  This is a thin jar including only the sources and resources."
  [opts]
  (-> opts
      write-pom
      write-jar))

(defn uberjar
  "Generates a `fermented-formatter-standalone.jar` file in the `target/` directory.

  This is an uberjar including both AOT-compiled Clojure, resources, and
  dependencies."
  [opts]
  (-> opts
      compile-clojure
      write-pom
      write-uberjar))

(defn install-lib
  "Installs a generated thin jar (as [[jar]]) to the local maven repo."
  [opts]
  (jar opts)
  (b/install {:basis basis
              :lib lib-coord
              :version version
              :jar-file jar-file
              :class-dir class-dir})
  opts)

(defn run-tasks
  "Runs a series of tasks with a set of options.

  The `:tasks` key is a list of symbols of other task names to call. The rest of
  the option keys are passed unmodified."
  [opts]
  (binding [*ns* (find-ns 'build)]
    (reduce
     (fn [opts task]
       ((resolve task) opts))
     opts
     (:tasks opts))))

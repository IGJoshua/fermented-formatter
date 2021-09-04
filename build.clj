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
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(def lib-coord 'org.suskalo/fermented-formatter)
(def version (format "0.1.%s-SNAPSHOT" (b/git-count-revs nil)))

(def compiled-namespaces '[fermented-formatter.cli fermented-formatter.cli.main])
(def main-class "fermented_formatter.cli.main")

(def source-dirs ["src/"])
(def resource-dirs ["resources/"])

(def target-dir "target/")
(def class-dir (str target-dir "classes/"))

(def basis (b/create-basis {:project "deps.edn"}))

(def jar-file (str target-dir "fermented-formatter.jar"))
(def uberjar-file (str target-dir "fermented-formatter-standalone.jar"))

(def windows? (str/starts-with? (System/getProperty "os.name")
                                "Windows"))

(def graalvm-home (System/getenv "GRAALVM_HOME"))

(defn- cmd
  "Constructs a platform-specific command name from a graalvm command name."
  [name]
  (str name (when windows? ".cmd")))

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

(defn- copy-resources
  "Copies the resources from the [[resource-dirs]] to the [[class-dir]]."
  [opts]
  (b/copy-dir {:target-dir class-dir
               :src-dirs resource-dirs})
  opts)

(defn jar
  "Generates a `fermented-formatter.jar` file in the `target/` directory.

  This is a thin jar including only the sources and resources."
  [opts]
  (write-pom opts)
  (copy-resources opts)
  (when-not (exists? target-dir jar-file)
    (b/copy-dir {:target-dir class-dir
                 :src-dirs resource-dirs})
    (b/jar {:class-dir class-dir
            :jar-file jar-file}))
  opts)

(defn uberjar
  "Generates a `fermented-formatter-standalone.jar` file in the `target/` directory.

  This is an uberjar including both AOT-compiled Clojure, resources, and
  dependencies."
  [opts]
  (write-pom opts)
  (copy-resources opts)
  (when-not (some #(str/ends-with? (.getName %) ".class")
                  (file-seq (io/file class-dir)))
    (b/compile-clj {:basis basis
                    :class-dir class-dir
                    :src-dirs source-dirs
                    :ns-compile compiled-namespaces
                    :compile-opts {:elide-meta [:doc :file :line]
                                   :direct-linking true}}))
  (when-not (exists? uberjar-file)
    (b/uber {:class-dir class-dir
             :uber-file uberjar-file
             :basis basis
             :main main-class}))
  opts)

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

(defn native-image
  "Generates a `fermented-formatter` native-image binary in the `target/` directory.

  To run this alias the machine must already have a GraalVM installation
  available under the environment variable GRAALVM_HOME."
  [opts]
  (uberjar opts)
  (when-not (exists? graalvm-home "bin" (cmd "native-image"))
    (let [graal-executable (str (io/file graalvm-home "bin" (cmd "gu")))]
      (b/process {:command-args [graal-executable "install" "native-image"]})))
  (when-not (exists? target-dir "fermented-formatter")
    (let [native-image (str (io/file graalvm-home "bin" (cmd "native-image")))]
      (b/process {:command-args [native-image
                                 "-jar" uberjar-file
                                 (str "-H:Name=" target-dir "fermented-formatter")
                                 "-H:+ReportExceptionStackTraces"
                                 "-H:IncludeResources=fermented_formatter/.*"
                                 "--initialize-at-build-time"
                                 "-H:Log=registerResource:"
                                 "--verbose"
                                 "--no-fallback" "--no-server"]})))
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

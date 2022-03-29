(defproject metabase/exasol-driver "0.3.1"
  :description "An Exasol driver for Metabase"
  :url "https://github.com/exasol/metabase-driver/"
  :min-lein-version "2.9.7"

  :repositories {"Local Metabase" "file:maven_repository"
                 "Exasol Maven Repo" "https://maven.exasol.com/artifactory/exasol-releases"}

  :aliases
  {"test"           ["with-profile" "+unit_tests" "test"]
   "clj-kondo-deps" ["clj-kondo" "--copy-configs" "--dependencies" "--lint" "$classpath" "--cache-dir" ".clj-kondo/.cache"]
   "lint"           ["do" ["clj-kondo-deps"] ["clj-kondo" "--lint" "src" "test" "--cache-dir" ".clj-kondo/.cache"]]}


  :profiles
  {:provided
   {:dependencies [[org.clojure/clojure "1.10.0"]
                   [metabase "0.42.3"]
                   [com.exasol/exasol-jdbc "7.1.7"]]}

   :unit_tests
   {:test-paths     ^:replace ["test_unit"]}

   :user {:plugins [[com.github.clj-kondo/lein-clj-kondo "0.1.3"]]}

   :uberjar
   {:auto-clean    true
    :aot           :all
    :omit-source   true
    :javac-options ["-target" "1.8", "-source" "1.8"]
    :target-path   "target/%s"
    :uberjar-name  "exasol.metabase-driver.jar"}})

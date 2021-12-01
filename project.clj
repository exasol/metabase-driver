(defproject metabase/exasol-driver "0.0.1"
  :description "An Exasol driver for Metabase"
  :url "https://github.com/exasol/metabase-driver/"
  :min-lein-version "2.9.7"

  :repositories {"Local Metabase" "file:maven_repository"}

  :aliases
  {"test"       ["with-profile" "+unit_tests" "test"]}

  :profiles
  {:provided
   {:dependencies [[org.clojure/clojure "1.10.0"]
                   [metabase "0.41.2"]]}

   :unit_tests
   {:test-paths     ^:replace ["test_unit"]}

   :uberjar
   {:auto-clean    true
    :aot           :all
    :omit-source   true
    :javac-options ["-target" "1.8", "-source" "1.8"]
    :target-path   "target/%s"
    :uberjar-name  "exasol.metabase-driver.jar"}})

(defproject metabase/exasol-driver "0.0.1"
  :description "An Exasol driver for Metabase"
  :url "https://github.com/exasol/metabase-driver/"
  :min-lein-version "2.9.7"

  :repositories {"Exasol driver" "https://maven.exasol.com/artifactory/exasol-releases"}

  :dependencies
  [[com.exasol/exasol-jdbc "7.1.2"]]

  :profiles
  {:provided
   {:dependencies [[org.clojure/clojure "1.10.0"]
                   [metabase-core "1.0.0-SNAPSHOT"]
                   ]}

   :uberjar
   {:auto-clean    true
    :aot           :all
    :omit-source   true
    :javac-options ["-target" "1.8", "-source" "1.8"]
    :target-path   "target/%s"
    :uberjar-name  "exasol.metabase-driver.jar"}})

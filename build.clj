(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/test/classes")
(def test-basis (b/create-basis {:project "deps.edn"
                                 :aliases [:test-jar]}))
(defn test-clean [_]
  (b/delete {:path "target/test"}))

(defn test-jar
  "Build a jar containing test sources."
  [_]
  (test-clean nil)
  (b/write-pom {:class-dir class-dir
                :basis     test-basis
                :lib       'exasol-metabase-driver/test-sources
                :version   "1.0"})
  (b/copy-dir {:target-dir class-dir
               :src-dirs ["test" "test_unit"]})
  (b/jar {:class-dir class-dir
          :jar-file "target/exasol-test-sources.jar"}))

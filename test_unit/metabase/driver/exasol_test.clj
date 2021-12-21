(ns metabase.driver.exasol-test
  "Tests for specific behavior of the Exasol driver."
  (:require [clojure.test :refer :all]
            [metabase.config :as config]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]))

(deftest connection-details->spec-test
  (doseq [[^String message expected-spec details]
          [["You should be able to connect with an custom port"
            {:classname   "com.exasol.jdbc.EXADriver"
             :subprotocol "exa"
             :subname     "exasoldb.example.com:1234"}
            {:host "exasoldb.example.com"
             :port 1234}]
           ["You should be able to omit the port"
            {:classname   "com.exasol.jdbc.EXADriver"
             :subprotocol "exa"
             :subname     "exasoldb.example.com:8563"}
            {:host         "exasoldb.example.com"}]]]
    (let [expected-spec-with-default-values (merge {:user "dbuser" :password "dbpassword"
                                                    :clientname "Metabase" :clientversion config/mb-version-string
                                                    :fingerprint nil :feedbackinterval "1"} expected-spec)]
      (is (= expected-spec-with-default-values
             (sql-jdbc.conn/connection-details->spec :exasol details)) message))))

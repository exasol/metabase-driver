(ns metabase.driver.exasol-test
  "Tests for specific behavior of the Exasol driver."
  (:require [clojure.test :refer [deftest testing is]]
            [metabase.config :as config]
            [metabase.driver :as driver]
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


(def unsupported-features [:nested-fields])

(deftest database-supports?-test
  (testing "Supported features"
    (doseq [feature (apply disj driver/driver-features unsupported-features)]
      (testing (format "Driver supports feature %s" feature)
        (is (= true (driver/database-supports? :exasol feature nil))))))
  (testing "Unsupported features"
    (doseq [feature unsupported-features]
      (testing (format "Driver does not support feature %s" feature)
        (is (= false (driver/database-supports? :exasol feature nil)))))))

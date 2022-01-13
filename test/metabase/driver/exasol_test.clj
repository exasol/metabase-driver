(ns metabase.driver.exasol-test
  (:require [clojure.test :refer [deftest testing is]]
            [honeysql.core :as hsql]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.query-processor :as qp]
            [metabase.test :as mt]
            [metabase.test.data.exasol :as exasol.tx]
            [metabase.test.util :as tu]
            [metabase.test.data :as td]
            [metabase.test.data.dataset-definitions :as dataset]
            [metabase.test.data.exasol-dataset-definitions :as exasol-dataset]
            [metabase.util.honeysql-extensions :as hx]))

(deftest timezone-id-test
  (mt/test-driver :exasol
                  (is (= "UTC"
                         (tu/db-timezone-id)))))

(deftest text-equals-empty-string-test
  (mt/test-driver :exasol
                  (testing ":= with empty string should work correctly"
                    (mt/dataset dataset/airports
                                (is (= [1]
                                       (mt/first-row
                                        (mt/run-mbql-query dataset/airport {:aggregation [:count], :filter [:= $code ""]}))))))))

(deftest exasol-data-types
  (mt/test-driver :exasol
                  (td/dataset exasol-dataset/exasol-data-types
                              (testing "row count"

                                (is (= [2]
                                       (mt/first-row
                                        (mt/run-mbql-query exasol-dataset/exasol-data-types {:aggregation [:count]}))))))))

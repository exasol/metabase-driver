(ns metabase.driver.exasol-test
  (:require [clojure.test :refer [deftest testing is]]
            [metabase.test :as mt]
            [metabase.test.util :as tu]
            [metabase.test.data :as td]
            [metabase.test.data.dataset-definitions :as dataset]
            [metabase.test.data.exasol-dataset-definitions :as exasol-dataset]))

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
                                        (mt/run-mbql-query "airport" {:aggregation [:count], :filter [:= $code ""]}))))))))

(deftest exasol-data-types
  (mt/test-driver :exasol
                  (td/dataset exasol-dataset/exasol-data-types
                              (is (= [["null-values"      nil                                nil      nil                nil]
                                      ["non-null-values" "550e8400e29b11d4a716446655440000" "+05-03" "+02 12:50:10.123" "POINT (2 5)"]]
                                     (mt/rows (mt/run-mbql-query "data_types" {:fields [$name $hash $interval_ytm $interval_dts $geo]
                                                                               :order-by [[:asc $row_order]]})))))))


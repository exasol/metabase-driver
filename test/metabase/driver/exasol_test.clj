(ns metabase.driver.exasol-test
  (:require [clojure.test :refer [deftest testing is]]
            [metabase.test :as mt]
            [metabase.test.util :as tu]
            [metabase.test.data :as td]
            [metabase.test.data.dataset-definitions :as dataset]
            [metabase.test.data.exasol-dataset-definitions :as exasol-dataset]
            [metabase.query-processor :as qp]
            [metabase.query-processor-test :as qp.test]))

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

(deftest join-test
  (mt/test-driver :exasol
                  (testing "Exasol supports inner joins"
                    (is (= [["Big Red"        "Bayview Brood"]
                            ["Callie Crow"    "Mission Street Murder"]
                            ["Carson Crow"    "Mission Street Murder"]
                            ["Chicken Little" "Bayview Brood"]
                            ["Gerald Goose"   "Green Street Gaggle"]
                            ["Greg Goose"     "Green Street Gaggle"]
                            ["McNugget"       "Bayview Brood"]
                            ["Oliver Owl"     "Portrero Hill Parliament"]
                            ["Orville Owl"    "Portrero Hill Parliament"]
                            ["Paul Pelican"   "SoMa Squadron"]
                            ["Peter Pelican"  "SoMa Squadron"]
                            ["Russell Crow"   "Mission Street Murder"]]
                           (mt/rows
                            (qp/process-query
                             (mt/dataset dataset/bird-flocks
                                         (mt/mbql-query "bird"
                                                        {:fields   [$name &f.flock.name]
                                                         :joins    [{:source-table $$flock
                                                                     :condition    [:= $flock_id &f.flock.id]
                                                                     :strategy     :inner-join
                                                                     :alias        "f"}]
                                                         :order-by [[:asc $name]]})))))))))


(deftest filter-test
  (testing "Exasol supports filtering with conditions"
    (mt/test-driver :exasol
                    (is (= [[21 "PizzaHacker"          58 37.7441 -122.421 2]
                            [23 "Taqueria Los Coyotes" 50 37.765  -122.42  2]]
                           (mt/formatted-rows :venues
                                              (mt/run-mbql-query venues
                                                                 {:filter   [:and [:< $id 24] [:> $id 20] [:!= $id 22]]
                                                                  :order-by [[:asc $id]]})))))))

(deftest select-fields-test

  (testing (str "Exasol supports selecting defined fields")
    (mt/test-driver :exasol
                    (is (= {:rows [["Red Medicine"                  1]
                                   ["Stout Burgers & Beers"         2]
                                   ["The Apple Pan"                 3]
                                   ["Wurstküche"                    4]
                                   ["Brite Spot Family Restaurant"  5]
                                   ["The 101 Coffee Shop"           6]
                                   ["Don Day Korean Restaurant"     7]
                                   ["25°"                           8]
                                   ["Krua Siri"                     9]
                                   ["Fred 62"                      10]]
                            :cols [(mt/col :venues :name)
                                   (mt/col :venues :id)]}
                           (mt/format-rows-by [str int]
                                              (qp.test/rows-and-cols
                                               (mt/run-mbql-query venues
                                                                  {:fields   [$name $id]
                                                                   :limit    10
                                                                   :order-by [[:asc $id]]}))))))))

(deftest string-function-regexp
  (mt/test-driver :exasol
                  (is (= ["Red"]
                         (mt/rows
                          (mt/run-mbql-query venues {:expressions {"test" [:regex-match-first [:field-id (td/id :venues :name)] "(.ed+)"]}
                                                     :fields      [[:expression "test"]]
                                                     :order-by    [[:asc [:field-id (td/id :venues :id)]]]
                                                     :limit       1}))))))
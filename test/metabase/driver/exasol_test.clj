(ns metabase.driver.exasol-test
  (:require [clojure.test :refer [deftest testing is]]
            [metabase.test :as mt]
            [metabase.test.util :as tu]
            [metabase.test.data :as td]
            [metabase.test.data.dataset-definitions :as dataset]
            [metabase.test.data.exasol-dataset-definitions :as exasol-dataset]
            [metabase.query-processor :as qp]
            [metabase.query-processor-test :as qp.test])
  (:import (java.util TimeZone)))

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
  (testing "Exasol supports selecting defined fields"
    (mt/test-driver :exasol
                    (is (= {:rows [["Red Medicine"                  1]
                                   ["Stout Burgers & Beers"         2]
                                   ["The Apple Pan"                 3]
                                   ["Wurstküche"                    4]]
                            :cols [(mt/col :venues :name)
                                   (mt/col :venues :id)]}
                           (mt/format-rows-by [str int]
                                              (qp.test/rows-and-cols
                                               (mt/run-mbql-query venues
                                                                  {:fields   [$name $id]
                                                                   :limit    4
                                                                   :order-by [[:asc $id]]}))))))))

(deftest numeric-expression
  (testing "Exasol supports numeric expressions, e.g. with +"
    (mt/test-driver :exasol
                    (is (= [[1 "Red Medicine"                 4  10.0646 -165.374 3 5.0]
                            [2 "Stout Burgers & Beers"        11 34.0996 -118.329 2 4.0]
                            [3 "The Apple Pan"                11 34.0406 -118.428 2 4.0]
                            [4 "Wurstküche"                   29 33.9997 -118.465 2 4.0]
                            [5 "Brite Spot Family Restaurant" 20 34.0778 -118.261 2 4.0]]
                           (mt/formatted-rows [int str int 4.0 4.0 int float]
                                              (mt/run-mbql-query venues
                                                                 {:expressions {:calculated-field [:+ $price 2]}
                                                                  :limit       5
                                                                  :order-by    [[:asc $id]]})))))))

(deftest math-functions
  (testing "Exasol supports math functions, e.g. POWER()"
    (mt/test-driver :exasol
                    (is (= [[3 9.0]
                            [2 4.0]
                            [2 4.0]]
                           (mt/rows
                            (mt/run-mbql-query venues
                                               {:expressions {"test" [:power [:field-id $price] 2]}
                                                :fields      [$price [:expression "test"]]
                                                :limit       3
                                                :order-by    [[:asc $id]]})))))))



(deftest string-function-regexp
  (testing "Exasol supports string functions, e.g. REGEX_MATCH_FIRST()"
    (mt/test-driver :exasol
                    (is (= [["Red Medicine" "Red"]
                            ["Stout Burgers & Beers" nil]]
                           (mt/rows
                            (mt/run-mbql-query venues {:expressions {"test" [:regex-match-first [:field-id $name] "(.ed+)"]}
                                                       :fields      [$name [:expression "test"]]
                                                       :order-by    [[:asc [:field-id (td/id :venues :id)]]]
                                                       :limit       2})))))))


(deftest aggregation
  (testing "Exasol supports aggregation"
    (mt/test-driver :exasol
                    (is (= {:cols [(qp.test/breakout-col :venues :price)
                                   (qp.test/aggregate-col :cum-count :venues :id)]
                            :rows [[1 22]
                                   [2 81]
                                   [3 94]
                                   [4 100]]}
                           (qp.test/rows-and-cols
                            (mt/format-rows-by [int int]
                                               (mt/run-mbql-query venues
                                                                  {:aggregation [[:cum-count $id]]
                                                                   :breakout    [$price]}))))))))

(deftest nested-query
  (testing "Exasol supports nested queries"
    (mt/test-driver :exasol
                    (is (= {:rows [[1 174] [2 474] [3 78] [4 39]]
                            :cols [(qp.test/breakout-col (qp.test/fk-col :checkins :venue_id :venues :price))
                                   (qp.test/aggregate-col :count)]}
                           (qp.test/rows-and-cols
                            (mt/format-rows-by [int int]
                                               (mt/run-mbql-query checkins
                                                                  {:source-query {:source-table $$checkins
                                                                                  :filter       [:> $date "2014-01-01"]}
                                                                   :aggregation  [:count]
                                                                   :order-by     [[:asc $venue_id->venues.price]]
                                                                   :breakout     [$venue_id->venues.price]}))))))))

(defn- do-with-java-timezone
  [timezone-id body]
  (let [org-timezone (TimeZone/getDefault)]
    (try
      (TimeZone/setDefault (when (not (nil? timezone-id)) (TimeZone/getTimeZone timezone-id)))
      (body)
      (finally
        (TimeZone/setDefault org-timezone)))))

(defn- get-timestamp-data-rows
  [db-session-timezone]
  (mt/with-temporary-setting-values [report-timezone db-session-timezone]
    (mt/rows
     (qp/process-query
      (mt/dataset exasol-dataset/timestamp-data
                  (mt/mbql-query "timestamp_data"
                                 {:fields   [$name $utc_string $timestamp $timestamp_local_tz]
                                  :order-by [[:asc $row_order]]}))))))

(deftest timestamp-test
  (testing "Timestamps are returned in the correct timezone"
    (mt/test-driver :exasol
                    (is (= [["winter"  "2021-01-31 08:15:30.123"  "2021-01-31T08:15:30.123+01:00"  "2021-01-31T09:15:30.123+01:00"]
                            ["summer"  "2021-08-01 17:20:35.321"  "2021-08-01T17:20:35.321+02:00"  "2021-08-01T19:20:35.321+02:00"]]
                           (get-timestamp-data-rows "Europe/Berlin"))
                        "Session TZ = Europe/Berlin")

                    (is (= [["winter"  "2021-01-31 08:15:30.123"  "2021-01-31T08:15:30.123-05:00"  "2021-01-31T03:15:30.123-05:00"]
                            ["summer"  "2021-08-01 17:20:35.321"  "2021-08-01T17:20:35.321-04:00"  "2021-08-01T13:20:35.321-04:00"]]
                           (get-timestamp-data-rows "America/New_York"))
                        "Session TZ = America/New_York"))))

(defn- get-db-timezone
  []
  (ffirst
   (mt/rows
    (qp/process-query
     {:database   (mt/id)
      :type       :native
      :native     {:query "SELECT DBTIMEZONE"}}))))

(defn- get-session-timezone
  [db-session-timezone java-timezone]
  (do-with-java-timezone java-timezone
                         #(mt/with-temporary-setting-values [report-timezone db-session-timezone]
                            (ffirst (mt/rows
                                     (qp/process-query
                                      {:database   (mt/id)
                                       :type       :native
                                       :native     {:query "select SESSIONTIMEZONE"}}))))))

(deftest db-timezone-test
  (testing "Exasol reports the expected time zones for DB and session"
    (mt/test-driver :exasol
                    (let [default-timezone (get-db-timezone)]
                      (is (= default-timezone
                             (get-session-timezone nil nil))
                          "No configuration returns UTC")
                      (is (= default-timezone
                             (get-session-timezone nil "America/New_York"))
                          "Java timezone is ignored")
                      (is (= "AMERICA/NEW_YORK"
                             (get-session-timezone "America/New_York" nil))
                          "Report timezone sets session timezone")
                      (is (= "AMERICA/NEW_YORK"
                             (get-session-timezone "America/New_York" "Europe/Berlin"))
                          "Report timezone overrides Java timezone")
                      (is (= "EUROPE/BERLIN"
                             (get-session-timezone "Europe/Berlin" "America/New_York"))
                          "Report timezone overrides Java timezone")))))

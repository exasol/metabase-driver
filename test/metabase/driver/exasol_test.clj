(ns metabase.driver.exasol-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [metabase.driver.exasol :as exasol]
            [metabase.query-processor :as qp]
            [metabase.query-processor-test.alternative-date-test :as alt-date-test]
            [metabase.test :as mt]
            [metabase.test.data :as td]
            [metabase.test.data.dataset-definitions :as dataset]
            [metabase.test.data.exasol-dataset-definitions :as exasol-dataset])
  (:import (java.util TimeZone)))

(set! *warn-on-reflection* true)

(deftest get-jdbc-driver-version-test
  (testing "Getting JDBC driver version succeeds"
    (is (not (str/blank? (exasol/get-jdbc-driver-version)))))
  (testing "Getting JDBC driver version returns expected value"
    (is (= "7.1.20" (exasol/get-jdbc-driver-version)))))

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
                              (is (= [["null-values"      nil                                nil      nil                nil          nil]
                                      ["non-null-values" "550e8400e29b11d4a716446655440000" "+05-03" "+02 12:50:10.123" "POINT (2 5)" 123.45M]]
                                     (mt/rows (mt/run-mbql-query "data_types" {:fields [$name $hash $interval_ytm $interval_dts $geo $decimal_5_2]
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
                    (is (= [[3M 9.0]
                            [2M 4.0]
                            [2M 4.0]]
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

(deftest iso-8601-text-fields
  (testing "text fields with semantic_type :type/ISO8601DateTimeString"
    (mt/test-drivers #{:exasol}
                     (is (= [[1M "foo" #t "2004-10-19T10:23:54" #t "2004-10-19"]
                             [2M "bar" #t "2008-10-19T10:23:54" #t "2008-10-19"]
                             [3M "baz" #t "2012-10-19T10:23:54" #t "2012-10-19"]]
                            (mt/rows (mt/dataset alt-date-test/just-dates
                                                 (qp/process-query
                                                  (assoc (mt/mbql-query just-dates)
                                                         :middleware {:format-rows? false})))))))))


(defn- fmt-str-or-int
  [x]
  (if (string? x)
    (str x)
    (int x)))

(deftest week-aggregation
  (testing "Verify that week aggregation correctly uses 'Start of week' setting"
    ; 2023-01-01 is a Sunday, 2023-01-02 is a Monday
    (mt/test-drivers #{:exasol}
                     (mt/dataset exasol-dataset/one-timestamp-per-day
                                 (letfn [(test-break-out [unit start-of-week-setting]
                                           (mt/with-temporary-setting-values [start-of-week start-of-week-setting]
                                             (->> (mt/mbql-query timestamps
                                                                 {:filter      [:between $col "2023-01-02" "2023-01-15"]
                                                                  :breakout    [:field $col {:temporal-unit unit}]
                                                                  :aggregation [[:count]]})
                                                  mt/process-query
                                                  (mt/formatted-rows [fmt-str-or-int int]))))]

                                   (testing "Break out by week, start of week = Monday"
                                     (is (= [["2023-01-02T00:00:00Z" 7] ["2023-01-09T00:00:00Z" 7]]
                                            (test-break-out :week :monday))))
                                   (testing "Break out by week, start of week = Tuesday"
                                     (is (= [["2022-12-27T00:00:00Z" 1]
                                             ["2023-01-03T00:00:00Z" 7]
                                             ["2023-01-10T00:00:00Z" 6]]
                                            (test-break-out :week :tuesday))))
                                   (testing "Break out by week, start of week = Sunday"
                                     (is (= [["2023-01-01T00:00:00Z" 6]
                                             ["2023-01-08T00:00:00Z" 7]
                                             ["2023-01-15T00:00:00Z" 1]]
                                            (test-break-out :week :sunday))))

                                   (testing "Break out by week-of-year, start of week = Monday"
                                     (is (= [[1 7] [2 7]]
                                            (test-break-out :week-of-year :monday))))
                                   (testing "Break out by week-of-year, start of week = Tuesday"
                                     (is (= [[1 7] [2 6] [52 1]]
                                            (test-break-out :week-of-year :tuesday))))
                                   (testing "Break out by week-of-year, start of week = Sunday"
                                     (is (= [[1 6] [2 7] [3 1]]
                                            (test-break-out :week-of-year :sunday)))))))))

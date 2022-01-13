(ns metabase.driver.exasol-test
  "Tests for specific behavior of the Exasol driver."
  (:require [clojure.test :refer [deftest testing is]]
            [honeysql.core :as hsql]
            [metabase.driver.exasol :as exasol]
            [metabase.config :as config]
            [metabase.driver :as driver]
            [metabase.driver.common :as driver.common]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.util.honeysql-extensions :as hx]))

(deftest connection-details->spec-test
  (doseq [[^String message expected-spec details]
          [["You should be able to connect with an custom port"
            {:subname     "exasoldb.example.com:1234"}
            {:host        "exasoldb.example.com"
             :port 1234}]
           ["You should be able to omit the port"
            {:subname     "exasoldb.example.com:8563"}
            {:host        "exasoldb.example.com"}]
           ["You should be able to specify a fingerprint"
            {:subname     "exasoldb.example.com:8563"
             :fingerprint "myfingerprint"}
            {:host        "exasoldb.example.com"
             :certificate-fingerprint "myfingerprint"}]]]
    (let [expected-spec-with-default-values (merge {:classname   "com.exasol.jdbc.EXADriver" :subprotocol "exa"
                                                    :user "dbuser" :password "dbpassword"
                                                    :clientname "Metabase" :clientversion config/mb-version-string
                                                    :fingerprint nil :feedbackinterval "1"} expected-spec)]
      (is (= expected-spec-with-default-values
             (sql-jdbc.conn/connection-details->spec :exasol details)) message))))


(def ^:private unsupported-features [:nested-fields])

(deftest database-supports?-test
  (testing "Supported features"
    (doseq [feature (apply disj driver/driver-features unsupported-features)]
      (testing (format "Driver supports feature %s" feature)
        (is (= true (driver/database-supports? :exasol feature nil))))))
  (testing "Unsupported features"
    (doseq [feature unsupported-features]
      (testing (format "Driver does not support feature %s" feature)
        (is (= false (driver/database-supports? :exasol feature nil)))))))

(deftest start-of-week-offset-test
  (is (= 0 (driver.common/start-of-week-offset :exasol)) "Start of week offset"))

(deftest display-name-test
  (is (= "Exasol" (driver/display-name :exasol)) "Driver display name"))

(deftest database-type->base-type-test
  (testing "Known types"
    (doseq [[db-type expected-type] [["BOOLEAN"                         :type/Boolean]
                                     ["CHAR"                            :type/Text]
                                     ["VARCHAR"                         :type/Text]
                                     ["HASHTYPE"                        :type/Text]
                                     ["BIGINT"                          :type/Decimal]
                                     ["DECIMAL"                         :type/Decimal]
                                     ["DOUBLE PRECISION"                :type/Float]
                                     ["DOUBLE"                          :type/Float]
                                     ["DATE"                            :type/Date]
                                     ["TIMESTAMP"                       :type/DateTime]
                                     ["TIMESTAMP WITH LOCAL TIME ZONE"  :type/DateTime]
                                     ["INTERVAL DAY TO SECOND"          :type/Text]
                                     ["INTERVAL YEAR TO MONTH"          :type/Text]
                                     ["GEOMETRY"                        :type/Text]]]
      (is (= expected-type (sql-jdbc.sync/database-type->base-type :exasol db-type))
          (format "Database type %s returns %s" db-type expected-type))))
  (testing "Unknown types return nil"
    (doseq [db-type ["unknown" "boolean" "" nil]]
      (is (= nil (sql-jdbc.sync/database-type->base-type :exasol db-type))
          (format "Database type %s returns nil" db-type)))))

(deftest create-set-timezone-sql-test
  (is (= "ALTER SESSION SET TIME_ZONE='my timezone'" (exasol/create-set-timezone-sql "my timezone"))))

(deftest date-test
  (let [value :dummy-value]
    (doseq [[type expected] [[:minute          (hsql/call :truncate value (hx/literal "mi"))]
                             [:minute-of-hour  (hsql/call :extract :minute (hx/with-type-info (hsql/call :cast value #sql/raw "timestamp") #:metabase.util.honeysql-extensions{:database-type "timestamp"}))]
                             [:hour            (hsql/call :truncate value (hx/literal "hh"))]
                             [:hour-of-day     (hsql/call :extract :hour (hx/with-type-info (hsql/call :cast value #sql/raw "timestamp") #:metabase.util.honeysql-extensions{:database-type "timestamp"}))]
                             [:day             (hsql/call :truncate value (hx/literal "dd"))]
                             [:day-of-month    (hsql/call :extract :day value)]
                             [:month           (hsql/call :truncate value (hx/literal "month"))]
                             [:month-of-year   (hsql/call :extract :month value)]
                             [:quarter         (hsql/call :truncate value (hx/literal "q"))]
                             [:year            (hsql/call :truncate value (hx/literal "year"))]
                             [:week            (hsql/call :truncate value (hx/literal "day"))]
                             [:week-of-year    (hsql/call :ceil (hsql/call :/ (hsql/call :+ (hsql/call :- (hsql/call :to_date (hsql/call :truncate (hsql/call :truncate value (hx/literal "day")) (hx/literal "dd"))) (hsql/call :to_date (hsql/call :truncate (hsql/call :truncate value (hx/literal "day")) (hx/literal "year")))) 1) 7.0))]
                             [:day-of-year     (hsql/call :+ (hsql/call :- (hsql/call :to_date (hsql/call :truncate value (hx/literal "dd"))) (hsql/call :to_date (hsql/call :truncate value (hx/literal "year")))) 1)]
                             [:quarter-of-year (hsql/call :/ (hsql/call :+ (hsql/call :extract :month (hsql/call :truncate value (hx/literal "q"))) 2) 3)]
                             [:day-of-week     (hx/with-type-info (hsql/call :cast (hsql/call :to_char value (hx/literal "d")) #sql/raw "integer") #:metabase.util.honeysql-extensions{:database-type "integer"})]]]
      (testing (format "Date function %s" type)
        (is (= expected (sql.qp/date :exasol type value)))))))


(deftest utc-calendar
  (testing "UTC Calendar has UTC time zone"
    (is (= "UTC" (.getID (.getTimeZone exasol/utc-calendar))))))

(defn- utc-timestamp [timestamp-string]
  (let [original-timezone (java.util.TimeZone/getDefault)
        utc-timezone (java.util.TimeZone/getTimeZone "UTC")]
    (try
      (java.util.TimeZone/setDefault utc-timezone)
      (java.sql.Timestamp/valueOf timestamp-string)
      (finally (java.util.TimeZone/setDefault original-timezone)))))

(deftest sql-timestamp->localdatetime
  (doseq [[timestamp expected] [[nil nil]
                                [(utc-timestamp "2021-12-31 23:55:30.123") (java.time.LocalDateTime/parse "2021-12-31T23:55:30.123")]
                                [(utc-timestamp "2007-12-03 10:15:30") (java.time.LocalDateTime/parse "2007-12-03T10:15:30")]]]
    (testing (format "Sql Timestamp %s converted to LocalDateTime %s" timestamp expected)
      (is (= expected (exasol/sql-timestamp->localdatetime timestamp))))))

(deftest current-datetime-honeysql-form-test
  (is (= (hsql/raw "SYSTIMESTAMP") (sql.qp/current-datetime-honeysql-form :exasol))))

(deftest ->honeysql-test
  (testing :regex-match-first
  (is (= (hsql/call :regexp_substr "arg" "pattern") (sql.qp/->honeysql :exasol [:regex-match-first "arg" "pattern"])))))

(deftest add-interval-honeysql-form-test
  (let [hsql-form (hx/literal "5")
        amount 42
        timestamp-form (hx/with-type-info (hsql/call :cast hsql-form #sql/raw "timestamp") #:metabase.util.honeysql-extensions{:database-type "timestamp"})]
    (doseq [[unit expected] [[:second  (hsql/call :+ timestamp-form (hsql/call :numtodsinterval amount (hx/literal "second")))]
                             [:minute  (hsql/call :+ timestamp-form (hsql/call :numtodsinterval amount (hx/literal "minute")))]
                             [:hour    (hsql/call :+ timestamp-form (hsql/call :numtodsinterval amount (hx/literal "hour")))]
                             [:day     (hsql/call :+ timestamp-form (hsql/call :numtodsinterval amount (hx/literal "day")))]
                             [:week    (hsql/call :+ timestamp-form (hsql/call :numtodsinterval (hsql/call :* amount #sql/raw "7") (hx/literal "day")))]
                             [:month   (hsql/call :+ timestamp-form (hsql/call :numtoyminterval amount (hx/literal "month")))]
                             [:quarter (hsql/call :+ timestamp-form (hsql/call :numtoyminterval (hsql/call :* amount #sql/raw "3") (hx/literal "month")))]
                             [:year    (hsql/call :+ timestamp-form (hsql/call :numtoyminterval amount (hx/literal "year")))]]]
      (testing (format "Add interval with unit %s" unit)
        (is (= expected  (sql.qp/add-interval-honeysql-form :exasol hsql-form amount unit)))))))


(deftest cast-temporal-string-test
  (let [expr (hx/literal "5")]
   (doseq [[coercion-strategy expected] [[:Coercion/ISO8601->DateTime              (hsql/call :to_timestamp expr "YYYY-MM-DD HH:mi:SS")]
                                         [:Coercion/ISO8601->Date                  (hsql/call :to_date expr "YYYY-MM-DD")]
                                         [:Coercion/YYYYMMDDHHMMSSString->Temporal (hsql/call :to_timestamp expr "YYYYMMDDHH24miSS")]]]
   (testing (format "Cast temporal string with coercion strategy %s" coercion-strategy)
     (is (= expected (sql.qp/cast-temporal-string :exasol coercion-strategy expr)))))))

(deftest unix-timestamp->honeysql-test
  (let [expr (hx/literal "5")]
    (doseq [[unit expected] [[:seconds      (hsql/call :from_posix_time expr)]
                             [:milliseconds (hsql/call :from_posix_time (hx// expr (hsql/raw 1000)))]
                             [:microseconds (hsql/call :from_posix_time (hx// expr (hsql/raw 1000000)))]]]
      (testing (format "Convert unix timestamp %s" unit)
        (is (= expected (sql.qp/unix-timestamp->honeysql :exasol unit expr)))))))

(deftest db-default-timezone-test
  (is (= "UTC" (driver/db-default-timezone :exasol :database))))

(deftest db-start-of-week-test
  (is (= :sunday (driver/db-start-of-week :exasol))))

(deftest excluded-schemas-test
  (is (= #{"EXA_STATISTICS" "SYS"} (sql-jdbc.sync/excluded-schemas :exasol))))

(deftest unprepare-value-test
    (doseq [[value expected] [[(java.time.OffsetDateTime/parse "2007-12-03T10:15:30+01:00") "timestamp '2007-12-03 10:15:30.000'"]
                             [(java.time.ZonedDateTime/parse "2007-12-03T10:15:30+01:00[Europe/Paris]") "timestamp '2007-12-03 10:15:30.000'"]]]
      (testing (format "Unprepare %s" (.getClass value))
        (is (= expected (unprepare/unprepare-value :exasol value))))))
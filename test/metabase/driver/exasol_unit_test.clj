(ns metabase.driver.exasol-unit-test
  "Tests for specific behavior of the Exasol driver."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [honey.sql :as sql]
            [metabase.config :as config]
            [metabase.driver :as driver]
            [metabase.driver.common :as driver.common]
            [metabase.driver.exasol :as exasol]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.util.honey-sql-2 :as h2x]))

(set! *warn-on-reflection* true)

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


(def ^:private unsupported-features [:nested-fields :nested-field-columns :persist-models :persist-models-enabled :actions :actions/custom :convert-timezone :datetime-diff :now
                                     :native-requires-specified-collection :connection-impersonation :connection-impersonation-requires-role
                                     :uploads :table-privileges
                                     ; New in v0.50.x
                                     :index-info :connection/multiple-databases :describe-fields :identifiers-with-spaces :uuid-type :temporal/requires-default-unit])

(deftest database-supports?-test
  (testing "Driver supports setting timezone"
    (is (= true (driver/database-supports? :exasol :set-timezone nil))))
  (testing "Supported features"
    (doseq [feature (apply disj driver/features unsupported-features)]
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
                                     ["BIGINT"                          :type/Decimal]
                                     ["INTEGER"                         :type/Decimal]
                                     ["SMALLINT"                        :type/Decimal]
                                     ["DECIMAL"                         :type/Decimal]
                                     ["DOUBLE PRECISION"                :type/Float]
                                     ["DOUBLE"                          :type/Float]
                                     ["DATE"                            :type/Date]
                                     ["TIMESTAMP"                       :type/DateTime]
                                     ["TIMESTAMP WITH LOCAL TIME ZONE"  :type/DateTime]
                                     ["INTERVAL DAY TO SECOND"          :type/*]
                                     ["INTERVAL YEAR TO MONTH"          :type/*]
                                     ["GEOMETRY"                        :type/*]
                                     ["HASHTYPE"                        :type/*]]]
      (is (= expected-type (sql-jdbc.sync/database-type->base-type :exasol db-type))
          (format "Database type %s returns %s" db-type expected-type))))
  (testing "Unknown types return nil"
    (doseq [db-type ["unknown" "boolean" "" nil " CHAR" "CHAR " "INTERVAL"]]
      (is (= nil (sql-jdbc.sync/database-type->base-type :exasol db-type))
          (format "Database type %s returns nil" db-type)))))

(deftest create-set-timezone-sql-test
  (is (= "ALTER SESSION SET TIME_ZONE='my timezone'" (exasol/create-set-timezone-sql "my timezone"))))

(deftest date-test
  (let [value :dummy-value]
    (doseq [[type expected] [[:minute          (sql/call :trunc (h2x/->timestamp value) (h2x/literal "mi"))]
                             [:minute-of-hour  (sql/call :metabase.util.honey-sql-2/extract :minute [:metabase.util.honey-sql-2/typed (sql/call :cast value [:raw "timestamp"]) {:database-type "timestamp"}])]
                             [:hour            (sql/call :trunc (h2x/->timestamp value) (h2x/literal "hh"))]
                             [:hour-of-day     (sql/call :metabase.util.honey-sql-2/extract :hour [:metabase.util.honey-sql-2/typed (sql/call :cast value  [:raw "timestamp"]) {:database-type "timestamp"}])]
                             [:day             (sql/call :trunc (h2x/->date value) (h2x/literal "dd"))]
                             [:day-of-month    (sql/call :metabase.util.honey-sql-2/extract :day (h2x/->timestamp value))]
                             [:month           (sql/call :trunc (h2x/->date value) (h2x/literal "month"))]
                             [:month-of-year   (sql/call :metabase.util.honey-sql-2/extract :month (h2x/->timestamp value))]
                             [:quarter         (sql/call :trunc (h2x/->date value) (h2x/literal "q"))]
                             [:year            (sql/call :trunc (h2x/->date value) (h2x/literal "year"))]
                             [:week            (sql/call :trunc (h2x/->date value) (h2x/literal "day"))]
                             [:week-of-year    (sql/call :ceil (sql/call :/ (sql/call :+ (sql/call :- (sql/call :to_date (sql/call :trunc (h2x/->date (sql/call :trunc (h2x/->date value) (h2x/literal "day"))) (h2x/literal "dd"))) (sql/call :to_date (sql/call :trunc (h2x/->date (sql/call :trunc (h2x/->date value) (h2x/literal "day"))) (h2x/literal "year")))) [:inline 1]) [:inline 7]))]
                             [:day-of-year     (sql/call :+ (sql/call :- (sql/call :to_date (sql/call :trunc (h2x/->date value) (h2x/literal "dd"))) (sql/call :to_date (sql/call :trunc (h2x/->date value) (h2x/literal "year")))) [:inline 1])]
                             [:quarter-of-year (sql/call :/ (sql/call :+ (sql/call :metabase.util.honey-sql-2/extract :month (h2x/->timestamp (sql/call :trunc (h2x/->date value) (h2x/literal "q")))) [:inline 2]) [:inline 3])]
                             [:day-of-week     [:metabase.util.honey-sql-2/typed (sql/call :cast (sql/call :to_char (h2x/->timestamp value) (h2x/literal "d")) [:raw "integer"]) {:database-type "integer"}]]]]
      (testing (format "Date function %s" type)
        (is (= expected (sql.qp/date :exasol type value)))))))

(deftest current-datetime-honeysql-form-test
  (is (= [:metabase.util.honey-sql-2/typed [:raw "SYSTIMESTAMP"] {:database-type "timestamp"}]
         (sql.qp/current-datetime-honeysql-form :exasol))))

(deftest regex-match-first->honeysql-test
  (testing :regex-match-first
    (is (= (sql/call :regexp_substr "arg" "pattern") (sql.qp/->honeysql :exasol [:regex-match-first "arg" "pattern"])))))

(deftest add-interval-honeysql-form-test
  (let [hsql-form (h2x/literal "5")
        amount 42
        timestamp-form  [:metabase.util.honey-sql-2/typed (sql/call :cast hsql-form [:raw "timestamp"]) {:database-type "timestamp"}]]
    (doseq [[unit expected expected-type] [[:second  (sql/call :+ timestamp-form (sql/call :numtodsinterval [:inline amount] (h2x/literal "second"))) "timestamp"]
                                           [:minute  (sql/call :+ timestamp-form (sql/call :numtodsinterval [:inline amount] (h2x/literal "minute"))) "timestamp"]
                                           [:hour    (sql/call :+ timestamp-form (sql/call :numtodsinterval [:inline amount] (h2x/literal "hour"))) "timestamp"]
                                           [:day     (sql/call :+ timestamp-form (sql/call :numtodsinterval [:inline amount] (h2x/literal "day"))) "timestamp"]
                                           [:week    (sql/call :+ timestamp-form (sql/call :numtodsinterval (sql/call :* [:inline amount] [:inline 7]) (h2x/literal "day"))) "timestamp"]
                                           [:month   (sql/call :+ timestamp-form (sql/call :numtoyminterval [:inline amount] (h2x/literal "month"))) "timestamp"]
                                           [:quarter (sql/call :+ timestamp-form (sql/call :numtoyminterval (sql/call :* [:inline amount] [:inline 3]) (h2x/literal "month"))) "timestamp"]
                                           [:year    (sql/call :+ timestamp-form (sql/call :numtoyminterval [:inline amount] (h2x/literal "year"))) "timestamp"]]]
      (testing (format "Add interval with unit %s" unit)
        (let [complete-expected [:metabase.util.honey-sql-2/typed expected {:database-type expected-type}]]
          (is (= complete-expected
                 (sql.qp/add-interval-honeysql-form :exasol hsql-form [:inline amount] unit))))))))

(deftest cast-temporal-string-test
  (let [expr (h2x/literal "5")]
    (doseq [[coercion-strategy expected] [[:Coercion/ISO8601->DateTime              (sql/call :to_timestamp expr "YYYY-MM-DD HH:mi:SS")]
                                          [:Coercion/ISO8601->Date                  (sql/call :to_date expr "YYYY-MM-DD")]
                                          [:Coercion/YYYYMMDDHHMMSSString->Temporal (sql/call :to_timestamp expr "YYYYMMDDHH24miSS")]]]
      (testing (format "Cast temporal string with coercion strategy %s" coercion-strategy)
        (is (= expected (sql.qp/cast-temporal-string :exasol coercion-strategy expr)))))))

(deftest unix-timestamp->honeysql-test
  (let [expr (h2x/literal "5")]
    (doseq [[unit expected] [[:seconds      (sql/call :from_posix_time expr)]
                             [:milliseconds (sql/call :from_posix_time (h2x// expr [:inline 1000]))]
                             [:microseconds (sql/call :from_posix_time (h2x// expr [:inline 1000000]))]]]
      (testing (format "Convert unix timestamp %s" unit)
        (is (= expected (sql.qp/unix-timestamp->honeysql :exasol unit expr)))))))

(deftest db-default-timezone-test
  (is (= "UTC" (driver/db-default-timezone :exasol :database))))

(deftest db-start-of-week-test
  (is (= :sunday (driver/db-start-of-week :exasol))))

(deftest excluded-schemas-test
  (let [excluded-schemas (sql-jdbc.sync/excluded-schemas :exasol)]
    (is (and (contains? excluded-schemas "EXA_STATISTICS")
             (contains? excluded-schemas "SYS")))))

(deftest unprepare-value-test
  (doseq [[value expected] [[(java.time.OffsetDateTime/parse "2007-12-03T10:15:30+01:00") "timestamp '2007-12-03 10:15:30.000'"]
                            [(java.time.ZonedDateTime/parse "2007-12-03T10:15:30+01:00[Europe/Paris]") "timestamp '2007-12-03 10:15:30.000'"]]]
    (testing (format "Unprepare %s" (.getClass value))
      (is (= expected (unprepare/unprepare-value :exasol value))))))

(deftest get-driver-version-test
  (testing "Reading driver version from non existing resource returns nil"
    (is (= nil (exasol/get-driver-version "non-existing-resource.yaml"))))
  (testing "Driver version read from existing resource"
    (is (not (str/blank? (exasol/get-driver-version)))))
  (testing "Driver version read from existing resource equal to expected version"
    (is (= "1.0.7" (exasol/get-driver-version)))))

(deftest humanize-connection-error-message-test
  (testing "Driver translates connection error message"
    (doseq [[message expected-message] [["Unknown host name. 192.168.56.5: nodename nor servname provided, or not known"
                                         :invalid-hostname]
                                        ["Connection exception - authentication failed."
                                         :username-or-password-incorrect]
                                        ["java.net.ConnectException: Connection refused (Connection refused)"
                                         :cannot-connect-check-host-and-port]
                                        ["java.io.IOException: TLS connection to host (192.168.56.5) failed: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target. If you trust the server, you can include the fingerprint in the connection string: 192.168.56.5/15F9CA9BC95E14F1F913FC449A26723841C118CFB644957866ABB73C1399A7FF:8563. "
                                         "The server's TLS certificate is not signed. If you trust the server specify the following fingerprint: 15F9CA9BC95E14F1F913FC449A26723841C118CFB644957866ABB73C1399A7FF."]
                                        ["[ERROR] Fingerprint did not match. The fingerprint provided: ABC. Server's certificate fingerprint: 15F9CA9BC95E14F1F913FC449A26723841C118CFB644957866ABB73C1399A7FF. "
                                         "The server's TLS certificate has fingerprint 15F9CA9BC95E14F1F913FC449A26723841C118CFB644957866ABB73C1399A7FF but we expected ABC."]
                                        ["Unknown error messages are unchanged"
                                         "Unknown error messages are unchanged"]
                                        [nil nil]]]
      (is (= expected-message (driver/humanize-connection-error-message :exasol message))))))

(deftest describe-fks-sql-test
  (testing "Driver generates correct SQL statement for retrieving foreign keys without filter"
    (is (= ["SELECT \"c\".\"REFERENCED_SCHEMA\" \"pk-table-schema\", \"c\".\"REFERENCED_TABLE\" \"pk-table-name\", \"c\".\"REFERENCED_COLUMN\" \"pk-column-name\", \"c\".\"CONSTRAINT_SCHEMA\" \"fk-table-schema\", \"c\".\"CONSTRAINT_TABLE\" \"fk-table-name\", \"c\".\"COLUMN_NAME\" \"fk-column-name\" FROM \"SYS\".\"EXA_DBA_CONSTRAINT_COLUMNS\" \"c\" WHERE (\"c\".\"CONSTRAINT_TYPE\" = 'FOREIGN KEY') AND (\"c\".\"REFERENCED_SCHEMA\" IS NOT NULL) ORDER BY \"fk-table-schema\" ASC, \"fk-table-name\" ASC"]
           (sql-jdbc.sync/describe-fks-sql :exasol))))
  (testing "Driver generates correct SQL statement for retrieving foreign keys with schema filter"
    (is (= ["SELECT \"c\".\"REFERENCED_SCHEMA\" \"pk-table-schema\", \"c\".\"REFERENCED_TABLE\" \"pk-table-name\", \"c\".\"REFERENCED_COLUMN\" \"pk-column-name\", \"c\".\"CONSTRAINT_SCHEMA\" \"fk-table-schema\", \"c\".\"CONSTRAINT_TABLE\" \"fk-table-name\", \"c\".\"COLUMN_NAME\" \"fk-column-name\" FROM \"SYS\".\"EXA_DBA_CONSTRAINT_COLUMNS\" \"c\" WHERE (\"c\".\"CONSTRAINT_TYPE\" = 'FOREIGN KEY') AND (\"c\".\"REFERENCED_SCHEMA\" IS NOT NULL) AND (\"c\".\"CONSTRAINT_SCHEMA\" IN (?, ?)) ORDER BY \"fk-table-schema\" ASC, \"fk-table-name\" ASC"
            "sch1"
            "sch2"]
           (sql-jdbc.sync/describe-fks-sql :exasol {:schema-names ["sch1" "sch2"]}))))
  (testing "Driver generates correct SQL statement for retrieving foreign keys with table filter"
    (is (= ["SELECT \"c\".\"REFERENCED_SCHEMA\" \"pk-table-schema\", \"c\".\"REFERENCED_TABLE\" \"pk-table-name\", \"c\".\"REFERENCED_COLUMN\" \"pk-column-name\", \"c\".\"CONSTRAINT_SCHEMA\" \"fk-table-schema\", \"c\".\"CONSTRAINT_TABLE\" \"fk-table-name\", \"c\".\"COLUMN_NAME\" \"fk-column-name\" FROM \"SYS\".\"EXA_DBA_CONSTRAINT_COLUMNS\" \"c\" WHERE (\"c\".\"CONSTRAINT_TYPE\" = 'FOREIGN KEY') AND (\"c\".\"REFERENCED_SCHEMA\" IS NOT NULL) AND (\"c\".\"CONSTRAINT_TABLE\" IN (?, ?)) ORDER BY \"fk-table-schema\" ASC, \"fk-table-name\" ASC"
            "tab2"
            "tab2"]
           (sql-jdbc.sync/describe-fks-sql :exasol {:table-names ["tab2" "tab2"]}))))
  (testing "Driver generates correct SQL statement for retrieving foreign keys with schema and table filter"
    (is (= ["SELECT \"c\".\"REFERENCED_SCHEMA\" \"pk-table-schema\", \"c\".\"REFERENCED_TABLE\" \"pk-table-name\", \"c\".\"REFERENCED_COLUMN\" \"pk-column-name\", \"c\".\"CONSTRAINT_SCHEMA\" \"fk-table-schema\", \"c\".\"CONSTRAINT_TABLE\" \"fk-table-name\", \"c\".\"COLUMN_NAME\" \"fk-column-name\" FROM \"SYS\".\"EXA_DBA_CONSTRAINT_COLUMNS\" \"c\" WHERE (\"c\".\"CONSTRAINT_TYPE\" = 'FOREIGN KEY') AND (\"c\".\"REFERENCED_SCHEMA\" IS NOT NULL) AND (\"c\".\"CONSTRAINT_SCHEMA\" IN (?, ?)) AND (\"c\".\"CONSTRAINT_TABLE\" IN (?, ?)) ORDER BY \"fk-table-schema\" ASC, \"fk-table-name\" ASC"
            "sch1"
            "sch2"
            "tab2"
            "tab2"]
           (sql-jdbc.sync/describe-fks-sql :exasol {:schema-names ["sch1" "sch2"] :table-names ["tab2" "tab2"]})))))

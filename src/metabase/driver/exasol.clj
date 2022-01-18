(ns metabase.driver.exasol
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [honeysql.core :as hsql]
            [honeysql.format :as hformat]
            [metabase.config :as config]
            [metabase.driver :as driver]
            [metabase.driver.common :as driver.common]
            [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.execute.legacy-impl :as legacy]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.sql.query-processor.empty-string-is-null :as sql.qp.empty-string-is-null]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.util :as u]
            [metabase.util.honeysql-extensions :as hx]
            [metabase.util.i18n :refer [trs]]
            [java-time :as t]))


(defn get-jdbc-driver-version []
  (com.exasol.jdbc.EXADriver/getVersionInfo))

(defn get-driver-version
  ([]
   (get-driver-version "META-INF/maven/metabase/exasol-driver/pom.properties"))
  ([resource]
   (when-let [url ^java.net.URL (io/resource resource)]
     (with-open [stream (.openStream url)]
       (let [properties (java.util.Properties.)]
         (try
           (.load properties stream)
           (.getProperty properties "version")
           (catch Exception _)))))))

(defn- log-driver-version []
  (log/info (u/format-color 'green (format "Loading Exasol Metabase driver %s, Exasol JDBC driver: %s"
                                           (get-driver-version) (get-jdbc-driver-version)))))

(log-driver-version)

(driver/register! :exasol, :parent #{:sql-jdbc ::sql.qp.empty-string-is-null/empty-string-is-null ::legacy/use-legacy-classes-for-read-and-set})

(defmethod driver/display-name :exasol [_]
  "Exasol")

(doseq [[feature supported?] {:set-timezone   true
                              :nested-fields  false}]
  #_{:clj-kondo/ignore [:deprecated-var]} ; Function is deprecated but no replacement is available
  (defmethod driver/supports? [:exasol feature] [_ _] supported?))

(defmethod sql-jdbc.conn/connection-details->spec :exasol
  [_ {:keys [user password host port certificate-fingerprint]
      :or   {user "dbuser", password "dbpassword", host "localhost", port 8563}
      :as   details}]
  (-> {:clientname         "Metabase"
       :clientversion      config/mb-version-string
       :classname          "com.exasol.jdbc.EXADriver"
       :subprotocol        "exa"
       :subname            (str host ":" port)
       :password           password
       :user               user
       :fingerprint        certificate-fingerprint
       :feedbackinterval   "1"
       :additional-options ""}

      (sql-jdbc.common/handle-additional-options details, :seperator-style :semicolon)))

(def ^:private database-type->base-type
  (sql-jdbc.sync/pattern-based-database-type->base-type
   [;; https://docs.exasol.com/sql_references/data_types/datatypesoverview.htm
    [#"^BOOLEAN$"                         :type/Boolean]
    [#"^CHAR$"                            :type/Text]
    [#"^VARCHAR$"                         :type/Text]
    [#"^BIGINT$"                          :type/Decimal]
    [#"^DECIMAL$"                         :type/Decimal]
    [#"^DOUBLE PRECISION$"                :type/Float]
    [#"^DOUBLE$"                          :type/Float]
    [#"^DATE$"                            :type/Date]
    [#"^TIMESTAMP$"                       :type/DateTime]
    [#"^TIMESTAMP WITH LOCAL TIME ZONE$"  :type/DateTime]
    [#"^HASHTYPE$"                        :type/*]
    [#"^INTERVAL DAY TO SECOND$"          :type/*]
    [#"^INTERVAL YEAR TO MONTH$"          :type/*]
    [#"^GEOMETRY$"                        :type/*]]))

(defmethod sql-jdbc.sync/database-type->base-type :exasol
  [_ column-type]
  (if (nil? column-type)
    nil
    (database-type->base-type column-type)))

(defn create-set-timezone-sql
  "Creates the SQL statement required for setting the session timezone"
  [timezone-id]
  (format "ALTER SESSION SET TIME_ZONE='%s'" timezone-id))

(defn- set-time-zone!
  "Set the session timezone for the given connection"
  [conn timezone-id]
  (when timezone-id
    (let [set-timezone-sql (create-set-timezone-sql timezone-id)]
      (with-open [stmt (.createStatement conn)]
        (.execute stmt set-timezone-sql)
        (log/tracef "Successfully set timezone for Exasol to %s using statement %s" timezone-id set-timezone-sql)))))

;; Same as default implementation but without calling the unsupported setHoldability() method
(defmethod sql-jdbc.execute/connection-with-timezone :exasol
  [driver database ^String timezone-id]
  (let [conn (.getConnection (sql-jdbc.execute/datasource-with-diagnostic-info! driver database))]
    (try
      (sql-jdbc.execute/set-best-transaction-level! driver conn)
      (set-time-zone! conn timezone-id)
      (try
        (.setReadOnly conn true)
        (catch Throwable e
          (log/warn e (trs "Error setting connection to read-only"))))
      (try
        (.setAutoCommit conn false)
        (catch Throwable e
          (log/warn e (trs "Error setting connection to autoCommit false"))))
      conn
      (catch Throwable e
        (.close conn)
        (throw e)))))

(defn- trunc
  "Truncate a date. See also this 
      (trunc :day date) -> TRUNC(date, 'day')"
  [format-template date]
  (hsql/call :truncate date (hx/literal format-template)))

(defn- extract
  "Extract a date. See also this 
      (extract :minute date) -> EXTRACT(MINUTE FROM date)"
  [unit date]
  (hsql/call :extract unit date))

(defn- extract-from-timestamp
  "Extract a date. See also this 
      (extract :minute timestamp) -> EXTRACT(MINUTE FROM timestamp)"
  [unit timestamp]
  (hsql/call :extract unit (hx/->timestamp timestamp)))

(defmethod sql.qp/date [:exasol :minute]         [_ _ date] (trunc :mi date))
(defmethod sql.qp/date [:exasol :minute-of-hour] [_ _ date] (extract-from-timestamp :minute date))
(defmethod sql.qp/date [:exasol :hour]           [_ _ date] (trunc :hh date))
(defmethod sql.qp/date [:exasol :hour-of-day]    [_ _ date] (extract-from-timestamp :hour date))
(defmethod sql.qp/date [:exasol :day]            [_ _ date] (trunc :dd date))
(defmethod sql.qp/date [:exasol :day-of-month]   [_ _ date] (extract :day date))
(defmethod sql.qp/date [:exasol :month]          [_ _ date] (trunc :month date))
(defmethod sql.qp/date [:exasol :month-of-year]  [_ _ date] (extract :month date))
(defmethod sql.qp/date [:exasol :quarter]        [_ _ date] (trunc :q date))
(defmethod sql.qp/date [:exasol :year]           [_ _ date] (trunc :year date))
; Default implementation for :week-of-year is OK

(defmethod sql.qp/date [:exasol :week]
  [driver _ date]
  (sql.qp/adjust-start-of-week driver (partial trunc :day) date))

(defn- to-date
  [value]
  (hsql/call :to_date value))

(defmethod sql.qp/date [:exasol :day-of-year]
  [_ _ date]
  (hx/inc  (hx/- (to-date (trunc :dd date)) (to-date (trunc :year date)))))

(defmethod sql.qp/date [:exasol :quarter-of-year]
  [driver _ date]
  (hx// (hx/+ (sql.qp/date driver :month-of-year (sql.qp/date driver :quarter date))
              2)
        3))

(defmethod sql.qp/date [:exasol :day-of-week]
  [driver _ date]
  (sql.qp/adjust-day-of-week
   driver
   (hx/->integer (hsql/call :to_char date (hx/literal :d)))
   (driver.common/start-of-week-offset driver)
   (partial hsql/call (u/qualified-name ::mod))))

;; Exasol mod is a function like mod(x, y) rather than an operator like x mod y
;; https://docs.exasol.com/sql_references/functions/alphabeticallistfunctions/mod.htm
(defmethod hformat/fn-handler (u/qualified-name ::mod)
  [_ x y]
  (format "mod(%s, %s)" (hformat/to-sql x) (hformat/to-sql y)))

;;;;

(def ^:private now (hsql/raw "SYSTIMESTAMP"))

(defmethod sql.qp/current-datetime-honeysql-form :exasol [_] now)

(defmethod sql.qp/->honeysql [:exasol :regex-match-first]
  [driver [_ arg pattern]]
  (hsql/call :regexp_substr (sql.qp/->honeysql driver arg) (sql.qp/->honeysql driver pattern)))

(defn- num-to-ds-interval [unit value] (hsql/call :numtodsinterval value (hx/literal unit)))
(defn- num-to-ym-interval [unit value] (hsql/call :numtoyminterval value (hx/literal unit)))

(defmethod sql.qp/add-interval-honeysql-form :exasol
  [_ hsql-form amount unit]
  (hx/+
   (hx/->timestamp hsql-form)
   (case unit
     :second  (num-to-ds-interval :second amount)
     :minute  (num-to-ds-interval :minute amount)
     :hour    (num-to-ds-interval :hour   amount)
     :day     (num-to-ds-interval :day    amount)
     :week    (num-to-ds-interval :day    (hx/* amount (hsql/raw 7)))
     :month   (num-to-ym-interval :month  amount)
     :quarter (num-to-ym-interval :month  (hx/* amount (hsql/raw 3)))
     :year    (num-to-ym-interval :year   amount))))

(defmethod sql.qp/cast-temporal-string [:exasol :Coercion/ISO8601->DateTime]
  [_ _ expr]
  (hsql/call :to_timestamp expr "YYYY-MM-DD HH:mi:SS"))

(defmethod sql.qp/cast-temporal-string [:exasol :Coercion/ISO8601->Date]
  [_ _ expr]
  (hsql/call :to_date expr "YYYY-MM-DD"))

(defmethod sql.qp/cast-temporal-string [:exasol :Coercion/YYYYMMDDHHMMSSString->Temporal]
  [_ _ expr]
  (hsql/call :to_timestamp expr "YYYYMMDDHH24miSS"))

(defmethod sql.qp/unix-timestamp->honeysql [:exasol :seconds]
  [_ _ expr]
  (hsql/call :from_posix_time expr))

(defmethod sql.qp/unix-timestamp->honeysql [:exasol :milliseconds]
  [driver _ field-or-value]
  (sql.qp/unix-timestamp->honeysql driver :seconds (hx// field-or-value (hsql/raw 1000))))

(defmethod sql.qp/unix-timestamp->honeysql [:exasol :microseconds]
  [driver _ field-or-value]
  (sql.qp/unix-timestamp->honeysql driver :seconds (hx// field-or-value (hsql/raw 1000000))))

(defmethod driver/db-default-timezone :exasol [_ _]
  "UTC")

(defmethod driver/db-start-of-week :exasol
  [_]
  :sunday)

(defmethod sql-jdbc.sync/excluded-schemas :exasol
  [_]
  #{"EXA_STATISTICS"
    "SYS"})

(defmethod unprepare/unprepare-value [:exasol java.time.OffsetDateTime]
  [_ t]
  (format "timestamp '%s'" (t/format "yyyy-MM-dd HH:mm:ss.SSS" t)))

(defmethod unprepare/unprepare-value [:exasol java.time.ZonedDateTime]
  [_ t]
  (format "timestamp '%s'" (t/format "yyyy-MM-dd HH:mm:ss.SSS" t)))

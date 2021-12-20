(ns metabase.driver.exasol
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [honeysql.core :as hsql]
            [honeysql.format :as hformat]
            [metabase.config :as config]
            [metabase.driver :as driver]
            [metabase.driver.common :as driver.common]
            [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.util :as u]
            [metabase.util.date-2 :as u.date]
            [metabase.util.honeysql-extensions :as hx]
            [metabase.util.i18n :refer [trs]]
            [java-time :as t])
  (:import))

(driver/register! :exasol, :parent :sql-jdbc)

(defmethod driver/display-name :exasol [_]
  "Exasol")

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

    [#"^BOOLEAN$"          :type/Boolean]

    [#"^CHAR$"             :type/Text]
    [#"^VARCHAR$"          :type/Text]
    [#"^HASHTYPE$"         :type/Text]

    [#"^BIGINT$"           :type/Decimal]
    [#"^DECIMAL$"          :type/Decimal]
    [#"^DOUBLE PRECISION$" :type/Decimal]
    [#"^DOUBLE$"           :type/Float]
    [#"^DATE$"             :type/Date]
    [#"^TIMESTAMP$"        :type/DateTime]
    [#"^TIMESTAMP WITH LOCAL TIME ZONE$"  :type/DateTime]
    [#"^INTERVAL DAY TO SECOND$"        :type/Text]
    [#"^INTERVAL YEAR TO MONTH$"        :type/Text]
    [#"^GEOMETRY$"         :type/Text]]))

(defmethod sql-jdbc.sync/database-type->base-type :exasol
  [_ column-type]
  (database-type->base-type column-type))

(defmethod sql-jdbc.execute/read-column-thunk [:exasol java.sql.Types/DATE]
  [_ ^java.sql.ResultSet rs _ ^Integer i]
  (fn []
    (when-let [s (.getString rs i)]
      (let [t (u.date/parse s)]
        (log/tracef "(.getString rs i) [DATE] -> %s -> %s" (pr-str s) (pr-str t))
        t))))

(defmethod sql-jdbc.execute/read-column-thunk [:exasol java.sql.Types/TIMESTAMP]
  [_ ^java.sql.ResultSet rs _ ^Integer i]
  (fn []
    (java.time.LocalDateTime/ofInstant (.toInstant (.getTimestamp rs i)) (java.time.ZoneId/of "UTC"))))

(defmethod sql-jdbc.execute/set-parameter [:exasol java.time.OffsetDateTime]
  [driver ps i t]
  (sql-jdbc.execute/set-parameter driver ps i (t/sql-timestamp (t/with-offset-same-instant t (t/zone-offset 0)))))

(defmethod sql-jdbc.execute/set-parameter [:exasol java.time.LocalDate]
  [driver ps i t]
  (sql-jdbc.execute/set-parameter driver ps i (t/sql-date t)))

(defmethod sql-jdbc.execute/set-parameter [:exasol java.time.LocalDateTime]
  [driver ps i t]
  (sql-jdbc.execute/set-parameter driver ps i (t/sql-timestamp t)))

;; TODO: is this ok?
(defmethod sql-jdbc.execute/set-parameter [:exasol java.time.LocalTime]
  [driver ps i t]
  (sql-jdbc.execute/set-parameter driver ps i (t/sql-timestamp t)))

(defmethod sql-jdbc.execute/set-parameter [:exasol java.time.OffsetTime]
  [driver ps i t]
  (sql-jdbc.execute/set-parameter driver ps i (t/sql-timestamp t)))

;; Same as default implementation but without calling the unsupported setHoldability() method
(defmethod sql-jdbc.execute/connection-with-timezone :exasol
  [driver database ^String timezone-id]
  (let [conn (.getConnection (sql-jdbc.execute/datasource-with-diagnostic-info! driver database))]
    (try
      (sql-jdbc.execute/set-best-transaction-level! driver conn)
      #_{:clj-kondo/ignore [:deprecated-var]} ; Function is deprecated
      (sql-jdbc.execute/set-time-zone-if-supported! driver conn timezone-id)
      (try
        (.setReadOnly conn true)
        (catch Throwable e
          (log/debug e (trs "Error setting connection to read-only"))))
      (try
        (.setAutoCommit conn false)
        (catch Throwable e
          (log/debug e (trs "Error setting connection to autoCommit false"))))
      conn
      (catch Throwable e
        (.close conn)
        (throw e)))))

(defn- trunc
  "Truncate a date. See also this 
      (trunc :day v) -> TRUNC(v, 'day')"
  [format-template v]
  (hsql/call :truncate v (hx/literal format-template)))

(defn- extract
  "Extract a date. See also this 
      (extract :minute v) -> EXTRACT(MINUTE FROM v)"
  [param v]
  (hsql/call :extract param v))

(defn- extract-from-timestamp
  "Extract a date. See also this 
      (extract :minute v) -> EXTRACT(MINUTE FROM v)"
  [param v]
  (hsql/call :extract param (hx/->timestamp v)))

(defmethod sql.qp/date [:exasol :minute]         [_ _ v] (trunc :mi v))
(defmethod sql.qp/date [:exasol :minute-of-hour] [_ _ v] (extract-from-timestamp :minute v))
(defmethod sql.qp/date [:exasol :hour]           [_ _ v] (trunc :hh v))
(defmethod sql.qp/date [:exasol :hour-of-day]    [_ _ v] (extract-from-timestamp :hour v))
(defmethod sql.qp/date [:exasol :day]            [_ _ v] (trunc :dd v))
(defmethod sql.qp/date [:exasol :day-of-month]   [_ _ v] (extract :day v))
(defmethod sql.qp/date [:exasol :month]          [_ _ v] (trunc :month v))
(defmethod sql.qp/date [:exasol :month-of-year]  [_ _ v] (extract :month v))
(defmethod sql.qp/date [:exasol :quarter]        [_ _ v] (trunc :q v))
(defmethod sql.qp/date [:exasol :year]           [_ _ v] (trunc :year v))

(defmethod sql.qp/date [:exasol :week]
  [driver _ v]
  (sql.qp/adjust-start-of-week driver (partial trunc :day) v))

(defmethod sql.qp/date [:exasol :day-of-year]
  [driver _ v]
  (hx/inc (hx/- (sql.qp/date driver :day v) (trunc :year v))))

(defmethod sql.qp/date [:exasol :quarter-of-year]
  [driver _ v]
  (hx// (hx/+ (sql.qp/date driver :month-of-year (sql.qp/date driver :quarter v))
              2)
        3))

(defmethod sql.qp/date [:exasol :day-of-week]
  [driver _ v]
  (sql.qp/adjust-day-of-week
   driver
   (hx/->integer (hsql/call :to_char v (hx/literal :d)))
   (driver.common/start-of-week-offset driver)
   (partial hsql/call (u/qualified-name ::mod))))


;;;;

(def ^:private now (hsql/raw "SYSDATE"))
(defn- num-to-ds-interval [unit v] (hsql/call :numtodsinterval v (hx/literal unit)))
(defn- num-to-ym-interval [unit v] (hsql/call :numtoyminterval v (hx/literal unit)))

(defmethod sql.qp/current-datetime-honeysql-form :exasol [_] now)

(defmethod sql.qp/->honeysql [:exasol :regex-match-first]
  [driver [_ arg pattern]]
  (hsql/call :regexp_substr (sql.qp/->honeysql driver arg) (sql.qp/->honeysql driver pattern)))

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

(defmethod sql.qp/unix-timestamp->honeysql [:exasol :seconds]
  [_ _ expr]
  (hsql/call :from_posix_time expr))


(defmethod sql.qp/cast-temporal-string [:exasol :Coercion/ISO8601->DateTime]
  [_driver _coercion-strategy expr]
  (hsql/call :to_timestamp expr "YYYY-MM-DD HH:mi:SS"))

(defmethod sql.qp/cast-temporal-string [:exasol :Coercion/ISO8601->Date]
  [_driver _coercion-strategy expr]
  (hsql/call :to_date expr "YYYY-MM-DD"))

(defmethod sql.qp/cast-temporal-string [:exasol :Coercion/YYYYMMDDHHMMSSString->Temporal]
  [_driver _coercion-strategy expr]
  (hsql/call :to_timestamp expr "YYYYMMDDHH24miSS"))

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

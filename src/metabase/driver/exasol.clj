(ns metabase.driver.exasol
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [java-time :as t]
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
            [metabase.util.honey-sql-2 :as h2x]
            [metabase.util.i18n :refer [trs]]
            [yaml.core :as yaml]))

(defn- invoke-static-method
  "Invoke a static method via reflection"
  [class-name method-name]
  (let [class (java.lang.Class/forName class-name)
        method (.getMethod class method-name (make-array Class 0))
        result (.invoke method nil (make-array Object 0))]
    result))

(defn get-jdbc-driver-version
  "Get the JDBC driver's version number via reflection. This avoids having a runtime dependency on the driver"
  []
  (try (invoke-static-method "com.exasol.jdbc.EXADriver" "getVersionInfo")
       (catch Exception e (log/warn (str "Error getting JDBC driver version: " e)))))

(defn get-driver-version
  "Reads the driver version from the plugin yaml file on the classpath."
  ([]
   (get-driver-version "metabase-plugin.yaml"))
  ([resource-name]
   (when-let [resource (io/resource resource-name)]
     (let [content (slurp resource)
           parsed-yaml (yaml/parse-string content)]
       (get-in parsed-yaml [:info :version])))))

(defn- log-driver-version []
  (log/info (u/format-color 'green (format "Loading Exasol Metabase driver %s, Exasol JDBC driver: %s"
                                           (get-driver-version) (get-jdbc-driver-version)))))

(log-driver-version)

(driver/register! :exasol :parent #{:sql-jdbc
                                    ::sql.qp.empty-string-is-null/empty-string-is-null
                                    ::legacy/use-legacy-classes-for-read-and-set})

(defmethod driver/display-name :exasol [_]
  "Exasol")

(doseq [[feature supported?] {:set-timezone           true
                              :nested-fields          false
                              :nested-field-columns   false}]
  (defmethod driver/database-supports? [:exasol feature] [_ _ _] supported?))

; Opt-in to use Honey SQL 2
(defmethod sql.qp/honey-sql-version :exasol
  [_driver]
  2)

(defmethod sql-jdbc.conn/connection-details->spec :exasol
  [_ {:keys [user password host port certificate-fingerprint]
      :or   {user "dbuser" password "dbpassword" host "localhost" port 8563}
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

(defmethod driver/humanize-connection-error-message :exasol
  [_ message]
  (when (not (nil? message))
    (condp re-matches message
      #"^java\.net\.ConnectException: Connection refused.*$"
      :cannot-connect-check-host-and-port

      #"^Connection exception - authentication failed\..*$"
      :username-or-password-incorrect

      #"^Unknown host name\..*$"
      :invalid-hostname

      #"^java\.io\.IOException: TLS connection to host .* failed: PKIX path building failed: sun\.security\.provider\.certpath\.SunCertPathBuilderException: unable to find valid certification path to requested target\. If you trust the server, you can include the fingerprint in the connection string: .*/(\w+):.*"
      :>>
      #(format "The server's TLS certificate is not signed. If you trust the server specify the following fingerprint: %s." (second %))

      #"^\[ERROR\] Fingerprint did not match\. The fingerprint provided: (.*)\. Server's certificate fingerprint: (\w+)\..*"
      :>>
      #(format "The server's TLS certificate has fingerprint %s but we expected %s." (nth % 2) (second %))

      #".*" ; default
      message)))

(def ^:private database-type->base-type
  (sql-jdbc.sync/pattern-based-database-type->base-type
   [;; https://docs.exasol.com/sql_references/data_types/datatypesoverview.htm
    [#"^BOOLEAN$"                         :type/Boolean]
    [#"^CHAR$"                            :type/Text]
    [#"^VARCHAR$"                         :type/Text]
    [#"^DECIMAL$"                         :type/Decimal]
    [#"^BIGINT$"                          :type/Decimal] ; Precision <= 18
    [#"^INTEGER$"                         :type/Decimal] ; Precision <= 9
    [#"^SMALLINT$"                        :type/Decimal] ; Precision <= 4
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

(defn- trunc-date
  "Truncate a date, e.g.:
      (trunc-date :day date) -> TRUNC(CAST(date AS DATE), 'day')"
  [format-template date]
  (sql/call :truncate (h2x/->date date) (h2x/literal format-template)))

(defn- trunc-timestamp
  "Truncate a timestamp, e.g.:
      (trunc-timestamp :day date) -> TRUNC(CAST(date AS TIMESTAMP), 'day')"
  [format-template date]
  (sql/call :truncate (h2x/->timestamp date) (h2x/literal format-template)))

(defn- extract-from-timestamp
  "Extract a date. See also this 
      (extract :minute timestamp) -> EXTRACT(MINUTE FROM timestamp)"
  [unit timestamp]
  (sql/call :extract unit (h2x/->timestamp timestamp)))

(defmethod sql.qp/date [:exasol :minute]         [_ _ date] (trunc-timestamp :mi date))
(defmethod sql.qp/date [:exasol :minute-of-hour] [_ _ date] (extract-from-timestamp :minute date))
(defmethod sql.qp/date [:exasol :hour]           [_ _ date] (trunc-timestamp :hh date))
(defmethod sql.qp/date [:exasol :hour-of-day]    [_ _ date] (extract-from-timestamp :hour date))
(defmethod sql.qp/date [:exasol :day]            [_ _ date] (trunc-date :dd date))
(defmethod sql.qp/date [:exasol :day-of-month]   [_ _ date] (extract-from-timestamp :day date))
(defmethod sql.qp/date [:exasol :month]          [_ _ date] (trunc-date :month date))
(defmethod sql.qp/date [:exasol :month-of-year]  [_ _ date] (extract-from-timestamp :month date))
(defmethod sql.qp/date [:exasol :quarter]        [_ _ date] (trunc-date :q date))
(defmethod sql.qp/date [:exasol :year]           [_ _ date] (trunc-date :year date))
(defmethod sql.qp/date [:exasol :week-of-year]   [_ _ expr] (sql/call :ceil (h2x// (sql.qp/date :exasol :day-of-year (sql.qp/date :exasol :week expr)) 7.0)))

(defmethod sql.qp/date [:exasol :week]
  [driver _ date]
  (sql.qp/adjust-start-of-week driver (partial trunc-date :day) date))

(defn- to-date
  [value]
  (sql/call :to_date value))

(defmethod sql.qp/date [:exasol :day-of-year]
  [_ _ date]
  (h2x/inc  (h2x/- (to-date (trunc-date :dd date)) (to-date (trunc-date :year date)))))

(defmethod sql.qp/date [:exasol :quarter-of-year]
  [driver _ date]
  (h2x// (h2x/+ (sql.qp/date driver :month-of-year (sql.qp/date driver :quarter date))
              2)
        3))

(defmethod sql.qp/date [:exasol :day-of-week]
  [driver _ date]
  (sql.qp/adjust-day-of-week
   driver
   (h2x/->integer (sql/call :to_char (h2x/->timestamp date) (h2x/literal :d)))
   (driver.common/start-of-week-offset driver)
   (partial sql/call (u/qualified-name ::mod))))

;; Exasol mod is a function like mod(x, y) rather than an operator like x mod y
;; https://docs.exasol.com/sql_references/functions/alphabeticallistfunctions/mod.htm
(defn- format-mod
  [_fn [x y]]
  (let [[x-sql & x-args] (sql/format-expr x {:nested true})
        [y-sql & y-args] (sql/format-expr y {:nested true})]
    (into [(format "mod(%s, %s)" x-sql y-sql)]
          cat
          [x-args
           y-args])))

(sql/register-fn! ::mod #'format-mod)

;;;;

(defmethod sql.qp/current-datetime-honeysql-form :exasol
  [_]
  (h2x/with-database-type-info [:raw "SYSTIMESTAMP"] "timestamp"))

(defmethod sql.qp/->honeysql [:exasol :regex-match-first]
  [driver [_ arg pattern]]
  (sql/call :regexp_substr (sql.qp/->honeysql driver arg) (sql.qp/->honeysql driver pattern)))

(defn- num-to-ds-interval [unit value] (sql/call :numtodsinterval value (h2x/literal unit)))
(defn- num-to-ym-interval [unit value] (sql/call :numtoyminterval value (h2x/literal unit)))

(def ^:private timestamp-types
  #{"timestamp" "timestamp with local time zone"})

(defn- cast-to-timestamp-if-needed
  "If `hsql-form` isn't already one of the [[timestamp-types]], cast it to `timestamp`."
  [hsql-form]
  (h2x/cast-unless-type-in "timestamp" timestamp-types hsql-form))

(defmethod sql.qp/add-interval-honeysql-form :exasol
  [_ hsql-form amount unit]
  (case unit
    :second  (h2x/+ (cast-to-timestamp-if-needed hsql-form) (num-to-ds-interval :second amount))
    :minute  (h2x/+ (cast-to-timestamp-if-needed hsql-form) (num-to-ds-interval :minute amount))
    :hour    (h2x/+ (cast-to-timestamp-if-needed hsql-form) (num-to-ds-interval :hour   amount))
    :day     (h2x/+ (cast-to-timestamp-if-needed hsql-form) (num-to-ds-interval :day    amount))
    :week    (h2x/+ (cast-to-timestamp-if-needed hsql-form) (num-to-ds-interval :day    (h2x/* amount [:inline 7])))
    :month   (h2x/+ (cast-to-timestamp-if-needed hsql-form) (num-to-ym-interval :month  amount))
    :quarter (h2x/+ (cast-to-timestamp-if-needed hsql-form) (num-to-ym-interval :month  (h2x/* amount [:inline 3])))
    :year    (h2x/+ (cast-to-timestamp-if-needed hsql-form) (num-to-ym-interval :year   amount))))

(defmethod sql.qp/cast-temporal-string [:exasol :Coercion/ISO8601->DateTime]
  [_ _ expr]
  (sql/call :to_timestamp expr "YYYY-MM-DD HH:mi:SS"))

(defmethod sql.qp/cast-temporal-string [:exasol :Coercion/ISO8601->Date]
  [_ _ expr]
  (sql/call :to_date expr "YYYY-MM-DD"))

(defmethod sql.qp/cast-temporal-string [:exasol :Coercion/YYYYMMDDHHMMSSString->Temporal]
  [_ _ expr]
  (sql/call :to_timestamp expr "YYYYMMDDHH24miSS"))

(defmethod sql.qp/unix-timestamp->honeysql [:exasol :seconds]
  [_ _ expr]
  (sql/call :from_posix_time expr))

(defmethod sql.qp/unix-timestamp->honeysql [:exasol :milliseconds]
  [driver _ field-or-value]
  (sql.qp/unix-timestamp->honeysql driver :seconds (h2x// field-or-value [:inline 1000])))

(defmethod sql.qp/unix-timestamp->honeysql [:exasol :microseconds]
  [driver _ field-or-value]
  (sql.qp/unix-timestamp->honeysql driver :seconds (h2x// field-or-value [:inline 1000000])))

(defmethod driver/db-default-timezone :exasol [_ _]
  "UTC")

; First day of the week in Exasol is Sunday = 7 by default, see https://docs.exasol.com/db/latest/sql/alter_system.htm
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

(ns metabase.driver.exasol
  (:require [clojure.tools.logging :as log]
            [metabase.config :as config]
            [metabase.driver :as driver]
            [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.util.date-2 :as u.date]
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
       :additional-options ""}

      (sql-jdbc.common/handle-additional-options details, :seperator-style :semicolon)))


(def ^:private database-type->base-type
  (sql-jdbc.sync/pattern-based-database-type->base-type
   [;; https://docs.exasol.com/sql_references/data_types/datatypesoverview.htm

    [#"^BOOLEAN$"          :type/Boolean]

    [#"^CHAR$"             :type/Text]
    [#"^VARCHAR$"          :type/Text]
    [#"^HASHTYPE$"         :type/Text]

    [#"^BIGINT$"           :type/BigInteger]
    [#"^DECIMAL$"          :type/Decimal]
    [#"^DOUBLE PRECISION$" :type/Decimal]
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
    (.getTimestamp rs i)))

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

;; Same as default implementation but without calling the unsupported setHoldability() method
(defmethod sql-jdbc.execute/connection-with-timezone :exasol
  [driver database ^String timezone-id]
  (let [conn (.getConnection (sql-jdbc.execute/datasource-with-diagnostic-info! driver database))]
    (try
      (sql-jdbc.execute/set-best-transaction-level! driver conn)
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

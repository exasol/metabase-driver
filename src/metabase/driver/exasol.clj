(ns metabase.driver.exasol
  (:require [metabase.config :as config]
            [metabase.driver :as driver]
            [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.query-processor.timezone :as qp.timezone]
            [java-time :as t]))

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
    (.getDate rs i)))

(defmethod sql-jdbc.execute/read-column-thunk [:exasol java.sql.Types/TIMESTAMP]
  [_ ^java.sql.ResultSet rs _ ^Integer i]
  (fn []
    (.getTimestamp rs i)))

;;(defmethod sql-jdbc.execute/set-parameter [:exasol java.time.OffsetDateTime]
;;  [driver ps i t]
;;  (println "###### set param exasol")
;;  (sql-jdbc.execute/set-parameter driver ps i (t/sql-timestamp (t/with-offset-same-instant t (t/zone-offset 0)))))


(defmethod sql-jdbc.execute/set-parameter [:exasol java.time.OffsetDateTime]
  [driver ^java.sql.PreparedStatement ps ^Integer i t]
  (let [zone   (t/zone-id (qp.timezone/results-timezone-id))
        offset (.. zone getRules (getOffset (t/instant t)))
        t      (t/local-date-time (t/with-offset-same-instant t offset))]
    (println "###### set param exasol" t)
    (sql-jdbc.execute/set-parameter driver ps i t)))

;;(defmethod sql-jdbc.execute/set-parameter [:exasol java.time.ZonedDateTime]
;;  [driver ps i t]
;;  (sql-jdbc.execute/set-parameter driver ps i (t/offset-date-time t)))

;(defmethod sql-jdbc.execute/set-parameter [:exasol java.time.ZonedDateTime]
;  [driver ps i t]
;  (sql-jdbc.execute/set-parameter driver ps i (t/sql-timestamp (t/with-zone-same-instant t (t/zone-id "UTC")))))

(println "######## Exasol driver loaded")

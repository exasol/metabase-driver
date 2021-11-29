(ns metabase.driver.exasol
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [honeysql.core :as hsql]
            [honeysql.helpers :as h]
            [metabase.config :as config]
            [metabase.driver :as driver]
            [metabase.driver.common :as driver.common]
            [metabase.driver.sql :as sql]
            [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.util.ssh :as ssh]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.mbql.util :as mbql.u]
            [metabase.query-processor.interface :as qp.i]
            [metabase.util.honeysql-extensions :as hx]
            [metabase.util.i18n :refer [trs]]))

(driver/register! :exasol, :parent :sql-jdbc)

(def ^:private database-type->base-type
  (sql-jdbc.sync/pattern-based-database-type->base-type
   [;; https://docs.exasol.com/sql_references/data_types/datatypesoverview.htm
    [#"BOOLEAN"           :type/Boolean]
    [#"CHAR"              :type/Text]
    [#"DATE"              :type/Date]
    [#"VARCHAR"           :type/Text]
    [#"DOUBLE PRECISION"      :type/Float]
    [#"DECIMAL"      :type/Decimal]
    [#"INTERVAL"    :type/DateTime]
    [#"GEOMETRY"    :type/*]
    [#"TIMESTAMP"   :type/DateTime]]))

(defmethod sql-jdbc.sync/database-type->base-type :exasol
  [_ column-type]
  (database-type->base-type column-type))

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

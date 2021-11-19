(ns metabase.driver.exasol
  (:require [clojure.tools.logging :as log]
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
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.mbql.util :as mbql.u]
            [metabase.query-processor.interface :as qp.i]
            [metabase.util.honeysql-extensions :as hx]
            [metabase.util.i18n :refer [trs]]))

(driver/register! :exasol, :parent :sql-jdbc)

(defmethod driver/display-name :exasol [_]
  "Exasol")


(defmethod sql-jdbc.conn/connection-details->spec :exasol
  [_ {:keys [user password host port]
      :or   {user "dbuser", password "dbpassword", host "localhost"}
      :as   details}]
  (-> {;;:applicationName    config/mb-version-and-process-identifier
       :subprotocol        "exa"
       ;; it looks like the only thing that actually needs to be passed as the `subname` is the host; everything else
       ;; can be passed as part of the Properties
       :subname            (str "//" host ":"  port)
       ;; everything else gets passed as `java.util.Properties` to the JDBC connection.  (passing these as Properties
       ;; instead of part of the `:subname` is preferable because they support things like passwords with special
       ;; characters)
       :password           password
       ;; Wait up to 10 seconds for connection success. If we get no response by then, consider the connection failed
       :loginTimeout       10
       :user               user
       ;;:encrypt            (boolean ssl)
       }
      (sql-jdbc.common/handle-additional-options details, :seperator-style :semicolon)))
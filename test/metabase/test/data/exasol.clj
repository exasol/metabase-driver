(ns metabase.test.data.exasol
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.test.data.interface :as tx]
            [metabase.test.data.sql :as sql.tx]
            [metabase.test.data.sql-jdbc :as sql-jdbc.tx]
            [metabase.test.data.sql-jdbc.execute :as execute]
            [metabase.test.data.sql-jdbc.load-data :as load-data]
            [metabase.util :as u]))

(set! *warn-on-reflection* true)

(sql-jdbc.tx/add-test-extensions! :exasol)

(defonce ^:private session-schema-number (rand-int 200))
(defonce           session-schema        (str "CAM_" session-schema-number))

(defn- connection-details []
  (let [details* {:host     (tx/db-test-env-var-or-throw :exasol :host)
                  :port     (Integer/parseInt (tx/db-test-env-var-or-throw :exasol :port "8563"))
                  :user     (tx/db-test-env-var-or-throw :exasol :user)
                  :password (tx/db-test-env-var-or-throw :exasol :password)
                  :certificate-fingerprint (tx/db-test-env-var-or-throw :exasol :certificate-fingerprint)
                  :dbname   "exasol-db" ; Required by test metabase.driver.sql-jdbc.connection-test/c3p0-datasource-name-test
                  }]
    details*))

(defmethod tx/dbdef->connection-details :exasol [& _]
  (connection-details))

(defmethod tx/sorts-nil-first? :exasol [_ _] false)

(defonce ^:private number-column-type
  "DECIMAL(36,0)")

(doseq [[base-type sql-type] {:type/Text                   "VARCHAR(4000)"
                              :type/BigInteger             number-column-type
                              :type/Integer                number-column-type
                              :type/Decimal                number-column-type
                              :type/Float                  "DOUBLE PRECISION"
                              :type/Boolean                "BOOLEAN"
                              :type/Date                   "DATE"
                              :type/Temporal               "TIMESTAMP"
                              :type/DateTime               "TIMESTAMP"
                              :type/DateTimeWithTZ         "TIMESTAMP"
                              :type/DateTimeWithZoneOffset "TIMESTAMP"
                              :type/DateTimeWithZoneID     "TIMESTAMP"
                              :type/DateTimeWithLocalTZ    "TIMESTAMP WITH LOCAL TIME ZONE"}]
  (defmethod sql.tx/field-base-type->sql-type [:exasol base-type] [_ _] sql-type))

(doseq [base-type [:type/Time]]
  (defmethod sql.tx/field-base-type->sql-type [:exasol base-type] [_ base-type]
    (throw (UnsupportedOperationException. (format "Exasol does not have a %s data type." base-type)))))

(defmethod sql.tx/drop-table-if-exists-sql :exasol
  [_ {:keys [database-name]} {:keys [table-name]}]
  (format "DROP TABLE IF EXISTS \"%s\".\"%s\" CASCADE CONSTRAINTS"
          session-schema
          (tx/db-qualified-table-name database-name table-name)))


(defmethod sql.tx/create-db-sql :exasol [& _] nil)

(defmethod sql.tx/drop-db-if-exists-sql :exasol [& _] nil)

(defmethod execute/execute-sql! :exasol [& args]
  (apply execute/sequentially-execute-sql! args))

(defmethod sql.tx/pk-sql-type :exasol [_]
  (str number-column-type " IDENTITY NOT NULL"))

(defmethod sql.tx/qualified-name-components :exasol [& args]
  (apply tx/single-db-qualified-name-components session-schema args))

(defmethod tx/id-field-type :exasol [_] :type/Decimal)

(defmethod load-data/row-xform :exasol
  [_driver _dbdef tabledef]
  (load-data/maybe-add-ids-xform tabledef))

(defn- dbspec [& _]
  (sql-jdbc.conn/connection-details->spec :exasol (connection-details)))

(defn- non-session-schemas
  "Return a set of the names of schemas that are not meant for use in this test session (i.e., ones that should
  be ignored). (This is used as part of the implementation of `excluded-schemas` for the Exasol driver during tests.)"
  []
  (set (map :schema_name (jdbc/query (dbspec) ["SELECT schema_name FROM SYS.EXA_ALL_SCHEMAS WHERE schema_name <> ?" session-schema]))))

(defonce ^:private original-excluded-schemas
  (get-method sql-jdbc.sync/excluded-schemas :exasol))

(defmethod sql-jdbc.sync/excluded-schemas :exasol
  [driver]
  (set/union
   (original-excluded-schemas driver)
   (non-session-schemas)))

;;; Clear out the session schema before and after tests run
(defn- execute! [format-string & args]
  (let [sql (apply format format-string args)]
    (println "[exasol] " sql)
    (jdbc/execute! (dbspec) sql))
  (println "[ok]"))

(defn create-schema!
  ;; default to using session-password for all users created this session
  ([schema-name]
   (execute! "CREATE SCHEMA \"%s\"" schema-name)))

(defn drop-schema! [schema-name]
  (u/ignore-exceptions
   (execute! "DROP SCHEMA \"%s\" CASCADE" schema-name)))

(defmethod sql-jdbc.execute/set-timezone-sql :exasol
  [_]
  "ALTER SESSION SET TIME_ZONE = %s")

(defmethod tx/before-run :exasol
  [_]
  (drop-schema! session-schema)
  (create-schema! session-schema))

(defmethod tx/aggregate-column-info :exasol
  ([driver ag-type]
   (merge
    ((get-method tx/aggregate-column-info ::tx/test-extensions) driver ag-type)
    (when (#{:count :cum-count} ag-type)
      {:base_type :type/Decimal})))

  ([driver ag-type field]
   (merge
    ((get-method tx/aggregate-column-info ::tx/test-extensions) driver ag-type field)
    (when (#{:count :cum-count} ag-type)
      {:base_type :type/Decimal}))))

(defmethod tx/dataset-already-loaded? :exasol
  [driver dbdef]
  ;; check and make sure the first table in the dbdef has been created.
  (let [tabledef       (first (:table-definitions dbdef))
        ;; table-name should be something like test_data_venues
        table-name     (tx/db-qualified-table-name (:database-name dbdef) (:table-name tabledef))]
    (sql-jdbc.execute/do-with-connection-with-options
     driver
     (sql-jdbc.conn/connection-details->spec driver (connection-details))
     {:write? false}
     (fn [^java.sql.Connection conn]
       (with-open [rset (.getTables (.getMetaData conn)
                                    #_catalog        nil
                                    #_schema-pattern session-schema
                                    #_table-pattern  table-name
                                    #_types          (into-array String ["TABLE"]))]
         ;; if the ResultSet returns anything we know the table is already loaded.
         (.next rset))))))

; Exasol does not allow creating or dropping databases, there is only one DB called "exasol-db"
(defmethod sql.tx/drop-db-if-exists-sql :exasol [& _] nil)
(defmethod sql.tx/create-db-sql         :exasol [& _] nil)

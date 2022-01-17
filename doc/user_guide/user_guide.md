# User Guide

To use the Exasol driver for Metabase please first follow the [Metabase installation guide](https://www.metabase.com/docs/latest/operations-guide/installing-metabase.html) to download and install Metabase.

We assume that you installed Metabase at `$METABASE_DIR`.

## Installing the Driver

1. Download the latest Exasol JDBC driver for your operating system from the [Exasol download page](https://www.exasol.com/portal/display/DOWNLOAD/).
2. Copy the Exasol JDBC driver `exajdbc.jar` to `$METABASE_DIR/plugins`.
3. Download the latest Exasol Metabase driver from the [GitHub release page](https://github.com/exasol/metabase-driver/releases).
4. Copy the Exasol Metabase driver `exasol.metabase-driver.jar` to `$METABASE_DIR/plugins`.
5. Restart Metabase.

## Connect to Exasol

After you have installed the driver, create a new connection to your Exasol database.

1. Start the Metabase administration
    ![Open Metabase administration](images/open_administration.png "Open Metabase administration")
2. Select the "Databases" section and click on "Add Database"
    ![Add Database](images/add_database.png "Add Database")
3. Select "Exasol" from the "Database type" drop down list and fill out the connection details:
  * Name: Name of the database connection in Metabase
  * Host: Hostname of your Exasol database
  * Port: Port number of your Exasol database, default: `8563`
  * Certificate Fingerprint: If your Exasol database uses a self-signed certificate, enter the certificate's checksum.
  * Username: Name of the database user
  * Password: Password of the database user
    ![Create Exasol Database](images/add_exasol_database.png "Create Exasol Database")
4. Click the "Save" button at the bottom of the page to verify and save the database connection.

## Known Issues

### Using Tables with Self-Referencing Foreign Keys

When selecting data from a table with a self-referencing foreign key the query may fail with an error similar to

```
java.sql.SQLException: identifier <table>.<column> is ambiguous.
```

This is a known issue in Metabase. See [this ticket](https://github.com/exasol/metabase-driver/issues/12) for details.

### Timestamp and Timezone

Queries involving `TIMESTAMP WITH TIMEZONE` columns my return wrong results depending on the timezone set for the session. See [this ticket](https://github.com/exasol/metabase-driver/issues/9) for details.

### Scanning field values logs an exception for `GEOMETRY` columns

When Metabase scans field values of a table with a GEOMETRY column (e.g. when you click the "Re-scan field values now" button on the Database page) it logs the following exception:

```
2022-01-17 09:01:18,009 ERROR models.field-values :: Error fetching field values
clojure.lang.ExceptionInfo: Error executing query {:sql "-- Metabase\nSELECT \"META\".\"DATA_TYPES\".\"GEO\" AS \"GEO\" FROM \"META\".\"DATA_TYPES\" GROUP BY \"META\".\"DATA_TYPES\".\"GEO\" ORDER BY \"META\".\"DATA_TYPES\".\"GEO\" ASC LIMIT 5000", :params nil, :type :invalid-query}
...
Caused by: java.sql.SQLException: Feature not supported: GEOMETRY type in GROUP BY (Session: 1722185677957169152)
...
```

There seem to be no consequences of this error, everything seems to work fine.

See issue [#20](https://github.com/exasol/metabase-driver/issues/20) for details and a workaround for avoiding this error message.

## Troubleshooting

### Exasol Driver Not Available

If Database Type "Exasol" is not availabe in the "Add Database" dialog and Metabase logs the following message at startup, the Exasol JDBC driver is not available.

```
INFO plugins.dependencies :: Metabase cannot initialize plugin Metabase Exasol Driver due to required dependencies. Metabase requires the Exasol JDBC driver in order to connect to Exasol databases, but we can't ship it as part of the driver due to licensing restrictions. See https://github.com/exasol/metabase-driver for more details.
````

Please download the latest Exasol JDBC driver from the [Exasol download page](https://www.exasol.com/portal/display/DOWNLOAD/) and copy `exajdbc.jar` to `$METABASE_DIR/plugins`.

# User Guide

To use the Exasol driver for Metabase please first follow the [Metabase installation guide](https://www.metabase.com/docs/latest/operations-guide/installing-metabase.html) to download and install Metabase.

We assume that you installed Metabase at `$METABASE_DIR`.

## Installing the Driver

1. Download and install the latest Exasol JDBC driver for your operating system from the [Exasol download page](https://www.exasol.com/portal/display/DOWNLOAD/).
2. Copy the Exasol JDBC driver `exajdbc.jar` to `$METABASE_DIR/plugins`.
3. Download the latest driver from the [GitHub release page](https://github.com/exasol/metabase-driver/releases).
4. Copy the driver `exasol.metabase-driver.jar` to `$METABASE_DIR/plugins`.
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

## Troubleshooting

### Metabase Startup fails with `ClassNotFoundException: com.exasol.jdbc.EXADriver`

```
Job DEFAULT.metabase.task.sync-and-analyze.job threw an unhandled Exception: 
java.lang.ClassNotFoundException: com.exasol.jdbc.EXADriver
```

Please download the latest Exasol JDBC driver from the [Exasol download page](https://www.exasol.com/portal/display/DOWNLOAD/) and copy `exajdbc.jar` to `$METABASE_DIR/plugins`.

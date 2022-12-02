# metabase-driver 1.0.4, released 2022-12-??

Code name: Remove Exasol Maven Repository

## Summary

In this release we adapted the driver to Metabase 0.44.6.

Integration tests download the Exasol JDBC driver from the deprecated Exasol Maven repository. Instead the JDBC driver is now available on Maven Central. We updated the build scripts to use the new location.

## Refactoring

* #57: Updated tests to download the Exasol JDBC driver from Maven Central

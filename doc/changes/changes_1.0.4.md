# metabase-driver 1.0.4, released 2023-01-31

Code name: Upgrade to Metabase 0.45.2.1

## Summary

In this release we adapted the driver to Metabase 0.45.2.1. New integration tests added to Metabase revealed some issues with date and timestamp calculations in the Exasol driver that are fixed in the new release.

Integration tests download the Exasol JDBC driver from the deprecated Exasol Maven repository. Instead the JDBC driver is now available on Maven Central. We updated the build scripts to use the new location.

## Refactoring

* #57: Updated tests to download the Exasol JDBC driver from Maven Central
* #61: Upgraded to Metabase 0.45.2.1

# metabase-driver 1.0.4, released 2022-12-??

Code name: Remove Exasol Maven Repository

## Summary

Integration tests download the Exasol JDBC driver from the deprecated Exasol Maven repository. Instead the JDBC driver is now available on Maven Central.

## Refactoring

* #57: Updated tests to download the Exasol JDBC driver from Maven Central

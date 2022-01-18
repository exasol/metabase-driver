# metabase-driver 0.2.0, released 2022-01-18

Code name: Exasol specific data types and fix for `TIMESTAMP` columns

## Summary

This release fixes two issues when reading `TIMESTAMP` columns:

* Reading null values caused a `NullPointerException` in the driver.
* Timestamp values are now read in UTC timezone instead of the local time zone.

The release also adds support for Exasol specific data types `INTERVAL`, `GEOMETRY` and `HASHTYPE`. As Metabase does not support these types, they are returned as strings of Metabase type `:type/*`. There is an issue with scanning values of `GEOMETRY` columns, see [issue #20](https://github.com/exasol/metabase-driver/issues/20) and [user guide](../user_guide/user_guide.md#scanning-field-values-logs-an-exception-for-geometry-columns) for details.

## Features

* #16: Added a log message containing driver version and JDBC driver version when Metabase loads the Exasol driver
* #6: Added support for Exasol specific data types `INTERVAL`, `GEOMETRY` and `HASHTYPE`

## Bugfixes

* #17: Fixed reading `TIMESTAMP` columns

## Tests

* #3: Added integration tests for filter, joins and sub-selects
* #4: Added integration tests for scalar functions
* #5: Added integration tests for aggregate functions

## Dependency Updates

* #14: Upgraded Metabase from 0.41.5 to 0.41.6

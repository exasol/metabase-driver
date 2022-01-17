# metabase-driver 0.2.0, released 2022-01-17

Code name: Fixed issues reading `TIMESTAMP` columns and added support for Exasol specific data types.

## Summary

This release fixes two issues when reading `TIMESTAMP` columns:

* Reading null values caused a `NullPointerException` in the driver.
* Timestamp values are now read in UTC timezone instead of the local time zone.

The release also adds support for Exasol specific data types `INTERVAL`, `GEOMETRY` and `HASHTYPE`. As Metabase does not support these types, they are returned as strings of Metabase type `:type/*`. There is an issue with scanning values of `GEOMETRY` columns, see [issue #20](https://github.com/exasol/metabase-driver/issues/20) for details.

## Bugfixes

* #17: Fixed reading `TIMESTAMP` columns
* #6: Added support for Exasol specific data types `INTERVAL`, `GEOMETRY` and `HASHTYPE`

## Dependency Updates

(none)

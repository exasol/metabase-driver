# metabase-driver 0.1.1, released 2022-01-13

Code name: Fixed issues reading `TIMESTAMP` columns.

## Summary

This release fixes two issues when reading `TIMESTAMP` columns:

* Reading null values caused a `NullPointerException` in the driver.
* Timestamp values are now read in UTC timezone instead of the local time zone.

## Bugfixes

* #17: Fixed reading `TIMESTAMP` columns

## Dependency Updates

(none)

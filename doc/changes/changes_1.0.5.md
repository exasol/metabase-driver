# metabase-driver 1.0.5, released 2023-03-30

Code name: Document known issue with `NLS_FIRST_DAY_OF_WEEK`

## Summary

In this release we documented a known issue with non-default configuration settings for `NLS_FIRST_DAY_OF_WEEK` and adapted the driver to Metabase v0.46.0:
* Migrated to [Honey SQL 2](https://www.metabase.com/docs/latest/developers-guide/driver-changelog.html#honey-sql-2)
* Implement `sql.qp/date` for `:second-of-minute`, allowing to extract the seconds from a timestamp

## Documentation

* #63: Documented known issue with hard coded first day of the week

## Features

* #65: Adapted to Metabase v0.46.0

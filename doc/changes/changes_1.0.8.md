# metabase-driver 1.0.8, released 2025-08-01

Code name: Upgrade to Metabase v0.50.36

## Summary

This release adapts the driver to Metabase v0.50.36.

The driver now supports Metabase feature `:describe-fks`. This allows Metabase to retrieve metadata about Foreign Key Constraints from Exasol.

Also the driver now replaces the deprecated method `sql-jdbc.execute/connection-with-timezone` with `sql-jdbc.execute/do-with-connection-with-options`. This will allow using the driver with later Metabase versions and avoids the following warning in the Metabase log:

```
WARN sql-jdbc.execute :: connection-with-timezone is deprecated in Metabase 0.47.0. Implement do-with-connection-with-options instead.
```

## Features

* #78: Upgraded to Metabase v0.50.36

## Bugfixes

* #77: Replace deprecated method `sql-jdbc.execute/connection-with-timezone`

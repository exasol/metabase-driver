# metabase-driver 1.0.1, released 2022-05-30

Code name: Adapt to Metabase 0.43.1

## Summary

In this release we adapted the driver to Metabase version 0.43.1 and migrated the build system to deps.edn. This release was only tested with Metabase 0.43.1. If you use an older version of Metabase please use metabase-driver 1.0.0.

## Features

* #38: Adapted driver to Metabase version 0.43.0
* #35: Migrated build system to deps.edn and adapted driver to Metabase version 0.43.1
* #41: Removed Exasol JDBC driver from built driver jar

## Bugfixes

* #40: Implemented reading driver version from metabase-plugin.yaml
* #43: Implemented reading JDBC driver version via reflection

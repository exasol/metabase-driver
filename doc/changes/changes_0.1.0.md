# metabase-driver 0.1.0, released 2022-01-11

Code name: Initial release

This is the initial release of the Exasol Metabase Driver. Basic features are working but there are some known issues that will be fixed in later versions:

* Queries of tables with self-referencing foreign keys may not work, see [#12](https://github.com/exasol/metabase-driver/issues/12).
* Queries using timestamps may return wrong results, see [#9](https://github.com/exasol/metabase-driver/issues/9).
* Proprietary Exasol data types like `INTERVAL` and `GEOMETRY` are not supported yet, see [#6](https://github.com/exasol/metabase-driver/issues/6).
* Not all scalar functions are supported yet, see [#4](https://github.com/exasol/metabase-driver/issues/4).
* Aggregate functions are not supported yet, see [#5](https://github.com/exasol/metabase-driver/issues/5).

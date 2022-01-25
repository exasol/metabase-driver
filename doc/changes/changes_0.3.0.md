# metabase-driver 0.3.0, released 2022-01-??

Code name: Enable SSH tunnel and improve error messages

## Summary

In this release we enable connecting to Exasol through an SSH tunnel (see the [user guide](../user_guide/user_guide.md#connecting-through-an–ssh–tunnel) for details) and we provide better error messages in case of connection errors.

We also explain how to use data types `TIMESTAMP` and `TIMESTAMP WITH LOCAL TIME ZONE` with timezones in the [user guide](../user_guide/user_guide.md#timestamps-and-time-zone).

## Features

* #25: Enabled SSH tunnel for connecting to Exasol
* #24: Provided helpful error messages in case of connection errors

## Documentation

* Improved the installation instructions in the [user guide](../user_guide/user_guide.md#installing-the-driver)
* #9: Explained how to use data types `TIMESTAMP` and `TIMESTAMP WITH LOCAL TIME ZONE` with timezones

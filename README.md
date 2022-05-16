# Exasol Metabase Driver

An Exasol driver for [Metabase](https://www.metabase.com).

[![CI Build](https://github.com/exasol/metabase-driver/actions/workflows/ci-build.yml/badge.svg)](https://github.com/exasol/metabase-driver/actions/workflows/ci-build.yml)

# Overview

[Metabase](https://www.metabase.com/) is a business intelligence tool. You can use Metabase to visualize data in [Exasol](https://www.exasol.com).

The Exasol Driver for Metabase is an adapter, that allows you to query data from an Exasol database using Metabase.

## Features

* Connect Metabase to an Exasol database
* Connections are encrypted via TLS by default
  * Specify a certificate fingerprint in case your database uses a self-signed TLS certificate
* Allows tunneling the connection to Exasol through an [SSH tunnel](./doc/user_guide/user_guide.md#connecting-through-an-ssh-tunnel)
* Provides helpful error messages in case of connection errors

# Table of Contents

## Information for Users

* [User Guide](doc/user_guide/user_guide.md)
* [Changelog](doc/changes/changelog.md)

## Information for Developers

* [Developer Guide](doc/developer_guide/developer_guide.md)

# Dependencies

## Runtime Dependencies

To use the Exasol Driver, you need Metabase Version 0.43.0 or later.

Follow the [Metabase installation guide](https://www.metabase.com/docs/latest/operations-guide/installing-metabase.html) to download and install Metabase.

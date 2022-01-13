# Developer Guide

## Required Build Tools

For building and testing the driver you will need
* [Clojure](https://clojure.org/) version 1.10.3 or later
* [Leiningen](https://leiningen.org/) version 2.9.8 or later

To build Metabase itself you will need
* [Node.js](https://nodejs.org/en/)
* [Yarn](https://yarnpkg.com/)

On Ubuntu you can install the dependencies by running

```shell
sudo apt install nodejs yarnpkg
```

Fedora:

```shell
yum install perl-Digest-SHA nodejs yarnpkg
```

On macOS you additionally need `gnu-sed`:

```shell
brew install nodejs yarnpkg clojure leiningen gnu-sed
# Then add gnubin to your PATH:
# export PATH="/usr/local/opt/gnu-sed/libexec/gnubin:$PATH"
```

Run the following commands to check the current versions:

```shell
clojure -M --eval "(clojure-version)"
clojure --version
lein --version
```

## Setup Development Environment

1. Checkout Metabase at `$HOME/git/metabase` (= `$METABASE_DIR`) and build it:

    ```bash
    export METABASE_VERSION=0.41.5
    cd $HOME/git
    git clone https://github.com/metabase/metabase.git
    cd metabase
    git fetch --all --tags
    git checkout "tags/v${METABASE_VERSION}" -b "v${METABASE_VERSION}-branch"
    # Build (this will take ~15min)
    ./bin/build
    # Run
    clojure -M:run
    ```

2. Download the Exasol JDBC driver from the [Download Portal](https://www.exasol.com/portal/display/DOWNLOAD/) and install it:

    ```bash
    cp exajdbc.jar "$METABASE_DIR/plugins"
    ```

3. Checkout the Exasol Metabase driver at `$HOME/git/metabase` (= `$METABASE_EXASOL_DRIVER`)

    ```bash
    git clone https://github.com/exasol/metabase-driver.git
    cd metabase-driver
    ```

## Run Driver Unit Tests

```bash
./scripts/install-metabase-jar.sh
lein test
```

## Start Metabase With the Exasol Driver

To start Metabase with the Exasol driver from source:

```bash
export METABASE_DIR="$HOME/git/metabase"
export METABASE_EXASOL_DRIVER="$HOME/git/metabase-driver"
cd $METABASE_DIR
clojure -Sdeps '{:deps {metabase/exasol-driver {:local/root "'"$METABASE_EXASOL_DRIVER"'"}}}' -J-Dmb.dev.additional.driver.manifest.paths=$METABASE_EXASOL_DRIVER/resources/metabase-plugin.yaml -M:run
```

<!-- markdown-link-check-disable-next-line -->
After startup is complete (log message: `Metabase Initialization COMPLETE`) you can access Metabase at [http://localhost:3000/](http://localhost:3000/).

## Build and Install Exasol Driver

To start Metabase with the built Exasol driver:

```bash
export METABASE_DIR="$HOME/git/metabase"
export METABASE_EXASOL_DRIVER="$HOME/git/metabase-driver"
cd $METABASE_EXASOL_DRIVER

# Install dependencies
./scripts/install-metabase-jar.sh

# Build driver
DEBUG=1 lein uberjar

# Install driver
cp -v "$METABASE_EXASOL_DRIVER/target/uberjar/exasol.metabase-driver.jar" "$METABASE_DIR/plugins/"

# Run Metabase
cd $METABASE_DIR
clojure -M:run
```

## Running the Integration Tests

You need to have metabase checked out next to this repository.

```shell
EXASOL_HOST=<hostname> EXASOL_PORT=8563 EXASOL_USER=sys EXASOL_PASSWORD=exasol ./scripts/run-integration-tests.sh
```

This script builds and installs the driver before running the integration tests. The driver must be installed to `$METABASE_DIR/plugins/` for running the integration tests.

To run only a single tests or only tests in a namespace add arguments:

```shell
./scripts/run-integration-tests.sh :only name.space/single-test
./scripts/run-integration-tests.sh :only name.space
```

### Using the REPL

```shell
export MB_EXASOL_TEST_HOST=<hostname>
export MB_EXASOL_TEST_PORT=8563
export MB_EXASOL_TEST_USER=sys
export MB_EXASOL_TEST_PASSWORD=exasol
export MB_EXASOL_TEST_CERTIFICATE_FINGERPRINT=<fingerprint>
export DRIVER=exasol

# Command line REPL:
clojure -A:dev:drivers:drivers-dev:test

# Network REPL:
clojure -M:dev:drivers:drivers-dev:test:nrepl # -> Connect with editor
```

In the REPL you can use the following commands:

```clojure
(require 'dev) ; this will take some seconds
(dev/start!)

; Run SQL query:
(dev/query-jdbc-db :exasol "SELECT 1")
(dev/query-jdbc-db [:exasol 'test-data] "SELECT * from CAM_179.\"test_data_users\"")
```

### Helfpul Files in Metabase

* [deps.edn](https://github.com/metabase/metabase/blob/master/deps.edn): Dependencies and comments with useful commands for building and testing
* [`dev/src/dev.clj`](https://github.com/metabase/metabase/blob/master/dev/src/dev.clj): Functions for developing with the REPL
* [`test/metabase/test_runner.clj`](https://github.com/metabase/metabase/blob/master/test/metabase/test_runner.clj): Functions for running tests in REPL
* [`test/metabase/test/data/dataset_definitions.clj`](https://github.com/metabase/metabase/blob/master/test/metabase/test/data/dataset_definitions.clj): Available test data sets
  * [`test/metabase/test/data/dataset_definitions/*.edn`](https://github.com/metabase/metabase/tree/master/test/metabase/test/data/dataset_definitions): Definitions of test data sets

### Configure Logging

To increase the log level for integration tests, edit file `$METABASE_DIR/test_config/log4j2-test.xml`.

### Unsupported Datasets / Excluded Tests

Exasol does not support the `TIME` data type. That is why we can't load the following datasets from the Metabase integration tests:

* `test-data-with-time`
* `attempted-murders`

We exclude `TIME` related and other broken tests by patching the metabase sources with patch `scripts/exclude_tests.diff`.

Script `run-integration-tests.sh` automatically applies this patch when file `$METABASE_DIR/target/patch_excluded_test_applied` does not exist.

When the patch file has changed or you updated to a new Metabase release, do the following and re-run the integration tests with `run-integration-tests.sh`.

```shell
cd $METABASE_DIR
git reset --hard
rm -vf target/patch_excluded_test_applied
```

# Troubleshooting

## `FileNotFoundException: Could not locate metabase/test/data/exasol__init.class, metabase/test/data/exasol.clj or metabase/test/data/exasol.cljc on classpath.`

Verify that `$METABASE_DIR/modules/drivers/exasol` is a symlink to the `metabase-driver` directory.

## Failing Integration Tests

### Different Decimal Point

Tests fail on macOS because they expect numbers with a `.` as decimal point (e.g. `1000.0 µs`) but get a `,` (e.g. `1000,0 µs`), e.g.:

```
expected: (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Timed out after 1000\.0 µs\."
           (if (instance? Throwable result) (throw result) result))
  actual: #<clojure.lang.ExceptionInfo@141971c7 clojure.lang.ExceptionInfo: Timed out after 1000,0 µs. {:status :timed-out, :type :timed-out}>
```

Solution: run tests under Linux with English locale.

### Time Dependent Tests

Some Metabase integration tests depend on the current timestamp and will fail when the year changes. See [issue #14](https://github.com/exasol/metabase-driver/issues/14) for details.

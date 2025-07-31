# Developer Guide

## Required Build Tools

For building and testing the driver you will need
* [Clojure](https://clojure.org/) version 1.10.3 or later

To build Metabase itself you will need
* [Node.js](https://nodejs.org/en/)
* [Yarn](https://yarnpkg.com/)

On Ubuntu you can install the dependencies by running

```sh
sudo apt install nodejs yarnpkg
```

Fedora:

```sh
yum install perl-Digest-SHA nodejs yarnpkg
```

On macOS you additionally need `gnu-sed`:

```sh
brew install nodejs yarnpkg clojure gnu-sed
# Then add gnubin to your PATH:
# export PATH="/usr/local/opt/gnu-sed/libexec/gnubin:$PATH"
```

Run the following commands to check the current versions:

```sh
clojure -M --eval "(clojure-version)"
clojure --version
```

### Verify Correct Java Version

Metabase uses Java 11 by default. Check which Java version is used by Clojure:

```sh
clojure -M --eval '(println "Java" (System/getProperty "java.version") "Clojure" (clojure-version))'
# Only major java version:
clojure -M --eval '(println (. (Runtime/version) major))'
```

If this reports another Java version, update `JAVA_HOME` and check again.

## Setup Development Environment

1. Checkout Metabase at `$HOME/git/metabase` (= `$METABASE_DIR`) and build it:

    ```sh
    export METABASE_DIR=$HOME/git/metabase
    export METABASE_VERSION=v0.50.36
    git clone https://github.com/metabase/metabase.git $METABASE_DIR
    git -C $METABASE_DIR fetch --all --tags
    git -C $METABASE_DIR reset --hard
    rm -vf $METABASE_DIR/target/patch_excluded_test_applied
    git -C $METABASE_DIR checkout "tags/${METABASE_VERSION}" -b "${METABASE_VERSION}-branch"
    # Build (this will take ~15min)
    $METABASE_DIR/bin/build.sh
    ```

2. Download the Exasol JDBC driver from the [Download Portal](https://downloads.exasol.com/clients-and-drivers) or get it from `~/.m2/repository/com/exasol/exasol-jdbc/$v/exasol-jdbc-$v.jar` and install it:

    ```sh
    cp exajdbc.jar "$METABASE_DIR/plugins"
    ```

3. Checkout the Exasol Metabase driver at `$HOME/git/metabase` (= `$METABASE_EXASOL_DRIVER`)

    ```sh
    git clone https://github.com/exasol/metabase-driver.git
    cd metabase-driver
    ```

## Upgrading Metabase

To ensure compatibility we need to regularly update to the latest Metabase version. You can find the latest Metabase version on the [GitHub release page](https://github.com/metabase/metabase/releases/).

Metabase publishes two variants:
* OSS: version numbers v0.x.y
* Enterprise: version numbers v1.x.y

We only use the OSS variant with version numbers v0.x.y.

To upgrade Metabase follow these steps:
1. Check the Metabase [driver changelog](https://www.metabase.com/docs/latest/developers-guide/driver-changelog.html) for breaking changes
2. Replace the previous version in all files with the new version by searching for `v0.x.y`
3. [Run unit tests](#running-driver-unit-tests)
4. [Run integration tests](#running-integration-tests)

The following things can go wrong:
* The patch excluding inapproriate tests cannot be applied. See [excluded tests](#excluded-tests) for details.
* Tests fail or abort
  * If possible fix the problem in the driver
  * If failures are related to Exasol specifics (e.g. missing `TIME` data type etc.) modify the test in Metabase and update the patch file, see [excluded tests](#excluded-tests) for details.
  * If failures are unrelated to Exasol or the driver, you might delete it and update the patch file, see [excluded tests](#excluded-tests) for details.

## Running Driver Unit Tests

```sh
./scripts/run-unit-tests.sh
```

## Start Metabase With the Exasol Driver

To start Metabase with the Exasol driver from source:

```sh
export METABASE_DIR="$HOME/git/metabase"
export METABASE_EXASOL_DRIVER="$HOME/git/metabase-driver"
cd $METABASE_DIR
clojure -Sdeps '{:deps {metabase/exasol-driver {:local/root "'"$METABASE_EXASOL_DRIVER"'"}}}' -J-Dmb.dev.additional.driver.manifest.paths=$METABASE_EXASOL_DRIVER/resources/metabase-plugin.yaml -M:run
```

<!-- markdown-link-check-disable-next-line -->
After startup is complete (log message: `Metabase Initialization COMPLETE`) you can access Metabase at [http://localhost:3000/](http://localhost:3000/).

## Build and Install Exasol Driver

To start Metabase with the built Exasol driver:

```sh
export METABASE_DIR="$HOME/git/metabase"
export METABASE_EXASOL_DRIVER="$HOME/git/metabase-driver"
cd $METABASE_EXASOL_DRIVER

# Build driver
./scripts/build.sh

# Install driver
cp -v "$METABASE_EXASOL_DRIVER/target/exasol.metabase-driver.jar" "$METABASE_DIR/plugins/"

# Run Metabase
cd $METABASE_DIR
clojure -M:run
```

## Running Integration Tests

### Preconditions

* You need to have metabase checked out next to this repository.

* Start Exasol docker container:
   ```sh
   docker run --publish 8563:8563 --publish 2580:2580 --publish 443:443 --detach --privileged --stop-timeout 120 exasol/docker-db:8.34.0
   ```

* Build frontend code required for running integration tests:
   ```sh
   cd $METABASE_DIR && yarn build-static-viz
   ```

### Running

Start integration tests:

```sh
EXASOL_HOST=<hostname> EXASOL_PORT=8563 EXASOL_USER=sys EXASOL_PASSWORD=exasol ./scripts/run-integration-tests.sh
```

This script builds and installs the driver before running the integration tests. The driver must be installed to `$METABASE_DIR/plugins/` for running the integration tests.

To run only a single tests or only tests in a namespace add arguments:

```sh
# Run single test
./scripts/run-integration-tests.sh :only name.space/single-test
# Run all tests in a name space
./scripts/run-integration-tests.sh :only name.space
# Run tests from multiple namespaces
./scripts/run-integration-tests.sh :only "[name.space1 name.space2]"
```

### Evaluating Test Results

When tests fail locally or in CI, search the log output for `ERROR in` or `FAIL in` to find test errors/failures.

### Using the REPL

```sh
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

### Helpful Files in Metabase

* [deps.edn](https://github.com/metabase/metabase/blob/master/deps.edn): Dependencies and comments with useful commands for building and testing
* [`dev/src/dev.clj`](https://github.com/metabase/metabase/blob/master/dev/src/dev.clj): Functions for developing with the REPL
* [`test/metabase/test_runner.clj`](https://github.com/metabase/metabase/blob/master/test/metabase/test_runner.clj): Functions for running tests in REPL
* [`test/metabase/test/data/dataset_definitions.clj`](https://github.com/metabase/metabase/blob/master/test/metabase/test/data/dataset_definitions.clj): Available test data sets
  * [`test/metabase/test/data/dataset_definitions/*.edn`](https://github.com/metabase/metabase/tree/master/test/metabase/test/data/dataset_definitions): Definitions of test data sets

### Configure Logging

To increase the log level for integration tests, edit file `$METABASE_DIR/test_config/log4j2-test.xml`.

### Excluded Tests

Exasol does not support the `TIME` data type. That is why we can't load the following datasets from the Metabase integration tests:

* `test-data-with-time`
* `attempted-murders`

We exclude `TIME` related and other broken tests by patching the metabase sources with patch `scripts/exclude_tests.diff`.

Script `run-integration-tests.sh` automatically applies this patch when file `$METABASE_DIR/target/patch_excluded_test_applied` does not exist.

When the patch file has changed or you updated to a new Metabase release, do the following and re-run the integration tests with `run-integration-tests.sh`.

```sh
export METABASE_DIR="$HOME/git/metabase"
git -C $METABASE_DIR reset --hard && rm -fv $METABASE_DIR/target/patch_excluded_test_applied
```

#### Applying Patch Fails

If applying the patch fails after upgrading to a new Metabase version, follow these steps:

1. Run `export METABASE_DIR="$HOME/git/metabase" && export METABASE_EXASOL_DRIVER="$HOME/git/metabase-driver"`
2. Run `git -C $METABASE_DIR reset --hard && rm -vf $METABASE_DIR/target/patch_excluded_test_applied`
3. Remove the failed part from `exclude_tests.diff`
4. Run integration tests `run-integration-tests.sh`. This will apply the patch.
5. Modify Metabase tests to adapt them to Exasol
6. Update patch by running `git -C $METABASE_DIR diff > $METABASE_EXASOL_DRIVER/scripts/exclude_tests.diff`

## Linting

```sh
clojure -M:clj-kondo --lint src test --debug
```

# Building a Release

Releases are built using [release-droid](https://github.com/exasol/release-droid).

# Troubleshooting

## Failing Integration Tests

### Error `Javascript resource not found`

Tests fail with the following error

```
Javascript resource not found: frontend_client/app/dist/lib-static-viz.bundle.js
```

then run

```sh
cd $METABASE_DIR && yarn build-static-viz
```

### Different Decimal Point

Tests fail on macOS because they expect numbers with a `.` as decimal point (e.g. `1000.0 µs`) but get a `,` (e.g. `1000,0 µs`), e.g.:

```
expected: (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Timed out after 1000\.0 µs\."
           (if (instance? Throwable result) (throw result) result))
  actual: #<clojure.lang.ExceptionInfo@141971c7 clojure.lang.ExceptionInfo: Timed out after 1000,0 µs. {:status :timed-out, :type :timed-out}>
```

Solution: run tests under Linux with English locale or pass arguments `-J-Duser.country=US -J-Duser.language=en` to clojure when starting the tests (default in `run-integration-tests.sh`).

### Time Dependent Tests

Some Metabase integration tests depend on the current timestamp and will fail when the year changes. See [issue #14](https://github.com/exasol/metabase-driver/issues/14) for details.

### Inconsistent Test Results in CI and Locally

If a tests fails in CI and succeeds locally or vice versa, ensure you have a clean working copy of `$METABASE_DIR`:

```sh
export METABASE_DIR="$HOME/git/metabase"
git -C $METABASE_DIR clean --no-quiet --force -d -x
# Rebuild UI, required by integration tests.
cd $METABASE_DIR && yarn build-static-viz
```

This will delete all ignored files.

## Timeout During Build of Metabase

Metabase build using script `./bin/build.sh` fails with a timeout in `yarn`:

```
...
        Step "$ \"yarn\"" failed with error "Timed out after 900000 ms."
What would you like to do?
[T]ry this step again
[F]ail -- pass the failure of this step to the parent step (which can be retried)
[S]kip this step
[R]EPL -- open a REPL so you can debug things
[Q]uit the build script (or return to the top level if running from the REPL) [T/F/S/R/Q] t
```

Type `t` to try again. It should work at a second try.

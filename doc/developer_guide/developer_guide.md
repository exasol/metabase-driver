# Developer Guide

## Required Build Tools

For building and testing the driver you will need
* [Clojure](https://clojure.org/)
* [Leiningen](https://leiningen.org/)

To build Metabase itself you will need
* [Node.js](https://nodejs.org/en/)
* [Yarn](https://yarnpkg.com/)

On Unbuntu you can install the dependencies by running

```shell
sudo apt install nodejs yarnpkg clojure leiningen
```

You will need Clojure 1.10.3 or later. Run the following command to check your current version:

```shell
clojure --eval "(clojure-version)"
```

## Setup Development Environment

1. Checkout Metabase at `$HOME/git/metabase` (= `$METABASE_DIR`) and build it:

    ```bash
    cd $HOME/git
    git clone https://github.com/metabase/metabase.git
    cd metabase
    # Build
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
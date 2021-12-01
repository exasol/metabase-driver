# Developer Guide

## Required Build Tools

* node.js, yarn
* clojure, Leiningen

You will need Clojure 1.10.3.905 or later. Run the following command to check your current version:

```shell
% clojure --version
Clojure CLI version 1.10.3.1020
```

## Setup Development Environment

1. Checkout Metabase at `$HOME/git/metabase` (= `$METABASE_DIR`)

    ```bash
    cd $HOME/git
    git clone https://github.com/metabase/metabase.git
    cd metabase
    yarn build
    # Build
    ./bin/build
    # Run
    clojure -M:run
    ```

2. Download the Exasol JDBC driver from the [Download Portal](https://www.exasol.com/portal/display/DOWNLOAD/) and install it

    ```bash
    cp exajdbc.jar "$METABASE_DIR/plugins"
    ```

3. Checkout the Exasol Metabase driver at `$HOME/git/metabase` (= `$METABASE_EXASOL_DRIVER`)

    ```bash
    git clone https://github.com/exasol/metabase-driver.git
    cd metabase-driver
    ```

## Start Metabase With the Exasol Driver

To start Metabase with the Exasol driver from source:

```bash
export METABASE_DIR="$HOME/git/metabase"
export METABASE_EXASOL_DRIVER="$HOME/git/metabase-driver"
cd $METABASE_DIR
clojure -Sdeps '{:deps {metabase/exasol-driver {:local/root "'"$METABASE_EXASOL_DRIVER"'"}}}' -J-Dmb.dev.additional.driver.manifest.paths=$METABASE_EXASOL_DRIVER/resources/metabase-plugin.yaml -M:run
```

After startup is complete (log message: `Metabase Initialization COMPLETE`) you can access it at [http://localhost:3000/](http://localhost:3000/).

## Build and Install Exasol Driver

```bash
# Download Metabase jar and install to local maven repo
./scripts/install-metabase-jar.sh

# Build driver jar
DEBUG=1 lein uberjar

export METABASE_DIR="$HOME/git/metabase"

# Install driver
cp -v target/uberjar/exasol.metabase-driver.jar $METABASE_DIR/plugins/

# Run Metabase
cd $METABASE_DIR
clojure -M:run
```
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
export METABASE_DIR="$HOME/git/metabase"
export METABASE_PLUGINS_DIR="$METABASE_DIR/plugins/"
export METABASE_EXASOL_DRIVER="$HOME/git/metabase-driver"
export METABASE_UBERJAR="$METABASE_DIR/target/uberjar/metabase.jar"
export DRIVER_UBERJAR="$METABASE_EXASOL_DRIVER/target/uberjar/exasol.metabase-driver.jar"
export METABASE_VERSION="0.42.0-SNAPSHOT"
export LOCAL_MAVEN_REPO="$METABASE_EXASOL_DRIVER/maven_repository"

cd $METABASE_DIR
./bin/build
ls -lha METABASE_UBERJAR

mvn deploy:deploy-file -Dfile=$METABASE_UBERJAR -DartifactId=metabase -Dversion=$METABASE_VERSION -DgroupId=metabase -Dpackaging=jar -Durl=file:$LOCAL_MAVEN_REPO

cd $METABASE_EXASOL_DRIVER
DEBUG=1 LEIN_SNAPSHOTS_IN_RELEASE=true lein uberjar
ls -lah $DRIVER_UBERJAR
cp -v $DRIVER_UBERJAR $METABASE_PLUGINS_DIR
cd METABASE_DIR
clojure -M:run
```
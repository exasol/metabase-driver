#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

jdbc_driver_version=7.1.3
exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
metabase_dir=$(cd "$exasol_driver_dir/../metabase"; pwd)

if [ ! -d "$metabase_dir" ]; then
    echo "Metabase does not exist at $metabase_dir. Clone repo https://github.com/metabase/metabase"
    exit 1
fi

if [[ -z "${EXASOL_HOST+x}" || -z "${EXASOL_PORT+x}" ]]; then
    echo "Environment variables 'EXASOL_HOST' and 'EXASOL_PORT' must be defined:"
    echo "EXASOL_HOST=localhost EXASOL_PORT=8563 $0"
    exit 1
fi

symlink_target="$metabase_dir/modules/drivers/exasol"
if [ ! -d "$symlink_target" ]; then
    echo "Creating symlink to $symlink_target -> $exasol_driver_dir"
    ln -s "$exasol_driver_dir" "$symlink_target"
else
    echo "Symlink already exists at $symlink_target"
fi

metabase_deps="$metabase_dir/deps.edn"
if ! grep --quiet "modules/drivers/exasol/test" "$metabase_deps"; then
    echo "Adding dependency to $metabase_deps"
    sed --in-place 's/"modules\/drivers\/druid\/test"/"modules\/drivers\/druid\/test" "modules\/drivers\/exasol\/test"/g' "$metabase_deps"
else
    echo "Dependency already added to $metabase_deps"
fi

metabase_plugin_dir="$metabase_dir/plugins/"
if [ ! -d "$metabase_plugin_dir" ]; then
    echo "Creating $metabase_plugin_dir"
    mkdir -p "$metabase_plugin_dir"
fi

if [ ! -f "$metabase_plugin_dir/exasol-jdbc.jar" ]; then
    echo "Installing Exasol JDBC driver..."
    mvn org.apache.maven.plugins:maven-dependency-plugin:3.2.0:get \
      -DremoteRepositories=https://maven.exasol.com/artifactory/exasol-releases \
      -Dartifact=com.exasol:exasol-jdbc:$jdbc_driver_version
    cp -v "$HOME/.m2/repository/com/exasol/exasol-jdbc/$jdbc_driver_version/exasol-jdbc-$jdbc_driver_version.jar" "$metabase_plugin_dir/exasol-jdbc.jar"
else
    echo "Exasol JDBC driver already exists in $metabase_plugin_dir"
fi

echo "Building exasol driver..."
cd "$exasol_driver_dir"
DEBUG=1 lein uberjar

driver_jar="$exasol_driver_dir/target/uberjar/exasol.metabase-driver.jar"

ls -lha "$driver_jar"
echo "Copy driver $driver_jar to $metabase_plugin_dir"
cp "$driver_jar" "$metabase_plugin_dir"

ls -lh "$metabase_plugin_dir"

cd "$metabase_dir"
echo "Starting integration tests in $metabase_dir..."
MB_EXASOL_TEST_HOST=$EXASOL_HOST \
  MB_EXASOL_TEST_PORT=$EXASOL_PORT \
  MB_EXASOL_TEST_USER=sys \
  MB_EXASOL_TEST_PASSWORD=exasol \
  MB_EXASOL_TEST_CERTIFICATE_FINGERPRINT=15F9CA9BC95E14F1F913FC449A26723841C118CFB644957866ABB73C1399A7FF \
  DRIVERS=exasol \
  clojure -X:dev:ci:drivers:drivers-dev:test

#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail


exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
metabase_dir="$exasol_driver_dir/../metabase"

if [ ! -d "$metabase_dir" ]; then
    echo "Metabase does not exist at $metabase_dir. Clone repo https://github.com/metabase/metabase"
    exit 1
fi

symlink_target="$metabase_dir/modules/drivers/exasol"
metabase_deps="$metabase_dir/deps.edn"

if [ ! -d "$symlink_target" ]; then
    echo "Creating symlink to $symlink_target -> $exasol_driver_dir"
    ln -s "$exasol_driver_dir" "$symlink_target"
else
    echo "Symlink already exists at $symlink_target"
fi

if ! grep --quiet "modules/drivers/exasol/test" "$metabase_deps"; then
    echo "Adding dependency to $metabase_deps"
   sed --in-place 's/"modules\/drivers\/druid\/test"/"modules\/drivers\/druid\/test" "modules\/drivers\/exasol\/test"/g' "$metabase_deps"
else
    echo "Dependency already added to $metabase_deps"
fi


#"$exasol_driver_dir/scripts/install-metabase-jar.sh"
echo "Building exasol driver..."
cd "$exasol_driver_dir"
DEBUG=1 lein uberjar

metabase_plugin_dir="$metabase_dir/plugins/"
driver_jar="$exasol_driver_dir/target/uberjar/exasol.metabase-driver.jar"
if [ ! -d "$metabase_plugin_dir" ]; then
    echo "Creating $metabase_plugin_dir"
    mkdir -p "$metabase_plugin_dir"
fi
ls -lha "$driver_jar"
echo "Copy driver $driver_jar to $metabase_plugin_dir"
cp "$driver_jar" "$metabase_plugin_dir"
ls -lah "$metabase_plugin_dir"

cd "$metabase_dir"
echo "Starting integration tests..."
MB_EXASOL_TEST_HOST=192.168.56.5 \
  MB_EXASOL_TEST_PORT=8563 \
  MB_EXASOL_TEST_USER=sys \
  MB_EXASOL_TEST_PASSWORD=exasol \
  MB_EXASOL_TEST_CERTIFICATE_FINGERPRINT=15F9CA9BC95E14F1F913FC449A26723841C118CFB644957866ABB73C1399A7FF \
  DRIVERS=exasol \
  clojure -X:dev:ci:drivers:drivers-dev:test

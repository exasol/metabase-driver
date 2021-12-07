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

cd "$metabase_dir"

MB_EXASOL_TEST_HOST=localhost \
  MB_EXASOL_TEST_PORT=8563 \
  MB_EXASOL_TEST_USER=sys \
  MB_EXASOL_TEST_PASSWORD=exasol \
  MB_EXASOL_TEST_CERTIFICATE_FINGERPRINT=ABC \
  DRIVERS=exasol \
  clojure -X:dev:ci:drivers:drivers-dev:test

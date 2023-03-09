#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"

source "$exasol_driver_dir/scripts/common.sh"

patch_metbase_build_scripts

log_info "Building exasol driver in $exasol_driver_dir using Metabase $metabase_dir..."
cd "$metabase_dir"
DRIVER_PATH=$exasol_driver_dir
clojure \
  -Sdeps "{:aliases {:exasol {:extra-deps {com.metabase/exasol-driver {:local/root \"$DRIVER_PATH\"}}}}}" \
  -X:build:exasol \
  build-drivers.build-driver/build-driver! \
  "{:driver :exasol, :project-dir \"$DRIVER_PATH\", :target-dir \"$DRIVER_PATH/target\"}"

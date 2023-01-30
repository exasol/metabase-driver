#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"

source "$exasol_driver_dir/scripts/common.sh"

patch_metbase_build_scripts

log_info "Building exasol driver in $exasol_driver_dir..."
cd "$exasol_driver_dir"
clojure -X:build :project-dir "\"$(pwd)\""

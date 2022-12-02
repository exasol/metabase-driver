#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
metabase_dir=$(cd "$exasol_driver_dir/../metabase"; pwd)

log_color() {
    local color="$1"
    local message="$2"
    local end_color='\033[0m'
    echo -e "${color}${message}${end_color}"
}

log_error() {
    RED='\033[0;31m'
    log_color "$RED" "$1"
}

log_info() {
    GREEN='\033[0;32m'
    log_color "$GREEN" "$1"
}

check_preconditions() {
    if [ ! -d "$metabase_dir" ]; then
        log_error "Metabase does not exist at $metabase_dir. Clone repo https://github.com/metabase/metabase"
        exit 1
    fi
}

###

check_preconditions

log_info "Starting unit tests in $metabase_dir..."
cd "$metabase_dir"

readonly dep_driver_dir="exasol/exasol-driver {:local/root \"$exasol_driver_dir\"}"
readonly dep_test_dir="exasol/exasol-tests {:local/root \"$exasol_driver_dir/test\"}"
readonly sdeps_option="{:deps { $dep_driver_dir $dep_test_dir } }"

clojure -J-Duser.country=US -J-Duser.language=en -J-Duser.timezone=UTC \
        -Sdeps "$sdeps_option" \
        -X:dev:ci:drivers:drivers-dev:test :only metabase.driver.exasol-unit-test

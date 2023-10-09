#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
readonly exasol_driver_dir
metabase_dir=$(cd "$exasol_driver_dir/../metabase"; pwd)
readonly metabase_dir


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

log_trace() {
    LIGHT_GREEN='\033[1;32m'
    log_color "$LIGHT_GREEN" "$1"
}


patch_excluded_tests() {
    local patch_applied="$metabase_dir/target/patch_excluded_test_applied"
    if [ ! -f "$patch_applied" ]; then
        local patch_file="$exasol_driver_dir/scripts/exclude_tests.diff"
        cd "$metabase_dir"
        log_info "Check if patch $patch_file can be applied..."
        if ! git apply --check --verbose "$patch_file" ; then
            log_error "Error applying patch $patch_file to $metabase_dir"
            log_error "Please revert your local changes"
            exit 1
        fi
        log_info "Applying patch $patch_file to exclude tests"
        git apply --apply --verbose "$patch_file"
        mkdir -p "$(dirname "$patch_applied")"
        touch "$patch_applied"
    else
        log_trace "Test exclusion patch already applied"
    fi
}

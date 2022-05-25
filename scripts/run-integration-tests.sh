#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
readonly exasol_driver_dir
metabase_dir=$(cd "$exasol_driver_dir/../metabase"; pwd)
readonly metabase_dir
readonly skip_build=${skip_build:-false}
readonly driver_jar="$exasol_driver_dir/target/exasol.metabase-driver.jar"

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

check_preconditions() {
    if [ ! -d "$metabase_dir" ]; then
        log_error "Metabase does not exist at $metabase_dir. Clone repo https://github.com/metabase/metabase"
        exit 1
    fi

    if [[ -z "${EXASOL_HOST+x}" || -z "${EXASOL_PORT+x}" || -z "${EXASOL_USER+x}" || -z "${EXASOL_PASSWORD+x}" ]] ; then
        log_error "Environment variables 'EXASOL_HOST', 'EXASOL_PORT', 'EXASOL_USER' and 'EXASOL_PASSWORD' must be defined:"
        log_error "EXASOL_HOST=localhost EXASOL_PORT=8563 EXASOL_USER=sys EXASOL_PASSWORD=exasol $0"
        exit 1
    fi
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
        log_trace "Patch already applied"
    fi
}

get_exasol_certificate_fingerprint() {
    local certificate
    local fingerprint
    local port=${alternative_tls_port:-$EXASOL_PORT}
    # Adding '|| true' is necessary as openssl returns exit code 1 and logs "poll error".
    certificate=$(openssl s_client -connect "$EXASOL_HOST:$port" < /dev/null 2>/dev/null) || true

    if [ -z "${certificate}" ]; then
        >&2 log_error "Error connecting to Exasol database at $EXASOL_HOST:$port"
        openssl s_client -connect "$EXASOL_HOST:$port" < /dev/null
        exit 1
    fi

    fingerprint=$(echo "$certificate" \
                | openssl x509 -fingerprint -sha256 -noout -in /dev/stdin \
                | sed 's/SHA256 Fingerprint=//' \
                | sed 's/://g')

    if [ -z "${fingerprint}" ]; then
        >&2 log_error "Error getting certificate from $EXASOL_HOST:$port"
        >&2 log_error "Try specifying alternative_tls_port=443"
        exit 1
    fi
    echo "$fingerprint"
}

###

check_preconditions
patch_excluded_tests

if [[ -z "${EXASOL_FINGERPRINT+x}" ]] ; then
    log_info "Getting certificate fingerprint from $EXASOL_HOST..."
    fingerprint=$(get_exasol_certificate_fingerprint)
else
    log_info "Using given certificate fingerprint $EXASOL_FINGERPRINT"
    fingerprint="$EXASOL_FINGERPRINT"
fi

log_info "Using Exasol database $EXASOL_HOST:$EXASOL_PORT with certificate fingerprint '$fingerprint'"
log_info "Starting integration tests in $metabase_dir..."
cd "$metabase_dir"

readonly dep_driver_dir="exasol/exasol-driver {:local/root \"$exasol_driver_dir\"}"
readonly dep_test_dir="exasol/exasol-tests {:local/root \"$exasol_driver_dir/test\"}"
readonly exasol_maven_repo=':mvn/repos {"Exasol" {:url "https://maven.exasol.com/artifactory/exasol-releases"}}'
readonly sdeps_option="{:deps { $dep_driver_dir $dep_test_dir } $exasol_maven_repo }"


MB_EXASOL_TEST_HOST=$EXASOL_HOST \
  MB_EXASOL_TEST_PORT=$EXASOL_PORT \
  MB_EXASOL_TEST_CERTIFICATE_FINGERPRINT=$fingerprint \
  MB_EXASOL_TEST_USER=$EXASOL_USER \
  MB_EXASOL_TEST_PASSWORD=$EXASOL_PASSWORD \
  MB_DEV_ADDITIONAL_DRIVER_MANIFEST_PATHS="$exasol_driver_dir/resources/metabase-plugin.yaml" \
  MB_ENCRYPTION_SECRET_KEY=$(openssl rand -base64 32) \
  DRIVERS=exasol \
  clojure -J-Duser.country=US -J-Duser.language=en -J-Duser.timezone=UTC \
          -Sdeps "$sdeps_option" \
          -X:dev:ci:drivers:drivers-dev:test "$@"

#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

readonly jdbc_driver_version=7.1.17

exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
metabase_dir=$(cd "$exasol_driver_dir/../metabase"; pwd)
readonly metabase_plugin_dir="$metabase_dir/plugins"
readonly skip_build=${skip_build:-false}

source "$exasol_driver_dir/scripts/common.sh"

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

install_jdbc_driver() {
    if [ -f "$metabase_plugin_dir" ]; then
        log_error "$metabase_plugin_dir exists but is a file, it should be a directory. Please delete it."
        exit 1
    fi

    if [ ! -d "$metabase_plugin_dir" ]; then
        log_info "Creating directory $metabase_plugin_dir"
        mkdir -p "$metabase_plugin_dir"
    fi

    local exasol_driver_filename="exasol-jdbc.jar"
    local exasol_driver_path="$metabase_plugin_dir/$exasol_driver_filename"
    local repo_exasol_driver_path="$HOME/.m2/repository/com/exasol/exasol-jdbc/$jdbc_driver_version/exasol-jdbc-$jdbc_driver_version.jar"
    if [ ! -f "$repo_exasol_driver_path" ]; then
        mvn org.apache.maven.plugins:maven-dependency-plugin:3.2.0:get --batch-mode \
          -Dartifact=com.exasol:exasol-jdbc:$jdbc_driver_version
    else
        log_trace "Exasol JDBC driver already exists in $repo_exasol_driver_path"
    fi
    log_info "Installing Exasol JDBC driver to $exasol_driver_path"
    cp -v "$repo_exasol_driver_path" "$exasol_driver_path"
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
                | sed 's/SHA256 Fingerprint=//i' \
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
patch_metbase_build_scripts
patch_excluded_tests
install_jdbc_driver

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
readonly sdeps_option="{:deps { $dep_driver_dir $dep_test_dir } }"


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

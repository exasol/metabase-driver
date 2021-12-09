#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

jdbc_driver_version=7.1.3
exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
metabase_dir=$(cd "$exasol_driver_dir/../metabase"; pwd)
metabase_plugin_dir="$metabase_dir/plugins/"

BLACK='\033[0;30m'
RED='\033[0;31m'
GREEN='\033[0;32m'
BROWN_ORANGE='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
LIGHT_GRAY='\033[0;37m'
DARK_GRAY='\033[1;30m'
LIGHT_RED='\033[1;31m'
LIGHT_GREEN='\033[1;32m'
YELLOW='\033[1;33m'
LIGHT_BLUE='\033[1;34m'
LIGHT_PURPLE='\033[1;35m'
LIGHT_CYAN='\033[1;36m'
WHITE='\033[1;37m'

log_color() {
    local color="$1"
    local message="$2"
    local end_color='\033[0m'
    echo -e "${color}${message}${end_color}"
}

log_error() {
    log_color "$RED" "$1"
}

log_info() {
    log_color "$GREEN" "$1"
}

log_trace() {
    log_color "$LIGHT_GREEN" "$1"
}

check_preconditions() {
    if [ ! -d "$metabase_dir" ]; then
        log_error "Metabase does not exist at $metabase_dir. Clone repo https://github.com/metabase/metabase"
        exit 1
    fi

    if [[ -z "${EXASOL_HOST+x}" || -z "${EXASOL_PORT+x}" ]]; then
        log_error "Environment variables 'EXASOL_HOST' and 'EXASOL_PORT' must be defined:"
        log_error "EXASOL_HOST=localhost EXASOL_PORT=8563 $0"
        exit 1
    fi
}

symlink_driver() {
    local symlink_target="$metabase_dir/modules/drivers/exasol"
    if [ ! -d "$symlink_target" ]; then
        log_info "Creating symlink to $symlink_target -> $exasol_driver_dir"
        ln -s "$exasol_driver_dir" "$symlink_target"
    else
        log_trace "Symlink already exists at $symlink_target"
    fi
}

patch_metabase_deps() {
    local metabase_deps="$metabase_dir/deps.edn"
    if ! grep --quiet "modules/drivers/exasol/test" "$metabase_deps"; then
        log_info "Adding dependency to $metabase_deps"
        sed --in-place 's/"modules\/drivers\/druid\/test"/"modules\/drivers\/druid\/test" "modules\/drivers\/exasol\/test"/g' "$metabase_deps"
    else
        log_trace "Dependency already added to $metabase_deps"
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

install_jdbc_driver() {
    if [ ! -d "$metabase_plugin_dir" ]; then
        log_info "Creating $metabase_plugin_dir"
        mkdir -p "$metabase_plugin_dir"
    fi

    if [ ! -f "$metabase_plugin_dir/exasol-jdbc.jar" ]; then
        log_info "Installing Exasol JDBC driver..."
        mvn org.apache.maven.plugins:maven-dependency-plugin:3.2.0:get --batch-mode \
          -DremoteRepositories=https://maven.exasol.com/artifactory/exasol-releases \
          -Dartifact=com.exasol:exasol-jdbc:$jdbc_driver_version
        cp -v "$HOME/.m2/repository/com/exasol/exasol-jdbc/$jdbc_driver_version/exasol-jdbc-$jdbc_driver_version.jar" "$metabase_plugin_dir/exasol-jdbc.jar"
    else
        log_trace "Exasol JDBC driver already exists in $metabase_plugin_dir"
    fi
}

build_and_install_driver() {
    local driver_jar="$exasol_driver_dir/target/uberjar/exasol.metabase-driver.jar"
    log_info "Building exasol driver..."
    cd "$exasol_driver_dir"
    DEBUG=1 lein uberjar
    log_info "Copy driver $driver_jar to $metabase_plugin_dir"
    cp -v "$driver_jar" "$metabase_plugin_dir"
}

get_exasol_certificate_fingerprint() {
    local fingerprint
    fingerprint=$(openssl s_client -connect "$EXASOL_HOST:$EXASOL_PORT" < /dev/null 2>/dev/null \
                | openssl x509 -fingerprint -sha256 -noout -in /dev/stdin \
                | sed 's/SHA256 Fingerprint=//' \
                | sed 's/://g') || true
    echo "$fingerprint"
}

###

check_preconditions
symlink_driver
patch_metabase_deps
patch_excluded_tests
install_jdbc_driver
build_and_install_driver
fingerprint=$(get_exasol_certificate_fingerprint)

log_info "Using Exasol database $EXASOL_HOST:$EXASOL_PORT with certificate fingerprint $fingerprint"
log_info "Starting integration tests in $metabase_dir..."
cd "$metabase_dir"
MB_EXASOL_TEST_HOST=$EXASOL_HOST \
  MB_EXASOL_TEST_PORT=$EXASOL_PORT \
  MB_EXASOL_TEST_USER=sys \
  MB_EXASOL_TEST_PASSWORD=exasol \
  MB_EXASOL_TEST_CERTIFICATE_FINGERPRINT=$fingerprint \
  DRIVERS=exasol \
  clojure -X:dev:ci:drivers:drivers-dev:test

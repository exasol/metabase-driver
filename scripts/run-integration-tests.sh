#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

jdbc_driver_version=7.1.4
exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
metabase_dir=$(cd "$exasol_driver_dir/../metabase"; pwd)
metabase_plugin_dir="$metabase_dir/plugins/"
skip_build=${skip_build:-false}

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

symlink_driver_sources() {
    local symlink_target="$metabase_dir/modules/drivers/exasol"
    if [[ -L "$symlink_target" && -d "$symlink_target" ]]; then
        log_trace "Symlink already exists at $symlink_target"
        return 0
    fi
    if [[ ! -d "$symlink_target" && ! -f "$symlink_target" ]]; then
        log_info "Creating symlink to $symlink_target -> $exasol_driver_dir"
        ln -s "$exasol_driver_dir" "$symlink_target"
        return 0
    fi

    log_error "A file or directory already exists at $symlink_target. Please delete it and try again."
    exit 1
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

#install_metabase_jar() {
#    "$exasol_driver_dir/scripts/install-metabase-jar.sh"
#}

build_and_install_driver() {
    local driver_jar="$exasol_driver_dir/target/exasol.metabase-driver.jar"
    log_info "Building exasol driver..."
    cd "$exasol_driver_dir"
    clojure -X:build :project-dir "\"$(pwd)\""
    log_info "Copy driver $driver_jar to $metabase_plugin_dir"
    cp -v "$driver_jar" "$metabase_plugin_dir"
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
symlink_driver_sources
patch_metabase_deps
patch_excluded_tests
#install_jdbc_driver
#install_metabase_jar

if [[ -z "${EXASOL_FINGERPRINT+x}" ]] ; then
    log_info "Getting certificate fingerprint from $EXASOL_HOST..."
    fingerprint=$(get_exasol_certificate_fingerprint)
else
    log_info "Using given certificate fingerprint $EXASOL_FINGERPRINT"
    fingerprint="$EXASOL_FINGERPRINT"
fi

if [ "$skip_build" == "true" ]; then
    log_error "Skipping driver build"
else
    build_and_install_driver
fi

log_info "Using Exasol database $EXASOL_HOST:$EXASOL_PORT with certificate fingerprint '$fingerprint'"
log_info "Starting integration tests in $metabase_dir..."
cd "$metabase_dir"
MB_EXASOL_TEST_HOST=$EXASOL_HOST \
  MB_EXASOL_TEST_PORT=$EXASOL_PORT \
  MB_EXASOL_TEST_CERTIFICATE_FINGERPRINT=$fingerprint \
  MB_EXASOL_TEST_USER=$EXASOL_USER \
  MB_EXASOL_TEST_PASSWORD=$EXASOL_PASSWORD \
  MB_ENCRYPTION_SECRET_KEY=$(openssl rand -base64 32) \
  DRIVERS=exasol \
  clojure -J-Duser.country=US -J-Duser.language=en -J-Duser.timezone=UTC \
          -X:dev:ci:drivers:drivers-dev:test "$@"

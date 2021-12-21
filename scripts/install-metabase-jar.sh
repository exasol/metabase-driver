#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

metabase_version=${METABASE_VERSION:-0.41.5}
metabase_sha256=${METABASE_SHA256:-0c7d71cb571354334d5f238869ac861f33a2e20d19ba434515b663b9f63e5cb9}

metabase_download_url="https://downloads.metabase.com/v$metabase_version/metabase.jar"

exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
local_maven_repo="$exasol_driver_dir/maven_repository"
installed_metabase_jar="$local_maven_repo/metabase/metabase/$metabase_version/metabase-$metabase_version.jar"

verify_checksum() {
    local file=$1
    local checksum=$2
    echo "Verifying checksum of $file..."
    if ! echo "$checksum  $file" | sha256sum --strict --check ; then
        shasum_output=($(sha256sum --binary "$file"))
        actual_sha256=${shasum_output[0]}
        echo "Checksum verification failed for $file. Expected $checksum but was $actual_sha256"
        exit 1
    fi
}

if [ ! -d "$local_maven_repo" ]; then
    mkdir -p "$local_maven_repo"
fi

if [ -f "$installed_metabase_jar" ]; then
    echo "Metabase $metabase_version already installed in $installed_metabase_jar"
    verify_checksum "$installed_metabase_jar" "$metabase_sha256"
    exit 0
fi

echo "Metabase $metabase_version not yet installed in $local_maven_repo"

temp_file="$local_maven_repo/metabase_$metabase_version.jar"

if [ -f "$temp_file" ]; then
    echo "Metabase $metabase_version already downloaded to $temp_file"
else
    echo "Downloading Metabase $metabase_version from $metabase_download_url to $temp_file..."
    http_code=$(curl "$metabase_download_url" --silent --output "$temp_file" --write-out "%{http_code}" "$@")
    if [[ ${http_code} -lt 200 || ${http_code} -gt 299 ]] ; then
        echo "Download of $metabase_download_url failed with status $http_code"
        exit 1
    fi
fi

echo "Verifying checksum..."

verify_checksum "$temp_file" "$metabase_sha256"

mvn deploy:deploy-file -Dfile="$temp_file" -Durl="file:$local_maven_repo" \
  -DgroupId=metabase -DartifactId=metabase -Dversion="$metabase_version" -Dpackaging=jar \
  --batch-mode \
  -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

rm -f "$temp_file"

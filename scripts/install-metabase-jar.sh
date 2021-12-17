#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

metabase_version=${METABASE_VERSION:-0.41.5}
metabase_sha256=${METABASE_SHA256:-0c7d71cb571354334d5f238869ac861f33a2e20d19ba434515b663b9f63e5cb9}

metabase_download_url="https://downloads.metabase.com/v$metabase_version/metabase.jar"

exasol_driver_dir="$( cd "$(dirname "$0")/.." >/dev/null 2>&1 ; pwd -P )"
local_maven_repo="$exasol_driver_dir/maven_repository"

if [ ! -d "$local_maven_repo" ]; then
    mkdir -p "$local_maven_repo"
fi

if [ -d "$local_maven_repo/metabase/metabase/$metabase_version" ]; then
    echo "Metabase $metabase_version already installed in $local_maven_repo"
    exit 0
fi

echo "Metabase $metabase_version not yet installed in $local_maven_repo"

temp_file="$local_maven_repo/metabase_$metabase_version.jar"

if [ -f "$temp_file" ]; then
    echo "Metabase $metabase_version already downloaded to $temp_file"
else
    echo "Downloading Metabase $metabase_version to $temp_file..."
    http_code=$(curl "$metabase_download_url" --silent --output "$temp_file" --write-out "%{http_code}" "$@")
    if [[ ${http_code} -lt 200 || ${http_code} -gt 299 ]] ; then
        echo "Download of $metabase_download_url failed with status $http_code"
        exit 1
    fi
fi

echo "Verifying checksum..."

if ! echo "$metabase_sha256  $temp_file" | shasum --strict --check --algorithm 256 ; then
    shasum_output=($(shasum --algorithm 256 "$temp_file"))
    actual_sha256=${shasum_output[0]}
    echo "Checksum verification failed for $temp_file. Expected $metabase_sha256 but was $actual_sha256"
    exit 1
fi

mvn deploy:deploy-file -Dfile="$temp_file" -Durl="file:$local_maven_repo" \
  -DgroupId=metabase -DartifactId=metabase -Dversion="$metabase_version" -Dpackaging=jar \
  --batch-mode \
  -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

rm -f "$temp_file"

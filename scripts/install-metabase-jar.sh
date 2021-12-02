#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

metabase_version="0.41.2"
metabase_sha256="d2303557342f3d88437d634fd38bd4b3657b7a31e5ad891e45b001801c121bf5"

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
    curl "$metabase_download_url" --output "$temp_file"
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
  -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

rm -f "$temp_file"
